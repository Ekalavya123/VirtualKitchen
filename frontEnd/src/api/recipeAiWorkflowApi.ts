/**
 * AI Recipe Creation API. The workflow orchestrates the existing generation, visualization and
 * narration jobs; nothing that spends image/TTS credits starts before `approve`.
 */

import { apiGet, apiPost } from './client'
import { API } from './endpoints'
import type {
  RecipeAiTaskSelection,
  RecipeAiTaskType,
  RecipeAiWorkflowCreateRequest,
  RecipeAiWorkflowEstimate,
  RecipeAiWorkflowResponse,
} from '../types/recipeAiWorkflow'

export const RecipeAiWorkflowApi = {
  async estimate(recipeId: number, selection: RecipeAiTaskSelection, signal?: AbortSignal): Promise<RecipeAiWorkflowEstimate> {
    return apiPost<RecipeAiWorkflowEstimate>(API.recipeAiWorkflows.estimate(recipeId), selection, { signal })
  },

  async create(recipeId: number, request: RecipeAiWorkflowCreateRequest): Promise<RecipeAiWorkflowResponse> {
    return apiPost<RecipeAiWorkflowResponse>(API.recipeAiWorkflows.list(recipeId), request)
  },

  async get(recipeId: number, workflowId: string, signal?: AbortSignal): Promise<RecipeAiWorkflowResponse> {
    return apiGet<RecipeAiWorkflowResponse>(API.recipeAiWorkflows.byId(recipeId, workflowId), { signal })
  },

  /** What approving would cost now, priced against the recipe's currently saved steps. */
  async estimateApproval(recipeId: number, workflowId: string, signal?: AbortSignal): Promise<RecipeAiWorkflowEstimate> {
    return apiGet<RecipeAiWorkflowEstimate>(API.recipeAiWorkflows.approvalEstimate(recipeId, workflowId), { signal })
  },

  /** `approvedRevision` must be the revision the editor just saved; a 409 means it moved on since. */
  async approve(recipeId: number, workflowId: string, approvedRevision: number): Promise<RecipeAiWorkflowResponse> {
    return apiPost<RecipeAiWorkflowResponse>(API.recipeAiWorkflows.approve(recipeId, workflowId), { approvedRevision })
  },

  async retryTask(recipeId: number, workflowId: string, task: RecipeAiTaskType): Promise<RecipeAiWorkflowResponse> {
    return apiPost<RecipeAiWorkflowResponse>(API.recipeAiWorkflows.retryTask(recipeId, workflowId, task), undefined)
  },

  async discard(recipeId: number, workflowId: string): Promise<RecipeAiWorkflowResponse> {
    return apiPost<RecipeAiWorkflowResponse>(API.recipeAiWorkflows.discard(recipeId, workflowId), undefined)
  },

  async dismiss(recipeId: number, workflowId: string): Promise<void> {
    return apiPost<void>(API.recipeAiWorkflows.dismiss(recipeId, workflowId), undefined)
  },
}
