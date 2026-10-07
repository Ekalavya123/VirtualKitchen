/**
 * Recipe-specific types: a recipe's detail view (ingredients, nutrition, main process), the AI
 * recipe process generation and visualization job shapes, and the Recipe Process navigation
 * trail. Field names mirror the backend DTOs (RecipeDetailResponseDTO, RecipeIngredientDTO,
 * NutritionInfoDTO, RecipeProcessGeneration*DTO, Generated*DTO, VisualizationJobResponseDTO).
 */

import type { RecipeAiWorkflowResponse } from './recipeAiWorkflow'

/** Mirrors the backend's UnitType enum (also duplicated locally in orderApi.ts as OrderUnitType — there is no single shared source for it yet). */
export type UnitType = 'KG' | 'GRAM' | 'LITER' | 'ML' | 'COUNT'

/**
 * AI-generated recipe process structures (recipe/dto/Generated*.java) — semantic
 * data only, no React Flow node/edge ids, positions, or dimensions. A
 * subprocess reference is a temporary generation-scoped `ref` string
 * (resolved to a real or client-temporary process id by
 * features/recipe-tool/process/adapters/recipeProcessGenerationConverter.ts), never a
 * database id.
 */
export interface GeneratedActionOnIngredient {
  ingredientId: string
  /** Null when no amount applies (e.g. unit "to-taste"). */
  quantity: number | null
  /** A catalog unit id (catalog/unitCatalog.ts); legacy UnitType values are still possible. */
  unit?: string | null
  preparationStyle?: string | null
  customIngredientName?: string | null
}

export interface GeneratedActionOn {
  ingredients: GeneratedActionOnIngredient[]
  processes: string[]
  /** `stepId`s of earlier steps in the same process whose Expected Output this step uses. */
  steps?: string[] | null
}

export interface GeneratedRecipeStep {
  nodeType: 'STEP' | 'CONDITION'
  /** Generation-scoped slug (e.g. "s1"), unique within its process — mapped to a real node id on conversion. */
  stepId?: string | null

  // STEP-only
  action?: string | null
  customActionName?: string | null
  actionOn?: GeneratedActionOn | null
  temperatureValue?: number | null
  temperatureUnit?: string | null
  /** Legacy free-text temperature ("180 C"). */
  temperature?: string | null
  flameLevel?: string | null
  duration?: string | null
  repeatInterval?: string | null

  // CONDITION-only
  title?: string | null
  expectedResult?: string | null

  // shared
  actionDescription: string
  expectedOutput: string
}

export interface GeneratedRecipeProcess {
  /** Absent/null for the MAIN process; a unique slug for a subprocess. */
  ref?: string | null
  name: string
  steps: GeneratedRecipeStep[]
}

export type ProcessGenerationMode = 'CREATE' | 'EDIT'

/**
 * One validated AI edit operation (recipe/dto/ProcessEditOperationDTO.java), applied in list order by
 * features/recipe-tool/process/adapters/recipeProcessEditApplier.ts. Node references are real node ids, the
 * `ref` of a node an earlier operation added, or 'START'.
 */
export type ProcessEditOperationType =
  | 'ADD_STEP' | 'ADD_CONDITION' | 'UPDATE_STEP' | 'UPDATE_CONDITION'
  | 'ADD_INGREDIENT' | 'UPDATE_INGREDIENT' | 'REMOVE_INGREDIENT' | 'REPLACE_INGREDIENT'
  | 'DELETE_NODE' | 'MOVE_NODE'

export interface ProcessEditOperation {
  op: ProcessEditOperationType
  target?: string | null
  after?: string | null
  /** Temporary reference of the node an ADD_* operation creates. */
  ref?: string | null
  /** ADD_*: the full new node. UPDATE_*: only the fields that change (actionOn.steps/processes replace whole lists). */
  step?: Partial<GeneratedRecipeStep> | null
  /** UPDATE_STEP: fields to reset ('temperature' resets value and unit). */
  clear?: string[] | null
  ingredient?: GeneratedActionOnIngredient | null
  ingredientId?: string | null
}

export interface RecipeProcessEditResult {
  targetProcessId: number
  summary: string
  /** Set (with no operations) when the AI needs more information instead of changing anything. */
  clarification?: string | null
  operations: ProcessEditOperation[]
}

export interface RecipeProcessGenerationResult {
  /** Null on CREATE results and on results stored before modes existed. */
  mode?: ProcessGenerationMode | null
  /** CREATE only. */
  mainProcess: GeneratedRecipeProcess
  subprocesses: GeneratedRecipeProcess[]
  /** EDIT only. */
  edit?: RecipeProcessEditResult | null
  modelUsed?: string
  modelTier?: string
  usedFallback?: boolean
  fallbackReason?: string | null
}

/** One existing node of the process an EDIT request changes (recipe/dto/EditTargetNodeDTO.java). */
export interface EditTargetNode {
  nodeId: string
  /** The number the editor shows on a STEP. */
  stepNumber?: number | null
  /** Semantic content; actionOn.steps holds node ids and actionOn.processes holds process ids as strings. */
  content: GeneratedRecipeStep
  nextNodeIds?: string[]
  yesNodeId?: string | null
  noNodeId?: string | null
}

export interface EditTargetProcess {
  processId: number
  name: string
  /** In reading order. */
  nodes: EditTargetNode[]
  subprocesses: { processId: number; name: string }[]
}

/** POST /api/v1/recipes/{recipeId}/processes/generate/jobs body. */
export interface RecipeProcessGenerationRequest {
  /** The recipe (CREATE) or the change instruction (EDIT). */
  recipeText: string
  clientRequestId?: string
  /** CREATE when omitted. */
  mode?: ProcessGenerationMode
  /** EDIT only. */
  targetProcess?: EditTargetProcess
  /** EDIT only: the node selected in the editor. */
  selectedNodeId?: string | null
}

export type RecipeProcessGenerationJobStatus = 'QUEUED' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED'

export interface RecipeProcessGenerationJobResponse {
  jobId: string
  status: RecipeProcessGenerationJobStatus
  stage: string
  /** Which kind of job this is (older backends omit it: CREATE). */
  mode?: ProcessGenerationMode
  progressPercent: number
  result?: RecipeProcessGenerationResult | null
  errorMessage?: string | null
  /** True when the start request joined a generation already running for this recipe. */
  reused?: boolean
}

/**
 * Async recipe process visualization job (one generated image per STEP of a MAIN/SUBPROCESS —
 * CONDITION nodes are never included). Mirrors the backend's `VisualizationJobResponseDTO`.
 */
export type RecipeProcessVisualizationJobStatus = 'QUEUED' | 'IN_PROGRESS' | 'COMPLETED' | 'COMPLETED_WITH_ERRORS' | 'FAILED'

export interface RecipeProcessVisualizationStepResult {
  stepId: string
  success: boolean
  visualizationAssetId?: number
  imageUrl?: string | null
  errorMessage?: string | null
  modelKey?: string
  tier?: 'PAID' | 'OPEN_SOURCE'
  usedFallback?: boolean
}

export interface RecipeProcessVisualizationJobResponse {
  jobId: string
  recipeId: string
  processId: number
  status: RecipeProcessVisualizationJobStatus
  totalSteps: number
  completedSteps: number
  steps: RecipeProcessVisualizationStepResult[]
  errorMessage?: string | null
  /** True when the start request joined a visualization already running for this process. */
  reused?: boolean
}

/**
 * The caller's background AI jobs for one recipe, used to re-attach to them after a reload or after
 * navigating away and back. Mirrors the backend's `RecipeActiveJobsResponseDTO`.
 */
export interface RecipeActiveJobsResponse {
  /** The running generation, else a completed one whose result was never applied, else null. */
  generation: RecipeProcessGenerationJobResponse | null
  visualizations: RecipeProcessVisualizationJobResponse[]
  /** The open AI Recipe Creation workflow, else a recently finished one not yet dismissed (older backends: absent). */
  workflow?: RecipeAiWorkflowResponse | null
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
 * store — see App.tsx's RecipeProcessEditorRoute.
 */
export type RecipeProcessBreadcrumbEntry = {
  processId: number
  name: string
}

/**
 * The Recipe Tool's view of a recipe (RecipeDetailResponseDTO), built
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
  /** Optimistic-concurrency counter of the recipe-level process save (0 until first saved). */
  processRevision?: number
  createdAt?: string
  updatedAt?: string
}
