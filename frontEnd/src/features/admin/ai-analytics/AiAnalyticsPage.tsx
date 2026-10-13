import { useCallback, useMemo, useState } from 'react'
import { NavLink, useNavigate, useParams } from 'react-router-dom'
import { AiAnalyticsApi } from './api/aiAnalyticsApi'
import FilterBar from './components/FilterBar'
import RequestDrawer from './components/RequestDrawer'
import ResultViewer from './components/ResultViewer'
import { useApiData } from './hooks/useApiData'
import { AI_ANALYTICS_PATH, emptyFilters, toQueryParams } from './model/analyticsQuery'
import { configureDisplayCurrency } from './model/analyticsFormat'
import type { AnalyticsFilters } from './model/analyticsQuery'
import ComparePage from './pages/ComparePage'
import CostEfficiencyPage from './pages/CostEfficiencyPage'
import DataPage from './pages/DataPage'
import OverviewPage from './pages/OverviewPage'
import RequestsPage from './pages/RequestsPage'
import './aiAnalytics.css'

const TABS = [
  { id: 'overview', label: 'Overview' },
  { id: 'requests', label: 'Requests' },
  { id: 'cost', label: 'Cost & efficiency' },
  { id: 'compare', label: 'Model comparison' },
  { id: 'data', label: 'Pricing & data' },
] as const

/**
 * Admin AI cost & efficiency analytics. The route is only offered to admins, but authorization is enforced by the
 * backend on every API call.
 */
export default function AiAnalyticsPage() {
  const { tab = 'overview' } = useParams()
  const navigate = useNavigate()
  const [filters, setFilters] = useState<AnalyticsFilters>(emptyFilters)
  const [drawerId, setDrawerId] = useState<string | null>(null)
  const [resultId, setResultId] = useState<string | null>(null)
  const options = useApiData('options', () => AiAnalyticsApi.options())
  const usdRate = options.data?.usdToDisplay ?? null
  if (options.data) {
    // Costs are stored in USD; shown in the backend's display currency at its recorded rate.
    configureDisplayCurrency(options.data.displayCurrency, usdRate)
  }
  // Relative ranges are anchored when the filters change (to the minute), so re-renders don't refetch.
  const params = useMemo(() => toQueryParams(filters, new Date(), usdRate ?? 1), [filters, usdRate])

  const closeDrawer = useCallback(() => setDrawerId(null), [])
  const closeResult = useCallback(() => setResultId(null), [])
  const drillDown = (extra: Record<string, string>) => {
    const next = { ...filters }
    for (const [key, value] of Object.entries(extra)) {
      if (key in next) {
        const current = next[key as keyof AnalyticsFilters]
        ;(next as Record<string, unknown>)[key] = Array.isArray(current) ? [value] : value
      }
    }
    setFilters(next)
    navigate(`${AI_ANALYTICS_PATH}/requests`)
  }

  return (
    <div className="aia-root">
      <header className="aia-header">
        <div>
          <h1>AI analytics</h1>
          <p className="aia-muted">Cost, usage and performance of every AI request, from persisted records.</p>
        </div>
      </header>
      <nav className="aia-tabs" aria-label="AI analytics sections">
        {TABS.map((t) => (
          <NavLink key={t.id} to={`${AI_ANALYTICS_PATH}/${t.id}`} className={() => `aia-tab${tab === t.id ? ' is-active' : ''}`}>
            {t.label}
          </NavLink>
        ))}
      </nav>
      {!options.data && !options.error && <div className="aia-loading">Loading…</div>}
      {options.error && <div className="aia-alert aia-alert--bad" role="alert">{options.error}</div>}
      {options.data && (
        <>
      {tab !== 'data' && <FilterBar filters={filters} options={options.data.values} onChange={setFilters} />}

      {tab === 'overview' && (
        <OverviewPage params={params} onOpenRequest={setDrawerId}
          onOpenWorkflow={(workflowId) => drillDown({ workflowId })} />
      )}
      {tab === 'requests' && <RequestsPage params={params} onOpenRequest={setDrawerId} onViewResult={setResultId} />}
      {tab === 'cost' && <CostEfficiencyPage params={params} onDrillDown={drillDown} />}
      {tab === 'compare' && <ComparePage params={params} contexts={options.data.values.operationContext ?? []} />}
      {tab === 'data' && <DataPage />}
        </>
      )}

      {drawerId && <RequestDrawer requestId={drawerId} onClose={closeDrawer} onViewResult={setResultId} />}
      {resultId && <ResultViewer requestId={resultId} onClose={closeResult} />}
    </div>
  )
}
