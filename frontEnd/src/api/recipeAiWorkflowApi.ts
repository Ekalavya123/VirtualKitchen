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
  RunModelCapability,
} from '../types/recipeAiWorkflow'

type RunModelKeys = Partial<Record<RunModelCapability, string>>

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
  async estimateApproval(
    recipeId: number,
    workflowId: string,
    signal?: AbortSignal,
    /** Price with these models instead of the run's ('' = account setting). */
    modelKeys?: RunModelKeys,
  ): Promise<RecipeAiWorkflowEstimate> {
    const query = new URLSearchParams(Object.entries(modelKeys ?? {}).map(([capability, key]) => [capability, key ?? ''])).toString()
    const url = API.recipeAiWorkflows.approvalEstimate(recipeId, workflowId)
    return apiGet<RecipeAiWorkflowEstimate>(query ? `${url}?${query}` : url, { signal })
  },

  /**
   * `approvedRevision` must be the revision the editor just saved; a 409 means it moved on since. `modelKeys`: the
   * models picked at approval ('' = account setting); omitted keeps the run's.
   */
  async approve(recipeId: number, workflowId: string, approvedRevision: number, modelKeys?: RunModelKeys): Promise<RecipeAiWorkflowResponse> {
    return apiPost<RecipeAiWorkflowResponse>(API.recipeAiWorkflows.approve(recipeId, workflowId), {
      approvedRevision,
      ...(modelKeys ? { modelKeys } : {}),
    })
  },

  /** `modelKeys`: the models picked for the retry ('' = account setting); omitted keeps the run's. */
  async retryTask(recipeId: number, workflowId: string, task: RecipeAiTaskType, modelKeys?: RunModelKeys): Promise<RecipeAiWorkflowResponse> {
    return apiPost<RecipeAiWorkflowResponse>(API.recipeAiWorkflows.retryTask(recipeId, workflowId, task), modelKeys ? { modelKeys } : undefined)
  },

  async discard(recipeId: number, workflowId: string): Promise<RecipeAiWorkflowResponse> {
    return apiPost<RecipeAiWorkflowResponse>(API.recipeAiWorkflows.discard(recipeId, workflowId), undefined)
  },

  async dismiss(recipeId: number, workflowId: string): Promise<void> {
    return apiPost<void>(API.recipeAiWorkflows.dismiss(recipeId, workflowId), undefined)
  },
}
