/**
 * Recipe & Flow API
 * Handles all recipe and flow-related API calls
 */

import { apiGet, apiPost, apiDelete, apiPut } from './client'
import { API } from './endpoints'
import type { FlowData, RecipeExecutionModel } from '../types/recipeFlow'
import type {
  NutritionInfo,
  Process,
  ProcessCreateRequest,
  ProcessUpdateRequest,
  RecipeDetail,
  RecipeIngredient,
  UnitType,
} from '../types/process'

export type RecipeVisibility = 'PUBLIC' | 'PRIVATE'

export interface Recipe {
  id: number
  name: string
  description?: string
  createdAt?: string
  createdBy?: number
  visibility?: RecipeVisibility
}

export interface RecipeCreateRequest {
  name: string
  description?: string
  createdBy: number
}

export interface RecipeUpdateRequest {
  name: string
  description?: string
}

export interface VisualizationRequest {
  nodes: FlowData['nodes']
  edges: FlowData['edges']
}

export interface RecipeFlowGenerationRequest {
  recipe: string
  /** Client-generated id (e.g. crypto.randomUUID()) used as the AI request's idempotency key, so an accidental double-submit never charges credits twice. */
  clientRequestId?: string
}

export type LegacyRecipeFlowGenerationResponse = {
  nodes: unknown[]
  edges: unknown[]
}

/** Which AI model actually served a request, and whether that was a fallback because premium credits are exhausted. */
export interface AiFallbackMetadata {
  modelUsed?: string
  modelTier?: 'PAID' | 'OPEN_SOURCE'
  usedFallback?: boolean
  fallbackReason?: 'INSUFFICIENT_CREDITS' | 'PREFERRED_MODEL_DISABLED' | null
}

export type RecipeFlowGenerationResponse = (RecipeExecutionModel | LegacyRecipeFlowGenerationResponse) & AiFallbackMetadata

export interface VisualizationClip {
  clipId?: string
  [key: string]: unknown
}

export interface VisualizationFinalClip {
  clipId?: string
  [key: string]: unknown
}

export interface VisualizationResponse {
  clips?: VisualizationClip[]
  finalClip?: VisualizationFinalClip
}

export const RecipeApi = {
  /**
   * Get all recipes owned by a user (My Recipes)
   */
  async getRecipesByUserId(userId: number): Promise<Recipe[]> {
    return apiGet<Recipe[]>(API.recipes.byUserId(userId))
  },

  /**
   * Get public recipes owned by other users (Global Recipes)
   */
  async getGlobalRecipes(userId: number): Promise<Recipe[]> {
    return apiGet<Recipe[]>(API.recipes.global(userId))
  },

  /**
   * Create a new recipe
   */
  async createRecipe(data: RecipeCreateRequest): Promise<Recipe> {
    return apiPost<Recipe>(API.recipes.list, data)
  },

  /**
   * Update a recipe's name/description. Only the owner may update.
   */
  async updateRecipe(recipeId: number, userId: number, data: RecipeUpdateRequest): Promise<Recipe> {
    return apiPut<Recipe>(API.recipes.byId(recipeId), data, { params: { userId } })
  },

  /**
   * Publish/unpublish a recipe. Only the owner may change visibility.
   */
  async updateVisibility(recipeId: number, userId: number, visibility: RecipeVisibility): Promise<Recipe> {
    return apiPut<Recipe>(API.recipes.visibility(recipeId), undefined, { params: { userId, visibility } })
  },

  /**
   * Copy a public recipe into the current user's My Recipes as an independent recipe.
   */
  async copyRecipe(recipeId: number, userId: number): Promise<Recipe> {
    return apiPost<Recipe>(API.recipes.copy(recipeId), undefined, { params: { userId } })
  },

  /**
   * Delete a recipe. Only the owner may delete.
   */
  async deleteRecipe(recipeId: number, userId: number): Promise<void> {
    return apiDelete<void>(API.recipes.byId(recipeId), { params: { userId } })
  },
}

export const FlowApi = {
  /**
   * Load flow data for a recipe
   */
  async getFlowByRecipeId(recipeId: number | string): Promise<FlowData> {
    return apiGet<FlowData>(API.flows.byId(recipeId))
  },

  /**
   * Save flow data for a recipe
   */
  async saveFlow(
    flowId: number | string,
    data: FlowData
  ): Promise<FlowData> {
    return apiPut<FlowData>(API.flows.byId(flowId), data)
  },

  /**
   * Generate an initial flow from recipe text
   */
  async generateFlowFromRecipe(data: RecipeFlowGenerationRequest): Promise<RecipeFlowGenerationResponse> {
    return apiPost<RecipeFlowGenerationResponse>(API.recipeGeneration.generateFlow, data)
  },
}

export const VisualizationApi = {
  /**
   * Generate visualization for a flow
   */
  async generateVisualization(
    recipeId: number | string,
    data: VisualizationRequest
  ): Promise<VisualizationResponse> {
    return apiPost<VisualizationResponse>(
      API.visualizations.byId(recipeId),
      data
    )
  },
}

export interface RecipeVisualizationStep extends AiFallbackMetadata {
  stepId: string
  visualizationAssetId: number
  imagePrompt?: string
  imageUrl?: string | null
  modelKey?: string
  modelTier?: 'PAID' | 'OPEN_SOURCE'
  usedFallback?: boolean
}

export interface RecipeVisualizationGenerateResponse {
  recipeId: string
  message?: string
  steps: RecipeVisualizationStep[]
}

export const RecipeVisualizationApi = {
  /**
   * Generate (or reuse) visualization assets for every step of a recipe
   */
  async generate(recipeId: number | string): Promise<RecipeVisualizationGenerateResponse> {
    return apiPost<RecipeVisualizationGenerateResponse>(
      API.recipeVisualization.generate(recipeId),
      {}
    )
  },

  /**
   * Generate (or reuse) the visualization asset for a single recipe step
   */
  async generateStep(recipeId: number | string, stepId: string): Promise<RecipeVisualizationStep> {
    return apiPost<RecipeVisualizationStep>(
      API.recipeVisualization.generateStep(recipeId, stepId),
      {}
    )
  },
}

export type VisualizationJobStatus = 'QUEUED' | 'IN_PROGRESS' | 'COMPLETED' | 'COMPLETED_WITH_ERRORS' | 'FAILED'

export interface VisualizationJobStepResult {
  stepId: string
  success: boolean
  visualizationAssetId?: number
  imageUrl?: string | null
  errorMessage?: string | null
  modelKey?: string
  tier?: 'PAID' | 'OPEN_SOURCE'
  usedFallback?: boolean
}

export interface VisualizationJobResponse {
  jobId: string
  recipeId: string
  status: VisualizationJobStatus
  totalSteps: number
  completedSteps: number
  steps: VisualizationJobStepResult[]
}

/**
 * Async, job-based visualization generation: {@link startJob} returns immediately with a
 * QUEUED job, and the caller polls {@link getJobStatus} until it reaches a terminal status
 * (COMPLETED / COMPLETED_WITH_ERRORS / FAILED). Preferred over {@link RecipeVisualizationApi}'s
 * synchronous per-step calls since the backend bounds concurrency and reports per-step
 * fallback/model metadata here.
 */
export const VisualizationJobApi = {
  async startJob(recipeId: number | string): Promise<VisualizationJobResponse> {
    return apiPost<VisualizationJobResponse>(API.recipeVisualization.startJob(recipeId), {})
  },

  async getJobStatus(jobId: string): Promise<VisualizationJobResponse> {
    return apiGet<VisualizationJobResponse>(API.recipeVisualization.jobStatus(jobId))
  },
}

export type RecipeFlowGenerationJobStatus = 'QUEUED' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED'

/**
 * Internal milestone of a recipe-flow generation job. There's no natural step
 * count for a single AI call, so progress is reported as these coarse, real
 * stage transitions instead — see {@link RecipeFlowGenerationJobResponse.progressPercent}.
 */
export type RecipeFlowGenerationStage =
  | 'QUEUED'
  | 'BUILDING_PROMPT'
  | 'CALLING_MODEL'
  | 'VALIDATING_RESPONSE'
  | 'RETRYING'
  | 'PERSISTING'
  | 'COMPLETED'

export interface RecipeFlowGenerationJobResponse {
  jobId: string
  status: RecipeFlowGenerationJobStatus
  stage: RecipeFlowGenerationStage
  progressPercent: number
  result: RecipeFlowGenerationResponse | null
  errorMessage?: string | null
}

/**
 * Async, job-based flow generation: {@link startJob} returns immediately with a QUEUED job, and
 * the caller polls {@link getJobStatus} until it reaches a terminal status (COMPLETED / FAILED).
 * Preferred over {@link FlowApi.generateFlowFromRecipe}'s synchronous call since it never blocks
 * the request for the full duration of the AI call and reports real progress milestones.
 */
export const FlowGenerationJobApi = {
  async startJob(data: RecipeFlowGenerationRequest): Promise<RecipeFlowGenerationJobResponse> {
    return apiPost<RecipeFlowGenerationJobResponse>(API.recipeGeneration.startJob, data)
  },

  async getJobStatus(jobId: string): Promise<RecipeFlowGenerationJobResponse> {
    return apiGet<RecipeFlowGenerationJobResponse>(API.recipeGeneration.jobStatus(jobId))
  },
}

/**
 * New Recipe Tool: a recipe's ingredients/nutrition/main-process, backed by
 * `/api/v1/recipes/{recipeId}/**`. Coexists with {@link RecipeApi} above,
 * which still serves the legacy `/api/v1/process-templates` endpoints. The
 * authenticated user is resolved by the backend from the request's JWT, so
 * — unlike {@link RecipeApi}'s mutating methods — none of these take a
 * userId parameter.
 */
export const RecipeDetailApi = {
  /**
   * Get a recipe's detail view (ingredients, nutrition, main process id, plus base fields).
   */
  async getRecipeDetail(recipeId: number): Promise<RecipeDetail> {
    return apiGet<RecipeDetail>(API.recipeDetail.byId(recipeId))
  },

  /**
   * Replace a recipe's ingredient list. Requires ownership.
   */
  async updateIngredients(recipeId: number, ingredients: RecipeIngredient[]): Promise<RecipeDetail> {
    return apiPut<RecipeDetail>(API.recipeDetail.ingredients(recipeId), ingredients)
  },

  /**
   * Replace a recipe's nutrition info. Requires ownership.
   */
  async updateNutrition(recipeId: number, nutrition: NutritionInfo): Promise<RecipeDetail> {
    return apiPut<RecipeDetail>(API.recipeDetail.nutrition(recipeId), nutrition)
  },

  /**
   * Get a recipe's MAIN process.
   */
  async getMainProcess(recipeId: number): Promise<Process> {
    return apiGet<Process>(API.recipeDetail.mainProcess(recipeId))
  },

  /**
   * Ensure a recipe has a MAIN process, creating one if it doesn't already have one
   * (idempotent — safe to call repeatedly). Requires ownership.
   */
  async createMainProcess(recipeId: number): Promise<Process> {
    return apiPost<Process>(API.recipeDetail.mainProcess(recipeId), undefined)
  },
}

/**
 * New Recipe Tool: Process (MAIN/SUBPROCESS) CRUD + recipe-scoped copy-on-insert reuse, backed
 * by `/api/v1/recipes/{recipeId}/processes/**`. Every process is scoped to a recipe id.
 */
export const ProcessApi = {
  /**
   * List every process (MAIN and any SUBPROCESS documents) belonging to a recipe.
   */
  async listByRecipe(recipeId: number): Promise<Process[]> {
    return apiGet<Process[]>(API.processes.list(recipeId))
  },

  /**
   * Create a new process under a recipe. Pass `type: 'SUBPROCESS'` to create a subprocess;
   * `type: 'MAIN'` is normally created via {@link RecipeDetailApi.createMainProcess} instead,
   * which also assigns it as the recipe's main process. Requires ownership.
   */
  async create(recipeId: number, data: ProcessCreateRequest): Promise<Process> {
    return apiPost<Process>(API.processes.list(recipeId), data)
  },

  /**
   * Get a single process by id, scoped to a recipe.
   */
  async get(recipeId: number, processId: number): Promise<Process> {
    return apiGet<Process>(API.processes.byId(recipeId, processId))
  },

  /**
   * Update a process's name/description and replace its node/edge graph and viewport.
   * Requires ownership.
   */
  async update(recipeId: number, processId: number, data: ProcessUpdateRequest): Promise<Process> {
    return apiPut<Process>(API.processes.byId(recipeId, processId), data)
  },

  /**
   * Delete a process. Rejected by the backend while it is the recipe's referenced MAIN
   * process, or while another process in the recipe still references it. Requires ownership.
   */
  async delete(recipeId: number, processId: number): Promise<void> {
    return apiDelete<void>(API.processes.byId(recipeId, processId))
  },

  /**
   * Copy-on-insert reuse: deep-clone a SUBPROCESS (recursively, including nested subprocess
   * references) into a new, independent process within the same recipe. Requires ownership.
   * The MAIN process cannot be copied.
   */
  async copy(recipeId: number, processId: number): Promise<Process> {
    return apiPost<Process>(API.processes.copy(recipeId, processId), undefined)
  },
}

/**
 * A global ingredient catalog entry (store/dto/IngredientResponseDTO), as referenced by a
 * recipe's ingredient list (RecipeIngredient.ingredientId). Distinct from the looser `ShopItem`
 * type in inventoryApi.ts — that one is shared with equipment and predates `imageUrl` — this one
 * is typed to exactly what the backend returns for an ingredient.
 */
export interface GlobalIngredient {
  id: number
  name: string
  description?: string
  defaultUnit: UnitType
  imageUrl?: string
  createdAt?: string
  updatedAt?: string
}

/**
 * The global ingredient catalog (`/api/v1/ingredients`, the same endpoint `ShopApi.getIngredients`
 * already calls) — reused here with accurate typing (including `imageUrl`) for the Recipe Tool's
 * ingredient selector. Recipes never copy an ingredient's own data; they only reference it by id.
 */
export const IngredientCatalogApi = {
  async list(): Promise<GlobalIngredient[]> {
    return apiGet<GlobalIngredient[]>(API.shop.ingredients)
  },
}
