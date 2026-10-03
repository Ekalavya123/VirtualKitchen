/**
 * Persisting one complete Recipe session snapshot (MAIN + every SUBPROCESS) — the single save path
 * shared by autosave and the explicit Save button (RecipeSessionContext calls it through
 * SaveCoordinator). Framework- and fetch-free: the API calls and the Action On reference
 * rewriting are injected, so it is unit-tested directly (frontEnd/tests/snapshotPersistence.test.ts).
 *
 * A save reconciles the backend's process set with the session's:
 * 1. processes in the session the backend doesn't have yet (a temporary negative id from AI
 *    generation, or a subprocess an undo removed and a redo brought back) are created first, and
 *    `onIdsAssigned` is told the new ids *immediately* — before anything else can fail — so a retry
 *    never creates them twice;
 * 2. the complete snapshot goes out in one batch update, carrying the revision it was based on
 *    (optimistic concurrency, 409 when the recipe was saved elsewhere since);
 * 3. processes the backend has but the session no longer does (an undo of AI generation or of a
 *    subprocess creation) are deleted — only after step 2 has removed every reference to them,
 *    only ones this session knows it persisted, and never a MAIN (the backend refuses that; MAIN
 *    creation is idempotent, so a later redo simply gets the same MAIN back).
 * A snapshot identical to the last persisted one (and with nothing to create/delete) is skipped.
 */

import type { Process, ProcessBatchUpdateItem, ProcessCreateRequest, ProcessType } from '../../../types/process'

export type ProcessNodesRemapper = (nodes: Process['nodes'], idMap: Map<number, number>) => Process['nodes']

export type PersistenceApi = {
  createMainProcess: () => Promise<Process>
  createProcess: (request: ProcessCreateRequest) => Promise<Process>
  updateAll: (items: ProcessBatchUpdateItem[], baseRevision: number) => Promise<{ revision: number }>
  deleteProcess: (processId: number) => Promise<void>
}

/** What this session knows the backend currently holds. Owned by the session, updated in place by `persistSnapshot`. */
export type PersistedState = {
  /** Every process id (with its type) known to exist on the backend. */
  processes: Map<number, ProcessType>
  /** The recipe's process revision the next save is based on. */
  revision: number
  /** `fingerprint` of the last snapshot the backend confirmed, null when unknown. */
  fingerprint: string | null
}

export const toBatchItems = (processes: Process[]): ProcessBatchUpdateItem[] =>
  processes.map((process) => ({
    processId: process.id,
    name: process.name,
    description: process.description,
    nodes: process.nodes,
    edges: process.edges,
    viewport: process.viewport,
  }))

/**
 * Content-only serialization: object keys sorted, null/undefined fields dropped — so the backend's
 * shape of a process (explicit nulls, its own key order) and the editor's shape of the same content
 * compare equal.
 */
const canonicalJson = (value: unknown) => JSON.stringify(value, (_key, field: unknown) => {
  if (field === null) return undefined
  if (typeof field !== 'object' || Array.isArray(field)) return field
  const sorted: Record<string, unknown> = {}
  for (const key of Object.keys(field as Record<string, unknown>).sort()) sorted[key] = (field as Record<string, unknown>)[key]
  return sorted
})

/** Fingerprint of exactly what a save sends — equal fingerprints mean nothing to save. */
export const fingerprint = (processes: Process[]) => canonicalJson(toBatchItems(processes))

export const createPersistedState = (processes: Process[], revision: number): PersistedState => ({
  processes: new Map(processes.map((process) => [process.id, process.type])),
  revision,
  fingerprint: fingerprint(processes),
})

/** Swaps process ids (and every STEP's Action On references to them) through `idMap`; untouched processes keep their identity. */
export const remapProcessIds = (processes: Process[], idMap: Map<number, number>, remapNodes: ProcessNodesRemapper): Process[] => {
  if (idMap.size === 0) return processes
  return processes.map((process) => {
    const id = idMap.get(process.id) ?? process.id
    const nodes = remapNodes(process.nodes, idMap)
    return id === process.id && nodes === process.nodes ? process : { ...process, id, nodes }
  })
}

export type PersistResult = { skipped: boolean }

export async function persistSnapshot(
  snapshot: Process[],
  state: PersistedState,
  api: PersistenceApi,
  remapNodes: ProcessNodesRemapper,
  onIdsAssigned: (idMap: Map<number, number>) => void,
): Promise<PersistResult> {
  const idMap = new Map<number, number>()
  for (const process of snapshot) {
    if (state.processes.has(process.id)) continue
    const created = process.type === 'MAIN'
      ? await api.createMainProcess()
      : await api.createProcess({ type: process.type, name: process.name, description: process.description })
    state.processes.set(created.id, created.type)
    const single = new Map([[process.id, created.id]])
    idMap.set(process.id, created.id)
    onIdsAssigned(single)
  }

  const resolved = remapProcessIds(snapshot, idMap, remapNodes)
  const keptIds = new Set(resolved.map((process) => process.id))
  const toDelete = Array.from(state.processes.entries())
    .filter(([id, type]) => !keptIds.has(id) && type !== 'MAIN')
    .map(([id]) => id)
  // A MAIN missing from the session can't be deleted (it's the recipe's linked MAIN); just stop tracking it.
  for (const [id, type] of Array.from(state.processes.entries())) {
    if (!keptIds.has(id) && type === 'MAIN') state.processes.delete(id)
  }

  const items = toBatchItems(resolved)
  const nextFingerprint = canonicalJson(items)
  if (idMap.size === 0 && toDelete.length === 0 && nextFingerprint === state.fingerprint) {
    return { skipped: true }
  }

  if (items.length > 0 && nextFingerprint !== state.fingerprint) {
    const { revision } = await api.updateAll(items, state.revision)
    state.revision = revision
    state.fingerprint = nextFingerprint
  }

  for (const id of toDelete) {
    await api.deleteProcess(id)
    state.processes.delete(id)
  }
  return { skipped: false }
}
