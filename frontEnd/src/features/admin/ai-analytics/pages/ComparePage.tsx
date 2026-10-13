import { useState } from 'react'
import { AiAnalyticsApi } from '../api/aiAnalyticsApi'
import type { GroupMetrics } from '../api/aiAnalyticsApi'
import { BarList } from '../components/charts'
import { LoadState, MetricsTable } from '../components/common'
import { useApiData } from '../hooks/useApiData'
import { displayCurrency, formatCost, formatMs, formatPercent, humanize } from '../model/analyticsFormat'
import { requestKey } from '../model/analyticsQuery'

interface ComparePageProps {
  params: Record<string, string>
  contexts: string[]
}

const COMPARE_COLUMNS = ['requests', 'attempts', 'total', 'avg', 'perSuccess', 'infra', 'in', 'out', 'latency', 'p95', 'failureRate', 'retryRate', 'validation', 'basis']

const modelLabel = (g: GroupMetrics) =>
  `${g.key.model ?? 'Unknown model'}${g.key.tier === 'OPEN_SOURCE' ? ' (local)' : ''}`

/** Models side by side for one operation context, so only models that did the same job are compared. */
export default function ComparePage({ params, contexts }: ComparePageProps) {
  const [context, setContext] = useState<string>('')
  const operationContext = context || contexts.find((c) => c !== 'UNKNOWN') || ''
  const query: Record<string, string> = { ...params, operationContext }
  delete query.model
  const { data, error, loading, reload } = useApiData(operationContext ? `compare?${requestKey(query)}` : null,
    () => AiAnalyticsApi.compare(query))
  const currency = data?.models.find((m) => m.currencies.length)?.currencies[0] ?? 'USD'

  return (
    <div className="aia-page">
      <section className="aia-card">
        <label className="aia-inline-field">
          <span>Operation</span>
          <select value={operationContext} onChange={(e) => setContext(e.target.value)}>
            {contexts.length === 0 && <option value="">No data yet</option>}
            {contexts.map((c) => <option key={c} value={c}>{humanize(c)}</option>)}
          </select>
        </label>
        <p className="aia-muted aia-small">
          Only models that served this same operation are compared. Prompts and output requirements are fixed per operation,
          but a fallback model may have produced a lower-quality result — check validation rates and inspect outputs before
          drawing routing conclusions. Local models show a provider charge of $0; their estimated compute cost (when a rate is
          configured) is shown separately. Routing is not changed by this tool.
        </p>
      </section>
      <LoadState loading={loading && !data} error={error} onRetry={reload} />
      {data && (
        <>
          {data.models.length === 0 && <p className="aia-empty">No requests for this operation in the selected period.</p>}
          {data.truncated && <p className="aia-notes">Only the first 200,000 matching requests were included; narrow the filters for exact figures.</p>}
          {data.models.length > 0 && (
            <>
              <div className="aia-grid-2">
                <section className="aia-card">
                  <h3>Average cost per request <span className="aia-muted">({displayCurrency()})</span></h3>
                  <BarList ariaLabel="Average cost per request by model" format={(v) => formatCost(v, currency)}
                    data={data.models.map((m) => ({ label: modelLabel(m), value: m.avgCost.value, detail: `n=${m.avgCost.samples}` }))} />
                </section>
                <section className="aia-card">
                  <h3>Cost per successful result</h3>
                  <BarList ariaLabel="Cost per successful result by model" format={(v) => formatCost(v, currency)}
                    data={data.models.map((m) => ({ label: modelLabel(m), value: m.costPerSuccess, detail: `${m.succeeded} succeeded` }))} />
                </section>
                <section className="aia-card">
                  <h3>p95 latency</h3>
                  <BarList ariaLabel="p95 latency by model" format={(v) => formatMs(v)}
                    data={data.models.map((m) => ({ label: modelLabel(m), value: m.p95LatencyMs.value, detail: `n=${m.p95LatencyMs.samples}` }))} />
                </section>
                <section className="aia-card">
                  <h3>Failure rate</h3>
                  <BarList ariaLabel="Failure rate by model" format={(v) => formatPercent(v)}
                    data={data.models.map((m) => ({ label: modelLabel(m), value: m.failureRate, detail: `${m.failed + m.timedOut} failed of ${m.succeeded + m.failed + m.timedOut}` }))} />
                </section>
              </div>
              <section className="aia-card">
                <h3>All metrics</h3>
                <MetricsTable groups={data.models} dimensions={['model', 'provider', 'tier']} columns={COMPARE_COLUMNS}
                  dimensionLabel={(d, v) => (v === null ? { model: 'Model', provider: 'Provider', tier: 'Category' }[d] ?? d : d === 'tier' ? humanize(v) : v)} />
              </section>
            </>
          )}
        </>
      )}
    </div>
  )
}
