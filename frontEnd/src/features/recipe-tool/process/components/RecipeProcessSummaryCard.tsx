import '../../styles/recipe-tool.css'
import type { Process } from '../../../../types/process'

type RecipeProcessSummaryCardProps = {
  process: Process
  onOpen?: () => void
  actionLabel?: string
}

/**
 * Compact, reusable summary of a Process (name/type/description/node
 * counts) for list-style UI — the recipe's process list here, and other
 * Recipe Tool surfaces that need the same "what is this process, at a
 * glance" card without embedding its full graph. A process graph only ever
 * contains STEP/CONDITION nodes (no embedded subprocess count to show). A
 * subprocess has no separate "output" field — its own name is its summary.
 * Duration isn't shown: nothing on the backend Process model records one,
 * and deriving it from every STEP node's own duration field isn't "easily
 * available" the way a node-kind count is — left for a later phase.
 */
export default function RecipeProcessSummaryCard({ process, onOpen, actionLabel = 'Open' }: RecipeProcessSummaryCardProps) {
  const stepCount = process.nodes.filter((node) => node.kind === 'STEP').length
  const conditionCount = process.nodes.filter((node) => node.kind === 'CONDITION').length

  return (
    <div
      className="flow-editor-surface"
      style={{
        border: '1px solid var(--flow-border)',
        borderRadius: 12,
        padding: '14px 16px',
        display: 'flex',
        flexDirection: 'column',
        gap: 8,
        background: 'var(--flow-surface)',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 8 }}>
        <div style={{ minWidth: 0 }}>
          <div style={{ fontWeight: 700, fontSize: 14, color: 'var(--flow-text)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {process.name}
          </div>
          {process.description && (
            <div style={{ fontSize: 12, color: 'var(--flow-text-muted)', marginTop: 2 }}>{process.description}</div>
          )}
        </div>
        <span
          style={{
            flexShrink: 0,
            fontSize: 10,
            fontWeight: 700,
            padding: '2px 8px',
            borderRadius: 999,
            background: process.type === 'MAIN' ? '#f0fdf4' : '#eef2ff',
            border: `1px solid ${process.type === 'MAIN' ? '#86efac' : '#c7d2fe'}`,
            color: process.type === 'MAIN' ? '#166534' : '#4338ca',
          }}
        >
          {process.type}
        </span>
      </div>

      <div style={{ display: 'flex', gap: 12, fontSize: 11, color: 'var(--flow-text-muted)', fontWeight: 600 }}>
        <span>{stepCount} step{stepCount === 1 ? '' : 's'}</span>
        <span>{conditionCount} condition{conditionCount === 1 ? '' : 's'}</span>
      </div>

      {onOpen && (
        <button
          type="button"
          onClick={onOpen}
          style={{
            alignSelf: 'flex-start',
            marginTop: 4,
            padding: '6px 12px',
            borderRadius: 8,
            border: '1px solid var(--flow-border)',
            background: 'var(--flow-surface-muted)',
            color: 'var(--flow-text)',
            fontSize: 12,
            fontWeight: 700,
            cursor: 'pointer',
          }}
        >
          {actionLabel} →
        </button>
      )}
    </div>
  )
}
