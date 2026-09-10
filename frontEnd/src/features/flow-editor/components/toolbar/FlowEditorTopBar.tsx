import React from 'react'
import '../../styles/flow-editor.css'

type FlowEditorTopBarProps = {
  title: string
  onUndo: () => void
  onRedo: () => void
  canUndo: boolean
  canRedo: boolean
  onExport: () => void
  onSave: () => void
  onGenerateVisuals: () => void
  isGeneratingVisuals?: boolean
  generateVisualsStatus?: { type: 'success' | 'error'; text: string } | null
  generateVisualsProgress?: { completed: number; total: number } | null
  onVisualize: () => void
  onBack?: () => void
  nodeZoomPercent: number
  onNodeZoomChange: (percent: number) => void
}

export default function FlowEditorTopBar({
  title,
  onUndo,
  onRedo,
  canUndo,
  canRedo,
  onExport,
  onSave,
  onGenerateVisuals,
  isGeneratingVisuals = false,
  generateVisualsStatus = null,
  generateVisualsProgress = null,
  onVisualize,
  onBack,
  nodeZoomPercent,
  onNodeZoomChange,
}: FlowEditorTopBarProps) {
  const btnStyle = (extra: React.CSSProperties = {}): React.CSSProperties => ({
    padding: '8px 12px', borderRadius: 8, border: '1px solid var(--flow-border)',
    background: 'var(--flow-surface)', color: 'var(--flow-text-muted)', fontSize: 12, fontWeight: 600,
    cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 6,
    ...extra,
  })

  const zoomStepStyle: React.CSSProperties = {
    padding: '4px 9px', borderRadius: 6, border: 'none',
    background: 'transparent', color: 'var(--flow-text-muted)', fontSize: 13, fontWeight: 700,
    cursor: 'pointer', lineHeight: 1,
  }

  return (
    <div className="flex h-[3.75rem] flex-shrink-0 items-center justify-between border-b border-[var(--flow-border)] bg-[var(--flow-surface)] px-4">
      <div className="flex items-center gap-2.5">
        {onBack && (
          <button onClick={onBack} style={{ ...btnStyle(), padding: '8px 10px' }} title="Back">
            ← Back
          </button>
        )}
        <div>
          <div className="text-sm font-semibold text-[var(--flow-text)]">{title}</div>
          <div className="text-[0.7rem] text-[var(--flow-text-muted)]">Design your recipe flow</div>
        </div>
      </div>

      <div className="flex items-center gap-2">
        <div
          className="flex items-center gap-1 rounded-lg border border-[var(--flow-border)] bg-[var(--flow-surface)] px-1 py-1"
          title="Uniform node size — zooms every node on the canvas together"
        >
          <button
            onClick={() => onNodeZoomChange(nodeZoomPercent - 10)}
            disabled={nodeZoomPercent <= 50}
            style={{ ...zoomStepStyle, color: nodeZoomPercent <= 50 ? 'var(--flow-border-strong)' : 'var(--flow-text-muted)', cursor: nodeZoomPercent <= 50 ? 'default' : 'pointer' }}
            title="Zoom out all nodes"
          >
            −
          </button>
          <span style={{ fontSize: 12, fontWeight: 700, color: 'var(--flow-text-muted)', minWidth: 40, textAlign: 'center' }}>
            {nodeZoomPercent}%
          </span>
          <button
            onClick={() => onNodeZoomChange(nodeZoomPercent + 10)}
            disabled={nodeZoomPercent >= 200}
            style={{ ...zoomStepStyle, color: nodeZoomPercent >= 200 ? 'var(--flow-border-strong)' : 'var(--flow-text-muted)', cursor: nodeZoomPercent >= 200 ? 'default' : 'pointer' }}
            title="Zoom in all nodes"
          >
            +
          </button>
          {nodeZoomPercent !== 100 && (
            <button onClick={() => onNodeZoomChange(100)} style={{ ...zoomStepStyle, fontSize: 10, color: 'var(--flow-accent)' }} title="Reset to 100%">
              Reset
            </button>
          )}
        </div>
        <button onClick={onUndo} disabled={!canUndo} style={btnStyle({ background: canUndo ? 'var(--flow-surface)' : 'var(--flow-surface-muted)', color: canUndo ? 'var(--flow-text-muted)' : 'var(--flow-border-strong)', cursor: canUndo ? 'pointer' : 'default' })} title="Undo">↩</button>
        <button onClick={onRedo} disabled={!canRedo} style={btnStyle({ background: canRedo ? 'var(--flow-surface)' : 'var(--flow-surface-muted)', color: canRedo ? 'var(--flow-text-muted)' : 'var(--flow-border-strong)', cursor: canRedo ? 'pointer' : 'default' })} title="Redo">↪</button>
        {generateVisualsStatus && (
          <span
            style={{
              fontSize: 11,
              fontWeight: 600,
              color: generateVisualsStatus.type === 'success' ? 'var(--flow-success)' : 'var(--flow-danger)',
              maxWidth: 220,
            }}
            title={generateVisualsStatus.text}
          >
            {generateVisualsStatus.type === 'success' ? '✅' : '⚠️'} {generateVisualsStatus.text}
          </span>
        )}
        {isGeneratingVisuals && generateVisualsProgress && generateVisualsProgress.total > 0 && (() => {
          const percent = Math.round((generateVisualsProgress.completed / generateVisualsProgress.total) * 100)
          return (
            <div
              style={{ display: 'flex', alignItems: 'center', gap: 6 }}
              title={`Generated ${generateVisualsProgress.completed} of ${generateVisualsProgress.total} steps`}
            >
              <div style={{ width: 72, height: 6, borderRadius: 999, background: 'var(--flow-magic-soft)', overflow: 'hidden' }}>
                <div style={{ width: `${percent}%`, height: '100%', background: 'var(--flow-magic)', transition: 'width 200ms ease' }} />
              </div>
              <span style={{ fontSize: 11, fontWeight: 700, color: 'var(--flow-magic)', minWidth: 32 }}>{percent}%</span>
            </div>
          )
        })()}
        <button onClick={onGenerateVisuals} disabled={isGeneratingVisuals} style={btnStyle({ background: 'var(--flow-magic-soft)', borderColor: 'var(--flow-magic-border)', color: 'var(--flow-magic)', opacity: isGeneratingVisuals ? 0.7 : 1 })}>
          {isGeneratingVisuals
            ? `⏳ Generating… ${generateVisualsProgress ? `${generateVisualsProgress.completed}/${generateVisualsProgress.total}` : ''}`
            : '🎨 Generate Visuals'}
        </button>
        <button onClick={onVisualize} style={btnStyle({ background: 'var(--flow-info-soft)', borderColor: 'var(--flow-info-border)', color: 'var(--flow-info)' })}>🎬 Visualize</button>
        <button onClick={onExport} style={btnStyle({ background: 'var(--flow-success-soft)', borderColor: 'var(--flow-success-border)', color: 'var(--flow-success)' })}>📤 Export</button>
        <button onClick={onSave} style={btnStyle({ background: 'var(--flow-warning-soft)', borderColor: 'var(--flow-warning-border)', color: 'var(--flow-warning)' })}>💾 Save</button>
      </div>
    </div>
  )
}
