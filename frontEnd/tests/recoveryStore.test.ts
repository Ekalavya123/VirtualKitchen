import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import {
  clearRecovery,
  pruneRecoveries,
  readRecovery,
  recoveryKey,
  writeRecovery,
  type StorageLike,
} from '../src/features/recipe-tool/persistence/recoveryStore.ts'
import { mergeRecoveredProcesses, restoreSnapshot } from '../src/features/recipe-tool/persistence/sessionSnapshot.ts'
import type { Process } from '../src/types/process.ts'

class MemoryStorage implements StorageLike {
  private items = new Map<string, string>()
  quotaBytes = Infinity
  get length() { return this.items.size }
  key(index: number) { return Array.from(this.items.keys())[index] ?? null }
  getItem(key: string) { return this.items.get(key) ?? null }
  setItem(key: string, value: string) {
    if (value.length > this.quotaBytes) throw new Error('QuotaExceededError')
    this.items.set(key, value)
  }
  removeItem(key: string) { this.items.delete(key) }
}

const process = (id: number, extra: Partial<Process> = {}): Process => ({
  id, type: id === 1 ? 'MAIN' : 'SUBPROCESS', recipeId: 9, name: `P${id}`, nodes: [], edges: [], ...extra,
})

const snapshot = (recipeId: number, writtenAt = 1000) => ({
  recipeId, writtenAt, baseRevision: 3, persistedIds: [1, 2], processes: [process(1), process(-1)],
})

describe('recoveryStore', () => {
  it('round-trips a snapshot under a recipe-scoped key', () => {
    const storage = new MemoryStorage()
    assert.deepEqual(writeRecovery(storage, snapshot(9)), { ok: true })
    assert.ok(storage.getItem(recoveryKey(9)))

    const read = readRecovery(storage, 9)
    assert.equal(read?.schema, 1)
    assert.deepEqual(read?.processes.map((p) => p.id), [1, -1])
    assert.equal(readRecovery(storage, 10), null)
  })

  it('clearing one recipe leaves other recipes alone', () => {
    const storage = new MemoryStorage()
    writeRecovery(storage, snapshot(9))
    writeRecovery(storage, snapshot(10))
    clearRecovery(storage, 9)
    assert.equal(readRecovery(storage, 9), null)
    assert.ok(readRecovery(storage, 10))
  })

  it('treats corrupt JSON, another schema version or a wrong shape as none, and removes it', () => {
    const storage = new MemoryStorage()
    for (const raw of ['{not json', JSON.stringify({ ...snapshot(9), schema: 2 }), JSON.stringify({ ...snapshot(9), processes: [{ id: 'x' }] }), JSON.stringify({ ...snapshot(8) })]) {
      storage.setItem(recoveryKey(9), raw)
      assert.equal(readRecovery(storage, 9), null)
      assert.equal(storage.getItem(recoveryKey(9)), null)
    }
  })

  it('reports quota errors instead of throwing', () => {
    const storage = new MemoryStorage()
    storage.quotaBytes = 10
    const result = writeRecovery(storage, snapshot(9))
    assert.equal(result.ok, false)
    assert.equal(readRecovery(storage, 9), null)
  })

  it('works without any storage at all', () => {
    assert.equal(writeRecovery(null, snapshot(9)).ok, false)
    assert.equal(readRecovery(null, 9), null)
    clearRecovery(null, 9)
    pruneRecoveries(null, 1)
  })

  it('prunes snapshots older than the max age and unreadable ones, keeping unrelated keys', () => {
    const storage = new MemoryStorage()
    writeRecovery(storage, snapshot(1, 0))
    writeRecovery(storage, snapshot(2, 9000))
    storage.setItem(recoveryKey(3), 'garbage')
    storage.setItem('theme', 'dark')

    pruneRecoveries(storage, 5000, 10000)

    assert.equal(storage.getItem(recoveryKey(1)), null)
    assert.ok(storage.getItem(recoveryKey(2)))
    assert.equal(storage.getItem(recoveryKey(3)), null)
    assert.equal(storage.getItem('theme'), 'dark')
  })
})

describe('mergeRecoveredProcesses', () => {
  it('keeps backend processes the recovery never knew about, but not ones it removed', () => {
    const recovered = [process(1), process(-1)]
    const backend = [process(1), process(2), process(5)] // 2 removed by the user before the crash; 5 created elsewhere later
    const merged = mergeRecoveredProcesses(recovered, [1, 2], backend)
    assert.deepEqual(merged.map((p) => p.id), [1, -1, 5])
  })
})

describe('restoreSnapshot', () => {
  it('restores content but keeps the current viewport and generated step images', () => {
    const before: Process = process(2, {
      name: 'old name',
      viewport: { x: 0, y: 0, zoom: 1 },
      nodes: [{ id: 's1', kind: 'STEP', data: { title: 'old' } }],
    })
    const current: Process = process(2, {
      name: 'new name',
      viewport: { x: 50, y: 10, zoom: 2 },
      nodes: [{ id: 's1', kind: 'STEP', data: { title: 'new', visualization: { imageUrl: 'https://cdn/x.png' } } }],
    })

    const [restored] = restoreSnapshot([before], [current])

    assert.equal(restored.name, 'old name')
    assert.deepEqual(restored.viewport, { x: 50, y: 10, zoom: 2 })
    assert.equal(restored.nodes[0].data?.title, 'old')
    assert.deepEqual(restored.nodes[0].data?.visualization, { imageUrl: 'https://cdn/x.png' })
  })

  it('returns untouched processes by identity and drops processes absent from the snapshot', () => {
    const shared = process(1)
    const restored = restoreSnapshot([shared], [shared, process(-1)])
    assert.equal(restored[0], shared)
    assert.equal(restored.length, 1)
  })
})
