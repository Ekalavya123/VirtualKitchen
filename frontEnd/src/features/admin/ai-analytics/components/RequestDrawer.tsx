import { useEffect } from 'react'
import { AiAnalyticsApi } from '../api/aiAnalyticsApi'
import type { Attempt } from '../api/aiAnalyticsApi'
import { useApiData } from '../../../../shared/hooks/useApiData'
import {
  costBasisLabel,
  costBreakdown,
  formatBytes,
  formatCost,
  formatDateTime,
  formatMs,
  formatTokens,
  humanize,
} from '../model/analyticsFormat'
import { KeyValue, LoadState, StatusBadge } from './common'

interface RequestDrawerProps {
  requestId: string
  onClose: () => void
  onViewResult: (requestId: string) => void
}

function AttemptRow({ attempt }: { attempt: Attempt }) {
  const u = attempt.usage
  return (
    <li className="aia-attempt">
      <div className="aia-attempt-head">
        <b>Call {attempt.callIndex}</b>
        <span>{humanize(attempt.purpose)}</span>
        <StatusBadge status={attempt.status} />
        {attempt.transportAttempt && attempt.transportAttempt > 1 && <span className="aia-tag">retry {attempt.transportAttempt - 1}</span>}
        {attempt.late && <span className="aia-tag aia-tag--warn" title="Finished after the request had timed out">late</span>}
        {attempt.validationPassed === false && <span className="aia-tag aia-tag--warn">failed validation</span>}
      </div>
      <div className="aia-attempt-body">
        <span>{attempt.provider ?? 'unknown'} · {attempt.reportedModel ?? attempt.providerModelId ?? 'model not reported'}</span>
        <span>{formatMs(attempt.durationMs)}</span>
        <span>{formatTokens(u?.inputTokens)} in{u?.cachedInputTokens ? ` (${u.cachedInputTokens.toLocaleString()} cached)` : ''} / {formatTokens(u?.outputTokens)} out{u?.reasoningTokens ? ` + ${u.reasoningTokens.toLocaleString()} reasoning` : ''}</span>
        {u?.imageCount != null && <span>{u.imageCount} image{u.imageWidth ? ` ${u.imageWidth}×${u.imageHeight}` : ''}</span>}
        {u?.audioDurationMs != null && <span>{formatMs(u.audioDurationMs)} audio</span>}
        {u?.outputBytes != null && <span>{formatBytes(u.outputBytes)}</span>}
        <span>{attempt.cost?.totalCost == null ? 'cost unavailable' : `${formatCost(attempt.cost.totalCost, attempt.cost.currency)} · ${costBasisLabel(attempt.cost.costBasis)}`}</span>
      </div>
      {attempt.cost?.formula && <p className="aia-formula">Rate card (USD): {attempt.cost.formula}</p>}
      {attempt.errorMessage && <p className="aia-error-text">{humanize(attempt.errorCategory)}{attempt.httpStatus ? ` (HTTP ${attempt.httpStatus})` : ''}: {attempt.errorMessage}</p>}
      {attempt.validationErrors && attempt.validationErrors.length > 0 && (
        <ul className="aia-validation">{attempt.validationErrors.map((e) => <li key={e}>{e}</li>)}</ul>
      )}
    </li>
  )
}

/** Side drawer with everything recorded about one request. */
export default function RequestDrawer({ requestId, onClose, onViewResult }: RequestDrawerProps) {
  const { data, error, loading, reload } = useApiData(requestId, () => AiAnalyticsApi.request(requestId))
  const r = data?.request

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  return (
    <div className="aia-drawer-backdrop" onClick={onClose}>
      <aside className="aia-drawer" role="dialog" aria-modal="true" aria-label="Request details" onClick={(e) => e.stopPropagation()}>
        <header className="aia-modal-header">
          <div>
            <h2>{r ? humanize(r.operationContext) : 'Request'}</h2>
            <p className="aia-muted aia-mono">{requestId}</p>
          </div>
          <button type="button" className="aia-icon-button" aria-label="Close" onClick={onClose}>×</button>
        </header>
        <LoadState loading={loading && !r} error={error} onRetry={reload} />
        {r && (
          <div className="aia-drawer-body">
            <div className="aia-drawer-actions">
              <StatusBadge status={r.status} />
              <button type="button" className="aia-button aia-button--primary" onClick={() => onViewResult(r.id)}>View result</button>
            </div>
            {r.dataCompleteness && r.dataCompleteness !== 'COMPLETE' && (
              <p className="aia-alert">
                {r.dataCompleteness === 'HISTORICAL_PARTIAL' ? 'Historical record recovered by backfill. ' : 'Partially recorded. '}
                Not recorded: {(r.missingFields ?? []).map(humanize).join(', ') || '—'}.
              </p>
            )}

            <section>
              <h3>Request</h3>
              <dl className="aia-kvs">
                <KeyValue label="Type" value={humanize(r.requestType)} />
                <KeyValue label="Operation" value={humanize(r.operationContext)} />
                <KeyValue label="Source" value={humanize(r.source)} />
                <KeyValue label="User" value={r.userId} />
                <KeyValue label="Recipe" value={r.recipeId} />
                <KeyValue label="Process" value={r.processId} />
                <KeyValue label="Step" value={r.stepId} />
                <KeyValue label="Workflow" value={r.workflowId && <span className="aia-mono">{r.workflowId}</span>} />
                <KeyValue label="Job" value={r.jobId && <span className="aia-mono">{r.jobId}</span>} />
                <KeyValue label="Artifact" value={r.artifactId && <span className="aia-mono">{r.artifactId}</span>} />
                {r.reusedFromRequestId && <KeyValue label="Reused from" value={<span className="aia-mono">{r.reusedFromRequestId}</span>} />}
              </dl>
            </section>

            <section>
              <h3>Model</h3>
              <dl className="aia-kvs">
                <KeyValue label="Model" value={r.resolvedModelKey ?? 'Unknown'} />
                <KeyValue label="Provider" value={r.finalProvider} />
                <KeyValue label="Provider model" value={r.finalProviderModelId} />
                <KeyValue label="Category" value={humanize(r.modelTier)} />
                <KeyValue label="Requested" value={r.requestedModelKey ?? 'Default'} />
                <KeyValue label="Fallback" value={r.usedFallback ? `Yes (${humanize(r.fallbackReason)})` : r.usedFallback === false ? 'No' : null} />
                <KeyValue label="Credits" value={r.creditCost} />
              </dl>
            </section>

            <section>
              <h3>Usage</h3>
              <dl className="aia-kvs">
                <KeyValue label="Input tokens" value={formatTokens(r.usageTotals?.inputTokens)} />
                <KeyValue label="Cached input" value={formatTokens(r.usageTotals?.cachedInputTokens)} />
                <KeyValue label="Output tokens" value={formatTokens(r.usageTotals?.outputTokens)} />
                <KeyValue label="Reasoning" value={formatTokens(r.usageTotals?.reasoningTokens)} />
                <KeyValue label="Basis" value={humanize(r.usageTotals?.usageBasis ?? 'NOT_REPORTED')} />
                <KeyValue label="Provider calls" value={r.attemptCount} />
              </dl>
            </section>

            <section>
              <h3>Cost</h3>
              <dl className="aia-kvs">
                {costBreakdown(r.costTotals).map((line) => <KeyValue key={line.label} label={line.label} value={line.value} />)}
              </dl>
              {r.costTotals?.formula && <p className="aia-formula">Rate card (USD): {r.costTotals.formula}</p>}
              {data.pricing.length > 0 && (
                <div className="aia-table-scroll">
                  <table className="aia-table aia-table--compact">
                    <thead><tr><th>Rate card</th><th className="aia-num">In /M</th><th className="aia-num">Cached /M</th><th className="aia-num">Out /M</th><th>Effective from</th></tr></thead>
                    <tbody>
                      {data.pricing.map((card) => (
                        <tr key={card.id}>
                          <td className="aia-mono">{card.id}</td>
                          <td className="aia-num">{formatCost(card.inputPerMTok, card.currency)}</td>
                          <td className="aia-num">{formatCost(card.cachedInputPerMTok, card.currency)}</td>
                          <td className="aia-num">{formatCost(card.outputPerMTok, card.currency)}</td>
                          <td>{formatDateTime(card.effectiveFrom)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </section>

            <section>
              <h3>Timing</h3>
              <dl className="aia-kvs">
                <KeyValue label="Queued" value={formatDateTime(r.queuedAt ?? r.createdAt)} />
                <KeyValue label="Started" value={formatDateTime(r.startedAt)} />
                <KeyValue label="Completed" value={formatDateTime(r.completedAt)} />
                <KeyValue label="Queue wait" value={formatMs(r.queueWaitMs)} />
                <KeyValue label="Execution" value={formatMs(r.executionMs)} />
                <KeyValue label="Total" value={formatMs(r.totalMs)} />
                <KeyValue label="Timeout" value={formatMs(r.timeoutMs)} />
                <KeyValue label="Pool / depth" value={r.poolName ? `${r.poolName}${r.queueDepthAtSubmit ? ` · ${r.queueDepthAtSubmit} in flight` : ''}` : null} />
                <KeyValue label="Transport attempts" value={r.transportAttempts} />
              </dl>
            </section>

            {r.errorMessage && (
              <section>
                <h3>Error</h3>
                <p className="aia-error-text">{humanize(r.errorCategory)}: {r.errorMessage}</p>
              </section>
            )}

            <section>
              <h3>Provider calls</h3>
              {(r.attempts ?? []).length === 0
                ? <p className="aia-muted">{r.source === 'LIVE' ? 'No provider call was made.' : 'Per-call detail was not recorded for historical requests.'}</p>
                : <ol className="aia-attempts">{(r.attempts ?? []).map((a) => <AttemptRow key={a.attemptId} attempt={a} />)}</ol>}
            </section>

            {(r.statusHistory ?? []).length > 0 && (
              <section>
                <h3>Status history</h3>
                <ol className="aia-history">
                  {(r.statusHistory ?? []).map((s, i) => <li key={`${s.status}-${i}`}>{humanize(s.status)} · {formatDateTime(s.at)}</li>)}
                </ol>
              </section>
            )}
          </div>
        )}
      </aside>
    </div>
  )
}
