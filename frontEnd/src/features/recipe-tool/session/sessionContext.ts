import { createContext, useContext } from 'react'
import type { Process, ProcessEdge, ProcessNode, ProcessViewport } from '../../../types/process'
import type { SaveState } from './persistence/saveCoordinator'
import type { RecordOptions } from './persistence/sessionHistory'

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
   * The recipe process revision of the last confirmed save. Right after a successful `saveNow` it
   * identifies exactly what the backend holds (e.g. what the user approved for AI Recipe Creation).
   */
  getPersistedRevision: () => number
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

export const RecipeSessionContext = createContext<RecipeSessionContextValue | null>(null)

/** Returns null outside a provider. Every route that renders RecipeProcessCanvas wraps it in a RecipeSessionProvider (see RecipeToolPage.tsx and the app's RecipeProcessEditorRoute), so this should only be null if that wiring is missing. */
export const useRecipeSession = () => useContext(RecipeSessionContext)
