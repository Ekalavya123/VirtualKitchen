import { useEffect, useState } from 'react'
import { AiApi, type UserAiCredit } from '../../api'

type AiCreditBadgeProps = {
  /** Bump this (e.g. a counter) after an AI generation completes to force a refetch. */
  refreshSignal?: number
}

/**
 * Self-contained "⚡ credits remaining" badge for the flow editor top bar. Fetches the current
 * user's AI credit balance on mount and whenever `refreshSignal` changes, and renders nothing if
 * the request fails (e.g. unauthenticated) rather than showing a broken/error state in the toolbar.
 */
export default function AiCreditBadge({ refreshSignal }: AiCreditBadgeProps) {
  const [credit, setCredit] = useState<UserAiCredit | null>(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    let cancelled = false

    AiApi.getMyCredits()
      .then((data) => {
        if (!cancelled) {
          setCredit(data)
          setFailed(false)
        }
      })
      .catch(() => {
        if (!cancelled) setFailed(true)
      })

    return () => {
      cancelled = true
    }
  }, [refreshSignal])

  if (failed || !credit) return null

  const isLow = credit.availableBalance <= Math.max(1, Math.round(credit.monthlyAllocation * 0.1))

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 6,
        padding: '7px 10px',
        borderRadius: 8,
        border: `1px solid ${isLow ? 'var(--flow-warning-border)' : 'var(--flow-border)'}`,
        background: isLow ? 'var(--flow-warning-soft)' : 'var(--flow-surface)',
        color: isLow ? 'var(--flow-warning)' : 'var(--flow-text-muted)',
        fontSize: 11,
        fontWeight: 700,
        whiteSpace: 'nowrap',
      }}
      title={`${credit.availableBalance} of ${credit.monthlyAllocation} AI credits remaining this cycle${isLow ? ' — running low, AI features will automatically fall back to a standard model' : ''}`}
    >
      ⚡ {credit.availableBalance}/{credit.monthlyAllocation}
    </div>
  )
}
