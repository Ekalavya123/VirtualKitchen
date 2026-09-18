import { useState } from 'react'
import '../toolbar/PropertiesPanel.css'
import '../../styles/flow-editor.css'
import SearchableSelect, { type SearchableSelectOption } from '../../../../shared/components/SearchableSelect'
import IngredientSelector from '../../../recipe-tool/components/IngredientSelector'
import type { GlobalIngredient } from '../../../../api'
import type { Process, RecipeIngredient } from '../../../../types/process'
import {
  ACTIONS_BY_CATEGORY,
  ACTION_CATEGORY_ORDER,
  CUSTOM_ACTION_ID,
} from '../../catalog/actionCatalog'
import { getStepActionSchema } from '../../catalog/actionSchemaCatalog'
import { DURATION_UNIT_OPTIONS } from '../../catalog/stepFieldCatalog'
import { CUSTOM_PREPARATION_STYLE_ID, PREPARATION_STYLE_CATALOG } from '../../catalog/preparationStyleCatalog'
import { CUSTOM_FLAME_LEVEL_ID, FLAME_LEVEL_CATALOG } from '../../catalog/flameLevelCatalog'
import { normalizeProcessStepNodeData } from '../../model/processStepData'

type ProcessStepPanelProps = {
  node: { id: string; data: unknown }
  ingredientCatalog: GlobalIngredient[]
  /** Already filtered to: same recipe, type === 'SUBPROCESS', excluding the process currently open — a MAIN process or a process from another recipe can never be an Action On target. */
  availableSubprocesses: Process[]
  updateStepField: (nodeId: string, field: string, value: string) => void
  updateActionOnIngredients: (nodeId: string, ingredients: RecipeIngredient[]) => void
  updateActionOnProcesses: (nodeId: string, processIds: number[]) => void
  onDeleteNode?: (nodeId: string) => void
  onDuplicateNode?: (nodeId: string) => void
  /** Navigates into the referenced subprocess's own canvas — the other way (besides the process list) to open a subprocess. */
  onOpenSubprocess?: (processId: number) => void
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
  ingredientCatalog,
  availableSubprocesses,
  updateStepField,
  updateActionOnIngredients,
  updateActionOnProcesses,
  onDeleteNode,
  onDuplicateNode,
  onOpenSubprocess,
}: ProcessStepPanelProps) {
  const [actionOnTab, setActionOnTab] = useState<ActionOnTab>('INGREDIENTS')
  const normalized = normalizeProcessStepNodeData(node.data)
  const step = normalized.step
  const schema = getStepActionSchema(step.action)
  // ingredientId/quantity/unitId are superseded entirely by Action On for the new Process model.
  const extraFieldKeys = schema.fields.map((field) => field.key).filter((key) => !['ingredientId', 'quantity', 'unitId', 'repeatInterval'].includes(key))

  const catalogById = new Map(ingredientCatalog.map((item) => [item.id, item] as const))
  const ingredientIds = step.actionOn.ingredients.map((entry) => entry.ingredientId)
  const selectedProcessIds = step.actionOn.processes.map((entry) => entry.processId)

  const addActionOnIngredient = (ingredient: RecipeIngredient) => {
    updateActionOnIngredients(node.id, [...step.actionOn.ingredients, ingredient])
  }

  const removeActionOnIngredient = (index: number) => {
    updateActionOnIngredients(node.id, step.actionOn.ingredients.filter((_, i) => i !== index))
  }

  const updateActionOnIngredientAt = (index: number, patch: Partial<RecipeIngredient>) => {
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
      case 'preparationStyleId':
        return (
          <div key={key} className="flow-properties-field">
            <label className="flow-properties-label">Preparation Style</label>
            <SearchableSelect
              value={step.preparationStyleId}
              onChange={(value) => updateStepField(node.id, 'preparationStyleId', value)}
              options={preparationStyleOptions}
              placeholder="Select Preparation Style"
            />
            {step.preparationStyleId === CUSTOM_PREPARATION_STYLE_ID && (
              <input
                className="flow-properties-input"
                style={{ marginTop: 6 }}
                value={step.customPreparationStyle}
                onChange={(e) => updateStepField(node.id, 'customPreparationStyle', e.target.value)}
                placeholder="Enter custom preparation style"
              />
            )}
          </div>
        )
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
      case 'notes':
        return (
          <div key={key} className="flow-properties-field">
            <label className="flow-properties-label">Notes</label>
            <textarea
              className="flow-properties-textarea"
              rows={3}
              value={step.notes}
              onChange={(e) => updateStepField(node.id, 'notes', e.target.value)}
              placeholder="Any additional instructions"
            />
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
                const catalogEntry = catalogById.get(entry.ingredientId)
                return (
                  <div
                    key={`${entry.ingredientId}-${index}`}
                    className="flex flex-wrap items-center gap-2 rounded-lg border p-2"
                    style={{ border: '1px solid var(--flow-border)', background: 'var(--flow-surface-muted)' }}
                  >
                    <div style={{ minWidth: 80, fontWeight: 700, fontSize: 12, color: 'var(--flow-text)' }}>
                      {catalogEntry?.name ?? `Ingredient #${entry.ingredientId}`}
                    </div>
                    <input
                      className="flow-properties-input"
                      style={{ width: 64 }}
                      value={String(entry.quantity)}
                      onChange={(e) => updateActionOnIngredientAt(index, { quantity: Number(e.target.value) || 0 })}
                    />
                    <span style={{ fontSize: 11, color: 'var(--flow-text-muted)', fontWeight: 600 }}>{UNIT_LABELS[entry.unit]}</span>
                    <input
                      className="flow-properties-input"
                      style={{ flex: 1, minWidth: 100 }}
                      value={entry.preparation ?? ''}
                      onChange={(e) => updateActionOnIngredientAt(index, { preparation: e.target.value || undefined })}
                      placeholder="Preparation"
                    />
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
            <IngredientSelector catalog={ingredientCatalog} excludeIngredientIds={ingredientIds} onAdd={addActionOnIngredient} />
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

        {extraFieldKeys.length > 0 && (
          <>
            <div className="mt-2 h-px" style={{ background: 'var(--flow-border)' }} />
            <div className="flow-editor-section-heading">{schema.category}</div>
            {extraFieldKeys.map(renderExtraField)}
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
