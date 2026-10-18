/**
 * Admin AI analytics API. Every figure comes from persisted backend analytics; nothing is computed here.
 * Nullable fields are null when the value was never reported or recorded — never treat them as zero.
 */

import { apiGet, apiPost } from '../../../../api/client'

const BASE = '/api/v1/admin/ai-analytics'

export type AnalyticsStatus =
  | 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'TIMED_OUT' | 'CANCELLED' | 'REJECTED' | 'REUSED'
export type CostBasis = 'PROVIDER_REPORTED' | 'CALCULATED' | 'ESTIMATED' | 'UNAVAILABLE'
export type UsageBasis = 'PROVIDER_REPORTED' | 'NOT_REPORTED' | 'DERIVED_LOCAL'

export interface Usage {
  inputTokens: number | null
  cachedInputTokens: number | null
  outputTokens: number | null
  reasoningTokens: number | null
  totalTokens: number | null
  imageCount: number | null
  imageWidth: number | null
  imageHeight: number | null
  audioDurationMs: number | null
  inputChars: number | null
  outputChars: number | null
  outputBytes: number | null
  providerDurationMs: number | null
  usageBasis: UsageBasis | null
}

export interface Cost {
  inputCost: number | null
  cachedInputCost: number | null
  outputCost: number | null
  reasoningCost: number | null
  modalityCost: number | null
  directCost: number | null
  infraCost: number | null
  totalCost: number | null
  currency: string | null
  costBasis: CostBasis | null
  infraCostBasis: CostBasis | null
  pricingRateId: string | null
  pricingVersion: string | null
  calculatedAt: string | null
  formula: string | null
  complete: boolean | null
  costedAttempts: number | null
  uncostedAttempts: number | null
}

export interface Attempt {
  attemptId: string
  callIndex: number
  transportAttempt: number | null
  purpose: string | null
  provider: string | null
  providerModelId: string | null
  reportedModel: string | null
  startedAt: string | null
  endedAt: string | null
  durationMs: number | null
  status: AnalyticsStatus | null
  errorCategory: string | null
  errorMessage: string | null
  httpStatus: number | null
  finishReason: string | null
  validationPassed: boolean | null
  validationErrors: string[] | null
  late: boolean
  usage: Usage | null
  cost: Cost | null
  outputId: string | null
}

export interface OutputRef {
  kind: string | null
  collection: string | null
  id: string | null
  url: string | null
  mimeType: string | null
  sizeBytes: number | null
}

export interface AnalyticsRequest {
  id: string
  source: string | null
  aiRequestJobId: string | null
  reusedFromRequestId: string | null
  userId: number | null
  jobId: string | null
  workflowId: string | null
  recipeId: string | null
  processId: string | null
  stepId: string | null
  correlationType: string | null
  correlationId: string | null
  artifactId: string | null
  outputRef: OutputRef | null
  requestType: string | null
  operationContext: string | null
  requestedModelKey: string | null
  resolvedModelKey: string | null
  finalProvider: string | null
  finalProviderModelId: string | null
  modelTier: string | null
  usedFallback: boolean | null
  fallbackReason: string | null
  modelSnapshot: Record<string, unknown> | null
  creditCost: number | null
  status: AnalyticsStatus | null
  errorCategory: string | null
  errorMessage: string | null
  retryable: boolean | null
  validationPassed: boolean | null
  statusHistory: { status: AnalyticsStatus; at: string }[] | null
  createdAt: string | null
  queuedAt: string | null
  startedAt: string | null
  completedAt: string | null
  queueWaitMs: number | null
  executionMs: number | null
  totalMs: number | null
  timeoutMs: number | null
  queueDepthAtSubmit: number | null
  poolName: string | null
  transportAttempts: number | null
  attempts: Attempt[] | null
  attemptCount: number | null
  failedAttemptCount: number | null
  usageTotals: Usage | null
  totalCost: number | null
  costTotals: Cost | null
  dataCompleteness: 'COMPLETE' | 'PARTIAL' | 'HISTORICAL_PARTIAL' | null
  missingFields: string[] | null
}

export interface PricingRateCard {
  id: string
  version: string
  modelKey: string
  provider: string | null
  providerModelId: string | null
  modality: string | null
  tier: string | null
  inputPerMTok: number | null
  cachedInputPerMTok: number | null
  outputPerMTok: number | null
  reasoningPerMTok: number | null
  perImage: number | null
  perAudioSecond: number | null
  infraPerComputeSecond: number | null
  currency: string
  effectiveFrom: string | null
  effectiveTo: string | null
  active: boolean
  source: string | null
}

export interface Page<T> {
  items: T[]
  total: number
  page: number
  size: number
}

export interface RequestDetail {
  request: AnalyticsRequest
  pricing: PricingRateCard[]
}

export interface Stat {
  value: number | null
  samples: number
}

export interface GroupMetrics {
  key: Record<string, string | null>
  requestCount: number
  attemptCount: number | null
  attemptSamples: number
  succeeded: number
  failed: number
  timedOut: number
  rejected: number
  reused: number
  cancelled: number
  inFlight: number
  retryRate: number | null
  retrySamples: number
  failureRate: number | null
  totalCost: number | null
  costedCount: number
  uncostedCount: number
  costComplete: boolean
  currencies: string[]
  costBases: string[]
  avgCost: Stat
  medianCost: Stat
  minCost: number | null
  maxCost: number | null
  costPerSuccess: number | null
  directCost: number | null
  infraCost: number | null
  infraCostSamples: number
  avgInputTokens: Stat
  avgOutputTokens: Stat
  avgLatencyMs: Stat
  p50LatencyMs: Stat
  p95LatencyMs: Stat
  avgQueueWaitMs: Stat
  validationPassRate: number | null
  validationSamples: number
  historicalCount: number
}

export interface AggregateResult {
  groupBy: string[]
  groups: GroupMetrics[]
  totals: GroupMetrics
  rowsScanned: number
  truncated: boolean
}

export interface Overview {
  totals: GroupMetrics
  byModel: GroupMetrics[]
  byContext: GroupMetrics[]
  byModality: GroupMetrics[]
  overTime: GroupMetrics[]
  bucket: string
  topWorkflows: GroupMetrics[]
  topRequests: AnalyticsRequest[]
  rowsScanned: number
  truncated: boolean
}

export interface Comparison {
  operationContext: string
  models: GroupMetrics[]
  rowsScanned: number
  truncated: boolean
}

export type Provenance = 'RETAINED' | 'JOB_RESULT' | 'CURRENT_ASSET' | 'CURRENT_ASSET_MAY_BE_NEWER' | 'REUSED_FROM'

export interface AttemptSummary {
  attemptId: string
  callIndex: number
  transportAttempt: number | null
  purpose: string | null
  status: string | null
  validationPassed: boolean | null
  errorCategory: string | null
  errorMessage: string | null
  hasOutput: boolean
}

export interface AiResult {
  requestId: string
  retained: boolean
  kind: 'TEXT' | 'JSON' | 'IMAGE' | 'AUDIO' | 'NONE'
  content: string | null
  url: string | null
  mimeType: string | null
  sizeBytes: number | null
  audioDurationMs: number | null
  imageWidth: number | null
  imageHeight: number | null
  truncated: boolean
  contentLength: number
  prompt: string | null
  provenance: Provenance | null
  provenanceNote: string | null
  message: string | null
  errorMessage: string | null
  attemptId: string | null
  attempts: AttemptSummary[]
  metrics: {
    model: string | null
    providerModelId: string | null
    status: string | null
    inputTokens: number | null
    outputTokens: number | null
    totalCost: number | null
    costBasis: string | null
    currency: string | null
    costComplete: boolean | null
    latencyMs: number | null
    attemptCount: number | null
    transportAttempts: number | null
    validationPassed: boolean | null
  }
}

export interface BackfillRun {
  id: string
  startedAt: string | null
  finishedAt: string | null
  triggeredBy: number | null
  status: string
  scanned: number
  recovered: number
  partiallyRecovered: number
  alreadyPresent: number
  skipped: number
  failed: number
  notes: string[]
  failures: string[]
}

export interface AnalyticsHealthSnapshot {
  writeFailures: number
  retriedOk: number
  dropped: number
  pendingRetries: number
  lastFailureAt: string | null
  lastFailure: string | null
}

export interface AnalyticsOptions {
  /** Every value each filter can take (from the model registry and the data). */
  values: Record<string, string[]>
  /** Currency the UI shows costs in, and the recorded rate from stored USD costs. */
  displayCurrency: string
  usdToDisplay: number
  /** Users and recipes that have recorded AI requests, labelled by name. */
  users: AnalyticsChoice[]
  recipes: AnalyticsChoice[]
}

export interface AnalyticsChoice {
  id: string
  label: string
}

export type QueryParams = Record<string, string | number | boolean>

export const AiAnalyticsApi = {
  requests: (params: QueryParams) => apiGet<Page<AnalyticsRequest>>(`${BASE}/requests`, { params }),
  request: (id: string) => apiGet<RequestDetail>(`${BASE}/requests/${encodeURIComponent(id)}`),
  result: (id: string, options: { attemptId?: string; full?: boolean; prompt?: boolean } = {}) => {
    const params: QueryParams = {}
    if (options.attemptId) params.attemptId = options.attemptId
    if (options.full) params.full = true
    if (options.prompt) params.prompt = true
    return apiGet<AiResult>(`${BASE}/requests/${encodeURIComponent(id)}/result`, { params })
  },
  aggregate: (params: QueryParams) => apiGet<AggregateResult>(`${BASE}/aggregate`, { params }),
  overview: (params: QueryParams) => apiGet<Overview>(`${BASE}/overview`, { params }),
  compare: (params: QueryParams) => apiGet<Comparison>(`${BASE}/compare`, { params }),
  options: () => apiGet<AnalyticsOptions>(`${BASE}/options`),
  pricing: () => apiGet<PricingRateCard[]>(`${BASE}/pricing`),
  backfillRuns: () => apiGet<BackfillRun[]>(`${BASE}/backfill/runs`),
  runBackfill: () => apiPost<BackfillRun>(`${BASE}/backfill`),
  health: () => apiGet<AnalyticsHealthSnapshot>(`${BASE}/health`),
}
