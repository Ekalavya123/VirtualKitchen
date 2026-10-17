import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import { withoutStrayMainProcesses } from '../src/features/recipe-tool/model/mainProcess.ts'

const process = (id: number, type: 'MAIN' | 'SUBPROCESS') => ({ id, type })

describe('withoutStrayMainProcesses', () => {
  it('keeps the MAIN process the recipe is linked to and drops a duplicate', () => {
    const kept = withoutStrayMainProcesses([process(49, 'MAIN'), process(50, 'MAIN'), process(51, 'SUBPROCESS')], 49)
    assert.deepEqual(kept.map((entry) => entry.id), [49, 51])
  })

  it('keeps the oldest MAIN when none is linked yet', () => {
    assert.deepEqual(withoutStrayMainProcesses([process(50, 'MAIN'), process(49, 'MAIN')], null).map((entry) => entry.id), [49])
  })

  it('leaves a normal recipe alone', () => {
    const processes = [process(49, 'MAIN'), process(51, 'SUBPROCESS')]
    assert.deepEqual(withoutStrayMainProcesses(processes, 99), processes)
  })
})
