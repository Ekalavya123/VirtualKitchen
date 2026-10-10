import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import {
  allowedViewsForStatus,
  buildOrderStepper,
  defaultViewForStatus,
  isRecipeOrderView,
  recipeOrderPath,
  resolveOrderView,
} from '../src/features/recipe-order/model/orderView.ts'
import type { RecipeOrderStatus } from '../src/types/recipeOrder.ts'

const ALL_STATUSES: RecipeOrderStatus[] = [
  'DRAFT', 'AWAITING_INGREDIENTS', 'RESERVING', 'AWAITING_PAYMENT', 'INVENTORY_CONFLICT', 'PAYMENT_FAILED',
  'CONFIRMED', 'PREPARING', 'QUALITY_CHECK', 'OUT_FOR_DELIVERY', 'COMPLETING', 'COMPLETED', 'CANCELLED',
]

describe('recipe order default view', () => {
  it('sends each status to the page that handles it', () => {
    assert.equal(defaultViewForStatus('DRAFT'), 'confirm')
    assert.equal(defaultViewForStatus('AWAITING_INGREDIENTS'), 'ingredients')
    assert.equal(defaultViewForStatus('INVENTORY_CONFLICT'), 'ingredients')
    assert.equal(defaultViewForStatus('RESERVING'), 'ingredients')
    assert.equal(defaultViewForStatus('AWAITING_PAYMENT'), 'payment')
    assert.equal(defaultViewForStatus('PAYMENT_FAILED'), 'payment')
    assert.equal(defaultViewForStatus('CONFIRMED'), 'receipt')
    assert.equal(defaultViewForStatus('PREPARING'), 'track')
    assert.equal(defaultViewForStatus('OUT_FOR_DELIVERY'), 'track')
    assert.equal(defaultViewForStatus('COMPLETED'), 'track')
    assert.equal(defaultViewForStatus('CANCELLED'), 'receipt')
  })

  it('always allows the default view', () => {
    for (const status of ALL_STATUSES) {
      assert.ok(allowedViewsForStatus(status).includes(defaultViewForStatus(status)), status)
    }
  })
})

describe('recipe order view resolution', () => {
  it('keeps an allowed view', () => {
    assert.deepEqual(resolveOrderView('AWAITING_INGREDIENTS', 'confirm'), { view: 'confirm', redirect: false })
    assert.deepEqual(resolveOrderView('PAYMENT_FAILED', 'ingredients'), { view: 'ingredients', redirect: false })
    assert.deepEqual(resolveOrderView('PREPARING', 'receipt'), { view: 'receipt', redirect: false })
  })

  it('redirects a missing, unknown or out-of-date view to the default', () => {
    assert.deepEqual(resolveOrderView('DRAFT', undefined), { view: 'confirm', redirect: true })
    assert.deepEqual(resolveOrderView('DRAFT', 'nonsense'), { view: 'confirm', redirect: true })
    // Paid: the payment form is gone, the receipt shows instead.
    assert.deepEqual(resolveOrderView('CONFIRMED', 'payment'), { view: 'receipt', redirect: true })
    // Reserved: back to the ingredient check is no longer possible.
    assert.deepEqual(resolveOrderView('AWAITING_PAYMENT', 'ingredients'), { view: 'payment', redirect: true })
    // Not paid yet: nothing to track.
    assert.deepEqual(resolveOrderView('AWAITING_PAYMENT', 'track'), { view: 'payment', redirect: true })
    assert.deepEqual(resolveOrderView('CANCELLED', 'track'), { view: 'receipt', redirect: true })
  })

  it('recognises views and builds paths', () => {
    assert.equal(isRecipeOrderView('track'), true)
    assert.equal(isRecipeOrderView('tracking'), false)
    assert.equal(recipeOrderPath(7), '/kitchen/recipe-orders/7')
    assert.equal(recipeOrderPath(7, 'payment'), '/kitchen/recipe-orders/7/payment')
  })
})

describe('recipe order stepper', () => {
  it('marks finished steps done and the open view current', () => {
    const steps = buildOrderStepper('AWAITING_PAYMENT', 'payment')
    assert.deepEqual(steps.map((step) => step.state), ['done', 'done', 'current', 'upcoming', 'upcoming'])
    assert.deepEqual(steps.map((step) => step.navigable), [false, false, false, false, false])
  })

  it('lets a declined payment go back to earlier steps', () => {
    const steps = buildOrderStepper('PAYMENT_FAILED', 'payment')
    assert.deepEqual(steps.map((step) => step.navigable), [true, true, false, false, false])
  })

  it('offers receipt and tracking once paid', () => {
    const steps = buildOrderStepper('PREPARING', 'track')
    assert.deepEqual(steps.map((step) => step.state), ['done', 'done', 'done', 'available', 'current'])
    assert.equal(steps[3].navigable, true)
  })
})
