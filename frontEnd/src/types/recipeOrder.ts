/**
 * Recipe order types: ordering one of the user's recipes to be "prepared" by their kitchen and
 * delivered (a simulated flow — no real payment, chef or courier). Field names mirror the backend
 * DTOs exactly (recipeorder/dto/RecipeOrderResponseDTO, RecipeOrderRequests,
 * IngredientAvailabilityService.AvailabilityReport, auth/model/DeliveryAddress). Every status change
 * is decided by the server; the client only ever renders what a response says.
 */

import type { Process } from './process'
import type { NutritionInfo, UnitType } from './recipe'

/** Dates are ISO local date-times ("2026-10-10T14:03:22.123") as serialized by the backend, or null. */
export type IsoDateTime = string

export interface DeliveryAddress {
  recipientName?: string
  line1?: string
  line2?: string
  city?: string
  state?: string
  postalCode?: string
  phone?: string
}

export type RecipeOrderStatus =
  | 'DRAFT'
  | 'AWAITING_INGREDIENTS'
  | 'RESERVING'
  | 'AWAITING_PAYMENT'
  | 'INVENTORY_CONFLICT'
  | 'PAYMENT_FAILED'
  | 'CONFIRMED'
  | 'PREPARING'
  | 'QUALITY_CHECK'
  | 'OUT_FOR_DELIVERY'
  | 'COMPLETING'
  | 'COMPLETED'
  | 'CANCELLED'

export type RecipeOrderAction = 'EDIT' | 'CONFIRM' | 'RESERVE' | 'PAY' | 'ADVANCE' | 'CANCEL'

/** How a requirement's quantity was converted into the inventory unit. DEFAULT_FACTOR is an estimate. */
export type RequirementBasis = 'EXACT' | 'INGREDIENT_OVERRIDE' | 'DEFAULT_FACTOR'

/** The recipe as captured when the order was confirmed (the live recipe may change afterwards). */
export interface RecipeOrderRecipe {
  recipeId: number
  name: string
  description?: string | null
  thumbnailUrl: string | null
  fallbackIcon?: string | null
  ownerId?: number | null
  ownerName?: string | null
  nutrition?: NutritionInfo | null
  stepCount: number
  capturedAt?: IsoDateTime | null
  /** Null on list (summary) responses; the full snapshot on `GET /recipe-orders/{id}`. */
  processes: Process[] | null
}

export interface RequirementSource {
  processId: number
  processName: string
  nodeId: string
  quantity: number | null
  unit: string
  convertedQuantity: number
}

export interface IngredientRequirement {
  ingredientId: number
  name: string
  icon?: string | null
  imageUrl?: string | null
  requiredQuantity: number
  unit: UnitType
  basis: RequirementBasis
  conversionNotes: string[]
  sources: RequirementSource[]
}

/** A step ingredient the server couldn't turn into a requirement. Any issue blocks reservation. */
export interface RequirementIssue {
  ingredientRef: string
  name: string
  processName: string
  nodeId: string
  message: string
}

export type AllocationState = 'PLANNED' | 'RESERVED' | 'RELEASED' | 'CONSUMED' | 'FAILED'

export interface InventoryAllocation {
  inventoryId: number
  ingredientId: number
  ingredientName: string
  quantity: number
  unit: UnitType
  requirementQuantity: number
  requirementUnit: UnitType
  state: AllocationState
  note?: string | null
}

export interface IngredientUsageLine {
  ingredientId: number
  ingredientName: string
  quantity: number
  unit: UnitType
}

export interface RecipeOrderPricing {
  ingredientsCost: number
  preparationFee: number
  deliveryFee: number
  total: number
  currency: string
  demo: boolean
}

export type PaymentMethod = 'DEMO_APPROVE' | 'DEMO_DECLINE'

export interface PaymentAttempt {
  idempotencyKey: string
  method: string
  status: 'PAID' | 'FAILED' | 'REFUNDED'
  reference?: string | null
  amount: number
  currency: string
  processedAt?: IsoDateTime | null
  failureReason?: string | null
  simulated: boolean
}

export interface RecipeOrderTimestamps {
  createdAt: IsoDateTime | null
  updatedAt: IsoDateTime | null
  recipeConfirmedAt: IsoDateTime | null
  reservedAt: IsoDateTime | null
  paidAt: IsoDateTime | null
  paymentFailedAt: IsoDateTime | null
  preparationStartedAt: IsoDateTime | null
  qualityCheckAt: IsoDateTime | null
  outForDeliveryAt: IsoDateTime | null
  deliveredAt: IsoDateTime | null
  completedAt: IsoDateTime | null
  cancelledAt: IsoDateTime | null
}

export interface RecipeOrderEvent {
  type: string
  fromStatus: RecipeOrderStatus | null
  toStatus: RecipeOrderStatus | null
  stepIndex: number | null
  message: string
  simulated: boolean
  at: IsoDateTime
}

export interface RecipeOrderDemoInfo {
  estimatedPreparationMinutes: number
  estimatedDeliveryMinutes: number
  snapshotRequests: number
}

export interface RecipeOrder {
  id: number
  orderCode: string
  status: RecipeOrderStatus
  kitchenId: number
  kitchenName: string
  recipeId: number
  servings: number
  /** The recipe's own serving count; null when it has none, in which case `servings` counts batches. */
  baseServings: number | null
  scale: number
  notes: string | null
  deliveryAddress: DeliveryAddress | null
  recipe: RecipeOrderRecipe | null
  requirements: IngredientRequirement[]
  requirementIssues: RequirementIssue[]
  allocations: InventoryAllocation[]
  /** Only once COMPLETED. */
  ingredientUsage: IngredientUsageLine[] | null
  pricing: RecipeOrderPricing
  payment: PaymentAttempt | null
  paymentAttempts: PaymentAttempt[]
  currentStepIndex: number | null
  totalSteps: number
  timestamps: RecipeOrderTimestamps
  events: RecipeOrderEvent[]
  allowedActions: RecipeOrderAction[]
  demo: RecipeOrderDemoInfo
}

export interface PurchaseSuggestion {
  ingredientId: number
  name: string
  /** What the shop sells: whole units of the ingredient's shop unit (e.g. 1 KG). */
  quantity: number
  unit: UnitType
  /** What the recipe is actually short of (e.g. 350 GRAM). */
  neededQuantity: number
  neededUnit: UnitType
}

export interface AvailabilityLine {
  ingredientId: number
  name: string
  icon?: string | null
  imageUrl?: string | null
  unit: UnitType
  required: number
  available: number
  missing: number
  sufficient: boolean
  /** Already held for this order (once reserved). */
  reservedForOrder: number
  basis: RequirementBasis
  conversionNotes: string[]
  purchase: PurchaseSuggestion | null
}

export interface AvailabilityReport {
  lines: AvailabilityLine[]
  issues: RequirementIssue[]
  allAvailable: boolean
  canReserve: boolean
}

/** POST /api/v1/recipe-orders body. */
export interface RecipeOrderCreateRequest {
  recipeId: number
  servings?: number
  deliveryAddress?: DeliveryAddress
  notes?: string
}

/** PUT /api/v1/recipe-orders/{id} body — omitted fields stay as they are. */
export interface RecipeOrderUpdateRequest {
  servings?: number
  deliveryAddress?: DeliveryAddress
  notes?: string
}
