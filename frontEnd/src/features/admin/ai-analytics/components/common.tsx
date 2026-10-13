import { useState } from 'react'
import type { Cost, GroupMetrics } from '../api/aiAnalyticsApi'
import type { ReactNode } from 'react'
import { MISSING, costBreakdown, formatCost, humanize, statusTone } from '../model/analyticsFormat'
import { DEFAULT_METRIC_COLUMNS, METRIC_COLUMNS } from '../model/metricColumns'

export function StatusBadge({ status }: { status: string | null | undefined }) {
  const tone = statusTone(status)
  const icon = tone === 'good' ? '✓' : tone === 'bad' ? '✕' : tone === 'warn' ? '!' : tone === 'info' ? '…' : '·'
  return (
    <span className={`aia-badge aia-badge--${tone}`}>
      <span aria-hidden="true">{icon}</span> {humanize(status)}
    </span>
  )
}

/** A cost with its basis, and its breakdown on hover/focus or click. */
export function CostCell({ cost, total }: { cost: Cost | null | undefined; total?: number | null }) {
  const [open, setOpen] = useState(false)
  const value = total !== undefined ? total : cost?.totalCost ?? null
  const basis = cost?.costBasis
  return (
    <span
      className="aia-cost"
      tabIndex={0}
      onMouseEnter={() => setOpen(true)}
      onMouseLeave={() => setOpen(false)}
      onFocus={() => setOpen(true)}
      onBlur={() => setOpen(false)}
    >
      <span className={value === null ? 'aia-muted' : undefined}>{value === null ? 'Unavailable' : formatCost(value, cost?.currency)}</span>
      {basis === 'ESTIMATED' && <span className="aia-tag" title="Estimate">est.</span>}
      {cost?.complete === false && value !== null && <span className="aia-tag aia-tag--warn" title="Some calls could not be priced">partial</span>}
      {open && cost && (
        <span className="aia-popover" role="tooltip">
          {costBreakdown(cost).map((line) => (
            <span key={line.label} className="aia-popover-row">
              <span>{line.label}</span>
              <span>{line.value}</span>
            </span>
          ))}
          {cost.formula && <span className="aia-popover-formula">Rate card (USD): {cost.formula}</span>}
        </span>
      )}
    </span>
  )
}

export function KeyValue({ label, value, title }: { label: string; value: ReactNode; title?: string }) {
  return (
    <div className="aia-kv" title={title}>
      <dt>{label}</dt>
      <dd>{value === null || value === undefined || value === '' ? <span className="aia-muted">{MISSING}</span> : value}</dd>
    </div>
  )
}

export function Stat({ label, value, note }: { label: string; value: string; note?: string }) {
  return (
    <div className="aia-stat">
      <span className="aia-stat-label">{label}</span>
      <span className="aia-stat-value">{value}</span>
      {note && <span className="aia-stat-note">{note}</span>}
    </div>
  )
}

export function LoadState({ loading, error, onRetry }: { loading: boolean; error: string | null; onRetry?: () => void }) {
  if (error) {
    return (
      <div className="aia-alert aia-alert--bad" role="alert">
        {error}
        {onRetry && <button type="button" className="aia-link" onClick={onRetry}>Retry</button>}
      </div>
    )
  }
  return loading ? <div className="aia-loading" aria-live="polite">Loading…</div> : null
}

/** A note when an aggregate rests on partial data, so incomplete history is never presented as complete. */
export function DataNotes({ metrics, truncated }: { metrics: GroupMetrics | null | undefined; truncated?: boolean }) {
  if (!metrics) return null
  const notes: string[] = []
  if (metrics.historicalCount > 0) {
    notes.push(`${metrics.historicalCount} of ${metrics.requestCount} requests are historical (recovered by backfill) and may lack usage, cost, timing or attempt detail.`)
  }
  if (metrics.uncostedCount > 0) {
    notes.push(`${metrics.uncostedCount} request(s) could not be costed; cost totals cover the ${metrics.costedCount} costed request(s) only.`)
  }
  if (metrics.currencies.length > 1) notes.push('Costs are in more than one currency and are not added together.')
  if (truncated) notes.push('Only the first 200,000 matching requests were included; narrow the filters for exact figures.')
  if (notes.length === 0) return null
  return (
    <ul className="aia-notes">
      {notes.map((note) => <li key={note}>{note}</li>)}
    </ul>
  )
}

interface MetricsTableProps {
  groups: GroupMetrics[]
  dimensions: string[]
  dimensionLabel: (dimension: string, value: string | null) => string
  columns?: string[]
  onSelect?: (group: GroupMetrics) => void
}

/** Grouped metrics as a table; group keys first, then the chosen metric columns. */
export function MetricsTable({ groups, dimensions, dimensionLabel, columns = DEFAULT_METRIC_COLUMNS, onSelect }: MetricsTableProps) {
  const shown = METRIC_COLUMNS.filter((c) => columns.includes(c.id))
  if (groups.length === 0) return <p className="aia-empty">No requests match these filters.</p>
  return (
    <div className="aia-table-scroll">
      <table className="aia-table">
        <thead>
          <tr>
            {dimensions.map((d) => <th key={d}>{dimensionLabel(d, null)}</th>)}
            {shown.map((c) => <th key={c.id} className="aia-num" title={c.title}>{c.label}</th>)}
          </tr>
        </thead>
        <tbody>
          {groups.map((g) => (
            <tr
              key={dimensions.map((d) => g.key[d] ?? '∅').join('|')}
              className={onSelect ? 'aia-clickable' : undefined}
              onClick={onSelect ? () => onSelect(g) : undefined}
            >
              {dimensions.map((d) => <td key={d}>{dimensionLabel(d, g.key[d])}</td>)}
              {shown.map((c) => <td key={c.id} className="aia-num">{c.render(g)}</td>)}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
