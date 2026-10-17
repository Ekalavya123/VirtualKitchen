import { useState } from 'react'
import type { ReadinessIssue, RecipeReadiness } from '../../model/recipeReadiness'

type RecipeReadinessPanelProps = {
  readiness: RecipeReadiness
  /** Opens the issue's process and selects its step. */
  onNavigate: (issue: ReadinessIssue) => void
  /** Changes whenever the panel should open by itself (e.g. after a blocked operation's "Show me"). */
  openToken?: number
}

/**
 * The editor's live readiness summary: "Ready to cook", or how many issues block cooking/ordering (✖) and how many
 * are only warnings (⚠). Expands into the list of issues; each opens its process and selects its step. Never
 * interrupts editing — blocked operations explain themselves when they're started (see useReadinessGate).
 */
export default function RecipeReadinessPanel({ readiness, onNavigate, openToken }: RecipeReadinessPanelProps) {
  const [expanded, setExpanded] = useState(false)
  const [handledOpenToken, setHandledOpenToken] = useState(openToken)
  if (openToken !== handledOpenToken) {
    setHandledOpenToken(openToken)
    if (openToken != null) setExpanded(true)
  }

  const { blockers, warnings, issues } = readiness
  const summary = blockers.length === 0 && warnings.length === 0
    ? '✓ Ready to cook'
    : [blockers.length > 0 ? `✖ ${blockers.length} to fix` : '✓ Ready', warnings.length > 0 ? `⚠ ${warnings.length}` : ''].filter(Boolean).join(' · ')
  const tone = blockers.length > 0 ? 'var(--flow-danger)' : warnings.length > 0 ? 'var(--flow-text-muted)' : 'var(--flow-success)'

  return (
    <div className="flex flex-shrink-0 flex-col border-b border-[var(--flow-border)]">
      <button
        type="button"
        onClick={() => setExpanded((current) => !current)}
        disabled={issues.length === 0}
        aria-expanded={issues.length > 0 ? expanded : undefined}
        title={blockers.length > 0
          ? 'These must be fixed before cooking, ordering or creating visuals for the whole recipe'
          : 'Cooking, ordering and whole-recipe visuals are available'}
        style={{
          display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8,
          padding: '8px 14px', border: 0, background: 'transparent', cursor: issues.length > 0 ? 'pointer' : 'default',
          fontSize: 11.5, fontWeight: 700, color: tone, textAlign: 'left',
        }}
      >
        <span>{summary}</span>
        {issues.length > 0 && <span aria-hidden style={{ color: 'var(--flow-text-subtle)' }}>{expanded ? '▾' : '▸'}</span>}
      </button>
      {expanded && issues.length > 0 && (
        <ul style={{ listStyle: 'none', margin: 0, padding: '0 8px 8px', display: 'flex', flexDirection: 'column', gap: 4, maxHeight: 240, overflowY: 'auto' }}>
          {[...blockers, ...warnings].map((issue, index) => (
            <li key={`${issue.code}-${issue.processId}-${issue.nodeId}-${index}`}>
              <button
                type="button"
                onClick={() => onNavigate(issue)}
                disabled={issue.processId == null}
                style={{
                  width: '100%', display: 'flex', gap: 6, alignItems: 'flex-start', textAlign: 'left',
                  padding: '6px 8px', borderRadius: 8, fontSize: 11, lineHeight: 1.4, cursor: issue.processId == null ? 'default' : 'pointer',
                  border: `1px solid ${issue.severity === 'BLOCKER' ? 'var(--flow-danger-border)' : 'var(--flow-warning-border)'}`,
                  background: issue.severity === 'BLOCKER' ? 'var(--flow-danger-soft)' : 'var(--flow-warning-soft)',
                  color: 'var(--flow-text)',
                }}
              >
                <span aria-hidden>{issue.severity === 'BLOCKER' ? '✖' : '⚠'}</span>
                <span>
                  {issue.message}
                  <span className="sr-only">{issue.severity === 'BLOCKER' ? ' (must be fixed)' : ' (warning)'}</span>
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
