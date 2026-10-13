/**
 * Display formatting for AI analytics. A missing value (null/undefined) is always shown as missing — "—" or
 * "Not reported" — and never as 0, so unrecorded historical usage or cost can't be mistaken for free.
 */

import { GROUP_BY_LABELS } from './analyticsQuery.ts'

export const MISSING = '—'

type Maybe<T> = T | null | undefined

const isMissing = (value: Maybe<number>): value is null | undefined =>
  value === null || value === undefined || Number.isNaN(value)

/**
 * Costs are stored in USD (provider price lists are in USD). The admin UI shows them in a display currency using
 * the backend's recorded conversion rate ({@code ai.request.usd-to-inr}); until a rate is configured, USD is shown.
 */
const display: { code: string; symbol: string; usdRate: number | null } = { code: 'USD', symbol: '$', usdRate: null }

const SYMBOLS: Record<string, string> = { INR: '₹', USD: '$' }

export function configureDisplayCurrency(code: string, usdRate: number | null): void {
  display.code = usdRate ? code : 'USD'
  display.symbol = usdRate ? SYMBOLS[code] ?? `${code} ` : '$'
  display.usdRate = usdRate
}

/** The currency costs are shown in, e.g. "INR". */
export function displayCurrency(): string {
  return display.code
}

/** The conversion applied to stored USD amounts, e.g. "1 USD = ₹88.00", or null when costs are shown in USD. */
export function conversionNote(): string | null {
  return display.usdRate ? `1 USD = ${display.symbol}${display.usdRate.toFixed(2)}` : null
}

/** USD amount → display currency amount (identity when showing USD). */
export function toDisplayAmount(usd: number): number {
  return display.usdRate ? usd * display.usdRate : usd
}

/** Display currency amount → USD, for cost filters the admin types in the display currency. */
export function fromDisplayAmount(amount: number): number {
  return display.usdRate ? amount / display.usdRate : amount
}

/**
 * A stored cost shown in the display currency (USD amounts are converted; any other currency is shown as is).
 * Very small amounts keep enough precision to stay distinguishable from zero.
 */
export function formatCost(value: Maybe<number>, currency: Maybe<string> = 'USD'): string {
  if (isMissing(value)) return MISSING
  const stored = currency || 'USD'
  const converted = stored === 'USD' && display.usdRate !== null
  const amount = converted ? value * (display.usdRate as number) : value
  const symbol = converted ? display.symbol : stored === 'USD' ? '$' : `${stored} `
  if (amount === 0) return `${symbol}0`
  const abs = Math.abs(amount)
  const digits = abs >= 100 ? 2 : abs >= 1 ? 3 : abs >= 0.01 ? 4 : 6
  return `${symbol}${amount.toFixed(digits)}`
}

export function formatCount(value: Maybe<number>): string {
  if (isMissing(value)) return MISSING
  return Math.round(value).toLocaleString('en-US')
}

/** Token counts, with an explicit label when the provider did not report them. */
export function formatTokens(value: Maybe<number>): string {
  return isMissing(value) ? 'Not reported' : formatCount(value)
}

export function formatMs(value: Maybe<number>): string {
  if (isMissing(value)) return MISSING
  if (value < 1000) return `${Math.round(value)} ms`
  if (value < 60_000) return `${(value / 1000).toFixed(value < 10_000 ? 2 : 1)} s`
  const minutes = Math.floor(value / 60_000)
  const seconds = Math.round((value % 60_000) / 1000)
  return `${minutes}m ${seconds}s`
}

export function formatPercent(value: Maybe<number>): string {
  if (isMissing(value)) return MISSING
  return `${(value * 100).toFixed(value < 0.1 && value > 0 ? 1 : 0)}%`
}

export function formatBytes(value: Maybe<number>): string {
  if (isMissing(value)) return MISSING
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / (1024 * 1024)).toFixed(1)} MB`
}

export function formatDateTime(value: Maybe<string>): string {
  if (!value) return MISSING
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return MISSING
  return date.toLocaleString('en-GB', {
    year: 'numeric', month: 'short', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit',
  })
}

/** "RECIPE_FLOW_GENERATION" → "Recipe flow generation". */
export function humanize(value: Maybe<string>): string {
  if (!value) return MISSING
  const words = value.replace(/[_-]+/g, ' ').toLowerCase().trim()
  return words.charAt(0).toUpperCase() + words.slice(1)
}

export const COST_BASIS_LABELS: Record<string, string> = {
  PROVIDER_REPORTED: 'Provider reported',
  CALCULATED: 'Calculated',
  ESTIMATED: 'Estimate',
  UNAVAILABLE: 'Unavailable',
}

export function costBasisLabel(basis: Maybe<string>): string {
  return basis ? COST_BASIS_LABELS[basis] ?? humanize(basis) : 'Unavailable'
}

export type StatusTone = 'good' | 'bad' | 'warn' | 'neutral' | 'info'

export function statusTone(status: Maybe<string>): StatusTone {
  switch (status) {
    case 'SUCCEEDED':
      return 'good'
    case 'FAILED':
    case 'TIMED_OUT':
      return 'bad'
    case 'REJECTED':
    case 'CANCELLED':
      return 'warn'
    case 'QUEUED':
    case 'RUNNING':
      return 'info'
    default:
      return 'neutral'
  }
}

/** A cost with its sample count, e.g. "$0.0042 (n=12)"; missing when no request in the group was costed. */
export function withSamples(text: string, samples: number): string {
  return samples > 0 ? `${text} (n=${samples})` : MISSING
}

/** Short id for tables: first 8 characters. */
export function shortId(id: Maybe<string>): string {
  if (!id) return MISSING
  return id.length > 10 ? id.slice(0, 8) : id
}

export interface CostLine {
  label: string
  value: string
}

interface CostLike {
  inputCost?: number | null
  cachedInputCost?: number | null
  outputCost?: number | null
  reasoningCost?: number | null
  modalityCost?: number | null
  directCost?: number | null
  infraCost?: number | null
  totalCost?: number | null
  currency?: string | null
  costBasis?: string | null
  infraCostBasis?: string | null
  pricingVersion?: string | null
  complete?: boolean | null
  uncostedAttempts?: number | null
}

/** The lines of a cost breakdown, only listing the parts that exist. */
export function costBreakdown(cost: Maybe<CostLike>): CostLine[] {
  if (!cost) return [{ label: 'Cost', value: 'Unavailable' }]
  const currency = cost.currency
  const lines: CostLine[] = []
  const add = (label: string, value: Maybe<number>) => {
    if (!isMissing(value) && value !== 0) lines.push({ label, value: formatCost(value, currency) })
  }
  add('Input', cost.inputCost)
  add('Cached input', cost.cachedInputCost)
  add('Output', cost.outputCost)
  add('Reasoning', cost.reasoningCost)
  add('Per image / audio', cost.modalityCost)
  lines.push({ label: 'Direct (provider bill)', value: formatCost(cost.directCost, currency) })
  if (!isMissing(cost.infraCost)) {
    lines.push({ label: 'Compute (estimated)', value: formatCost(cost.infraCost, currency) })
  } else if (cost.infraCostBasis === 'UNAVAILABLE' && cost.directCost === 0) {
    lines.push({ label: 'Compute', value: 'Not estimated' })
  }
  lines.push({ label: 'Total', value: formatCost(cost.totalCost, currency) })
  lines.push({ label: 'Basis', value: costBasisLabel(cost.costBasis) })
  if (cost.pricingVersion) lines.push({ label: 'Rate card', value: cost.pricingVersion })
  if (cost.complete === false) {
    lines.push({
      label: 'Incomplete',
      value: `${cost.uncostedAttempts ?? 'Some'} call(s) could not be priced`,
    })
  }
  return lines
}

/** A group-by dimension value for display; with a null value, the dimension's own name. */
export function dimensionLabel(dimension: string, value: string | null): string {
  if (value === null) return GROUP_BY_LABELS[dimension] ?? humanize(dimension)
  if (dimension === 'workflow') return shortId(value)
  return ['operationContext', 'requestType', 'tier', 'status', 'source'].includes(dimension) ? humanize(value) : value
}
