import { useCallback, useMemo, useState } from 'react'
import { AiAnalyticsApi } from './api/aiAnalyticsApi'
import FilterBar from './components/FilterBar'
import RequestDrawer from './components/RequestDrawer'
import RequestSummary from './components/RequestSummary'
import ResultViewer from './components/ResultViewer'
import { useApiData } from '../../../shared/hooks/useApiData'
import { emptyFilters, toQueryParams } from './model/analyticsQuery'
import { configureDisplayCurrency } from './model/analyticsFormat'
import type { AnalyticsFilters } from './model/analyticsQuery'
import RequestsPage from './pages/RequestsPage'
import './aiAnalytics.css'

/**
 * Admin AI requests: date range, filter dropdowns, the paginated request list and a summary of every matching
 * request. The route is only offered to admins, but authorization is enforced by the backend on every API call.
 * (The Overview / Cost / Model comparison / Pricing pages are hidden for now; their files are kept.)
 */
export default function AiAnalyticsPage() {
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

  return (
    <div className="aia-root">
      <header className="aia-header">
        <div>
          <h1>AI requests</h1>
          <p className="aia-muted">Every AI request with its cost, usage and outcome, from persisted records.</p>
        </div>
      </header>
      {!options.data && !options.error && <div className="aia-loading">Loading…</div>}
      {options.error && <div className="aia-alert aia-alert--bad" role="alert">{options.error}</div>}
      {options.data && (
        <>
          <FilterBar filters={filters} options={options.data.values} users={options.data.users ?? []}
            recipes={options.data.recipes ?? []} onChange={setFilters} />
          <RequestsPage params={params} onOpenRequest={setDrawerId} onViewResult={setResultId} />
          <RequestSummary params={params} />
        </>
      )}

      {drawerId && <RequestDrawer requestId={drawerId} onClose={closeDrawer} onViewResult={setResultId} />}
      {resultId && <ResultViewer requestId={resultId} onClose={closeResult} />}
    </div>
  )
}
