/**
 * Process graph types: the persisted MAIN/SUBPROCESS graph of STEP/CONDITION nodes and its CRUD
 * requests. Field names and optionality mirror the backend DTOs exactly
 * (recipe/dto/{ProcessResponseDTO,ProcessNodeDTO,ProcessEdgeDTO,ProcessRequestDTO,
 * ProcessUpdateDTO}.java). What a node's `data` means (recipe steps, conditions) lives in
 * features/recipe-tool/process/model; React Flow presentation is bridged by
 * features/recipe-tool/process/adapters/recipeProcessCanvasAdapter.ts.
 */

export type ProcessType = 'MAIN' | 'SUBPROCESS'

/** A Process graph never embeds another process as a node — a subprocess is referenced only by id, from a STEP's own Action On data (see features/recipe-tool/process/model/recipeStepData.ts). */
export type ProcessNodeKind = 'STEP' | 'CONDITION'

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
 * Action On ingredient/subprocess references — see
 * features/recipe-tool/process/model/recipeStepData.ts).
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

/**
 * One process's replacement content within a recipe-level batch save — see
 * ProcessApi.updateAll and RecipeSessionContext.saveAll. Sent as
 * `{ processes: ProcessBatchUpdateItem[] }` to
 * `PUT /api/v1/recipes/{recipeId}/processes` (ProcessBatchUpdateRequestDTO).
 */
export interface ProcessBatchUpdateItem extends ProcessUpdateRequest {
  processId: number
}
