import type { CSSProperties } from 'react'
import '../../styles/flow-editor.css'
import type { ProcessBreadcrumbEntry, ProcessType } from '../../../../types/process'
import ProcessBreadcrumb from './ProcessBreadcrumb'
import AiCreditBadge from '../toolbar/AiCreditBadge'

type ProcessTopBarProps = {
  name: string
  processType: ProcessType
  breadcrumbAncestors: ProcessBreadcrumbEntry[]
  onNavigateToList: () => void
  onNavigateToAncestor: (index: number) => void
  onBack?: () => void
  onSave: () => void
  isSaving: boolean
  isDirty: boolean
  saveError?: string | null
  onUndo?: () => void
  onRedo?: () => void
  canUndo?: boolean
  canRedo?: boolean
  onDeleteSelected?: () => void
  zoomPercent?: number
  onZoomIn?: () => void
  onZoomOut?: () => void
  onFitView?: () => void
  onVisualize?: () => void
  onExport?: () => void
  /** Starts the AI visualization job for this process's own steps (see ProcessCanvas's generateVisuals). */
  onGenerateVisuals?: () => void
  isGeneratingVisuals?: boolean
  generateVisualsStatus?: { type: 'success' | 'error'; text: string } | null
  /** Adds a new STEP/CONDITION node to the canvas — relocated here from the sidebar's old "Quick Add". */
  onAddStep?: () => void
  onAddCondition?: () => void
  /** STEP nodes of the current process, in canvas order — for the step selector below (relocated from the sidebar's old node list; conditions are never included). */
  steps?: { id: string; label: string }[]
  /** The currently selected node's id, but only when it's a STEP — clears the selector when a CONDITION or nothing is selected. */
  currentStepId?: string | null
  /** Selects and brings a STEP node into view on the canvas — kept separate from process navigation (breadcrumb/onNavigateToList/onNavigateToAncestor above), which moves between processes, not steps within one. */
  onSelectStep?: (stepId: string) => void
}

const btnStyle = (extra: CSSProperties = {}): CSSProperties => ({
  padding: '8px 12px',
  borderRadius: 8,
  border: '1px solid var(--flow-border)',
  background: 'var(--flow-surface)',
  color: 'var(--flow-text-muted)',
  fontSize: 12,
  fontWeight: 600,
  cursor: 'pointer',
  display: 'flex',
  alignItems: 'center',
  gap: 6,
  ...extra,
})

export default function ProcessTopBar({
  name,
  processType,
  breadcrumbAncestors,
  onNavigateToList,
  onNavigateToAncestor,
  onBack,
  onSave,
  isSaving,
  isDirty,
  saveError,
  onUndo,
  onRedo,
  canUndo = false,
  canRedo = false,
  onDeleteSelected,
  zoomPercent,
  onZoomIn,
  onZoomOut,
  onFitView,
  onVisualize,
  onExport,
  onGenerateVisuals,
  isGeneratingVisuals = false,
  generateVisualsStatus,
  onAddStep,
  onAddCondition,
  steps = [],
  currentStepId,
  onSelectStep,
}: ProcessTopBarProps) {
  return (
    <div className="flex h-[3.75rem] flex-shrink-0 items-center justify-between border-b border-[var(--flow-border)] bg-[var(--flow-surface)] px-4">
      <div className="flex items-center gap-2.5" style={{ minWidth: 0 }}>
        {onBack && (
          <button onClick={onBack} style={{ ...btnStyle(), padding: '8px 10px' }} title="Back to the parent process">
            ← Back
          </button>
        )}
        <ProcessBreadcrumb
          ancestors={breadcrumbAncestors}
          currentName={name || 'Untitled Process'}
          onNavigateToList={onNavigateToList}
          onNavigateToAncestor={onNavigateToAncestor}
        />
        <span
          style={{
            fontSize: 10,
            fontWeight: 700,
            padding: '2px 8px',
            borderRadius: 999,
            background: processType === 'MAIN' ? '#f0fdf4' : '#eef2ff',
            border: `1px solid ${processType === 'MAIN' ? '#86efac' : '#c7d2fe'}`,
            color: processType === 'MAIN' ? '#166534' : '#4338ca',
          }}
        >
          {processType}
        </span>

        {(onAddStep || onAddCondition) && (
          <div className="flex items-center gap-1.5 border-l border-[var(--flow-border)] pl-2.5" style={{ flexShrink: 0 }}>
            {onAddStep && (
              <button
                onClick={onAddStep}
                style={btnStyle({ padding: '6px 9px', fontSize: 11.5, background: '#f0f9ff', borderColor: '#bae6fd', color: '#0369a1' })}
                title="Add a step node"
              >
                + Step
              </button>
            )}
            {onAddCondition && (
              <button
                onClick={onAddCondition}
                style={btnStyle({ padding: '6px 9px', fontSize: 11.5, background: '#ecfdf5', borderColor: '#a7f3d0', color: '#047857' })}
                title="Add a condition node"
              >
                + Condition
              </button>
            )}
          </div>
        )}

        {onSelectStep && (
          <select
            value={currentStepId ?? ''}
            onChange={(event) => {
              if (event.target.value) onSelectStep(event.target.value)
            }}
            disabled={steps.length === 0}
            title="Select a step to view/edit"
            style={{
              flexShrink: 1, minWidth: 0, maxWidth: 200, padding: '6px 8px', borderRadius: 8,
              border: '1px solid var(--flow-border)', background: 'var(--flow-surface)',
              color: 'var(--flow-text-muted)', fontSize: 11.5, fontWeight: 600,
              cursor: steps.length === 0 ? 'default' : 'pointer', opacity: steps.length === 0 ? 0.6 : 1,
            }}
          >
            {/* Placeholder only — once a step is selected, the select's own value shows that
                step's label instead (native <select> behavior), matching "show the selected node". */}
            <option value="" disabled>Select node</option>
            {steps.map((step) => (
              <option key={step.id} value={step.id}>{step.label}</option>
            ))}
          </select>
        )}

        {isDirty && !isSaving && (
          <span style={{ fontSize: 11, color: 'var(--flow-warning)', fontWeight: 600 }}>● Unsaved changes</span>
        )}
        {saveError && (
          <span style={{ fontSize: 11, color: '#dc2626', fontWeight: 600 }}>{saveError}</span>
        )}
        {generateVisualsStatus && (
          <span style={{ fontSize: 11, fontWeight: 600, color: generateVisualsStatus.type === 'success' ? 'var(--flow-success)' : '#dc2626' }}>
            {generateVisualsStatus.text}
          </span>
        )}
      </div>

      <div className="flex items-center gap-2">
        <AiCreditBadge />

        {onFitView && (
          <button onClick={onFitView} style={btnStyle({ padding: '8px 10px' })} title="Fit view">
            ⛶
          </button>
        )}

        {zoomPercent != null && onZoomIn && onZoomOut && (
          <div className="flex items-center gap-1 rounded-lg border border-[var(--flow-border)] bg-[var(--flow-surface)] px-1 py-1">
            <button onClick={onZoomOut} style={{ border: 'none', background: 'transparent', padding: '4px 8px', fontSize: 13, fontWeight: 700, cursor: 'pointer', color: 'var(--flow-text-muted)' }} title="Zoom out">−</button>
            <span style={{ fontSize: 12, fontWeight: 700, color: 'var(--flow-text-muted)', minWidth: 38, textAlign: 'center' }}>{zoomPercent}%</span>
            <button onClick={onZoomIn} style={{ border: 'none', background: 'transparent', padding: '4px 8px', fontSize: 13, fontWeight: 700, cursor: 'pointer', color: 'var(--flow-text-muted)' }} title="Zoom in">+</button>
          </div>
        )}

        {onUndo && onRedo && (
          <>
            <button onClick={onUndo} disabled={!canUndo} style={btnStyle({ padding: '8px 10px', opacity: canUndo ? 1 : 0.5, cursor: canUndo ? 'pointer' : 'default' })} title="Undo">↩</button>
            <button onClick={onRedo} disabled={!canRedo} style={btnStyle({ padding: '8px 10px', opacity: canRedo ? 1 : 0.5, cursor: canRedo ? 'pointer' : 'default' })} title="Redo">↪</button>
          </>
        )}

        {onDeleteSelected && (
          <button onClick={onDeleteSelected} style={btnStyle({ padding: '8px 10px', color: '#dc2626', borderColor: '#fda4af' })} title="Delete selected node">
            🗑
          </button>
        )}

        {onGenerateVisuals && (
          <button
            onClick={onGenerateVisuals}
            disabled={isGeneratingVisuals}
            style={btnStyle({
              background: 'var(--flow-magic-soft)',
              borderColor: 'var(--flow-magic-border)',
              color: 'var(--flow-magic)',
              opacity: isGeneratingVisuals ? 0.7 : 1,
              cursor: isGeneratingVisuals ? 'wait' : 'pointer',
            })}
            title="Generate an AI image for every step of this process"
          >
            {isGeneratingVisuals ? 'Generating…' : '🖼️ Generate Visuals'}
          </button>
        )}

        {onVisualize && (
          <button onClick={onVisualize} style={btnStyle({ background: 'var(--flow-info-soft)', borderColor: 'var(--flow-info-border)', color: 'var(--flow-info)' })}>
            🎬 Visualize
          </button>
        )}

        {onExport && (
          <button onClick={onExport} style={btnStyle({ background: 'var(--flow-success-soft)', borderColor: 'var(--flow-success-border)', color: 'var(--flow-success)' })}>
            📤 Export
          </button>
        )}

        <button
          onClick={onSave}
          disabled={isSaving}
          style={btnStyle({
            background: 'var(--flow-accent)',
            color: 'white',
            border: '1px solid var(--flow-accent)',
            opacity: isSaving ? 0.7 : 1,
            cursor: isSaving ? 'wait' : 'pointer',
          })}
        >
          {isSaving ? 'Saving…' : '💾 Save'}
        </button>
      </div>
    </div>
  )
}
