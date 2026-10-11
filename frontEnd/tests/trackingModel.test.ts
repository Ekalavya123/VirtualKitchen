import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import {
  canAdvanceStatus,
  deriveTrackingStages,
  isTrackableStatus,
  nextActionLabel,
  trackingProgressPercent,
} from '../src/features/recipe-order/model/trackingModel.ts'

const states = (...args: Parameters<typeof deriveTrackingStages>) => deriveTrackingStages(...args).map((stage) => stage.state)

describe('recipe order tracking stages', () => {
  it('starts at "order confirmed" once paid', () => {
    assert.deepEqual(states('CONFIRMED', null, 4), ['current', 'upcoming', 'upcoming', 'upcoming', 'upcoming', 'upcoming'])
  })

  it('treats preparation as ingredients prepared and cooking in progress, with the step', () => {
    const stages = deriveTrackingStages('PREPARING', 2, 4)
    assert.deepEqual(stages.map((stage) => stage.state), ['done', 'done', 'current', 'upcoming', 'upcoming', 'upcoming'])
    assert.deepEqual(stages.find((stage) => stage.id === 'cooking')?.stepProgress, { index: 2, total: 4 })
  })

  it('clamps an out-of-range step index', () => {
    assert.deepEqual(deriveTrackingStages('PREPARING', 9, 3)[2].stepProgress, { index: 2, total: 3 })
    assert.deepEqual(deriveTrackingStages('PREPARING', null, 3)[2].stepProgress, { index: 0, total: 3 })
  })

  it('moves through quality check and delivery', () => {
    assert.deepEqual(states('QUALITY_CHECK', null, 4), ['done', 'done', 'done', 'current', 'upcoming', 'upcoming'])
    assert.deepEqual(states('OUT_FOR_DELIVERY', null, 4), ['done', 'done', 'done', 'done', 'current', 'upcoming'])
    assert.deepEqual(states('COMPLETING', null, 4), ['done', 'done', 'done', 'done', 'done', 'current'])
  })

  it('is all done once completed, with no step progress', () => {
    const stages = deriveTrackingStages('COMPLETED', null, 4)
    assert.ok(stages.every((stage) => stage.state === 'done'))
    assert.equal(stages[2].stepProgress, undefined)
    assert.equal(trackingProgressPercent(stages), 100)
  })

  it('does not track unpaid or cancelled orders', () => {
    for (const status of ['DRAFT', 'AWAITING_PAYMENT', 'PAYMENT_FAILED', 'CANCELLED'] as const) {
      assert.equal(isTrackableStatus(status), false)
      assert.ok(states(status, null, 4).every((state) => state === 'upcoming'))
    }
  })

  it('handles a recipe without steps', () => {
    assert.equal(deriveTrackingStages('PREPARING', 0, 0)[2].stepProgress, undefined)
    assert.deepEqual(states('QUALITY_CHECK', null, 0), ['done', 'done', 'done', 'current', 'upcoming', 'upcoming'])
  })
})

describe('recipe order simulation controls', () => {
  it('labels the next action', () => {
    assert.equal(nextActionLabel('CONFIRMED', null, 3), 'Start simulation')
    assert.equal(nextActionLabel('PREPARING', 0, 3), 'Next step')
    assert.equal(nextActionLabel('PREPARING', 2, 3), 'Finish cooking')
    assert.equal(nextActionLabel('QUALITY_CHECK', null, 3), 'Send out for delivery')
    assert.equal(nextActionLabel('OUT_FOR_DELIVERY', null, 3), 'Mark as delivered')
    assert.equal(nextActionLabel('COMPLETED', null, 3), null)
    assert.equal(nextActionLabel('CANCELLED', null, 3), null)
  })

  it('only advances paid, unfinished orders', () => {
    assert.equal(canAdvanceStatus('CONFIRMED'), true)
    assert.equal(canAdvanceStatus('COMPLETING'), true)
    assert.equal(canAdvanceStatus('COMPLETED'), false)
    assert.equal(canAdvanceStatus('AWAITING_PAYMENT'), false)
  })

  it('reports partial progress', () => {
    assert.equal(trackingProgressPercent(deriveTrackingStages('CONFIRMED', null, 2)), 8)
    assert.equal(trackingProgressPercent(deriveTrackingStages('DRAFT', null, 2)), 0)
  })
})
