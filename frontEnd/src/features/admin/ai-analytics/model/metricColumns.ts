import type { GroupMetrics } from '../api/aiAnalyticsApi'
import {
  MISSING,
  costBasisLabel,
  formatCost,
  formatCount,
  formatMs,
  formatPercent,
  withSamples,
} from './analyticsFormat'

/** The metric columns grouped tables can show; definitions in {@code AnalyticsMath} on the backend. */
export interface MetricColumn {
  id: string
  label: string
  render: (m: GroupMetrics) => string
  title?: string
}

export const METRIC_COLUMNS: MetricColumn[] = [
  { id: 'requests', label: 'Requests', render: (m) => formatCount(m.requestCount), title: 'Logical requests (not provider calls)' },
  { id: 'attempts', label: 'Calls', render: (m) => (m.attemptCount === null ? MISSING : formatCount(m.attemptCount)), title: 'Provider calls, retries included' },
  { id: 'ok', label: 'OK', render: (m) => formatCount(m.succeeded) },
  { id: 'failed', label: 'Failed', render: (m) => formatCount(m.failed) },
  { id: 'timeouts', label: 'Timeouts', render: (m) => formatCount(m.timedOut) },
  { id: 'failureRate', label: 'Failure rate', render: (m) => formatPercent(m.failureRate) },
  { id: 'retryRate', label: 'Retry rate', render: (m) => formatPercent(m.retryRate) },
  { id: 'total', label: 'Total cost', render: (m) => formatCost(m.totalCost, m.currencies[0]), title: 'Costed requests only' },
  { id: 'avg', label: 'Avg / request', render: (m) => withSamples(formatCost(m.avgCost.value, m.currencies[0]), m.avgCost.samples) },
  { id: 'median', label: 'Median', render: (m) => formatCost(m.medianCost.value, m.currencies[0]) },
  { id: 'minmax', label: 'Min – max', render: (m) => (m.minCost === null ? MISSING : `${formatCost(m.minCost, m.currencies[0])} – ${formatCost(m.maxCost, m.currencies[0])}`) },
  { id: 'perSuccess', label: 'Per success', render: (m) => formatCost(m.costPerSuccess, m.currencies[0]), title: 'Total cost, failures included, ÷ successful requests' },
  { id: 'infra', label: 'Est. compute', render: (m) => (m.infraCostSamples > 0 ? withSamples(formatCost(m.infraCost), m.infraCostSamples) : MISSING), title: 'Estimated local compute cost (separate from provider charges)' },
  { id: 'in', label: 'Avg in tokens', render: (m) => withSamples(formatCount(m.avgInputTokens.value), m.avgInputTokens.samples) },
  { id: 'out', label: 'Avg out tokens', render: (m) => withSamples(formatCount(m.avgOutputTokens.value), m.avgOutputTokens.samples) },
  { id: 'latency', label: 'Avg latency', render: (m) => withSamples(formatMs(m.avgLatencyMs.value), m.avgLatencyMs.samples) },
  { id: 'p50', label: 'p50', render: (m) => formatMs(m.p50LatencyMs.value) },
  { id: 'p95', label: 'p95', render: (m) => formatMs(m.p95LatencyMs.value) },
  { id: 'queue', label: 'Avg queue wait', render: (m) => withSamples(formatMs(m.avgQueueWaitMs.value), m.avgQueueWaitMs.samples) },
  { id: 'validation', label: 'Validation pass', render: (m) => (m.validationSamples ? withSamples(formatPercent(m.validationPassRate), m.validationSamples) : MISSING) },
  { id: 'basis', label: 'Cost basis', render: (m) => m.costBases.map(costBasisLabel).join(', ') || MISSING },
]

export const DEFAULT_METRIC_COLUMNS = ['requests', 'attempts', 'failureRate', 'retryRate', 'total', 'avg', 'perSuccess', 'in', 'out', 'latency', 'p95']

