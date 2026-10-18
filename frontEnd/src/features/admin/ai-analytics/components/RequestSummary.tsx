import { AiAnalyticsApi } from '../api/aiAnalyticsApi'
import { LoadState, Stat } from './common'
import { useApiData } from '../../../../shared/hooks/useApiData'
import { formatCost, formatCount, formatMs, formatPercent, formatTokens } from '../model/analyticsFormat'
import { requestKey } from '../model/analyticsQuery'

/** Totals over every request matching the filters (not just the page shown), computed by the backend. */
export default function RequestSummary({ params }: { params: Record<string, string> }) {
  const { data, error, loading, reload } = useApiData(`summary?${requestKey(params)}`,
    () => AiAnalyticsApi.aggregate(params))
  const t = data?.totals
  const currency = t?.currencies.length === 1 ? t.currencies[0] : 'USD'
  const cost = (value: number | null | undefined) => formatCost(value ?? null, currency)
  const failed = t ? t.failed + t.timedOut : 0
  const other = t ? t.rejected + t.cancelled + t.reused + t.inFlight : 0
  const finished = t ? t.succeeded + failed : 0
  const costNote = t && t.uncostedCount > 0 ? `${formatCount(t.uncostedCount)} without a known cost` : undefined

  return (
    <section className="aia-summary" aria-label="Summary of matching requests">
      <h2>Summary of all matching requests</h2>
      <LoadState loading={loading && !data} error={error} onRetry={reload} />
      {t && (
        <div className={loading ? 'is-loading' : undefined}>
          <div className="aia-stats">
            <Stat label="Requests" value={formatCount(t.requestCount)} />
            <Stat label="Succeeded" value={formatCount(t.succeeded)} />
            <Stat label="Failed" value={formatCount(failed)}
              note={t.timedOut > 0 ? `incl. ${formatCount(t.timedOut)} timed out` : undefined} />
            <Stat label="Success rate" value={finished > 0 ? formatPercent(t.succeeded / finished) : '—'}
              note="of finished requests" />
            {other > 0 && (
              <Stat label="Other" value={formatCount(other)}
                note={[
                  t.rejected && `${t.rejected} rejected`,
                  t.cancelled && `${t.cancelled} cancelled`,
                  t.reused && `${t.reused} reused`,
                  t.inFlight && `${t.inFlight} in flight`,
                ].filter(Boolean).join(', ')} />
            )}
          </div>
          <div className="aia-stats">
            <Stat label="Total cost" value={cost(t.totalCost)} note={costNote} />
            <Stat label="Average cost" value={cost(t.avgCost.value)} />
            <Stat label="Median cost" value={cost(t.medianCost.value)} />
            <Stat label="Min cost" value={cost(t.minCost)} />
            <Stat label="Max cost" value={cost(t.maxCost)} />
          </div>
          <div className="aia-stats">
            <Stat label="Avg latency" value={formatMs(t.avgLatencyMs.value)} />
            <Stat label="P95 latency" value={formatMs(t.p95LatencyMs.value)} />
            <Stat label="Avg input tokens" value={formatTokens(t.avgInputTokens.value)} />
            <Stat label="Avg output tokens" value={formatTokens(t.avgOutputTokens.value)} />
            <Stat label="Retry rate" value={formatPercent(t.retryRate)} note="requests needing a transport retry" />
          </div>
          {data.truncated && (
            <p className="aia-muted aia-small">Summary covers the first 200,000 matching requests; narrow the filters for exact figures.</p>
          )}
        </div>
      )}
    </section>
  )
}
