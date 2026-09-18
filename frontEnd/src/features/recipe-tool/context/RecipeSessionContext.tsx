import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { ProcessApi, RecipeDetailApi } from '../../../api'
import type { Process, ProcessEdge, ProcessNode, ProcessViewport } from '../../../types/process'
import { normalizeProcessStepNodeData, withProcessStepActionOnProcesses } from '../../flow-editor/model/processStepData'

/**
 * A process not yet created on the backend (e.g. from AI generation — see
 * processGenerationConverter.ts) is held in the session under a temporary
 * negative id; a real Process id is always a positive sequence number, so
 * the two can never collide.
 */
export const isPendingProcessId = (id: number) => id < 0

/**
 * Rewrites every STEP node's Action On subprocess references (`processId`) through `idMap` —
 * used only right after `saveAll` creates pending (temp-id) processes for real, so any step that
 * referenced one of those temp ids (from any process, not only the ones that were themselves
 * pending) points at the real id before the save's batch update goes out.
 */
const remapActionOnProcessRefs = (nodes: ProcessNode[], idMap: Map<number, number>): ProcessNode[] =>
  nodes.map((node) => {
    if (node.kind !== 'STEP') return node

    const { step } = normalizeProcessStepNodeData(node.data)
    const references = step.actionOn.processes
    if (references.length === 0) return node

    const remapped = references.map((entry) => idMap.get(entry.processId) ?? entry.processId)
    const changed = remapped.some((id, index) => id !== references[index].processId)
    if (!changed) return node

    return { ...node, data: withProcessStepActionOnProcesses(node.data, remapped) as unknown as Record<string, unknown> }
  })

/**
 * A Recipe is ONE unified working document — recipe metadata, ingredients,
 * nutrition, the MAIN process, and every SUBPROCESS are different views of
 * the same editing session, not independent flows. This context is that
 * session's process-data half (recipe metadata/ingredients/nutrition keep
 * their own already-working save flows — see RecipeToolPage/NutritionSection
 * — this covers what was previously the *independent-per-process* part:
 * loading every process once, keeping every process's current in-memory
 * edits regardless of which one is currently displayed, and saving all of
 * them as one recipe-level operation).
 *
 * Loaded once per provider instance via `ProcessApi.listByRecipe` (already
 * returns full nodes/edges/viewport per process — no need to re-fetch a
 * process individually just to open/display it). Process content lives in a
 * ref (not reactive state) so that ProcessCanvas can write to it on every
 * keystroke without forcing every consumer (the process list sidebar, the
 * Ingredients view) to re-render on every keystroke via prop/array identity
 * churn; the exposed getters are stable functions that always read the
 * latest ref content. Consumers that need to *react* to changes (sidebar,
 * Ingredients) depend on `version`, which is bumped on every mutation —
 * mirrors the same ref+version pattern the now-superseded
 * ProcessLiveGraphContext used.
 */
export type RecipeSessionContextValue = {
  recipeId: number
  loading: boolean
  loadError: string | null
  /** Bumped on every mutation (updateProcess/addProcess/removeProcess) and after a successful save — depend on this in a useMemo/useEffect to react to changes; read current content via the getters below, not by memoizing this context's own identity. */
  version: number
  /** Current in-memory processes (MAIN + every SUBPROCESS) — always the latest, including unsaved edits. */
  getProcesses: () => Process[]
  getProcess: (processId: number) => Process | undefined
  isProcessDirty: (processId: number) => boolean
  anyDirty: boolean
  /**
   * Applies an in-memory edit to one process — a field update (rename), and/or a full graph
   * replacement (nodes/edges/viewport, as ProcessCanvas already builds for a save). Marks that
   * process dirty; does not touch the backend.
   */
  updateProcess: (
    processId: number,
    patch: Partial<{ name: string; description?: string; nodes: ProcessNode[]; edges: ProcessEdge[]; viewport?: ProcessViewport }>,
  ) => void
  /**
   * Registers a process into the session without a re-fetch — either already persisted (e.g. just
   * created or copied via the Process API) or still pending (a negative, client-temporary id, e.g.
   * freshly AI-generated and not yet saved — see processGenerationConverter.ts). `saveAll` creates
   * any pending process for real (and resolves any Action On subprocess reference pointing at its
   * temp id) before persisting content.
   */
  addProcess: (process: Process) => void
  /** Drops a deleted process from the session. */
  removeProcess: (processId: number) => void
  saving: boolean
  saveError: string | null
  /** Persists the complete current snapshot (every process currently in the session) in one call. */
  saveAll: () => Promise<void>
}

const RecipeSessionContext = createContext<RecipeSessionContextValue | null>(null)

export function RecipeSessionProvider({ recipeId, children }: { recipeId: number; children: ReactNode }) {
  const processesRef = useRef<Process[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [version, setVersion] = useState(0)
  const [dirtyIds, setDirtyIds] = useState<Set<number>>(new Set())
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setLoadError(null)
    ProcessApi.listByRecipe(recipeId)
      .then((processes) => {
        if (cancelled) return
        processesRef.current = processes
        setDirtyIds(new Set())
        setVersion((v) => v + 1)
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
  }, [recipeId])

  const getProcesses = useCallback(() => processesRef.current, [])
  const getProcess = useCallback((processId: number) => processesRef.current.find((process) => process.id === processId), [])

  const updateProcess = useCallback<RecipeSessionContextValue['updateProcess']>((processId, patch) => {
    const current = processesRef.current
    const index = current.findIndex((process) => process.id === processId)
    if (index === -1) return

    const next = current.slice()
    next[index] = { ...current[index], ...patch }
    processesRef.current = next

    setDirtyIds((ids) => (ids.has(processId) ? ids : new Set(ids).add(processId)))
    setVersion((v) => v + 1)
  }, [])

  const addProcess = useCallback((process: Process) => {
    const current = processesRef.current
    const index = current.findIndex((existing) => existing.id === process.id)
    processesRef.current = index === -1
      ? [...current, process]
      : current.map((existing, i) => (i === index ? process : existing))
    setVersion((v) => v + 1)
  }, [])

  const removeProcess = useCallback((processId: number) => {
    processesRef.current = processesRef.current.filter((process) => process.id !== processId)
    setDirtyIds((ids) => {
      if (!ids.has(processId)) return ids
      const next = new Set(ids)
      next.delete(processId)
      return next
    })
    setVersion((v) => v + 1)
  }, [])

  const isProcessDirty = useCallback((processId: number) => dirtyIds.has(processId), [dirtyIds])

  const saveAll = useCallback(async () => {
    setSaving(true)
    setSaveError(null)
    try {
      const current = processesRef.current

      // Any process still under a temporary (negative) id — e.g. AI-generated and never saved —
      // must be created for real first: the recipe-level batch update below only ever updates
      // existing processes by id. A pending MAIN goes through the same idempotent "ensure a MAIN
      // exists" endpoint the manual "Create Main Process" flow uses (also wires up the recipe's
      // mainProcessId); by construction a pending MAIN only ever occurs when the recipe had none
      // yet (see processGenerationConverter.ts's caller), so this is always the correct call here.
      const idMap = new Map<number, number>()
      for (const process of current) {
        if (!isPendingProcessId(process.id)) continue
        const created = process.type === 'MAIN'
          ? await RecipeDetailApi.createMainProcess(recipeId)
          : await ProcessApi.create(recipeId, { type: process.type, name: process.name, description: process.description })
        idMap.set(process.id, created.id)
      }

      // Every process's nodes are remapped (not just the ones that were themselves pending) since
      // an already-existing process's step could reference a subprocess that only just got a real id.
      const resolved = idMap.size === 0
        ? current
        : current.map((process) => ({
            ...process,
            id: idMap.get(process.id) ?? process.id,
            nodes: remapActionOnProcessRefs(process.nodes, idMap),
          }))

      const items = resolved.map((process) => ({
        processId: process.id,
        name: process.name,
        description: process.description,
        nodes: process.nodes,
        edges: process.edges,
        viewport: process.viewport,
      }))
      const saved = await ProcessApi.updateAll(recipeId, items)
      processesRef.current = saved
      setDirtyIds(new Set())
      setVersion((v) => v + 1)
    } catch (error) {
      setSaveError(error instanceof Error ? error.message : 'Unable to save this recipe right now')
      throw error
    } finally {
      setSaving(false)
    }
  }, [recipeId])

  const value = useMemo<RecipeSessionContextValue>(() => ({
    recipeId,
    loading,
    loadError,
    version,
    getProcesses,
    getProcess,
    isProcessDirty,
    anyDirty: dirtyIds.size > 0,
    updateProcess,
    addProcess,
    removeProcess,
    saving,
    saveError,
    saveAll,
  }), [recipeId, loading, loadError, version, getProcesses, getProcess, isProcessDirty, dirtyIds, updateProcess, addProcess, removeProcess, saving, saveError, saveAll])

  return <RecipeSessionContext.Provider value={value}>{children}</RecipeSessionContext.Provider>
}

/** Returns null outside a provider. Every route that renders ProcessCanvas wraps it in a RecipeSessionProvider (see RecipeToolPage.tsx and App.tsx's ProcessEditorRoute), so this should only be null if that wiring is missing. */
export const useRecipeSession = () => useContext(RecipeSessionContext)
