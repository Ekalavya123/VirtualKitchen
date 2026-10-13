import { useState } from 'react'
import { AiAnalyticsApi } from '../api/aiAnalyticsApi'
import type { BackfillRun } from '../api/aiAnalyticsApi'
import { LoadState, Stat } from '../components/common'
import { useApiData } from '../hooks/useApiData'
import { MISSING, formatCost, formatDateTime, humanize } from '../model/analyticsFormat'

/** Rate cards, the historical backfill, and analytics write health. */
export default function DataPage() {
  const pricing = useApiData('pricing', () => AiAnalyticsApi.pricing())
  const runs = useApiData('runs', () => AiAnalyticsApi.backfillRuns())
  const health = useApiData('health', () => AiAnalyticsApi.health())
  const [running, setRunning] = useState(false)
  const [runError, setRunError] = useState<string | null>(null)
  const [lastRun, setLastRun] = useState<BackfillRun | null>(null)

  const runBackfill = async () => {
    setRunning(true)
    setRunError(null)
    try {
      setLastRun(await AiAnalyticsApi.runBackfill())
      runs.reload()
    } catch (error) {
      setRunError(error instanceof Error ? error.message : 'Backfill failed')
    } finally {
      setRunning(false)
    }
  }

  const h = health.data
  return (
    <div className="aia-page">
      <section className="aia-card">
        <h3>Analytics health</h3>
        <LoadState loading={health.loading && !h} error={health.error} onRetry={health.reload} />
        {h && (
          <>
            <div className="aia-stats">
              <Stat label="Failed writes" value={h.writeFailures.toLocaleString()} note="queued for retry" />
              <Stat label="Retried OK" value={h.retriedOk.toLocaleString()} />
              <Stat label="Pending retries" value={h.pendingRetries.toLocaleString()} />
              <Stat label="Dropped" value={h.dropped.toLocaleString()} note={h.dropped > 0 ? 'analytics data was lost' : 'no data lost'} />
            </div>
            {h.lastFailure && <p className="aia-muted aia-small">Last failure: {h.lastFailure} at {formatDateTime(h.lastFailureAt)}</p>}
            <p className="aia-muted aia-small">Counters reset when the backend restarts.</p>
          </>
        )}
      </section>

      <section className="aia-card">
        <h3>Historical backfill</h3>
        <p className="aia-muted aia-small">
          Imports AI requests made before analytics existed, from ai_request_jobs and chat ai_responses. Only recorded values
          are imported — missing usage, cost and timing stay unavailable, and legacy costs are labelled as estimates. Safe to
          run again: existing analytics records are never duplicated or changed, and source data is only read.
        </p>
        <button type="button" className="aia-button aia-button--primary" disabled={running} onClick={runBackfill}>
          {running ? 'Running…' : 'Run backfill'}
        </button>
        {runError && <div className="aia-alert aia-alert--bad" role="alert">{runError}</div>}
        {lastRun && <p className="aia-alert">Finished: {lastRun.recovered} recovered, {lastRun.partiallyRecovered} partial, {lastRun.alreadyPresent} already present, {lastRun.skipped} skipped, {lastRun.failed} failed.</p>}
        <LoadState loading={runs.loading && !runs.data} error={runs.error} onRetry={runs.reload} />
        {runs.data && runs.data.length > 0 && (
          <div className="aia-table-scroll">
            <table className="aia-table aia-table--compact">
              <thead>
                <tr>
                  <th>Started</th><th>Status</th><th className="aia-num">Scanned</th><th className="aia-num">Recovered</th>
                  <th className="aia-num">Partial</th><th className="aia-num">Already present</th><th className="aia-num">Skipped</th>
                  <th className="aia-num">Failed</th><th>Notes</th>
                </tr>
              </thead>
              <tbody>
                {runs.data.map((run) => (
                  <tr key={run.id}>
                    <td>{formatDateTime(run.startedAt)}</td>
                    <td>{humanize(run.status)}</td>
                    <td className="aia-num">{run.scanned}</td>
                    <td className="aia-num">{run.recovered}</td>
                    <td className="aia-num">{run.partiallyRecovered}</td>
                    <td className="aia-num">{run.alreadyPresent}</td>
                    <td className="aia-num">{run.skipped}</td>
                    <td className="aia-num">{run.failed}</td>
                    <td className="aia-small">{[...run.notes, ...run.failures].join(' ') || MISSING}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section className="aia-card">
        <h3>Rate cards</h3>
        <p className="aia-muted aia-small">
          Prices come from the model registry configuration and are versioned (vk.ai.pricing.version). Each request keeps the
          card it was priced with; a price change publishes a new version and never rewrites past costs.
        </p>
        <LoadState loading={pricing.loading && !pricing.data} error={pricing.error} onRetry={pricing.reload} />
        {pricing.data && (
          <div className="aia-table-scroll">
            <table className="aia-table aia-table--compact">
              <thead>
                <tr>
                  <th>Card</th><th>Modality</th><th>Category</th><th className="aia-num">In /1M</th><th className="aia-num">Cached /1M</th>
                  <th className="aia-num">Out /1M</th><th className="aia-num">Per image</th><th className="aia-num">Compute /s</th>
                  <th>Effective</th><th>Active</th>
                </tr>
              </thead>
              <tbody>
                {pricing.data.map((card) => (
                  <tr key={card.id}>
                    <td className="aia-mono">{card.id}</td>
                    <td>{humanize(card.modality)}</td>
                    <td>{humanize(card.tier)}</td>
                    <td className="aia-num">{formatCost(card.inputPerMTok, card.currency)}</td>
                    <td className="aia-num">{formatCost(card.cachedInputPerMTok, card.currency)}</td>
                    <td className="aia-num">{formatCost(card.outputPerMTok, card.currency)}</td>
                    <td className="aia-num">{formatCost(card.perImage, card.currency)}</td>
                    <td className="aia-num">{formatCost(card.infraPerComputeSecond, card.currency)}</td>
                    <td>{formatDateTime(card.effectiveFrom)}{card.effectiveTo ? ` → ${formatDateTime(card.effectiveTo)}` : ''}</td>
                    <td>{card.active ? 'Yes' : 'No'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  )
}
