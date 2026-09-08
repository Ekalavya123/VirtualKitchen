/**
 * Recipe & Flow API
 * Handles all recipe and flow-related API calls
 */

import { apiGet, apiPost, apiDelete, apiPut } from './client'
import { API } from './endpoints'
import type { FlowData, RecipeExecutionModel } from '../types/recipeFlow'

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
}

export type LegacyRecipeFlowGenerationResponse = {
  nodes: unknown[]
  edges: unknown[]
}

export type RecipeFlowGenerationResponse = RecipeExecutionModel | LegacyRecipeFlowGenerationResponse

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

export interface RecipeVisualizationStep {
  stepId: string
  visualizationAssetId: number
  imagePrompt?: string
  imageUrl?: string | null
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
