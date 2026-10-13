import { AiAnalyticsApi } from '../api/aiAnalyticsApi'
import type { GroupMetrics } from '../api/aiAnalyticsApi'
import { BarList, LineChart } from '../components/charts'
import { CostCell, DataNotes, LoadState, MetricsTable, Stat, StatusBadge } from '../components/common'
import { useApiData } from '../../../../shared/hooks/useApiData'
import {
  conversionNote,
  displayCurrency,
  formatCost,
  formatCount,
  formatDateTime,
  formatMs,
  formatPercent,
  humanize,
  shortId,
} from '../model/analyticsFormat'
import { requestKey } from '../model/analyticsQuery'

interface OverviewPageProps {
  params: Record<string, string>
  onOpenRequest: (id: string) => void
  onOpenWorkflow: (workflowId: string) => void
}

const costDetail = (g: GroupMetrics) =>
  `${formatCount(g.requestCount)} requests · ${g.uncostedCount ? `${g.uncostedCount} not costed` : 'all costed'}`

export default function OverviewPage({ params, onOpenRequest, onOpenWorkflow }: OverviewPageProps) {
  const { data, error, loading, reload } = useApiData(`overview?${requestKey(params)}`, () => AiAnalyticsApi.overview(params))
  const t = data?.totals
  const currency = t?.currencies[0] ?? 'USD'
  const money = (v: number | null) => formatCost(v, currency)

  return (
    <div className="aia-page">
      <LoadState loading={loading} error={error} onRetry={reload} />
      {data && t && (
        <>
          <div className="aia-stats">
            <Stat
              label={`Total cost (${displayCurrency()})`}
              value={money(t.totalCost)}
              note={t.costBases.length ? `${t.costBases.map(humanize).join(' + ')} · ${t.costedCount} costed` : 'No costed requests'}
            />
            <Stat label="Requests" value={formatCount(t.requestCount)} note={`${formatCount(t.attemptCount)} provider calls`} />
            <Stat label="Cost per successful result" value={money(t.costPerSuccess)} note={`${formatCount(t.succeeded)} succeeded`} />
            <Stat label="Avg / p95 latency" value={`${formatMs(t.avgLatencyMs.value)} / ${formatMs(t.p95LatencyMs.value)}`} note={`n=${t.avgLatencyMs.samples}`} />
            <Stat label="Failure rate" value={formatPercent(t.failureRate)} note={`${t.failed} failed · ${t.timedOut} timed out`} />
            <Stat label="Retry rate" value={formatPercent(t.retryRate)} note={`n=${t.retrySamples}`} />
          </div>
          <DataNotes metrics={t} truncated={data.truncated} />
          {conversionNote() && <p className="aia-muted aia-small">Costs are stored in USD and shown in {displayCurrency()} at {conversionNote()}.</p>}

          <div className="aia-grid-2">
            <section className="aia-card">
              <h3>Cost over time <span className="aia-muted">({data.bucket}, {displayCurrency()})</span></h3>
              <LineChart
                ariaLabel="Cost over time"
                format={money}
                data={data.overTime.map((g) => ({ label: g.key[data.bucket] ?? '—', value: g.totalCost, detail: costDetail(g) }))}
              />
            </section>
            <section className="aia-card">
              <h3>Requests over time</h3>
              <LineChart
                ariaLabel="Requests over time"
                format={(v) => formatCount(v)}
                data={data.overTime.map((g) => ({ label: g.key[data.bucket] ?? '—', value: g.requestCount, detail: `${g.failed + g.timedOut} failed` }))}
              />
            </section>
            <section className="aia-card">
              <h3>Cost by operation</h3>
              <BarList ariaLabel="Cost by operation" format={money}
                data={data.byContext.map((g) => ({ label: humanize(g.key.operationContext), value: g.totalCost, detail: costDetail(g) }))} />
            </section>
            <section className="aia-card">
              <h3>Cost by modality</h3>
              <BarList ariaLabel="Cost by modality" format={money}
                data={data.byModality.map((g) => ({ label: humanize(g.key.requestType), value: g.totalCost, detail: costDetail(g) }))} />
            </section>
            <section className="aia-card">
              <h3>Model usage (requests)</h3>
              <BarList ariaLabel="Requests by model" format={(v) => formatCount(v)}
                data={[...data.byModel].sort((a, b) => b.requestCount - a.requestCount)
                  .map((g) => ({ label: g.key.model ?? 'Unknown model', value: g.requestCount, detail: `${money(g.totalCost)} total` }))} />
            </section>
            <section className="aia-card">
              <h3>Most expensive workflows</h3>
              <BarList ariaLabel="Most expensive workflows" format={money} emptyText="No workflow-linked requests in this period."
                data={data.topWorkflows.map((g) => ({ label: shortId(g.key.workflow), value: g.totalCost, detail: costDetail(g) }))} />
              {data.topWorkflows.length > 0 && (
                <p className="aia-muted aia-small">
                  Open:{' '}
                  {data.topWorkflows.slice(0, 5).map((g) => (
                    <button key={g.key.workflow} type="button" className="aia-link" onClick={() => g.key.workflow && onOpenWorkflow(g.key.workflow)}>
                      {shortId(g.key.workflow)}
                    </button>
                  ))}
                </p>
              )}
            </section>
          </div>

          <section className="aia-card">
            <h3>Most expensive requests</h3>
            {data.topRequests.length === 0 ? <p className="aia-empty">No costed requests in this period.</p> : (
              <div className="aia-table-scroll">
                <table className="aia-table">
                  <thead><tr><th>When</th><th>Request</th><th>Operation</th><th>Model</th><th>Status</th><th className="aia-num">Cost</th></tr></thead>
                  <tbody>
                    {data.topRequests.map((r) => (
                      <tr key={r.id} className="aia-clickable" onClick={() => onOpenRequest(r.id)}>
                        <td>{formatDateTime(r.createdAt)}</td>
                        <td className="aia-mono">{shortId(r.id)}</td>
                        <td>{humanize(r.operationContext)}</td>
                        <td>{r.resolvedModelKey ?? 'Unknown'}</td>
                        <td><StatusBadge status={r.status} /></td>
                        <td className="aia-num"><CostCell cost={r.costTotals} total={r.totalCost} /></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>

          <section className="aia-card">
            <h3>By operation — table</h3>
            <MetricsTable groups={data.byContext} dimensions={['operationContext']}
              dimensionLabel={(_, v) => (v === null ? 'Operation' : humanize(v))} />
          </section>
        </>
      )}
    </div>
  )
}
