/**
 * AI model registry & credits API
 * Self-service view of the current user's AI credit balance/history, and the
 * read-only AI model registry (for a future model picker / support debugging).
 */

import { apiGet } from './client'
import { API } from './endpoints'

export type ModelTier = 'PAID' | 'OPEN_SOURCE'
export type AiCapability = 'TEXT_TO_TEXT' | 'TEXT_TO_IMAGE'

export interface ModelDefinition {
  key: string
  capability: AiCapability
  tier: ModelTier
  providerModelId: string
  creditCost: number
  enabled: boolean
}

export interface UserAiCredit {
  userId: number
  monthlyAllocation: number
  availableBalance: number
  reservedBalance: number
  cycleStart: string
  cycleEnd: string
}

export type CreditTransactionType = 'RESERVE' | 'CONSUME' | 'RELEASE' | 'MONTHLY_GRANT' | 'ADMIN_ADJUSTMENT'

export interface AiCreditTransaction {
  id: string
  requestId?: string
  type: CreditTransactionType
  amount: number
  balanceAfter: number
  capability?: AiCapability
  modelKey?: string
  tier?: ModelTier
  createdAt: string
}

export const AiApi = {
  async getModels(capability?: AiCapability): Promise<ModelDefinition[]> {
    return apiGet<ModelDefinition[]>(API.ai.models, { params: capability ? { capability } : undefined })
  },

  async getMyCredits(): Promise<UserAiCredit> {
    return apiGet<UserAiCredit>(API.ai.myCredits)
  },

  async getMyCreditHistory(): Promise<AiCreditTransaction[]> {
    return apiGet<AiCreditTransaction[]>(API.ai.myCreditHistory)
  },
}
