import { useState } from 'react'
import '../toolbar/PropertiesPanel.css'
import '../../styles/flow-editor.css'
import SearchableSelect, { type SearchableSelectOption } from '../../../../shared/components/SearchableSelect'
import IngredientSelector from '../../../recipe-tool/components/IngredientSelector'
import type { Process } from '../../../../types/process'
import {
  ACTIONS_BY_CATEGORY,
  ACTION_CATEGORY_ORDER,
  CUSTOM_ACTION_ID,
} from '../../catalog/actionCatalog'
import { getStepActionSchema, isStepFieldEnabled } from '../../catalog/actionSchemaCatalog'
import { getIngredientById } from '../../catalog/ingredientCatalog'
import { DURATION_UNIT_OPTIONS } from '../../catalog/stepFieldCatalog'
import { CUSTOM_PREPARATION_STYLE_ID, PREPARATION_STYLE_CATALOG } from '../../catalog/preparationStyleCatalog'
import { CUSTOM_FLAME_LEVEL_ID, FLAME_LEVEL_CATALOG } from '../../catalog/flameLevelCatalog'
import { normalizeProcessStepNodeData, type ActionOnIngredient } from '../../model/processStepData'

type ProcessStepPanelProps = {
  node: { id: string; data: unknown }
  /** Already filtered to: same recipe, type === 'SUBPROCESS', excluding the process currently open — a MAIN process or a process from another recipe can never be an Action On target. */
  availableSubprocesses: Process[]
  updateStepField: (nodeId: string, field: string, value: string) => void
  updateActionOnIngredients: (nodeId: string, ingredients: ActionOnIngredient[]) => void
  updateActionOnProcesses: (nodeId: string, processIds: number[]) => void
  onDeleteNode?: (nodeId: string) => void
  onDuplicateNode?: (nodeId: string) => void
  /** Navigates into the referenced subprocess's own canvas — the other way (besides the process list) to open a subprocess. */
  onOpenSubprocess?: (processId: number) => void
  /**
   * Triggers the whole-process visualization job (see ProcessCanvas's generateVisuals) — V1 has no
   * single-step generation endpoint, so "Regenerate" here re-runs the same process-wide job rather
   * than a step-scoped call; steps that already have an image are still re-checked cheaply (the
   * backend's asset lookup only regenerates what's actually missing).
   */
  onGenerateVisuals?: () => void
  isGeneratingVisuals?: boolean
}

type ActionOnTab = 'INGREDIENTS' | 'PROCESSES'

const actionOptions: SearchableSelectOption[] = ACTION_CATEGORY_ORDER.flatMap((category) =>
  ACTIONS_BY_CATEGORY[category].map((action) => ({ value: action.id, label: action.displayName, icon: action.icon, category }))
)

const preparationStyleOptions: SearchableSelectOption[] = PREPARATION_STYLE_CATALOG.map((style) => ({ value: style.id, label: style.label }))
const flameLevelOptions: SearchableSelectOption[] = FLAME_LEVEL_CATALOG.map((level) => ({ value: level.id, label: level.label }))

const UNIT_LABELS: Record<string, string> = { COUNT: 'count', GRAM: 'g', KG: 'kg', ML: 'mL', LITER: 'L' }

export default function ProcessStepPanel({
  node,
  availableSubprocesses,
  updateStepField,
  updateActionOnIngredients,
  updateActionOnProcesses,
  onDeleteNode,
  onDuplicateNode,
  onOpenSubprocess,
  onGenerateVisuals,
  isGeneratingVisuals,
}: ProcessStepPanelProps) {
  const [actionOnTab, setActionOnTab] = useState<ActionOnTab>('INGREDIENTS')
  const [advancedOpen, setAdvancedOpen] = useState(false)
  const normalized = normalizeProcessStepNodeData(node.data)
  const step = normalized.step
  const visualization = normalized.visualization
  const schema = getStepActionSchema(step.action)
  const preparationStyleRelevant = isStepFieldEnabled(step.action, 'preparationStyleId')
  // ingredientId/quantity/unitId are superseded entirely by Action On for the new Process model.
  // preparationStyleId is per-ingredient (see the Action On ingredient rows below), not step-level.
  // notes is superseded by the core, always-shown Action Description field below (not schema-gated).
  const extraFieldKeys = schema.fields.map((field) => field.key).filter((key) => !['ingredientId', 'quantity', 'unitId', 'repeatInterval', 'preparationStyleId', 'notes'].includes(key))

  const ingredientIds = step.actionOn.ingredients.map((entry) => entry.ingredientId)
  const selectedProcessIds = step.actionOn.processes.map((entry) => entry.processId)

  const addActionOnIngredient = (ingredient: ActionOnIngredient) => {
    updateActionOnIngredients(node.id, [...step.actionOn.ingredients, ingredient])
  }

  const removeActionOnIngredient = (index: number) => {
    updateActionOnIngredients(node.id, step.actionOn.ingredients.filter((_, i) => i !== index))
  }

  const updateActionOnIngredientAt = (index: number, patch: Partial<ActionOnIngredient>) => {
    updateActionOnIngredients(node.id, step.actionOn.ingredients.map((entry, i) => (i === index ? { ...entry, ...patch } : entry)))
  }

  const addActionOnProcess = (processId: number) => {
    if (selectedProcessIds.includes(processId)) return
    updateActionOnProcesses(node.id, [...selectedProcessIds, processId])
  }

  const removeActionOnProcess = (processId: number) => {
    updateActionOnProcesses(node.id, selectedProcessIds.filter((id) => id !== processId))
  }

  const unselectedSubprocesses = availableSubprocesses.filter((process) => !selectedProcessIds.includes(process.id))

  const renderExtraField = (key: string) => {
    switch (key) {
      case 'flameLevelId':
        return (
          <div key={key} className="flow-properties-field">
            <label className="flow-properties-label">Flame Level</label>
            <SearchableSelect
              value={step.flameLevelId}
              onChange={(value) => updateStepField(node.id, 'flameLevelId', value)}
              options={flameLevelOptions}
              placeholder="Select Flame Level"
            />
            {step.flameLevelId === CUSTOM_FLAME_LEVEL_ID && (
              <input
                className="flow-properties-input"
                style={{ marginTop: 6 }}
                value={step.customFlameLevel}
                onChange={(e) => updateStepField(node.id, 'customFlameLevel', e.target.value)}
                placeholder="Enter custom flame level"
              />
            )}
          </div>
        )
      case 'temperature':
        return (
          <div key={key} className="flow-properties-field">
            <label className="flow-properties-label">Temperature</label>
            <input
              className="flow-properties-input"
              value={step.temperature}
              onChange={(e) => updateStepField(node.id, 'temperature', e.target.value)}
              placeholder="180 C"
            />
          </div>
        )
      case 'duration':
        return (
          <div key={key} className="flow-properties-field">
            <label className="flow-properties-label">Duration</label>
            <div className="flow-properties-actions" style={{ marginTop: 0, paddingTop: 0, borderTop: 'none' }}>
              <input
                className="flow-properties-input"
                value={step.durationValue}
                onChange={(e) => updateStepField(node.id, 'durationValue', e.target.value)}
                placeholder="5"
              />
              <select
                className="flow-properties-input"
                value={step.durationUnit}
                onChange={(e) => updateStepField(node.id, 'durationUnit', e.target.value)}
              >
                <option value="">Unit</option>
                {DURATION_UNIT_OPTIONS.map((option) => (
                  <option key={option} value={option}>{option}</option>
                ))}
              </select>
            </div>
          </div>
        )
      default:
        return null
    }
  }

  return (
    <div className="flow-properties-panel">
      <div className="flow-properties-header">
        <div className="flow-properties-header-content">
          <span className="flow-properties-icon">🍳</span>
          <div className="flow-properties-header-info">
            <div className="flow-properties-header-title">{normalized.title}</div>
            <span className="flow-properties-type-badge" style={{ background: '#f0fdf4', border: '1px solid #86efac', color: '#16a34a' }}>
              Step
            </span>
          </div>
        </div>
      </div>

      <div className="flow-properties-content">
        <div className="flow-editor-section-heading">Action</div>
        <div className="flow-properties-field">
          <label className="flow-properties-label">Action *</label>
          <SearchableSelect
            value={step.action}
            onChange={(value) => updateStepField(node.id, 'action', value)}
            options={actionOptions}
            placeholder="Select Action"
          />
        </div>
        {step.action === CUSTOM_ACTION_ID && (
          <div className="flow-properties-field">
            <label className="flow-properties-label">Custom Action Name</label>
            <input
              className="flow-properties-input"
              value={step.customActionName}
              onChange={(e) => updateStepField(node.id, 'customActionName', e.target.value)}
              placeholder="Enter action name"
            />
          </div>
        )}

        <div className="mt-2 h-px" style={{ background: 'var(--flow-border)' }} />
        <div className="flow-editor-section-heading">Action On</div>
        <div style={{ display: 'flex', gap: 6, marginBottom: 10 }}>
          <button
            type="button"
            onClick={() => setActionOnTab('INGREDIENTS')}
            style={{
              flex: 1, padding: '6px 8px', borderRadius: 8, fontSize: 11.5, fontWeight: 700, cursor: 'pointer',
              border: `1px solid ${actionOnTab === 'INGREDIENTS' ? 'var(--flow-accent)' : 'var(--flow-border)'}`,
              background: actionOnTab === 'INGREDIENTS' ? 'var(--flow-accent)' : 'var(--flow-surface)',
              color: actionOnTab === 'INGREDIENTS' ? 'white' : 'var(--flow-text-muted)',
            }}
          >
            🥕 Ingredients ({ingredientIds.length})
          </button>
          <button
            type="button"
            onClick={() => setActionOnTab('PROCESSES')}
            style={{
              flex: 1, padding: '6px 8px', borderRadius: 8, fontSize: 11.5, fontWeight: 700, cursor: 'pointer',
              border: `1px solid ${actionOnTab === 'PROCESSES' ? 'var(--flow-accent)' : 'var(--flow-border)'}`,
              background: actionOnTab === 'PROCESSES' ? 'var(--flow-accent)' : 'var(--flow-surface)',
              color: actionOnTab === 'PROCESSES' ? 'white' : 'var(--flow-text-muted)',
            }}
          >
            🔗 Processes ({selectedProcessIds.length})
          </button>
        </div>

        {actionOnTab === 'INGREDIENTS' ? (
          <div className="flex flex-col gap-2">
            {step.actionOn.ingredients.length === 0 ? (
              <div style={{ fontSize: 12, color: 'var(--flow-text-subtle)' }}>No ingredients on this step yet.</div>
            ) : (
              step.actionOn.ingredients.map((entry, index) => {
                const catalogEntry = getIngredientById(entry.ingredientId)
                return (
                  <div
                    key={`${entry.ingredientId}-${index}`}
                    className="flex flex-wrap items-center gap-2 rounded-lg border p-2"
                    style={{ border: '1px solid var(--flow-border)', background: 'var(--flow-surface-muted)' }}
                  >
                    <div style={{ minWidth: 80, fontWeight: 700, fontSize: 12, color: 'var(--flow-text)' }}>
                      {catalogEntry.icon} {catalogEntry.name}
                    </div>
                    <input
                      className="flow-properties-input"
                      style={{ width: 64 }}
                      value={String(entry.quantity)}
                      onChange={(e) => updateActionOnIngredientAt(index, { quantity: Number(e.target.value) || 0 })}
                    />
                    <span style={{ fontSize: 11, color: 'var(--flow-text-muted)', fontWeight: 600 }}>{UNIT_LABELS[entry.unit]}</span>
                    {preparationStyleRelevant && (
                      <div style={{ flex: 1, minWidth: 120 }}>
                        <SearchableSelect
                          value={entry.preparationStyleId ?? ''}
                          onChange={(value) => updateActionOnIngredientAt(index, {
                            preparationStyleId: value as ActionOnIngredient['preparationStyleId'],
                            customPreparationStyle: value === CUSTOM_PREPARATION_STYLE_ID ? entry.customPreparationStyle : undefined,
                          })}
                          options={preparationStyleOptions}
                          placeholder="Preparation style"
                        />
                      </div>
                    )}
                    {preparationStyleRelevant && entry.preparationStyleId === CUSTOM_PREPARATION_STYLE_ID && (
                      <input
                        className="flow-properties-input"
                        style={{ flex: 1, minWidth: 100 }}
                        value={entry.customPreparationStyle ?? ''}
                        onChange={(e) => updateActionOnIngredientAt(index, { customPreparationStyle: e.target.value })}
                        placeholder="Custom preparation style"
                      />
                    )}
                    <button
                      type="button"
                      onClick={() => removeActionOnIngredient(index)}
                      title="Remove"
                      style={{ border: 'none', background: 'transparent', cursor: 'pointer', fontSize: 13, color: '#dc2626' }}
                    >
                      🗑
                    </button>
                  </div>
                )
              })
            )}
            <IngredientSelector excludeIngredientIds={ingredientIds} action={step.action} onAdd={addActionOnIngredient} />
          </div>
        ) : (
          <div className="flex flex-col gap-2">
            {step.actionOn.processes.length === 0 ? (
              <div style={{ fontSize: 12, color: 'var(--flow-text-subtle)' }}>No subprocesses referenced by this step yet.</div>
            ) : (
              step.actionOn.processes.map((entry) => {
                const process = availableSubprocesses.find((candidate) => candidate.id === entry.processId)
                return (
                  <div
                    key={entry.processId}
                    className="flex items-center justify-between gap-2 rounded-lg border p-2"
                    style={{ border: '1px solid #c7d2fe', background: '#eef2ff' }}
                  >
                    <div style={{ minWidth: 0 }}>
                      <div style={{ fontSize: 12, fontWeight: 700, color: '#312e81', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        🔗 {process ? process.name : `Process #${entry.processId}`}
                      </div>
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexShrink: 0 }}>
                      {process && onOpenSubprocess && (
                        <button
                          type="button"
                          onClick={() => onOpenSubprocess(process.id)}
                          title="Open this subprocess"
                          style={{ border: 'none', background: 'transparent', cursor: 'pointer', fontSize: 11, fontWeight: 700, color: '#4338ca' }}
                        >
                          Open →
                        </button>
                      )}
                      <button
                        type="button"
                        onClick={() => removeActionOnProcess(entry.processId)}
                        title="Remove"
                        style={{ border: 'none', background: 'transparent', cursor: 'pointer', fontSize: 13, color: '#dc2626' }}
                      >
                        🗑
                      </button>
                    </div>
                  </div>
                )
              })
            )}

            <div className="flex items-end gap-2">
              <div style={{ flex: 1 }}>
                <SearchableSelect
                  value=""
                  onChange={(value) => value && addActionOnProcess(Number(value))}
                  options={unselectedSubprocesses.map((process) => ({
                    value: String(process.id),
                    label: process.name,
                  }))}
                  placeholder={unselectedSubprocesses.length ? 'Add a subprocess' : 'No more subprocesses available'}
                  emptyLabel="No matching subprocesses"
                />
              </div>
            </div>
            <div
              style={{ fontSize: 10.5, color: 'var(--flow-text-muted)', background: 'var(--flow-warning-soft)', border: '1px solid var(--flow-warning-border)', borderRadius: 8, padding: '6px 8px' }}
            >
              Only SUBPROCESS documents belonging to this recipe can be chosen — never the MAIN process, and never a process from another recipe.
            </div>
          </div>
        )}

        <div className="mt-2 h-px" style={{ background: 'var(--flow-border)' }} />
        <div className="flow-editor-section-heading">Action Description *</div>
        <div className="flow-properties-field">
          <textarea
            className="flow-properties-textarea"
            rows={3}
            value={step.actionDescription}
            onChange={(e) => updateStepField(node.id, 'actionDescription', e.target.value)}
            placeholder="Describe what this step does"
          />
        </div>

        <div className="flow-editor-section-heading">Expected Output *</div>
        <div className="flow-properties-field">
          <textarea
            className="flow-properties-textarea"
            rows={3}
            value={step.expectedOutput}
            onChange={(e) => updateStepField(node.id, 'expectedOutput', e.target.value)}
            placeholder="Describe the expected result after this step"
          />
        </div>

        {extraFieldKeys.length > 0 && (
          <>
            <div className="mt-2 h-px" style={{ background: 'var(--flow-border)' }} />
            <button
              type="button"
              onClick={() => setAdvancedOpen((value) => !value)}
              className="flow-editor-section-heading"
              style={{ display: 'flex', alignItems: 'center', gap: 6, border: 'none', background: 'transparent', cursor: 'pointer', padding: 0, width: '100%', textAlign: 'left' }}
            >
              <span>{advancedOpen ? '▾' : '▸'}</span> Advanced Options
            </button>
            {advancedOpen && extraFieldKeys.map(renderExtraField)}
          </>
        )}

        {onGenerateVisuals && (
          <>
            <div className="mt-2 h-px" style={{ background: 'var(--flow-border)' }} />
            <div className="flow-editor-section-heading">Visualization</div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {visualization?.imageUrl && (
                <div style={{ width: '100%', aspectRatio: '16 / 10', borderRadius: 10, overflow: 'hidden', border: '1px solid var(--flow-border)', background: '#0f172a' }}>
                  <img src={visualization.imageUrl} alt="Generated visualization" style={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }} />
                </div>
              )}
              <button
                type="button"
                onClick={onGenerateVisuals}
                disabled={isGeneratingVisuals}
                style={{
                  alignSelf: 'flex-start', padding: '6px 12px', borderRadius: 7,
                  border: '1px solid var(--flow-magic-border)', background: 'var(--flow-magic-soft)', color: 'var(--flow-magic)',
                  fontSize: 11.5, fontWeight: 700, cursor: isGeneratingVisuals ? 'wait' : 'pointer', opacity: isGeneratingVisuals ? 0.7 : 1,
                }}
              >
                {isGeneratingVisuals ? 'Generating…' : visualization?.imageUrl ? '🔄 Regenerate visuals' : '🖼️ Generate visuals'}
              </button>
              <div style={{ fontSize: 10, color: 'var(--flow-text-subtle)' }}>
                Generates an image for every step of this process — not only this one.
              </div>
            </div>
          </>
        )}

        <div className="my-2 h-px" style={{ background: 'var(--flow-border)' }} />
        <div className="flow-editor-section-heading">Actions</div>
        <div className="flow-properties-actions">
          <button className="flow-properties-action-btn flow-properties-duplicate-btn" onClick={() => onDuplicateNode?.(node.id)}>
            📋 Duplicate
          </button>
          <button className="flow-properties-action-btn flow-properties-delete-btn" onClick={() => onDeleteNode?.(node.id)}>
            🗑 Delete
          </button>
        </div>
      </div>
    </div>
  )
}
