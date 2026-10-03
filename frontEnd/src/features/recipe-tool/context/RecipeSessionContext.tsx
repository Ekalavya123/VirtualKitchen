import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, useSyncExternalStore } from 'react'
import type { ReactNode } from 'react'
import { ProcessApi, RecipeDetailApi, httpStatusOf } from '../../../api'
import type { Process, ProcessEdge, ProcessNode, ProcessViewport } from '../../../types/process'
import { normalizeRecipeStepNodeData, withRecipeStepActionOnProcesses, withRecipeStepVisualization } from '../process/model/recipeStepData'
import { useJobTracker } from './useJobTracker'
import { SaveCoordinator, type SaveState } from '../persistence/saveCoordinator'
import { SessionHistory, type RecordOptions } from '../persistence/sessionHistory'
import { fingerprint, persistSnapshot, remapProcessIds, type PersistenceApi } from '../persistence/snapshotPersistence'
import {
  browserStorage,
  clearRecovery,
  pruneRecoveries,
  readRecovery,
  writeRecovery,
  type RecoverySnapshot,
} from '../persistence/recoveryStore'
import { mergeRecoveredProcesses, restoreSnapshot } from '../persistence/sessionSnapshot'
import { SessionStore } from '../persistence/sessionStore'
import RecoveryPrompt from '../components/RecoveryPrompt'

/**
 * A process not yet created on the backend (e.g. from AI generation — see
 * recipeProcessGenerationConverter.ts) is held in the session under a temporary
 * negative id; a real Process id is always a positive sequence number, so
 * the two can never collide.
 */
export const isPendingProcessId = (id: number) => id < 0

/** Pause in editing after which the session autosaves. */
const AUTOSAVE_DEBOUNCE_MS = 1500
/** Unsaved edits reach the local recovery copy at most this long after they happen. */
const RECOVERY_WRITE_THROTTLE_MS = 500
/** Recovery copies nobody came back for are dropped after this long. */
const RECOVERY_MAX_AGE_MS = 30 * 24 * 60 * 60 * 1000

/**
 * Rewrites every STEP node's Action On subprocess references (`processId`) through `idMap` — used
 * whenever a save creates processes for real (temporary/undone ids -> real ids), so any step that
 * referenced one of them, in any process, points at the real id. Returns `nodes` itself when
 * nothing referenced a remapped id.
 */
const remapActionOnProcessRefs = (nodes: ProcessNode[], idMap: Map<number, number>): ProcessNode[] => {
  let anyChanged = false
  const next = nodes.map((node) => {
    if (node.kind !== 'STEP') return node

    const { step } = normalizeRecipeStepNodeData(node.data)
    const references = step.actionOn.processes
    if (references.length === 0) return node

    const remapped = references.map((entry) => idMap.get(entry.processId) ?? entry.processId)
    const changed = remapped.some((id, index) => id !== references[index].processId)
    if (!changed) return node

    anyChanged = true
    return { ...node, data: withRecipeStepActionOnProcesses(node.data, remapped) as unknown as Record<string, unknown> }
  })
  return anyChanged ? next : nodes
}

type RecoveryOffer = { snapshot: RecoverySnapshot; backendChanged: boolean }

/**
 * A Recipe is ONE unified working document — MAIN and every SUBPROCESS are views of the same
 * editing session, not independent flows. This context is that session, in three layers:
 *
 * 1. **Working state** (this provider): every process loaded once via `ProcessApi.listByRecipe`,
 *    edited in memory; the UI never waits for the backend. Content lives in a plain mutable store
 *    (see SessionStore), read through stable getters; consumers that must *react* depend on
 *    `version`, which is bumped on every mutation. Undo/redo is recipe-wide
 *    (persistence/sessionHistory.ts).
 * 2. **Durable state** (the backend): every change is autosaved — debounced, single-flight,
 *    latest-snapshot, with backoff on failures (persistence/saveCoordinator.ts) — through the one
 *    save path the explicit Save button also uses (persistence/snapshotPersistence.ts), guarded by
 *    the recipe's process revision (409 when it was saved elsewhere since).
 * 3. **Crash recovery** (localStorage, persistence/recoveryStore.ts): unsaved edits are mirrored
 *    locally until a save confirms them, and offered back (RecoveryPrompt) when the recipe is
 *    reopened after a refresh, crash or closed tab.
 *
 * Nutrition is not part of the session (its own form, NutritionEditor); ingredients are derived
 * from the steps' Action On data, so they are covered by the process snapshot.
 */
export type RecipeSessionContextValue = {
  recipeId: number
  loading: boolean
  loadError: string | null
  /** False for readers: edits are never autosaved or kept for recovery. */
  canEdit: boolean
  /** Bumped on every mutation — depend on this in a useMemo/useEffect to react to changes; read current content via the getters below, not by memoizing this context's own identity. */
  version: number
  /** Current in-memory processes (MAIN + every SUBPROCESS) — always the latest, including unsaved edits. */
  getProcesses: () => Process[]
  getProcess: (processId: number) => Process | undefined
  /** True until every edit is confirmed persisted. */
  anyDirty: boolean
  /**
   * Applies an in-memory edit to one process — a field update (rename), and/or a full graph
   * replacement (nodes/edges/viewport, as RecipeProcessCanvas builds it). Schedules an autosave;
   * does not touch history — record that first with `recordHistory` when the edit is undoable.
   */
  updateProcess: (
    processId: number,
    patch: Partial<{ name: string; description?: string; nodes: ProcessNode[]; edges: ProcessEdge[]; viewport?: ProcessViewport }>,
  ) => void
  /**
   * Adds a process to the session, or replaces one with the same id (e.g. AI generation replacing
   * MAIN — the canvas re-seeds from it). Pass `{ persisted: true }` for a process just created
   * via the Process API; anything else not yet on the backend (e.g. a negative, client-temporary
   * id — see recipeProcessGenerationConverter.ts) is created for real by the next save.
   */
  addProcess: (process: Process, options?: { persisted?: boolean }) => void
  /** Drops a process from the session; the next save deletes it on the backend. */
  removeProcess: (processId: number) => void
  saveState: SaveState
  /** "Save everything now": flushes any pending autosave immediately (queued behind one in flight). */
  saveNow: () => Promise<{ ok: boolean; error: string | null }>
  /**
   * Records an undo step — call *before* applying a mutation. `before` defaults to the session's
   * current content; pass the snapshot captured at a gesture's start (drag/resize) instead.
   */
  recordHistory: (options: RecordOptions & { before?: Process[] }) => void
  undo: () => void
  redo: () => void
  canUndo: boolean
  canRedo: boolean
  /**
   * Calls `listener` whenever session content changed from outside the canvas (undo/redo,
   * recovery, AI replacing a process, ids assigned by a save) — the canvas re-seeds from the
   * session then. Returns the unsubscribe function.
   */
  subscribeReseed: (listener: () => void) => () => void
  /** Set by undo/redo to the process the restored change was made in, so the editor can show it. */
  focusRequest: { processId: number; token: number } | null
  /** Follows a temporary id to the real id a save gave it (identity for every other id). */
  resolveProcessId: (processId: number) => number
  /** After a 409: reload the latest saved recipe (dropping local edits), or overwrite it with this session. */
  resolveConflict: (choice: 'reload' | 'keepMine') => Promise<void>
}

const RecipeSessionContext = createContext<RecipeSessionContextValue | null>(null)

export function RecipeSessionProvider({ recipeId, canEdit = true, children }: { recipeId: number; canEdit?: boolean; children: ReactNode }) {
  const [store] = useState(() => new SessionStore())
  const [history] = useState(() => new SessionHistory<Process[]>())
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [loadNonce, setLoadNonce] = useState(0)
  const [version, setVersion] = useState(0)
  const [focusRequest, setFocusRequest] = useState<{ processId: number; token: number } | null>(null)
  const [historyFlags, setHistoryFlags] = useState({ canUndo: false, canRedo: false })
  const [recoveryOffer, setRecoveryOffer] = useState<RecoveryOffer | null>(null)
  const [storage] = useState(browserStorage)
  const jobTracker = useJobTracker()

  const notifyReseed = useCallback(() => store.notifyReseed(), [store])
  const subscribeReseed = useCallback((listener: () => void) => store.subscribeReseed(listener), [store])

  const syncHistoryFlags = useCallback(() => {
    setHistoryFlags((flags) => (flags.canUndo === history.canUndo && flags.canRedo === history.canRedo
      ? flags
      : { canUndo: history.canUndo, canRedo: history.canRedo }))
  }, [history])

  /** A save created processes for real: swap their temporary ids everywhere the session keeps them. */
  const applyAssignedIds = useCallback((idMap: Map<number, number>) => {
    store.setProcesses(remapProcessIds(store.processes, idMap, remapActionOnProcessRefs))
    history.map((snapshot) => remapProcessIds(snapshot, idMap, remapActionOnProcessRefs), (id) => idMap.get(id) ?? id)
    store.addAliases(idMap)
    notifyReseed()
    setVersion((v) => v + 1)
  }, [store, history, notifyReseed])

  const [coordinator] = useState(() => {
    const api: PersistenceApi = {
      createMainProcess: () => RecipeDetailApi.createMainProcess(recipeId),
      createProcess: (request) => ProcessApi.create(recipeId, request),
      updateAll: (items, baseRevision) => ProcessApi.updateAll(recipeId, items, baseRevision),
      deleteProcess: async (processId) => {
        try {
          await ProcessApi.delete(recipeId, processId)
        } catch (error) {
          if (httpStatusOf(error) !== 404) throw error // already gone is what we wanted
        }
      },
    }
    return new SaveCoordinator({
      // Reads the session's latest content at call time; persistSnapshot validates/saves it exactly
      // like an explicit Save would — there is no separate, weaker autosave path.
      save: async () => {
        await persistSnapshot(store.processes, store.persisted, api, remapActionOnProcessRefs, applyAssignedIds)
      },
      statusOf: httpStatusOf,
      autosave: canEdit,
      debounceMs: AUTOSAVE_DEBOUNCE_MS,
    })
  })
  const saveState = useSyncExternalStore(coordinator.subscribe, coordinator.getState)

  // --- local recovery copy -------------------------------------------------------------------

  const recoveryTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const recoveryWarnedRef = useRef(false)

  /** Writes the current unsaved session to localStorage (synchronously — also used on pagehide/unmount). */
  const writeRecoveryNow = useCallback(() => {
    if (recoveryTimerRef.current != null) clearTimeout(recoveryTimerRef.current)
    recoveryTimerRef.current = null
    if (!canEdit || !coordinator.hasPendingChanges()) return
    const result = writeRecovery(storage, {
      recipeId,
      writtenAt: Date.now(),
      baseRevision: store.persisted.revision,
      persistedIds: Array.from(store.persisted.processes.keys()),
      processes: store.processes,
    })
    if (!result.ok && !recoveryWarnedRef.current) {
      recoveryWarnedRef.current = true
      console.warn('Unable to keep a local recovery copy of unsaved recipe changes:', result.error)
    }
  }, [canEdit, coordinator, store, storage, recipeId])

  /** Throttled, so continuous typing still reaches the recovery copy every half second at most. */
  const noteChange = useCallback(() => {
    coordinator.markChanged()
    if (canEdit && recoveryTimerRef.current == null) {
      recoveryTimerRef.current = setTimeout(writeRecoveryNow, RECOVERY_WRITE_THROTTLE_MS)
    }
  }, [coordinator, canEdit, writeRecoveryNow])

  // Once a save confirms everything, the recovery copy is stale — drop it; if edits arrived during
  // that save, rewrite it instead (its base revision just moved on).
  // (A save's success is announced while its request is still settling, so "everything saved" is
  // only known once the status itself reaches `saved`.)
  const handledSaveRef = useRef<number | null>(null)
  useEffect(() => {
    handledSaveRef.current = coordinator.getState().lastSavedAt
    return coordinator.subscribe(() => {
      const { status, lastSavedAt } = coordinator.getState()
      if (lastSavedAt == null || lastSavedAt === handledSaveRef.current) return
      if (status === 'saving') return // wait for this save to settle
      handledSaveRef.current = lastSavedAt
      if (status === 'saved') {
        if (recoveryTimerRef.current != null) clearTimeout(recoveryTimerRef.current)
        recoveryTimerRef.current = null
        clearRecovery(storage, recipeId)
      } else {
        writeRecoveryNow()
      }
    })
  }, [coordinator, writeRecoveryNow, storage, recipeId])

  // Tab close / refresh / crash-adjacent exits: keep the unsaved work locally, warn when leaving
  // with changes not yet saved, and retry a failed save as soon as the network is back.
  useEffect(() => {
    if (!canEdit) return undefined
    const onPageHide = () => writeRecoveryNow()
    const onBeforeUnload = (event: BeforeUnloadEvent) => {
      if (!coordinator.hasPendingChanges()) return
      writeRecoveryNow()
      event.preventDefault()
    }
    const onOnline = () => coordinator.retryNow()
    window.addEventListener('pagehide', onPageHide)
    window.addEventListener('beforeunload', onBeforeUnload)
    window.addEventListener('online', onOnline)
    return () => {
      window.removeEventListener('pagehide', onPageHide)
      window.removeEventListener('beforeunload', onBeforeUnload)
      window.removeEventListener('online', onOnline)
    }
  }, [canEdit, coordinator, writeRecoveryNow])

  // Leaving the recipe inside the app (navbar Back, another route): save what's pending right
  // away; the recovery copy stays until that save is confirmed.
  useEffect(() => () => {
    writeRecoveryNow()
    const savedAtBefore = coordinator.getState().lastSavedAt
    void coordinator.flush()
      .then((ok) => {
        // Only clear when *this* flush actually saved something — StrictMode's simulated unmount
        // must not wipe a recovery copy nobody has been offered yet.
        if (ok && coordinator.getState().lastSavedAt !== savedAtBefore && !coordinator.hasPendingChanges()) {
          clearRecovery(storage, recipeId)
        }
      })
      .finally(() => coordinator.dispose())
  }, [coordinator, writeRecoveryNow, storage, recipeId])

  // --- loading -------------------------------------------------------------------------------

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setLoadError(null)
    const load = async () => {
      // The revision is read *before* the processes: a save landing between the two reads can then
      // only cause a false conflict later, never a silent overwrite.
      const revision = canEdit ? ((await RecipeDetailApi.getRecipeDetail(recipeId)).processRevision ?? 0) : 0
      const processes = await ProcessApi.listByRecipe(recipeId)
      return { revision, processes }
    }
    load()
      .then(({ revision, processes }) => {
        if (cancelled) return
        store.load(processes, revision)
        history.clear()
        coordinator.reset()
        syncHistoryFlags()
        setVersion((v) => v + 1)
        notifyReseed()

        if (canEdit) {
          pruneRecoveries(storage, RECOVERY_MAX_AGE_MS)
          const recovered = readRecovery(storage, recipeId)
          if (recovered && fingerprint(recovered.processes) === store.persisted.fingerprint) {
            clearRecovery(storage, recipeId) // the backend already has exactly this
          } else if (recovered) {
            setRecoveryOffer({ snapshot: recovered, backendChanged: recovered.baseRevision !== revision })
          }
        }
        // Re-attach to any generation/visuals job still running (or finished while the user was
        // away) — the visuals applier below then fills in the steps generated so far.
        void jobTracker.discover(recipeId)
      })
      .catch((error) => {
        if (!cancelled) setLoadError(error instanceof Error ? error.message : 'Unable to load this recipe\'s processes')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [recipeId, canEdit, jobTracker, store, coordinator, history, storage, syncHistoryFlags, notifyReseed, loadNonce])

  // Applies Generate Visuals step results to the session as they arrive, whether or not that
  // process's canvas is open — so leaving a process mid-job (or coming back after a reload) still
  // shows every image generated so far, and a later save can't send nodes missing them. Not an
  // edit: no history entry and no autosave of its own (the job already stored these images on the
  // backend); the next save simply carries them along.
  const appliedStepsRef = useRef(new Set<string>())
  useEffect(() => {
    if (loading) return undefined
    const applyVisuals = () => {
      let changed = false
      for (const tracked of jobTracker.all()) {
        if (tracked.kind !== 'visuals' || tracked.recipeId !== recipeId) continue
        const fresh = tracked.job.steps.filter((step) =>
          step.success && step.visualizationAssetId != null && !appliedStepsRef.current.has(`${tracked.job.jobId}:${step.stepId}`))
        if (fresh.length === 0) continue

        const freshByStepId = new Map(fresh.map((step) => [step.stepId, step]))
        store.setProcesses(store.processes.map((process) => {
          if (process.id !== tracked.processId) return process
          return {
            ...process,
            nodes: process.nodes.map((node) => {
              const step = freshByStepId.get(node.id)
              if (!step || node.kind !== 'STEP') return node
              return {
                ...node,
                data: withRecipeStepVisualization(node.data, {
                  assetId: step.visualizationAssetId,
                  imageUrl: step.imageUrl ?? undefined,
                  status: 'generated',
                }) as unknown as Record<string, unknown>,
              }
            }),
          }
        }))
        fresh.forEach((step) => appliedStepsRef.current.add(`${tracked.job.jobId}:${step.stepId}`))
        changed = true
      }
      if (changed) setVersion((v) => v + 1)
    }
    applyVisuals()
    return jobTracker.subscribe(applyVisuals)
  }, [loading, recipeId, jobTracker, store])

  // --- mutations -----------------------------------------------------------------------------

  const getProcesses = useCallback(() => store.processes, [store])
  const getProcess = useCallback((processId: number) => store.processes.find((process) => process.id === processId), [store])
  const resolveProcessId = useCallback((processId: number) => store.resolveId(processId), [store])

  const updateProcess = useCallback<RecipeSessionContextValue['updateProcess']>((processId, patch) => {
    const current = store.processes
    const index = current.findIndex((process) => process.id === processId)
    if (index === -1) return

    const next = current.slice()
    next[index] = { ...current[index], ...patch }
    store.setProcesses(next)
    setVersion((v) => v + 1)
    noteChange()
  }, [store, noteChange])

  const addProcess = useCallback<RecipeSessionContextValue['addProcess']>((process, options) => {
    if (options?.persisted) store.markPersisted(process)
    const current = store.processes
    const index = current.findIndex((existing) => existing.id === process.id)
    store.setProcesses(index === -1
      ? [...current, process]
      : current.map((existing, i) => (i === index ? process : existing)))
    if (index !== -1) notifyReseed()
    setVersion((v) => v + 1)
    noteChange()
  }, [store, noteChange, notifyReseed])

  const removeProcess = useCallback((processId: number) => {
    store.setProcesses(store.processes.filter((process) => process.id !== processId))
    setVersion((v) => v + 1)
    noteChange()
  }, [store, noteChange])

  // --- history -------------------------------------------------------------------------------

  const recordHistory = useCallback<RecipeSessionContextValue['recordHistory']>(({ before, ...options }) => {
    history.record(before ?? store.processes, options)
    syncHistoryFlags()
  }, [store, history, syncHistoryFlags])

  const restore = useCallback((entry: { snapshot: Process[]; focusProcessId: number | null } | null) => {
    if (!entry) return
    store.setProcesses(restoreSnapshot(entry.snapshot, store.processes))
    const focusProcessId = entry.focusProcessId
    if (focusProcessId != null) setFocusRequest((request) => ({ processId: focusProcessId, token: (request?.token ?? 0) + 1 }))
    syncHistoryFlags()
    notifyReseed()
    setVersion((v) => v + 1)
    noteChange()
  }, [store, syncHistoryFlags, noteChange, notifyReseed])

  const undo = useCallback(() => restore(history.undo(store.processes)), [store, history, restore])
  const redo = useCallback(() => restore(history.redo(store.processes)), [store, history, restore])

  // --- saving / recovery / conflicts ------------------------------------------------------------

  const saveNow = useCallback(async () => {
    const ok = await coordinator.flush()
    return { ok, error: ok ? null : coordinator.getState().error }
  }, [coordinator])

  const resolveConflict = useCallback(async (choice: 'reload' | 'keepMine') => {
    if (choice === 'reload') {
      clearRecovery(storage, recipeId)
      coordinator.reset()
      setLoadNonce((nonce) => nonce + 1)
      return
    }
    // Rebase this session onto the recipe's current revision and save it over the newer one.
    const detail = await RecipeDetailApi.getRecipeDetail(recipeId)
    store.rebase(detail.processRevision ?? 0)
    coordinator.resolveConflict()
    await coordinator.flush()
  }, [coordinator, store, storage, recipeId])

  const recoverChanges = useCallback(() => {
    if (!recoveryOffer) return
    const { snapshot } = recoveryOffer
    // Undoable like any other operation: Undo returns to what the backend had.
    history.record(store.processes, { focusProcessId: null })
    store.setProcesses(mergeRecoveredProcesses(snapshot.processes, snapshot.persistedIds, store.processes))
    setRecoveryOffer(null)
    syncHistoryFlags()
    notifyReseed()
    setVersion((v) => v + 1)
    noteChange() // autosave persists it; the recovery copy is cleared once that's confirmed
  }, [store, recoveryOffer, history, syncHistoryFlags, noteChange, notifyReseed])

  const discardRecovery = useCallback(() => {
    clearRecovery(storage, recipeId)
    setRecoveryOffer(null)
  }, [storage, recipeId])

  const value = useMemo<RecipeSessionContextValue>(() => ({
    recipeId,
    loading,
    loadError,
    canEdit,
    version,
    getProcesses,
    getProcess,
    anyDirty: saveState.status !== 'saved',
    updateProcess,
    addProcess,
    removeProcess,
    saveState,
    saveNow,
    recordHistory,
    undo,
    redo,
    canUndo: historyFlags.canUndo,
    canRedo: historyFlags.canRedo,
    subscribeReseed,
    focusRequest,
    resolveProcessId,
    resolveConflict,
  }), [recipeId, loading, loadError, canEdit, version, getProcesses, getProcess, saveState, updateProcess, addProcess, removeProcess,
    saveNow, recordHistory, undo, redo, historyFlags, subscribeReseed, focusRequest, resolveProcessId, resolveConflict])

  return (
    <RecipeSessionContext.Provider value={value}>
      {children}
      {recoveryOffer && !loading && (
        <RecoveryPrompt
          writtenAt={recoveryOffer.snapshot.writtenAt}
          backendChanged={recoveryOffer.backendChanged}
          onRecover={recoverChanges}
          onDiscard={discardRecovery}
        />
      )}
    </RecipeSessionContext.Provider>
  )
}

/** Returns null outside a provider. Every route that renders RecipeProcessCanvas wraps it in a RecipeSessionProvider (see RecipeToolPage.tsx and App.tsx's RecipeProcessEditorRoute), so this should only be null if that wiring is missing. */
export const useRecipeSession = () => useContext(RecipeSessionContext)
