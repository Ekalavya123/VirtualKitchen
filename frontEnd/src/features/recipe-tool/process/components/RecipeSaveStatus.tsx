import { useEffect, useState } from 'react'
import type { SaveState } from '../../persistence/saveCoordinator'

type RecipeSaveStatusProps = {
  saveState: SaveState
  onResolveConflict: (choice: 'reload' | 'keepMine') => void
}

const secondsUntil = (time: number, now: number) => Math.max(0, Math.ceil((time - now) / 1000))

const formatAgo = (time: number, now: number) => {
  const seconds = Math.floor((now - time) / 1000)
  if (seconds < 10) return 'just now'
  if (seconds < 60) return `${seconds}s ago`
  const minutes = Math.floor(seconds / 60)
  return minutes < 60 ? `${minutes} min ago` : new Date(time).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}

const chip = (color: string, text: string, title?: string) => (
  <span role="status" title={title ?? text} style={{ fontSize: 11, fontWeight: 600, color, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', maxWidth: 320 }}>
    {text}
  </span>
)

const linkButton = { border: 'none', background: 'transparent', padding: 0, fontSize: 11, fontWeight: 700, color: 'var(--flow-accent)', cursor: 'pointer', textDecoration: 'underline' } as const

/**
 * The Recipe Editor's unobtrusive persistence indicator (see RecipeSessionContext's autosave):
 * "✓ Saved · 12s ago", "Unsaved changes", "Saving…", "⚠ Not saved — retrying in 8s", or a conflict
 * notice with the two ways out of it.
 */
export default function RecipeSaveStatus({ saveState, onResolveConflict }: RecipeSaveStatusProps) {
  const { status, error, errorKind, lastSavedAt, retryAt } = saveState
  // Ticks only while a relative time is actually on screen.
  const [now, setNow] = useState(() => Date.now())
  const ticking = (status === 'saved' && lastSavedAt != null) || (status === 'failed' && retryAt != null)
  useEffect(() => {
    if (!ticking) return undefined
    // Refresh right away too: `now` is stale whenever a new save/retry time just appeared.
    const first = window.setTimeout(() => setNow(Date.now()), 0)
    const timer = window.setInterval(() => setNow(Date.now()), retryAt != null ? 1000 : 10000)
    return () => {
      window.clearTimeout(first)
      window.clearInterval(timer)
    }
  }, [ticking, retryAt, lastSavedAt])

  if (status === 'conflict') {
    return (
      <span role="alert" style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 11, fontWeight: 600, color: 'var(--flow-danger)' }}>
        ⚠ Changed on another tab or device — not saved.
        <button type="button" style={linkButton} onClick={() => onResolveConflict('reload')} title="Discard the changes made here and load the latest saved recipe">
          Load latest
        </button>
        <button type="button" style={linkButton} onClick={() => onResolveConflict('keepMine')} title="Save this version over the newer one">
          Keep mine
        </button>
      </span>
    )
  }
  if (status === 'saving') return chip('var(--flow-text-muted)', 'Saving…')
  if (status === 'dirty') return chip('var(--flow-warning)', '● Unsaved changes')
  if (status === 'failed') {
    if (errorKind === 'network') {
      const retry = retryAt != null ? ` — retrying in ${secondsUntil(retryAt, now)}s` : ' — press Save to retry'
      return chip('var(--flow-danger)', `⚠ Changes not saved${retry}`, error ?? undefined)
    }
    return chip('var(--flow-danger)', `⚠ Not saved: ${error ?? 'unknown error'}`, error ?? undefined)
  }
  return chip('var(--flow-success)', lastSavedAt != null ? `✓ Saved · ${formatAgo(lastSavedAt, now)}` : '✓ Saved')
}
