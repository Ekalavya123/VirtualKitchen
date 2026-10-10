/**
 * The tracking timeline of a paid recipe order: which stage of the simulated preparation and
 * delivery is done, in progress or still to come, derived only from what the server says (status,
 * current step, step count). Nothing here moves an order on — the "Next step" control asks the
 * backend, and the timeline is re-derived from its answer.
 *
 * Pure and import-free (types only), so node:test covers it directly.
 */

import type { RecipeOrderStatus, RecipeOrderTimestamps } from '../../../types/recipeOrder'

export type TrackingStageId = 'confirmed' | 'ingredients' | 'cooking' | 'quality' | 'delivery' | 'delivered'

export type TrackingStageState = 'done' | 'current' | 'upcoming'

export type TrackingStage = {
  id: TrackingStageId
  label: string
  state: TrackingStageState
  /** When the stage was reached, from the order's timestamps. */
  timestampKey: keyof RecipeOrderTimestamps
  /** Cooking only, while PREPARING: the (0-based) recipe step being prepared, of `total`. */
  stepProgress?: { index: number; total: number }
}

const STAGES: readonly { id: TrackingStageId; label: string; timestampKey: keyof RecipeOrderTimestamps }[] = [
  { id: 'confirmed', label: 'Order confirmed', timestampKey: 'paidAt' },
  { id: 'ingredients', label: 'Ingredients prepared', timestampKey: 'preparationStartedAt' },
  { id: 'cooking', label: 'Cooking', timestampKey: 'preparationStartedAt' },
  { id: 'quality', label: 'Quality check', timestampKey: 'qualityCheckAt' },
  { id: 'delivery', label: 'Out for delivery', timestampKey: 'outForDeliveryAt' },
  { id: 'delivered', label: 'Delivered', timestampKey: 'deliveredAt' },
]

/**
 * The stage in progress for a status (its index in STAGES), STAGES.length when everything is done,
 * or -1 when the order isn't being tracked (not paid yet, or cancelled).
 */
const currentStageIndex = (status: RecipeOrderStatus): number => {
  switch (status) {
    case 'CONFIRMED':
      return 0
    // Preparation starting is what "ingredients prepared" means, so PREPARING is already cooking.
    case 'PREPARING':
      return 2
    case 'QUALITY_CHECK':
      return 3
    case 'OUT_FOR_DELIVERY':
      return 4
    // The backend finalises ingredient consumption here before COMPLETED.
    case 'COMPLETING':
      return 5
    case 'COMPLETED':
      return STAGES.length
    default:
      return -1
  }
}

export const isTrackableStatus = (status: RecipeOrderStatus) => currentStageIndex(status) >= 0

/** The timeline for an order at `status` on (0-based) step `currentStepIndex` of `totalSteps`. */
export const deriveTrackingStages = (
  status: RecipeOrderStatus,
  currentStepIndex: number | null,
  totalSteps: number,
): TrackingStage[] => {
  const current = currentStageIndex(status)
  return STAGES.map((stage, index) => {
    const state: TrackingStageState = current < 0 ? 'upcoming' : index < current ? 'done' : index === current ? 'current' : 'upcoming'
    const result: TrackingStage = { ...stage, state }
    if (stage.id === 'cooking' && status === 'PREPARING' && totalSteps > 0) {
      const stepIndex = Math.min(Math.max(currentStepIndex ?? 0, 0), totalSteps - 1)
      result.stepProgress = { index: stepIndex, total: totalSteps }
    }
    return result
  })
}

/** Share of the timeline that's done, 0–100 (a stage in progress counts half). */
export const trackingProgressPercent = (stages: readonly TrackingStage[]): number => {
  if (stages.length === 0) return 0
  const score = stages.reduce((sum, stage) => sum + (stage.state === 'done' ? 1 : stage.state === 'current' ? 0.5 : 0), 0)
  return Math.round((score / stages.length) * 100)
}

/** Whether the simulation can still be moved on from this status (mirrors the backend's ADVANCEABLE). */
export const canAdvanceStatus = (status: RecipeOrderStatus) =>
  status === 'CONFIRMED' || status === 'PREPARING' || status === 'QUALITY_CHECK' || status === 'OUT_FOR_DELIVERY' || status === 'COMPLETING'

/** The label of the control that moves the simulation on from here; null once there's nothing to do. */
export const nextActionLabel = (status: RecipeOrderStatus, currentStepIndex: number | null, totalSteps: number): string | null => {
  switch (status) {
    case 'CONFIRMED':
      return 'Start simulation'
    case 'PREPARING':
      return (currentStepIndex ?? 0) + 1 < totalSteps ? 'Next step' : 'Finish cooking'
    case 'QUALITY_CHECK':
      return 'Send out for delivery'
    case 'OUT_FOR_DELIVERY':
    case 'COMPLETING':
      return 'Mark as delivered'
    default:
      return null
  }
}
