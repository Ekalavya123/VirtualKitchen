import type { CSSProperties } from 'react'
import '../../styles/flow-editor.css'
import type { ProcessBreadcrumbEntry } from '../../../../types/process'

type ProcessBreadcrumbProps = {
  /** Ancestors from the recipe's process list down to (but not including) the process currently open, root-first. */
  ancestors: ProcessBreadcrumbEntry[]
  currentName: string
  onNavigateToList: () => void
  onNavigateToAncestor: (index: number) => void
}

const crumbButtonStyle: CSSProperties = {
  border: 'none',
  background: 'transparent',
  color: 'var(--flow-text-muted)',
  fontSize: 12,
  fontWeight: 600,
  cursor: 'pointer',
  padding: '2px 4px',
  borderRadius: 6,
}

/**
 * Lightweight "Recipe -> Main Process -> Cut Vegetables" navigation trail
 * for the Process Editor. Purely a display + click-to-navigate component —
 * the actual trail is owned by App.tsx's ProcessEditorRoute as router state,
 * not by this component or by ProcessCanvas.
 */
export default function ProcessBreadcrumb({ ancestors, currentName, onNavigateToList, onNavigateToAncestor }: ProcessBreadcrumbProps) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 4, flexWrap: 'wrap', minWidth: 0 }}>
      <button type="button" onClick={onNavigateToList} style={crumbButtonStyle} title="All processes for this recipe">
        Recipe
      </button>

      {ancestors.map((ancestor, index) => (
        <span key={ancestor.processId} style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
          <span style={{ color: 'var(--flow-text-subtle)', fontSize: 12 }}>→</span>
          <button
            type="button"
            onClick={() => onNavigateToAncestor(index)}
            style={crumbButtonStyle}
            title={`Back to ${ancestor.name}`}
          >
            {ancestor.name}
          </button>
        </span>
      ))}

      <span style={{ color: 'var(--flow-text-subtle)', fontSize: 12 }}>→</span>
      <span
        style={{
          fontSize: 12,
          fontWeight: 700,
          color: 'var(--flow-text)',
          padding: '2px 4px',
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
        }}
        title={currentName}
      >
        {currentName}
      </span>
    </div>
  )
}
