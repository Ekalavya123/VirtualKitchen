import { useState } from 'react'
import '../styles/RecipePropertiesPanel.css'
import '../../styles/recipe-tool.css'
import SearchableSelect, { type SearchableSelectOption } from '../../../../shared/components/SearchableSelect'
import IngredientSelector from '../../components/IngredientSelector'
import type { Process } from '../../../../types/process'
import { PreparationStyleSelect, UnitSelect } from '../../components/StepFieldInputs'
import {
  ACTIONS_BY_CATEGORY,
  ACTION_CATEGORY_ORDER,
  CUSTOM_ACTION_ID,
  actionAllowsIngredients,
  actionAllowsProcesses,
  getActionCategoryLabel,
  getStepActionById,
} from '../../catalog/actionCatalog'
import { getStepActionSchema, isStepFieldEnabled, type StepSchemaFieldConfig } from '../../catalog/actionSchemaCatalog'
import { getIngredientById } from '../../catalog/ingredientCatalog'
import { isQuantifiableUnit } from '../../catalog/unitCatalog'
import { DURATION_UNIT_OPTIONS, TEMPERATURE_UNIT_OPTIONS } from '../../catalog/stepFieldCatalog'
import { CUSTOM_FLAME_LEVEL_ID, FLAME_LEVEL_CATALOG } from '../../catalog/flameLevelCatalog'
import { getActionOnIngredientDisplayName, normalizeRecipeStepNodeData, type ActionOnIngredient } from '../model/recipeStepData'
import {
  STEP_OUTPUT_PROBLEM_LABELS,
  getAvailableStepOutputs,
  getStepOutputLabel,
  getStepOutputReferenceProblem,
  type StepOutputGraph,
} from '../model/recipeStepOutputs'

type RecipeStepPanelProps = {
  node: { id: string; data: unknown }
  /** Already filtered to: same recipe, type === 'SUBPROCESS', excluding the process currently open — a MAIN process or a process from another recipe can never be an Action On target. */
  availableSubprocesses: Process[]
  updateStepField: (nodeId: string, field: string, value: string) => void
  updateActionOnIngredients: (nodeId: string, ingredients: ActionOnIngredient[]) => void
  updateActionOnProcesses: (nodeId: string, processIds: number[]) => void
  updateActionOnSteps: (nodeId: string, stepIds: string[]) => void
  /** The open process's step-output sources/reachability — decides which earlier steps' outputs this step may use. */
  stepOutputGraph: StepOutputGraph
  onDeleteNode?: (nodeId: string) => void
  onDuplicateNode?: (nodeId: string) => void
  /** Navigates into the referenced subprocess's own canvas — the other way (besides the process list) to open a subprocess. */
  onOpenSubprocess?: (processId: number) => void
  /**
   * Triggers the whole-process visualization job (see RecipeProcessCanvas's generateVisuals) — V1 has no
   * single-step generation endpoint, so "Regenerate" here re-runs the same process-wide job rather
   * than a step-scoped call; steps that already have an image are still re-checked cheaply (the
   * backend's asset lookup only regenerates what's actually missing).
   */
  onGenerateVisuals?: () => void
  isGeneratingVisuals?: boolean
}

type ActionOnTab = 'INGREDIENTS' | 'PROCESSES' | 'STEPS'

const actionOptions: SearchableSelectOption[] = ACTION_CATEGORY_ORDER.flatMap((category) =>
  ACTIONS_BY_CATEGORY[category].map((action) => ({
    value: action.id,
    label: action.displayName,
    icon: action.icon,
    category: getActionCategoryLabel(category),
    description: action.description,
    keywords: action.aliases,
  }))
)

const flameLevelOptions: SearchableSelectOption[] = FLAME_LEVEL_CATALOG.map((level) => ({ value: level.id, label: level.label, description: level.description }))

/** Step-level schema fields rendered under Advanced Options (per-ingredient ones live on the Action On rows). */
const ADVANCED_FIELD_KEYS = new Set(['temperature', 'flameLevelId', 'duration', 'repeatInterval'])

const requirementHint = (field: StepSchemaFieldConfig) =>
  field.requirement === 'required' ? ' *' : field.requirement === 'recommended' ? ' (recommended)' : ''

export default function RecipeStepPanel({
  node,
  availableSubprocesses,
  updateStepField,
  updateActionOnIngredients,
  updateActionOnProcesses,
  updateActionOnSteps,
  stepOutputGraph,
  onDeleteNode,
  onDuplicateNode,
  onOpenSubprocess,
  onGenerateVisuals,
  isGeneratingVisuals,
}: RecipeStepPanelProps) {
  const [actionOnTab, setActionOnTab] = useState<ActionOnTab>('INGREDIENTS')
  const [advancedOpen, setAdvancedOpen] = useState(false)
  const normalized = normalizeRecipeStepNodeData(node.data)
  const step = normalized.step
  const visualization = normalized.visualization
  const schema = getStepActionSchema(step.action)
  const actionDefinition = step.action ? getStepActionById(step.action) : null
  const quantityRelevant = isStepFieldEnabled(step.action, 'quantity')
  // quantity/unitId/preparationStyleId are per-ingredient (the Action On rows below), not step-level.
  const extraFields = schema.fields.filter((field) => ADVANCED_FIELD_KEYS.has(field.key))

  // Only the Action On target kinds the action accepts are offered (catalog `actionOn`); an existing
  // value of a now-disallowed kind stays visible under its tab so it can still be removed.
  const ingredientsAllowed = actionAllowsIngredients(step.action) || step.actionOn.ingredients.length > 0
  const processesAllowed = actionAllowsProcesses(step.action) || step.actionOn.processes.length > 0
  // A step output is a prepared intermediate, like a subprocess output, so it's offered wherever the
  // catalog lets the action act on subprocess outputs.
  const stepOutputsAllowed = actionAllowsProcesses(step.action) || step.actionOn.steps.length > 0
  const tabAllowed: Record<ActionOnTab, boolean> = { INGREDIENTS: ingredientsAllowed, PROCESSES: processesAllowed, STEPS: stepOutputsAllowed }
  const effectiveTab: ActionOnTab | null = tabAllowed[actionOnTab]
    ? actionOnTab
    : (['INGREDIENTS', 'PROCESSES', 'STEPS'] as const).find((tab) => tabAllowed[tab]) ?? null

  const ingredientIds = step.actionOn.ingredients.map((entry) => entry.ingredientId)
  const selectedProcessIds = step.actionOn.processes.map((entry) => entry.processId)
  const selectedStepIds = step.actionOn.steps.map((entry) => entry.stepId)
  const unselectedStepOutputs = getAvailableStepOutputs(stepOutputGraph, node.id).filter((source) => !selectedStepIds.includes(source.stepId))

  const addActionOnStep = (stepId: string) => {
    if (selectedStepIds.includes(stepId)) return
    updateActionOnSteps(node.id, [...selectedStepIds, stepId])
  }

  const removeActionOnStep = (stepId: string) => {
    updateActionOnSteps(node.id, selectedStepIds.filter((id) => id !== stepId))
  }

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

  const renderAmountWithUnit = (
    key: string,
    label: string,
    valueField: 'durationValue' | 'repeatIntervalValue',
    unitField: 'durationUnit' | 'repeatIntervalUnit',
    placeholder: string,
  ) => (
    <div key={key} className="flow-properties-field">
      <label className="flow-properties-label">{label}</label>
      <div className="flow-properties-actions" style={{ marginTop: 0, paddingTop: 0, borderTop: 'none' }}>
        <input
          className="flow-properties-input"
          inputMode="decimal"
          value={step[valueField]}
          onChange={(e) => updateStepField(node.id, valueField, e.target.value)}
          placeholder={placeholder}
        />
        <select
          className="flow-properties-input"
          value={step[unitField]}
          onChange={(e) => updateStepField(node.id, unitField, e.target.value)}
        >
          <option value="">Unit</option>
          {DURATION_UNIT_OPTIONS.map((option) => (
            <option key={option} value={option}>{option}</option>
          ))}
        </select>
      </div>
    </div>
  )

  const renderExtraField = (field: StepSchemaFieldConfig) => {
    const { key } = field
    const label = `${field.label}${requirementHint(field)}`
    switch (key) {
      case 'flameLevelId':
        return (
          <div key={key} className="flow-properties-field">
            <label className="flow-properties-label">{label}</label>
            <SearchableSelect
              value={step.flameLevelId}
              onChange={(value) => updateStepField(node.id, 'flameLevelId', value)}
              options={flameLevelOptions}
              placeholder="Select Heat Level"
            />
            {step.flameLevelId === CUSTOM_FLAME_LEVEL_ID && (
              <input
                className="flow-properties-input"
                style={{ marginTop: 6 }}
                value={step.customFlameLevel}
                onChange={(e) => updateStepField(node.id, 'customFlameLevel', e.target.value)}
                placeholder="Enter custom heat level"
              />
            )}
          </div>
        )
      case 'temperature':
        return (
          <div key={key} className="flow-properties-field">
            <label className="flow-properties-label">{label}</label>
            <div className="flow-properties-actions" style={{ marginTop: 0, paddingTop: 0, borderTop: 'none' }}>
              <input
                className="flow-properties-input"
                inputMode="decimal"
                value={step.temperatureValue}
                onChange={(e) => updateStepField(node.id, 'temperatureValue', e.target.value)}
                placeholder="180"
              />
              <select
                className="flow-properties-input"
                value={step.temperatureUnit}
                onChange={(e) => updateStepField(node.id, 'temperatureUnit', e.target.value)}
              >
                <option value="">Unit</option>
                {TEMPERATURE_UNIT_OPTIONS.map((option) => (
                  <option key={option.id} value={option.id}>{option.label}</option>
                ))}
              </select>
            </div>
          </div>
        )
      case 'duration':
        return renderAmountWithUnit(key, label, 'durationValue', 'durationUnit', '5')
      case 'repeatInterval':
        return renderAmountWithUnit(key, label, 'repeatIntervalValue', 'repeatIntervalUnit', 'every 2')
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
            placeholder="Search actions (e.g. sauté, temper, knead)"
          />
          {actionDefinition && actionDefinition.id !== CUSTOM_ACTION_ID && (
            <div style={{ marginTop: 4, fontSize: 11, color: 'var(--flow-text-subtle)', lineHeight: 1.35 }}>{actionDefinition.description}</div>
          )}
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
        {effectiveTab === null ? (
          <div style={{ fontSize: 12, color: 'var(--flow-text-subtle)', marginBottom: 10 }}>
            {actionDefinition?.displayName ?? 'This action'} has no Action On target — it acts on equipment or time.
          </div>
        ) : (
          <div style={{ display: 'flex', gap: 6, marginBottom: 10 }}>
            {([
              ['INGREDIENTS', ingredientsAllowed, `🥕 Ingredients (${ingredientIds.length})`],
              ['PROCESSES', processesAllowed, `🔗 Processes (${selectedProcessIds.length})`],
              ['STEPS', stepOutputsAllowed, `↳ Step Outputs (${selectedStepIds.length})`],
            ] as const).filter(([, allowed]) => allowed).map(([tab, , label]) => (
              <button
                key={tab}
                type="button"
                onClick={() => setActionOnTab(tab)}
                style={{
                  flex: 1, padding: '6px 8px', borderRadius: 8, fontSize: 11.5, fontWeight: 700, cursor: 'pointer',
                  border: `1px solid ${effectiveTab === tab ? 'var(--flow-accent)' : 'var(--flow-border)'}`,
                  background: effectiveTab === tab ? 'var(--flow-accent)' : 'var(--flow-surface)',
                  color: effectiveTab === tab ? 'white' : 'var(--flow-text-muted)',
                }}
              >
                {label}
              </button>
            ))}
          </div>
        )}

        {effectiveTab === 'INGREDIENTS' && (
          <div className="flex flex-col gap-2">
            {step.actionOn.ingredients.length === 0 ? (
              <div style={{ fontSize: 12, color: 'var(--flow-text-subtle)' }}>No ingredients on this step yet.</div>
            ) : (
              step.actionOn.ingredients.map((entry, index) => {
                const catalogEntry = getIngredientById(entry.ingredientId)
                const quantifiable = isQuantifiableUnit(entry.unit)
                return (
                  <div
                    key={`${entry.ingredientId}-${index}`}
                    className="flex flex-wrap items-center gap-2 rounded-lg border p-2"
                    style={{ border: '1px solid var(--flow-border)', background: 'var(--flow-surface-muted)' }}
                  >
                    <div style={{ minWidth: 80, fontWeight: 700, fontSize: 12, color: 'var(--flow-text)' }}>
                      {catalogEntry.icon} {getActionOnIngredientDisplayName(entry)}
                    </div>
                    {quantityRelevant && (
                      <>
                        <input
                          className="flow-properties-input"
                          style={{ width: 64 }}
                          inputMode="decimal"
                          disabled={!quantifiable}
                          value={quantifiable && entry.quantity != null ? String(entry.quantity) : ''}
                          placeholder={quantifiable ? 'qty' : '—'}
                          onChange={(e) => {
                            const text = e.target.value.trim()
                            const parsed = Number(text)
                            updateActionOnIngredientAt(index, { quantity: text && Number.isFinite(parsed) ? parsed : null })
                          }}
                        />
                        <UnitSelect
                          ingredientId={entry.ingredientId}
                          value={entry.unit}
                          style={{ width: 110 }}
                          onChange={(unit) => updateActionOnIngredientAt(index, isQuantifiableUnit(unit) ? { unit } : { unit, quantity: null })}
                        />
                      </>
                    )}
                    <PreparationStyleSelect
                      action={step.action}
                      ingredientId={entry.ingredientId}
                      value={entry.preparationStyleId ?? ''}
                      customValue={entry.customPreparationStyle ?? ''}
                      onChange={(preparationStyleId, customPreparationStyle) =>
                        updateActionOnIngredientAt(index, { preparationStyleId, customPreparationStyle: customPreparationStyle || undefined })}
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
            {actionAllowsIngredients(step.action) ? (
              <IngredientSelector excludeIngredientIds={ingredientIds} action={step.action} onAdd={addActionOnIngredient} />
            ) : (
              <div style={{ fontSize: 11, color: 'var(--flow-text-muted)', background: 'var(--flow-warning-soft)', border: '1px solid var(--flow-warning-border)', borderRadius: 8, padding: '6px 8px' }}>
                {actionDefinition?.displayName ?? 'This action'} doesn't act on ingredients — remove these, or choose another action.
              </div>
            )}
          </div>
        )}
        {effectiveTab === 'PROCESSES' && (
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

            {!actionAllowsProcesses(step.action) && (
              <div style={{ fontSize: 11, color: 'var(--flow-text-muted)', background: 'var(--flow-warning-soft)', border: '1px solid var(--flow-warning-border)', borderRadius: 8, padding: '6px 8px' }}>
                {actionDefinition?.displayName ?? 'This action'} doesn't act on subprocess outputs — remove these, or choose another action.
              </div>
            )}
            {actionAllowsProcesses(step.action) && (
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
            )}
            <div
              style={{ fontSize: 10.5, color: 'var(--flow-text-muted)', background: 'var(--flow-warning-soft)', border: '1px solid var(--flow-warning-border)', borderRadius: 8, padding: '6px 8px' }}
            >
              Only SUBPROCESS documents belonging to this recipe can be chosen — never the MAIN process, and never a process from another recipe.
            </div>
          </div>
        )}
        {effectiveTab === 'STEPS' && (
          <div className="flex flex-col gap-2">
            {step.actionOn.steps.length === 0 ? (
              <div style={{ fontSize: 12, color: 'var(--flow-text-subtle)' }}>No earlier step outputs used by this step yet.</div>
            ) : (
              step.actionOn.steps.map((entry) => {
                // Always the referenced step's *current* Expected Output — edits there show up here.
                const label = getStepOutputLabel(stepOutputGraph, entry.stepId)
                const problem = getStepOutputReferenceProblem(stepOutputGraph, entry.stepId, node.id)
                return (
                  <div
                    key={entry.stepId}
                    className="flex items-center justify-between gap-2 rounded-lg border p-2"
                    style={{ border: `1px solid ${problem ? '#fecaca' : '#bbf7d0'}`, background: problem ? '#fef2f2' : '#f0fdf4' }}
                  >
                    <div style={{ minWidth: 0 }}>
                      <div style={{ fontSize: 12, fontWeight: 700, color: problem ? '#991b1b' : '#166534', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        ↳ {label || 'Unavailable step output'}
                      </div>
                      {problem && (
                        <div style={{ fontSize: 10.5, color: '#b91c1c' }}>Unavailable — {STEP_OUTPUT_PROBLEM_LABELS[problem]}. Remove it or reconnect the steps before saving.</div>
                      )}
                    </div>
                    <button
                      type="button"
                      onClick={() => removeActionOnStep(entry.stepId)}
                      title="Remove"
                      style={{ border: 'none', background: 'transparent', cursor: 'pointer', fontSize: 13, color: '#dc2626', flexShrink: 0 }}
                    >
                      🗑
                    </button>
                  </div>
                )
              })
            )}

            {actionAllowsProcesses(step.action) ? (
              <SearchableSelect
                value=""
                onChange={(value) => value && addActionOnStep(value)}
                options={unselectedStepOutputs.map((source) => ({
                  value: source.stepId,
                  label: source.label,
                  category: 'Step Outputs',
                  description: `From step ${source.stepNumber}`,
                }))}
                placeholder={unselectedStepOutputs.length ? 'Add an earlier step output' : 'No earlier step outputs available'}
                emptyLabel="No matching step outputs"
              />
            ) : (
              <div style={{ fontSize: 11, color: 'var(--flow-text-muted)', background: 'var(--flow-warning-soft)', border: '1px solid var(--flow-warning-border)', borderRadius: 8, padding: '6px 8px' }}>
                {actionDefinition?.displayName ?? 'This action'} doesn't act on prepared outputs — remove these, or choose another action.
              </div>
            )}
            <div style={{ fontSize: 10.5, color: 'var(--flow-text-muted)', background: 'var(--flow-surface-muted)', border: '1px solid var(--flow-border)', borderRadius: 8, padding: '6px 8px' }}>
              Only steps connected before this one that have an Expected Output are listed.
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

        {extraFields.length > 0 && (
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
            {advancedOpen && extraFields.map(renderExtraField)}
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
