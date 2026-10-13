/**
 * Filter state for the analytics pages and its translation to backend query parameters. Every filter is applied
 * by the backend; this only serializes.
 */

export type DatePreset = '24h' | '7d' | '30d' | '90d' | 'all' | 'custom'

export interface AnalyticsFilters {
  preset: DatePreset
  /** ISO date (yyyy-mm-dd), used when preset is 'custom'. */
  fromDate: string
  toDate: string
  requestType: string[]
  operationContext: string[]
  provider: string[]
  model: string[]
  tier: string[]
  status: string[]
  source: string[]
  userId: string
  recipeId: string
  workflowId: string
  jobId: string
  stepId: string
  requestId: string
  costMin: string
  costMax: string
  latencyMin: string
  latencyMax: string
  retryMin: string
}

export const emptyFilters = (): AnalyticsFilters => ({
  preset: '30d',
  fromDate: '',
  toDate: '',
  requestType: [],
  operationContext: [],
  provider: [],
  model: [],
  tier: [],
  status: [],
  source: [],
  userId: '',
  recipeId: '',
  workflowId: '',
  jobId: '',
  stepId: '',
  requestId: '',
  costMin: '',
  costMax: '',
  latencyMin: '',
  latencyMax: '',
  retryMin: '',
})

const PRESET_MS: Record<string, number> = {
  '24h': 24 * 3600_000,
  '7d': 7 * 24 * 3600_000,
  '30d': 30 * 24 * 3600_000,
  '90d': 90 * 24 * 3600_000,
}

/** The [from, to) range a preset covers, as ISO instants; undefined bounds are open. */
export function dateRange(filters: AnalyticsFilters, now: Date = new Date()): { from?: string; to?: string } {
  if (filters.preset === 'all') return {}
  if (filters.preset === 'custom') {
    const range: { from?: string; to?: string } = {}
    if (filters.fromDate) range.from = new Date(`${filters.fromDate}T00:00:00Z`).toISOString()
    if (filters.toDate) {
      const end = new Date(`${filters.toDate}T00:00:00Z`)
      end.setUTCDate(end.getUTCDate() + 1) // the whole end day is included
      range.to = end.toISOString()
    }
    return range
  }
  // Anchored to the minute, so the same filters always produce the same request (and cache key).
  const anchor = Math.floor(now.getTime() / 60_000) * 60_000
  return { from: new Date(anchor - PRESET_MS[filters.preset]).toISOString() }
}

const isNumber = (value: string) => value.trim() !== '' && !Number.isNaN(Number(value))

/**
 * Query parameters for the backend; empty filters are omitted, lists are comma-joined. Cost filters are typed in the
 * display currency and sent in USD (stored costs are USD): pass the display currency's USD rate, or 1 for USD.
 */
export function toQueryParams(filters: AnalyticsFilters, now: Date = new Date(), usdRate = 1): Record<string, string> {
  const params: Record<string, string> = {}
  const { from, to } = dateRange(filters, now)
  if (from) params.from = from
  if (to) params.to = to
  const lists: (keyof AnalyticsFilters)[] = ['requestType', 'operationContext', 'provider', 'model', 'tier', 'status', 'source']
  for (const key of lists) {
    const values = (filters[key] as string[]).filter((v) => v.trim() !== '')
    if (values.length) params[key] = values.join(',')
  }
  const texts: (keyof AnalyticsFilters)[] = ['recipeId', 'workflowId', 'jobId', 'stepId', 'requestId']
  for (const key of texts) {
    const value = (filters[key] as string).trim()
    if (value) params[key] = value
  }
  const numbers: (keyof AnalyticsFilters)[] = ['userId', 'latencyMin', 'latencyMax', 'retryMin']
  for (const key of numbers) {
    const value = filters[key] as string
    if (isNumber(value)) params[key] = value.trim()
  }
  for (const key of ['costMin', 'costMax'] as const) {
    const value = filters[key]
    if (isNumber(value)) params[key] = String(Number(value) / (usdRate || 1))
  }
  return params
}

/** How many filters beyond the date range are active (for the "Filters (n)" badge). */
export function activeFilterCount(filters: AnalyticsFilters): number {
  const params = toQueryParams(filters)
  return Object.keys(params).filter((key) => key !== 'from' && key !== 'to').length
}

/** A stable cache key for a request: sorted params plus any extras. */
export function requestKey(params: Record<string, string | number | boolean>): string {
  return Object.keys(params)
    .sort()
    .map((key) => `${key}=${params[key]}`)
    .join('&')
}

export const AI_ANALYTICS_PATH = '/kitchen/admin/ai-analytics'

export const GROUP_BY_LABELS: Record<string, string> = {
  requestType: 'Request type',
  operationContext: 'Operation context',
  provider: 'Provider',
  model: 'Model',
  tier: 'Model category',
  status: 'Status',
  source: 'Data source',
  workflow: 'Workflow',
  recipe: 'Recipe',
  step: 'Step',
  user: 'User',
  day: 'Day',
  week: 'Week',
  month: 'Month',
}
