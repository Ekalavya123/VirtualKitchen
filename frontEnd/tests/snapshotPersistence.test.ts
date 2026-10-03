import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import {
  createPersistedState,
  fingerprint,
  persistSnapshot,
  remapProcessIds,
  type PersistenceApi,
  type ProcessNodesRemapper,
} from '../src/features/recipe-tool/persistence/snapshotPersistence.ts'
import type { Process, ProcessBatchUpdateItem } from '../src/types/process.ts'

const process = (id: number, type: Process['type'] = 'SUBPROCESS', refs: number[] = []): Process => ({
  id,
  type,
  recipeId: 9,
  name: `P${id}`,
  nodes: [{ id: `n${id}`, kind: 'STEP', data: { refs } }],
  edges: [],
})

/** Test stand-in for remapActionOnProcessRefs: rewrites `data.refs`. */
const remapNodes: ProcessNodesRemapper = (nodes, idMap) => {
  let changed = false
  const next = nodes.map((node) => {
    const refs = (node.data?.refs as number[]) ?? []
    const mapped = refs.map((ref) => idMap.get(ref) ?? ref)
    if (mapped.every((ref, i) => ref === refs[i])) return node
    changed = true
    return { ...node, data: { ...node.data, refs: mapped } }
  })
  return changed ? next : nodes
}

const fakeApi = (options: { failUpdate?: Error; failDelete?: Error } = {}) => {
  let nextId = 100
  let revision = 4
  const calls: string[] = []
  const updates: { items: ProcessBatchUpdateItem[]; baseRevision: number }[] = []
  const api: PersistenceApi = {
    createMainProcess: async () => {
      calls.push('createMain')
      return process(nextId++, 'MAIN')
    },
    createProcess: async (request) => {
      calls.push(`create:${request.name}`)
      return { ...process(nextId++), name: request.name }
    },
    updateAll: async (items, baseRevision) => {
      calls.push('updateAll')
      if (options.failUpdate) throw options.failUpdate
      updates.push({ items, baseRevision })
      revision += 1
      return { revision }
    },
    deleteProcess: async (id) => {
      calls.push(`delete:${id}`)
      if (options.failDelete) throw options.failDelete
    },
  }
  return { api, calls, updates }
}

describe('persistSnapshot', () => {
  it('sends the complete snapshot (every process) in one batch with the base revision', async () => {
    const loaded = [process(1, 'MAIN'), process(2), process(3)]
    const state = createPersistedState(loaded, 4)
    const { api, calls, updates } = fakeApi()
    const edited = loaded.map((p) => ({ ...p, name: `${p.name}!` }))

    const result = await persistSnapshot(edited, state, api, remapNodes, () => {})

    assert.equal(result.skipped, false)
    assert.deepEqual(calls, ['updateAll'])
    assert.deepEqual(updates[0].items.map((item) => item.processId), [1, 2, 3])
    assert.equal(updates[0].baseRevision, 4)
    assert.equal(state.revision, 5)
    assert.equal(state.fingerprint, fingerprint(edited))
  })

  it('skips the request when nothing changed since the last confirmed save', async () => {
    const loaded = [process(1, 'MAIN'), process(2)]
    const state = createPersistedState(loaded, 4)
    const { api, calls } = fakeApi()
    // New array/object identities but the same content (e.g. a selection-only canvas change).
    const same = loaded.map((p) => ({ ...p }))

    assert.equal((await persistSnapshot(same, state, api, remapNodes, () => {})).skipped, true)
    assert.deepEqual(calls, [])
  })

  it('fingerprints content, not serialization: backend nulls and key order compare equal', () => {
    const fromBackend = { ...process(2), viewport: { zoom: 1, y: 0, x: 0 }, nodes: [{ id: 'n2', kind: 'STEP' as const, extent: null as unknown as undefined, data: { refs: [] } }] }
    const fromEditor = { ...process(2), viewport: { x: 0, y: 0, zoom: 1 }, nodes: [{ data: { refs: [] }, kind: 'STEP' as const, id: 'n2' }] }
    assert.equal(fingerprint([fromBackend]), fingerprint([fromEditor]))
    assert.notEqual(fingerprint([fromBackend]), fingerprint([{ ...fromEditor, name: 'renamed' }]))
  })

  it('creates pending processes first, reports the new ids at once, and remaps references', async () => {
    const main = process(1, 'MAIN', [-1, -2])
    const state = createPersistedState([main], 4)
    const snapshot = [main, process(-1, 'SUBPROCESS', [-2]), process(-2)]
    const { api, calls, updates } = fakeApi()
    const assigned: [number, number][] = []

    await persistSnapshot(snapshot, state, api, remapNodes, (idMap) => assigned.push(...idMap.entries()))

    assert.deepEqual(calls, ['create:P-1', 'create:P-2', 'updateAll'])
    assert.deepEqual(assigned, [[-1, 100], [-2, 101]])
    const sent = updates[0].items
    assert.deepEqual(sent.map((item) => item.processId), [1, 100, 101])
    assert.deepEqual(sent[0].nodes?.[0].data?.refs, [100, 101], 'an existing process referencing new ones is remapped too')
    assert.deepEqual(sent[1].nodes?.[0].data?.refs, [101])
    assert.deepEqual(Array.from(state.processes.keys()).sort(), [1, 100, 101])
  })

  it('a failure after creating processes does not create them again on retry', async () => {
    const state = createPersistedState([process(1, 'MAIN')], 4)
    let snapshot = [process(1, 'MAIN'), process(-1)]
    const failing = fakeApi({ failUpdate: new Error('offline') })
    // The session applies reported ids to its live state, as RecipeSessionContext does.
    const applyIds = (idMap: Map<number, number>) => { snapshot = remapProcessIds(snapshot, idMap, remapNodes) }

    await assert.rejects(persistSnapshot(snapshot, state, failing.api, remapNodes, applyIds), /offline/)
    assert.deepEqual(failing.calls, ['create:P-1', 'updateAll'])

    const retry = fakeApi()
    await persistSnapshot(snapshot, state, retry.api, remapNodes, applyIds)
    assert.deepEqual(retry.calls, ['updateAll'], 'no second create on retry')
    assert.equal(state.revision, 4 + 1, 'a failed update never advanced the revision')
  })

  it('deletes processes the session removed (undo of AI generation), after the batch update', async () => {
    const loaded = [process(1, 'MAIN', [2]), process(2), process(3)]
    const state = createPersistedState(loaded, 4)
    const { api, calls } = fakeApi()

    await persistSnapshot([process(1, 'MAIN'), process(3)], state, api, remapNodes, () => {})

    assert.deepEqual(calls, ['updateAll', 'delete:2'])
    assert.deepEqual(Array.from(state.processes.keys()).sort(), [1, 3])
  })

  it('never deletes a MAIN, and never anything it did not know was persisted', async () => {
    const state = createPersistedState([process(1, 'MAIN'), process(2)], 4)
    const { api, calls } = fakeApi()

    // MAIN gone from the session (undo of a generation that created it); 77 was never persisted by us.
    await persistSnapshot([process(2)], state, api, remapNodes, () => {})

    assert.ok(!calls.some((call) => call.startsWith('delete')))
    assert.equal(state.processes.has(1), false, 'just stops tracking it')
  })

  it('retries a failed delete without re-sending the unchanged batch', async () => {
    const state = createPersistedState([process(1, 'MAIN'), process(2)], 4)
    const snapshot = [process(1, 'MAIN')]
    const failing = fakeApi({ failDelete: new Error('offline') })
    await assert.rejects(persistSnapshot(snapshot, state, failing.api, remapNodes, () => {}))
    assert.deepEqual(failing.calls, ['updateAll', 'delete:2'])

    const retry = fakeApi()
    await persistSnapshot(snapshot, state, retry.api, remapNodes, () => {})
    assert.deepEqual(retry.calls, ['delete:2'])
  })

  it('a redo of an undone, since-deleted subprocess creates it again under a new id', async () => {
    const state = createPersistedState([process(1, 'MAIN')], 4) // 57 was deleted by an earlier save
    const { api, calls } = fakeApi()
    const assigned: [number, number][] = []

    await persistSnapshot([process(1, 'MAIN', [57]), process(57)], state, api, remapNodes, (idMap) => assigned.push(...idMap.entries()))

    assert.deepEqual(calls, ['create:P57', 'updateAll'])
    assert.deepEqual(assigned, [[57, 100]])
  })
})
