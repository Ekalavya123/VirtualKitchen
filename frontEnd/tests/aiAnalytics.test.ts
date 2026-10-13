import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import {
  MISSING,
  configureDisplayCurrency,
  conversionNote,
  costBreakdown,
  formatCost,
  formatMs,
  formatPercent,
  formatTokens,
  humanize,
  withSamples,
} from '../src/features/admin/ai-analytics/model/analyticsFormat.ts'
import {
  activeFilterCount,
  dateRange,
  emptyFilters,
  requestKey,
  toQueryParams,
} from '../src/features/admin/ai-analytics/model/analyticsQuery.ts'

describe('analytics formatting', () => {
  it('never shows a missing cost or token count as zero', () => {
    assert.equal(formatCost(null), MISSING)
    assert.equal(formatCost(undefined), MISSING)
    assert.equal(formatTokens(null), 'Not reported')
    assert.equal(formatCost(0), '$0')
    assert.equal(formatTokens(0), '0')
  })

  it('keeps tiny costs distinguishable from zero', () => {
    assert.equal(formatCost(0.000042), '$0.000042')
    assert.equal(formatCost(0.0421), '$0.0421')
    assert.equal(formatCost(12.5, 'EUR'), 'EUR 12.500')
  })

  it('formats durations and rates', () => {
    assert.equal(formatMs(450), '450 ms')
    assert.equal(formatMs(2500), '2.50 s')
    assert.equal(formatMs(125_000), '2m 5s')
    assert.equal(formatPercent(0.25), '25%')
    assert.equal(formatPercent(0.05), '5.0%')
    assert.equal(formatPercent(null), MISSING)
    assert.equal(humanize('RECIPE_FLOW_GENERATION'), 'Recipe flow generation')
  })

  it('reports sample counts and hides statistics with no samples', () => {
    assert.equal(withSamples('$0.01', 12), '$0.01 (n=12)')
    assert.equal(withSamples('$0.01', 0), MISSING)
  })

  it('builds a cost breakdown that labels basis, local compute and incomplete totals', () => {
    const lines = costBreakdown({
      inputCost: 0.15, cachedInputCost: 0.01, outputCost: 0.3, directCost: 0.46, totalCost: 0.46,
      currency: 'USD', costBasis: 'CALCULATED', pricingVersion: 'v1', complete: false, uncostedAttempts: 1,
    })
    const byLabel = Object.fromEntries(lines.map((l) => [l.label, l.value]))
    assert.equal(byLabel.Input, '$0.1500')
    assert.equal(byLabel.Total, '$0.4600')
    assert.equal(byLabel.Basis, 'Calculated')
    assert.equal(byLabel['Rate card'], 'v1')
    assert.match(byLabel.Incomplete, /1 call/)

    const local = Object.fromEntries(costBreakdown({ directCost: 0, totalCost: 0, infraCostBasis: 'UNAVAILABLE', costBasis: 'CALCULATED' })
      .map((l) => [l.label, l.value]))
    assert.equal(local.Compute, 'Not estimated')
    assert.equal(costBreakdown(null)[0].value, 'Unavailable')
  })
})

describe('analytics query parameters', () => {
  const now = new Date('2026-10-11T12:00:00Z')

  it('omits empty filters and joins lists', () => {
    const filters = { ...emptyFilters(), operationContext: ['CHAT', 'RECIPE_FLOW_GENERATION'], model: [], recipeId: ' 55 ' }
    const params = toQueryParams(filters, now)
    assert.equal(params.operationContext, 'CHAT,RECIPE_FLOW_GENERATION')
    assert.equal(params.recipeId, '55')
    assert.equal(params.model, undefined)
    assert.equal(params.from, '2026-09-11T12:00:00.000Z')
    assert.equal(params.to, undefined)
  })

  it('ignores non-numeric numeric filters', () => {
    const params = toQueryParams({ ...emptyFilters(), costMin: 'abc', latencyMax: '5000', retryMin: '1' }, now)
    assert.equal(params.costMin, undefined)
    assert.equal(params.latencyMax, '5000')
    assert.equal(params.retryMin, '1')
  })

  it('custom ranges include the whole end day; "all" is unbounded', () => {
    assert.deepEqual(dateRange({ ...emptyFilters(), preset: 'custom', fromDate: '2026-10-01', toDate: '2026-10-05' }, now), {
      from: '2026-10-01T00:00:00.000Z',
      to: '2026-10-06T00:00:00.000Z',
    })
    assert.deepEqual(dateRange({ ...emptyFilters(), preset: 'all' }, now), {})
  })

  it('counts active filters beyond the date range and builds stable keys', () => {
    assert.equal(activeFilterCount(emptyFilters()), 0)
    assert.equal(activeFilterCount({ ...emptyFilters(), status: ['FAILED'], userId: '7' }), 2)
    assert.equal(requestKey({ b: 1, a: 'x' }), requestKey({ a: 'x', b: 1 }))
  })
})

describe('display currency', () => {
  it('shows stored USD costs in INR at the recorded rate, and missing costs as missing', () => {
    configureDisplayCurrency('INR', 88)
    try {
      assert.equal(formatCost(0.0391), '₹3.441')
      assert.equal(formatCost(0.00004), '₹0.003520')
      assert.equal(formatCost(null), MISSING)
      assert.equal(formatCost(0), '₹0')
      assert.equal(conversionNote(), '1 USD = ₹88.00')
    } finally {
      configureDisplayCurrency('USD', null)
    }
    assert.equal(formatCost(0.0391), '$0.0391')
  })

  it('sends cost filters typed in INR to the backend in USD', () => {
    const params = toQueryParams({ ...emptyFilters(), costMin: '8.8', costMax: '' }, new Date('2026-10-11T12:00:00Z'), 88)
    assert.equal(params.costMin, '0.1')
    assert.equal(params.costMax, undefined)
  })
})
