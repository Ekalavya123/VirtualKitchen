import { useState } from 'react'
import { AiAnalyticsApi } from '../api/aiAnalyticsApi'
import type { GroupMetrics } from '../api/aiAnalyticsApi'
import { BarList } from '../components/charts'
import { DataNotes, LoadState, MetricsTable } from '../components/common'
import { DEFAULT_METRIC_COLUMNS, METRIC_COLUMNS } from '../model/metricColumns'
import { useApiData } from '../hooks/useApiData'
import { dimensionLabel, displayCurrency, formatCost, formatCount } from '../model/analyticsFormat'
import { GROUP_BY_LABELS, requestKey } from '../model/analyticsQuery'

interface CostEfficiencyPageProps {
  params: Record<string, string>
  onDrillDown: (filter: Record<string, string>) => void
}

const DIMENSIONS = ['operationContext', 'requestType', 'model', 'provider', 'tier', 'workflow', 'recipe', 'step', 'user', 'status', 'source', 'day', 'week', 'month']
const PRESETS: { label: string; groupBy: string[] }[] = [
  { label: 'Operation × model', groupBy: ['operationContext', 'model'] },
  { label: 'Per workflow', groupBy: ['workflow'] },
  { label: 'Per recipe', groupBy: ['recipe'] },
  { label: 'Per step', groupBy: ['recipe', 'step'] },
  { label: 'Per day', groupBy: ['day'] },
]

/** Which request filter a group's key corresponds to, for drilling into the request explorer. */
const DRILL_FILTERS: Record<string, string> = {
  operationContext: 'operationContext', requestType: 'requestType', model: 'model', provider: 'provider', tier: 'tier',
  workflow: 'workflowId', recipe: 'recipeId', step: 'stepId', user: 'userId', status: 'status', source: 'source',
}


const labelOf = (g: GroupMetrics, dims: string[]) =>
  dims.map((d) => (g.key[d] === null || g.key[d] === undefined ? (d === 'step' ? 'Not step-specific' : 'None') : dimensionLabel(d, g.key[d]))).join(' · ')

export default function CostEfficiencyPage({ params, onDrillDown }: CostEfficiencyPageProps) {
  const [groupBy, setGroupBy] = useState<string[]>(['operationContext', 'model'])
  const [columns, setColumns] = useState<string[]>(DEFAULT_METRIC_COLUMNS)
  const query = { ...params, groupBy: groupBy.join(',') }
  const { data, error, loading, reload } = useApiData(groupBy.length ? `aggregate?${requestKey(query)}` : null,
    () => AiAnalyticsApi.aggregate(query))
  const currency = data?.totals.currencies[0] ?? 'USD'

  const toggleDim = (d: string) => setGroupBy(groupBy.includes(d) ? groupBy.filter((x) => x !== d) : [...groupBy, d].slice(-3))

  const drill = (g: GroupMetrics) => {
    const filter: Record<string, string> = {}
    for (const d of groupBy) {
      const target = DRILL_FILTERS[d]
      const value = g.key[d]
      if (target && value) filter[target] = value
    }
    onDrillDown(filter)
  }

  return (
    <div className="aia-page">
      <section className="aia-card">
        <div className="aia-choice-row">
          <span className="aia-choice-label">Group by (up to 3)</span>
          {DIMENSIONS.map((d) => (
            <button key={d} type="button" className={`aia-chip${groupBy.includes(d) ? ' is-active' : ''}`} aria-pressed={groupBy.includes(d)} onClick={() => toggleDim(d)}>
              {GROUP_BY_LABELS[d]}
            </button>
          ))}
        </div>
        <div className="aia-choice-row">
          <span className="aia-choice-label">Presets</span>
          {PRESETS.map((p) => (
            <button key={p.label} type="button" className="aia-link" onClick={() => setGroupBy(p.groupBy)}>{p.label}</button>
          ))}
        </div>
        <details className="aia-details">
          <summary>Metric columns</summary>
          <div className="aia-choice-row">
            {METRIC_COLUMNS.map((c) => (
              <label key={c.id} className="aia-inline-field" title={c.title}>
                <input type="checkbox" checked={columns.includes(c.id)}
                  onChange={(e) => setColumns(e.target.checked ? [...columns, c.id] : columns.filter((x) => x !== c.id))} />
                <span>{c.label}</span>
              </label>
            ))}
          </div>
        </details>
      </section>

      {groupBy.length === 0 && <p className="aia-empty">Choose at least one dimension.</p>}
      <LoadState loading={loading && !data} error={error} onRetry={reload} />
      {data && groupBy.length > 0 && (
        <>
          <DataNotes metrics={data.totals} truncated={data.truncated} />
          <section className="aia-card">
            <h3>Total cost by {groupBy.map((d) => GROUP_BY_LABELS[d].toLowerCase()).join(' × ')} <span className="aia-muted">({displayCurrency()})</span></h3>
            <BarList ariaLabel="Total cost by group" format={(v) => formatCost(v, currency)}
              data={data.groups.slice(0, 15).map((g) => ({
                label: labelOf(g, data.groupBy),
                value: g.totalCost,
                detail: `${formatCount(g.requestCount)} requests · ${g.uncostedCount} not costed`,
              }))} />
            {data.groups.length > 15 && <p className="aia-muted aia-small">Top 15 of {data.groups.length} groups shown; all are in the table below.</p>}
          </section>
          <section className="aia-card">
            <h3>Metrics <span className="aia-muted">(click a row to see its requests)</span></h3>
            <MetricsTable groups={data.groups} dimensions={data.groupBy} columns={columns} onSelect={drill}
              dimensionLabel={(d, v) => (v === null ? (GROUP_BY_LABELS[d] ?? d) : dimensionLabel(d, v))} />
            <p className="aia-muted aia-small">
              Requests are logical requests; calls include retries. A request's cost includes all its billable calls, failed ones
              included. Workflow, recipe and step totals add up each request once. "Not step-specific" holds workflow-level work
              (such as flow generation) that is not attributable to a single step — it is shown, not allocated.
            </p>
          </section>
        </>
      )}
    </div>
  )
}
