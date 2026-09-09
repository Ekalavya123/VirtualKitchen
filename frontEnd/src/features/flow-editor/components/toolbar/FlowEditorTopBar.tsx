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
    padding: '8px 12px', borderRadius: 8, border: '1px solid #e2e8f0',
    background: 'white', color: '#475569', fontSize: 12, fontWeight: 600,
    cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 6,
    ...extra,
  })

  const zoomStepStyle: React.CSSProperties = {
    padding: '4px 9px', borderRadius: 6, border: 'none',
    background: 'transparent', color: '#475569', fontSize: 13, fontWeight: 700,
    cursor: 'pointer', lineHeight: 1,
  }

  return (
    <div className="flex h-[3.75rem] flex-shrink-0 items-center justify-between border-b border-[var(--flow-border)] bg-white px-4">
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
          className="flex items-center gap-1 rounded-lg border border-[var(--flow-border)] bg-white px-1 py-1"
          title="Uniform node size — zooms every node on the canvas together"
        >
          <button
            onClick={() => onNodeZoomChange(nodeZoomPercent - 10)}
            disabled={nodeZoomPercent <= 50}
            style={{ ...zoomStepStyle, color: nodeZoomPercent <= 50 ? '#cbd5e1' : '#475569', cursor: nodeZoomPercent <= 50 ? 'default' : 'pointer' }}
            title="Zoom out all nodes"
          >
            −
          </button>
          <span style={{ fontSize: 12, fontWeight: 700, color: '#475569', minWidth: 40, textAlign: 'center' }}>
            {nodeZoomPercent}%
          </span>
          <button
            onClick={() => onNodeZoomChange(nodeZoomPercent + 10)}
            disabled={nodeZoomPercent >= 200}
            style={{ ...zoomStepStyle, color: nodeZoomPercent >= 200 ? '#cbd5e1' : '#475569', cursor: nodeZoomPercent >= 200 ? 'default' : 'pointer' }}
            title="Zoom in all nodes"
          >
            +
          </button>
          {nodeZoomPercent !== 100 && (
            <button onClick={() => onNodeZoomChange(100)} style={{ ...zoomStepStyle, fontSize: 10, color: '#6366f1' }} title="Reset to 100%">
              Reset
            </button>
          )}
        </div>
        <button onClick={onUndo} disabled={!canUndo} style={btnStyle({ background: canUndo ? 'white' : '#f8fafc', color: canUndo ? '#475569' : '#cbd5e1', cursor: canUndo ? 'pointer' : 'default' })} title="Undo">↩</button>
        <button onClick={onRedo} disabled={!canRedo} style={btnStyle({ background: canRedo ? 'white' : '#f8fafc', color: canRedo ? '#475569' : '#cbd5e1', cursor: canRedo ? 'pointer' : 'default' })} title="Redo">↪</button>
        {generateVisualsStatus && (
          <span
            style={{
              fontSize: 11,
              fontWeight: 600,
              color: generateVisualsStatus.type === 'success' ? '#16a34a' : '#dc2626',
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
              <div style={{ width: 72, height: 6, borderRadius: 999, background: '#f3e8ff', overflow: 'hidden' }}>
                <div style={{ width: `${percent}%`, height: '100%', background: '#a21caf', transition: 'width 200ms ease' }} />
              </div>
              <span style={{ fontSize: 11, fontWeight: 700, color: '#a21caf', minWidth: 32 }}>{percent}%</span>
            </div>
          )
        })()}
        <button onClick={onGenerateVisuals} disabled={isGeneratingVisuals} style={btnStyle({ background: '#fdf4ff', borderColor: '#e9d5ff', color: '#a21caf', opacity: isGeneratingVisuals ? 0.7 : 1 })}>
          {isGeneratingVisuals
            ? `⏳ Generating… ${generateVisualsProgress ? `${generateVisualsProgress.completed}/${generateVisualsProgress.total}` : ''}`
            : '🎨 Generate Visuals'}
        </button>
        <button onClick={onVisualize} style={btnStyle({ background: '#eff6ff', borderColor: '#93c5fd', color: '#2563eb' })}>🎬 Visualize</button>
        <button onClick={onExport} style={btnStyle({ background: '#f0fdf4', borderColor: '#86efac', color: '#16a34a' })}>📤 Export</button>
        <button onClick={onSave} style={btnStyle({ background: '#fef3c7', borderColor: '#fde68a', color: '#92400e' })}>💾 Save</button>
      </div>
    </div>
  )
}
