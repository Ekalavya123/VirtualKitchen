import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import { SessionHistory } from '../src/features/recipe-tool/persistence/sessionHistory.ts'

const setup = (options: { limit?: number } = {}) => {
  let now = 0
  const history = new SessionHistory<string>({ coalesceMs: 1000, now: () => now, ...options })
  return { history, tick: (ms: number) => { now += ms } }
}

/** Applies an edit the way the session does: record the state before, then change it. */
const editor = (history: SessionHistory<string>, initial: string) => {
  let state = initial
  return {
    get state() { return state },
    edit(next: string, focusProcessId: number | null = 1, coalesceKey?: string) {
      history.record(state, { focusProcessId, coalesceKey })
      state = next
    },
    undo() {
      const entry = history.undo(state)
      if (entry) state = entry.snapshot
      return entry
    },
    redo() {
      const entry = history.redo(state)
      if (entry) state = entry.snapshot
      return entry
    },
  }
}

describe('SessionHistory', () => {
  it('undoes and redoes A -> B -> C -> D', () => {
    const { history } = setup()
    const doc = editor(history, 'A')
    doc.edit('B'); doc.edit('C'); doc.edit('D')

    doc.undo()
    assert.equal(doc.state, 'C')
    doc.undo()
    assert.equal(doc.state, 'B')
    doc.redo()
    assert.equal(doc.state, 'C')
    doc.redo()
    assert.equal(doc.state, 'D')
    assert.equal(history.canRedo, false)
    assert.equal(doc.redo(), null)
  })

  it('a new edit after undo clears the redo stack', () => {
    const { history } = setup()
    const doc = editor(history, 'A')
    doc.edit('B'); doc.edit('C')
    doc.undo()
    doc.edit('X')
    assert.equal(history.canRedo, false)
    doc.undo()
    assert.equal(doc.state, 'B')
  })

  it('coalesces a typing burst in one field into a single undo step', () => {
    const { history, tick } = setup()
    const doc = editor(history, '')
    for (const text of ['o', 'on', 'oni', 'onio', 'onion']) {
      doc.edit(text, 1, 'node-1:expectedOutput')
      tick(200)
    }
    doc.undo()
    assert.equal(doc.state, '')
    assert.equal(history.canUndo, false)
  })

  it('starts a new step after a pause, a different field, or an undo', () => {
    const { history, tick } = setup()
    const doc = editor(history, '')
    doc.edit('a', 1, 'f1')
    tick(1500)
    doc.edit('ab', 1, 'f1') // after a pause
    doc.edit('ab|', 1, 'f2') // other field
    doc.undo()
    doc.edit('ab!', 1, 'f2') // right after undo
    assert.equal(doc.undo()?.snapshot, 'ab')
    assert.equal(doc.undo()?.snapshot, 'a')
    assert.equal(doc.undo()?.snapshot, '')
  })

  it('remembers which process each step was made in, for both directions', () => {
    const { history } = setup()
    const doc = editor(history, 'A')
    doc.edit('B', 7)
    assert.equal(doc.undo()?.focusProcessId, 7)
    assert.equal(doc.redo()?.focusProcessId, 7)
  })

  it('drops the oldest steps beyond the limit', () => {
    const { history } = setup({ limit: 3 })
    const doc = editor(history, '0')
    for (const value of ['1', '2', '3', '4', '5']) doc.edit(value)
    let undos = 0
    while (doc.undo()) undos += 1
    assert.equal(undos, 3)
    assert.equal(doc.state, '2')
  })

  it('map rewrites stored snapshots and focus ids (temp id -> real id after a save)', () => {
    const { history } = setup()
    const doc = editor(history, 'p-1')
    doc.edit('p-1 edited', -1)
    history.map((snapshot) => snapshot.replace('p-1', 'p57'), (id) => (id === -1 ? 57 : id))
    const entry = doc.undo()
    assert.equal(entry?.snapshot, 'p57')
    assert.equal(entry?.focusProcessId, 57)
  })
})
