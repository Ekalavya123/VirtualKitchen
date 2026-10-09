import { useEffect, useState } from 'react'
import { AiApi, type UserAiCredit } from '../../api'

type AiCreditBadgeProps = {
  /** Bump this (e.g. a counter) after an AI generation completes to force a refetch. */
  refreshSignal?: number
}

export type AiCreditSummary = {
  credit: UserAiCredit
  /** At or below 10% of the monthly allocation — AI then falls back to a standard model. */
  isLow: boolean
  /** Tooltip text describing the balance. */
  description: string
}

/**
 * The current user's AI credit balance, fetched on mount and whenever `refreshSignal` changes.
 * Null until loaded, and also if the request fails (e.g. unauthenticated) — callers then simply
 * show nothing rather than a broken/error state.
 */
// eslint-disable-next-line react-refresh/only-export-components -- the badge and its data hook belong together
export function useAiCredit(refreshSignal?: number): AiCreditSummary | null {
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
  return {
    credit,
    isLow,
    description: `${credit.availableBalance} of ${credit.monthlyAllocation} AI credits remaining this cycle${isLow ? ' — running low, AI features will automatically fall back to a standard model' : ''}`,
  }
}

/** Self-contained "⚡ credits remaining" badge (see useAiCredit). */
export default function AiCreditBadge({ refreshSignal }: AiCreditBadgeProps) {
  const summary = useAiCredit(refreshSignal)
  if (!summary) return null
  const { credit, isLow, description } = summary

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
      title={description}
    >
      ⚡ {credit.availableBalance}/{credit.monthlyAllocation}
    </div>
  )
}
