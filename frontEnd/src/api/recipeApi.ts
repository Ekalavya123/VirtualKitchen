/**
 * Recipe API
 * Recipes, their detail (ingredients/nutrition/main process), their Process graphs, and the
 * recipe-process AI generation/visualization jobs.
 */

import { apiGet, apiPost, apiDelete, apiPut } from './client'
import { API } from './endpoints'
import type { Process, ProcessBatchUpdateItem, ProcessCreateRequest, ProcessUpdateRequest } from '../types/process'
import type { NutritionInfo, RecipeProcessGenerationJobResponse, RecipeProcessGenerationRequest, RecipeProcessVisualizationJobResponse, RecipeDetail, RecipeIngredient, UnitType } from '../types/recipe'

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

/**
 * A recipe's ingredients/nutrition/main-process, backed by `/api/v1/recipes/{recipeId}/**`
 * ({@link RecipeApi} above serves the recipe list/create/publish/copy endpoints). The
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
 * Process (MAIN/SUBPROCESS) graph CRUD + recipe-scoped copy-on-insert reuse, backed
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
   * Recipe-level save: replace name/description/nodes/edges/viewport for every listed process in
   * one call — the Recipe Tool's single "Save" action for its unified editing session (MAIN plus
   * every loaded SUBPROCESS), instead of one independent save per process. The backend validates
   * every item before persisting any of them, so one invalid process rejects the whole batch.
   * Requires ownership.
   */
  async updateAll(recipeId: number, items: ProcessBatchUpdateItem[]): Promise<Process[]> {
    return apiPut<Process[]>(API.processes.list(recipeId), { processes: items })
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
 * AI-driven recipe process generation (semantic MAIN + subprocesses from free-form recipe text) —
 * async job only. The result is never persisted by the backend; the frontend loads it into the
 * current Recipe working session (see
 * features/recipe-tool/process/adapters/recipeProcessGenerationConverter.ts and
 * RecipeSessionContext) and the user Saves explicitly.
 */
export const RecipeProcessGenerationApi = {
  async startJob(recipeId: number, data: RecipeProcessGenerationRequest): Promise<RecipeProcessGenerationJobResponse> {
    return apiPost<RecipeProcessGenerationJobResponse>(API.recipeProcessGeneration.startJob(recipeId), data)
  },

  async getJobStatus(recipeId: number, jobId: string): Promise<RecipeProcessGenerationJobResponse> {
    return apiGet<RecipeProcessGenerationJobResponse>(API.recipeProcessGeneration.jobStatus(recipeId, jobId))
  },
}

/**
 * Async, per-step recipe process visualization (one generated image per STEP of the given
 * MAIN/SUBPROCESS). Scoped to one
 * process at a time: visualizing a process only generates images for its own STEP nodes.
 */
export const RecipeProcessVisualizationApi = {
  async startJob(recipeId: number, processId: number): Promise<RecipeProcessVisualizationJobResponse> {
    return apiPost<RecipeProcessVisualizationJobResponse>(API.recipeProcessVisualization.startJob(recipeId, processId), {})
  },

  async getJobStatus(recipeId: number, processId: number, jobId: string): Promise<RecipeProcessVisualizationJobResponse> {
    return apiGet<RecipeProcessVisualizationJobResponse>(API.recipeProcessVisualization.jobStatus(recipeId, processId, jobId))
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
