/**
 * Frontend domain types for the new Recipe -> Process -> ProcessNode model
 * (backend Phase 1/2). Field names and optionality mirror the backend DTOs
 * exactly (recipe/dto/{ProcessResponseDTO,ProcessNodeDTO,ProcessEdgeDTO,
 * ProcessRequestDTO,ProcessUpdateDTO,RecipeDetailResponseDTO,
 * RecipeIngredientDTO,NutritionInfoDTO}.java) so the wire shape needs no
 * translation beyond JSON (de)serialization. These are semantic/domain
 * types only — React Flow presentation concerns are bridged separately by
 * features/flow-editor/adapters/processFlowAdapter.ts.
 */

export type ProcessType = 'MAIN' | 'SUBPROCESS'

/** A Process graph never embeds another process as a node — a subprocess is referenced only by id, from a STEP's own Action On data (see RecipeIngredient-style refs in features/flow-editor/model/processStepData.ts). */
export type ProcessNodeKind = 'STEP' | 'CONDITION'

/** Mirrors the backend's UnitType enum (also duplicated locally in orderApi.ts as OrderUnitType — there is no single shared source for it yet). */
export type UnitType = 'KG' | 'GRAM' | 'LITER' | 'ML' | 'COUNT'

export type ProcessPosition = {
  x?: number
  y?: number
}

export type ProcessMeasured = {
  width?: number
  height?: number
}

export type ProcessViewport = {
  x?: number
  y?: number
  zoom?: number
}

/**
 * A single STEP or CONDITION node in a Process graph. `data` holds the
 * node's own field bag (opaque to the frontend domain layer — the flow
 * editor's node components are what interpret it, including a STEP's
 * Action On ingredient/subprocess references — see model/processStepData.ts).
 */
export type ProcessNode = {
  id: string
  kind: ProcessNodeKind
  data?: Record<string, unknown>

  // --- presentation fields, passed through unchanged (see ProcessNodeDTO) ---
  type?: string
  position?: ProcessPosition
  measured?: ProcessMeasured
  width?: number
  height?: number
  parentId?: string
  extent?: string
  draggable?: boolean
  selectable?: boolean
  deletable?: boolean
}

export type ProcessEdge = {
  id: string
  source: string
  target: string
  label?: string | null

  // --- presentation fields, passed through unchanged (see ProcessEdgeDTO) ---
  sourceHandle?: string | null
  targetHandle?: string | null
  type?: string
  animated?: boolean
  style?: Record<string, unknown>
  data?: Record<string, unknown>
}

export interface Process {
  id: number
  type: ProcessType
  recipeId: number
  name: string
  description?: string
  nodes: ProcessNode[]
  edges: ProcessEdge[]
  viewport?: ProcessViewport
  createdAt?: string
  updatedAt?: string
}

/** POST /api/v1/recipes/{recipeId}/processes body (ProcessRequestDTO). */
export interface ProcessCreateRequest {
  type: ProcessType
  name: string
  description?: string
}

/** PUT /api/v1/recipes/{recipeId}/processes/{processId} body (ProcessUpdateDTO). */
export interface ProcessUpdateRequest {
  name: string
  description?: string
  nodes?: ProcessNode[]
  edges?: ProcessEdge[]
  viewport?: ProcessViewport
}

export interface RecipeIngredient {
  ingredientId: number
  quantity: number
  unit: UnitType
  notes?: string
  preparation?: string
}

export interface NutritionInfo {
  calories?: number
  proteinGrams?: number
  carbohydratesGrams?: number
  fatGrams?: number
  fiberGrams?: number
  sodiumMilligrams?: number
  servings?: number
}

/**
 * One ancestor entry in a Process Editor's navigation trail (Recipe -> Main
 * Process -> ... -> the process currently open), carried as router state
 * between /process/:processId route entries rather than in any app-level
 * store — see App.tsx's ProcessEditorRoute.
 */
export type ProcessBreadcrumbEntry = {
  processId: number
  name: string
}

/**
 * The new Recipe Tool's view of a recipe (RecipeDetailResponseDTO), built
 * on top of the same backend entity the existing `Recipe` type
 * (recipeApi.ts) reads via `/api/v1/process-templates` — kept as a
 * separate type since the two endpoints return different shapes.
 */
export interface RecipeDetail {
  id: number
  name: string
  description?: string
  createdBy?: number
  visibility?: 'PUBLIC' | 'PRIVATE'
  ingredients: RecipeIngredient[]
  nutrition?: NutritionInfo | null
  mainProcessId?: number | null
  createdAt?: string
  updatedAt?: string
}
