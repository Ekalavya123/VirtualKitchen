/**
 * Step narration API. Reading never generates audio; `ensure` generates only when the step has no
 * narration for its current (saved) text, and returns GENERATING while that runs — poll `list`.
 */

import { apiDelete, apiGet, apiPost } from './client'
import { API } from './endpoints'
import type { StepNarration } from '../types/narration'

export const StepNarrationApi = {
  async list(recipeId: number, processId: number, signal?: AbortSignal): Promise<StepNarration[]> {
    return apiGet<StepNarration[]>(API.stepNarration.list(recipeId, processId), { signal })
  },

  async get(recipeId: number, processId: number, stepId: string, signal?: AbortSignal): Promise<StepNarration> {
    return apiGet<StepNarration>(API.stepNarration.step(recipeId, processId, stepId), { signal })
  },

  async ensure(recipeId: number, processId: number, stepId: string, force = false): Promise<StepNarration> {
    return apiPost<StepNarration>(API.stepNarration.step(recipeId, processId, stepId), { force })
  },

  /** Ensures several steps at once (all when `stepIds` is omitted); each step succeeds or fails alone. */
  async ensureAll(recipeId: number, processId: number, stepIds?: string[]): Promise<StepNarration[]> {
    return apiPost<StepNarration[]>(API.stepNarration.list(recipeId, processId), { stepIds })
  },

  async remove(recipeId: number, processId: number, stepId: string): Promise<void> {
    await apiDelete<void>(API.stepNarration.step(recipeId, processId, stepId))
  },
}
