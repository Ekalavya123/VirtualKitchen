/**
 * Which page of a recipe order (`/kitchen/recipe-orders/:orderId/:view`) may show for the order's
 * status, and where a missing or out-of-date view goes instead. The server owns the status; this
 * only maps it to screens, so the URL can never show e.g. the payment form for an order that is
 * already paid.
 *
 * Pure and import-free (types only), so node:test covers it directly.
 */

import type { RecipeOrderStatus } from '../../../types/recipeOrder'

export const RECIPE_ORDER_VIEWS = ['confirm', 'ingredients', 'payment', 'receipt', 'track'] as const

export type RecipeOrderView = (typeof RECIPE_ORDER_VIEWS)[number]

export const isRecipeOrderView = (value: unknown): value is RecipeOrderView =>
  typeof value === 'string' && (RECIPE_ORDER_VIEWS as readonly string[]).includes(value)

export const recipeOrderPath = (orderId: number, view?: RecipeOrderView) =>
  view ? `/kitchen/recipe-orders/${orderId}/${view}` : `/kitchen/recipe-orders/${orderId}`

/** Statuses whose order has been paid (CONFIRMED and everything the simulation moves it through). */
export const PAID_STATUSES: readonly RecipeOrderStatus[] = [
  'CONFIRMED', 'PREPARING', 'QUALITY_CHECK', 'OUT_FOR_DELIVERY', 'COMPLETING', 'COMPLETED',
]

export const isPaidStatus = (status: RecipeOrderStatus) => PAID_STATUSES.includes(status)

/**
 * The views each status may show. Details stay editable (so the confirm page stays reachable) until
 * ingredients are reserved for payment; PAYMENT_FAILED may go back to the ingredient check, since
 * stock can change before the retry reserves again.
 */
const ALLOWED_VIEWS: Record<RecipeOrderStatus, readonly RecipeOrderView[]> = {
  DRAFT: ['confirm'],
  AWAITING_INGREDIENTS: ['confirm', 'ingredients'],
  RESERVING: ['ingredients'],
  INVENTORY_CONFLICT: ['confirm', 'ingredients'],
  AWAITING_PAYMENT: ['payment'],
  PAYMENT_FAILED: ['confirm', 'ingredients', 'payment'],
  CONFIRMED: ['receipt', 'track'],
  PREPARING: ['receipt', 'track'],
  QUALITY_CHECK: ['receipt', 'track'],
  OUT_FOR_DELIVERY: ['receipt', 'track'],
  COMPLETING: ['receipt', 'track'],
  COMPLETED: ['receipt', 'track'],
  CANCELLED: ['receipt'],
}

export const allowedViewsForStatus = (status: RecipeOrderStatus): readonly RecipeOrderView[] =>
  ALLOWED_VIEWS[status] ?? ['receipt']

/**
 * Where an order with this status lands by default. A just-paid order (CONFIRMED, preparation not
 * started) shows its receipt; once the simulation has started it shows tracking. A cancelled order
 * shows its summary on the receipt page.
 */
export const defaultViewForStatus = (status: RecipeOrderStatus): RecipeOrderView => {
  switch (status) {
    case 'DRAFT':
      return 'confirm'
    case 'AWAITING_INGREDIENTS':
    case 'RESERVING':
    case 'INVENTORY_CONFLICT':
      return 'ingredients'
    case 'AWAITING_PAYMENT':
    case 'PAYMENT_FAILED':
      return 'payment'
    case 'CONFIRMED':
    case 'CANCELLED':
      return 'receipt'
    default:
      return 'track'
  }
}

export const isViewAllowed = (status: RecipeOrderStatus, view: RecipeOrderView) =>
  allowedViewsForStatus(status).includes(view)

/**
 * The view to show for a requested URL segment: the requested view when this status allows it,
 * otherwise the status's default — `redirect` tells the caller to replace the URL.
 */
export const resolveOrderView = (
  status: RecipeOrderStatus,
  requested: string | undefined,
): { view: RecipeOrderView; redirect: boolean } => {
  if (isRecipeOrderView(requested) && isViewAllowed(status, requested)) return { view: requested, redirect: false }
  return { view: defaultViewForStatus(status), redirect: true }
}

// ---------------------------------------------------------------------------------------------
// Stepper (Confirm -> Ingredients -> Payment -> Receipt -> Tracking)
// ---------------------------------------------------------------------------------------------

export const ORDER_STEPS: readonly { view: RecipeOrderView; label: string }[] = [
  { view: 'confirm', label: 'Confirm' },
  { view: 'ingredients', label: 'Ingredients' },
  { view: 'payment', label: 'Payment' },
  { view: 'receipt', label: 'Receipt' },
  { view: 'track', label: 'Tracking' },
]

export type OrderStepState = 'done' | 'current' | 'available' | 'upcoming'

/** How far the order has got: steps before this index are complete. */
const progressIndex = (status: RecipeOrderStatus): number => {
  if (status === 'DRAFT') return 0
  if (status === 'AWAITING_INGREDIENTS' || status === 'RESERVING' || status === 'INVENTORY_CONFLICT') return 1
  if (status === 'AWAITING_PAYMENT' || status === 'PAYMENT_FAILED') return 2
  if (isPaidStatus(status)) return 3
  // Cancelled: nothing further is done or reachable except the summary.
  return -1
}

export type OrderStepperItem = {
  view: RecipeOrderView
  label: string
  state: OrderStepState
  /** Whether the step can be opened (its view is allowed for the status). */
  navigable: boolean
}

/** Each step's state for the stepper header: the open view is current; finished steps are done. */
export const buildOrderStepper = (status: RecipeOrderStatus, currentView: RecipeOrderView): OrderStepperItem[] => {
  const progress = progressIndex(status)
  return ORDER_STEPS.map((step, index) => {
    const navigable = isViewAllowed(status, step.view)
    const state: OrderStepState = step.view === currentView
      ? 'current'
      : index < progress
        ? 'done'
        : navigable
          ? 'available'
          : 'upcoming'
    return { ...step, state, navigable: navigable && step.view !== currentView }
  })
}
