import { useState } from 'react'
import type { AnalyticsFilters, DatePreset } from '../model/analyticsQuery'
import { activeFilterCount, emptyFilters } from '../model/analyticsQuery'
import { displayCurrency, humanize } from '../model/analyticsFormat'

interface FilterBarProps {
  filters: AnalyticsFilters
  options: Record<string, string[]> | null
  onChange: (filters: AnalyticsFilters) => void
}

const PRESETS: { id: DatePreset; label: string }[] = [
  { id: '24h', label: '24h' },
  { id: '7d', label: '7 days' },
  { id: '30d', label: '30 days' },
  { id: '90d', label: '90 days' },
  { id: 'all', label: 'All time' },
  { id: 'custom', label: 'Custom' },
]

type ListKey = 'operationContext' | 'requestType' | 'model' | 'provider' | 'tier' | 'status' | 'source'
type TextKey = 'requestId' | 'userId' | 'recipeId' | 'workflowId' | 'jobId' | 'stepId'
  | 'costMin' | 'costMax' | 'latencyMin' | 'latencyMax' | 'retryMin'

/** "What ran" filters: pick any number of values; a request matches if it has any of them. */
const LIST_FILTERS: { key: ListKey; label: string; hint: string }[] = [
  { key: 'operationContext', label: 'Operation', hint: 'What the app used AI for (flow generation, step images, narration…)' },
  { key: 'requestType', label: 'Request type', hint: 'The modality: text→text, text→image or text→audio' },
  { key: 'model', label: 'Model', hint: 'The registry model that served the request (after any credit fallback)' },
  { key: 'provider', label: 'Provider', hint: 'Who ran it: gemini, ollama, drawThings, local TTS…' },
  { key: 'tier', label: 'Category', hint: 'Paid API models vs open-source / local models' },
  { key: 'status', label: 'Status', hint: 'How the request ended' },
  { key: 'source', label: 'Data source', hint: 'Live = recorded as it ran; backfill = recovered from older records (often partial)' },
]

const LINK_FILTERS: { key: TextKey; label: string; hint: string; numeric?: boolean }[] = [
  { key: 'recipeId', label: 'Recipe ID', hint: 'Every AI request made for one recipe' },
  { key: 'workflowId', label: 'Workflow ID', hint: 'One "create with AI" run (flow + visuals + narration)' },
  { key: 'stepId', label: 'Step ID', hint: 'Requests for one process step (step image, its prompt, narration)' },
  { key: 'jobId', label: 'Job ID', hint: 'One generation or visualization job' },
  { key: 'userId', label: 'User ID', hint: 'Requests made by one user (numeric id)', numeric: true },
  { key: 'requestId', label: 'Request ID', hint: 'A full request id, or its first 6+ characters' },
]

const thresholdFilters = (currency: string): { key: TextKey; label: string; hint: string }[] => [
  { key: 'costMin', label: `Cost at least (${currency})`, hint: 'Total cost of the request, all calls included' },
  { key: 'costMax', label: `Cost at most (${currency})`, hint: 'Requests with no known cost are excluded by either cost filter' },
  { key: 'latencyMin', label: 'Latency at least (ms)', hint: 'End-to-end time incl. queue wait; 1000 ms = 1 s' },
  { key: 'latencyMax', label: 'Latency at most (ms)', hint: '' },
  { key: 'retryMin', label: 'Retries at least', hint: '1 = requests that needed at least one transport retry' },
]

/** Model keys and provider names are shown exactly as configured; enum values are humanized. */
const valueLabel = (key: ListKey, value: string) => (key === 'model' || key === 'provider' ? value : humanize(value))

/**
 * Date range always visible; every other filter in a panel grouped by question. All filters combine with AND
 * (within one list, any selected value matches) and are applied by the backend.
 */
export default function FilterBar({ filters, options, onChange }: FilterBarProps) {
  const [open, setOpen] = useState(false)
  const count = activeFilterCount(filters)
  const set = <K extends keyof AnalyticsFilters>(key: K, value: AnalyticsFilters[K]) => onChange({ ...filters, [key]: value })
  const toggle = (key: ListKey, value: string) => {
    const current = filters[key]
    set(key, current.includes(value) ? current.filter((v) => v !== value) : [...current, value])
  }
  const clearAll = () => onChange({ ...emptyFilters(), preset: filters.preset, fromDate: filters.fromDate, toDate: filters.toDate })

  // The applied filters, as removable chips.
  const applied: { id: string; label: string; remove: () => void }[] = []
  for (const f of LIST_FILTERS) {
    for (const value of filters[f.key]) {
      applied.push({ id: `${f.key}:${value}`, label: `${f.label}: ${valueLabel(f.key, value)}`, remove: () => toggle(f.key, value) })
    }
  }
  for (const f of [...LINK_FILTERS, ...thresholdFilters(displayCurrency())]) {
    const value = filters[f.key].trim()
    if (value) applied.push({ id: f.key, label: `${f.label}: ${value}`, remove: () => set(f.key, '') })
  }

  return (
    <div className="aia-filters">
      <div className="aia-filter-row">
        <div className="aia-segmented" role="group" aria-label="Date range">
          {PRESETS.map((p) => (
            <button
              key={p.id}
              type="button"
              className={filters.preset === p.id ? 'is-active' : undefined}
              aria-pressed={filters.preset === p.id}
              onClick={() => set('preset', p.id)}
            >
              {p.label}
            </button>
          ))}
        </div>
        {filters.preset === 'custom' && (
          <span className="aia-date-range">
            <input type="date" aria-label="From" value={filters.fromDate} onChange={(e) => set('fromDate', e.target.value)} />
            <span>to</span>
            <input type="date" aria-label="To" value={filters.toDate} onChange={(e) => set('toDate', e.target.value)} />
          </span>
        )}
        <button type="button" className="aia-button" aria-expanded={open} onClick={() => setOpen(!open)}>
          {open ? 'Hide filters' : 'More filters'}{count > 0 ? ` (${count})` : ''}
        </button>
      </div>

      {applied.length > 0 && (
        <div className="aia-applied" aria-label="Applied filters">
          {applied.map((a) => (
            <button key={a.id} type="button" className="aia-chip is-active" onClick={a.remove} title="Remove this filter">
              {a.label} <span aria-hidden="true">×</span>
            </button>
          ))}
          <button type="button" className="aia-link" onClick={clearAll}>Clear all</button>
        </div>
      )}

      {open && (
        <div className="aia-filter-panel">
          <p className="aia-filter-intro">
            Filters apply immediately and combine with each other (AND). Within one list, selecting several values
            matches any of them (OR). Click a selected value again to remove it.
          </p>

          <section className="aia-filter-section">
            <h4>What ran</h4>
            {LIST_FILTERS.map((f) => {
              const values = options?.[f.key] ?? []
              return (
                <div key={f.key} className="aia-filter-group">
                  <div className="aia-filter-label">
                    <span>{f.label}</span>
                    <span className="aia-filter-hint">{f.hint}</span>
                  </div>
                  <div className="aia-chip-row" role="group" aria-label={f.label}>
                    {values.length === 0 && <span className="aia-muted aia-small">No values yet</span>}
                    {values.map((v) => (
                      <button
                        key={v}
                        type="button"
                        className={`aia-chip${filters[f.key].includes(v) ? ' is-active' : ''}`}
                        aria-pressed={filters[f.key].includes(v)}
                        onClick={() => toggle(f.key, v)}
                      >
                        {valueLabel(f.key, v)}
                      </button>
                    ))}
                  </div>
                </div>
              )
            })}
          </section>

          <section className="aia-filter-section">
            <h4>Belongs to</h4>
            <div className="aia-filter-fields">
              {LINK_FILTERS.map((f) => (
                <label key={f.key} className="aia-field">
                  <span>{f.label}</span>
                  <input
                    type={f.numeric ? 'number' : 'text'}
                    value={filters[f.key]}
                    onChange={(e) => set(f.key, e.target.value)}
                  />
                  <small className="aia-filter-hint">{f.hint}</small>
                </label>
              ))}
            </div>
          </section>

          <section className="aia-filter-section">
            <h4>Thresholds</h4>
            <div className="aia-filter-fields">
              {thresholdFilters(displayCurrency()).map((f) => (
                <label key={f.key} className="aia-field">
                  <span>{f.label}</span>
                  <input type="number" min="0" step="any" inputMode="decimal" value={filters[f.key]}
                    onChange={(e) => set(f.key, e.target.value)} />
                  {f.hint && <small className="aia-filter-hint">{f.hint}</small>}
                </label>
              ))}
            </div>
          </section>
        </div>
      )}
    </div>
  )
}
