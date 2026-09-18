/**
 * STEP node data model for the *new* Process Builder (Recipe -> Process ->
 * ProcessNode), distinct from the legacy flow model's `StepNodeStructuredFields`
 * (types/recipeFlow.ts). The legacy shape stores a single ingredient picked
 * from a hardcoded local catalog; the Process model instead needs "Action On"
 * — a step's action can apply to *multiple* ingredients (from the recipe's
 * real global ingredient catalog) and/or *multiple* subprocess references.
 *
 * This is stored as free-form JSON inside `ProcessNode.data` (already a
 * `Map<String,Object>` on the backend — see Process.java), so no backend
 * change is needed to support it. It intentionally does not reuse
 * StepNodeStructuredFields: reusing it would mean bolting a second,
 * conflicting ingredient model onto a shape already built around a single
 * ingredientId, which is exactly the "two independent sources of truth" the
 * brief says to avoid.
 */

import type { RecipeIngredient } from '../../../types/process'
import { getActionDisplayName, resolveStepActionId, type StepActionId } from '../catalog/actionCatalog'
import {
  resolvePreparationStyleId,
  type PreparationStyleId,
} from '../catalog/preparationStyleCatalog'
import { resolveFlameLevelId, type FlameLevelId } from '../catalog/flameLevelCatalog'
import type { DurationUnitOption } from '../catalog/stepFieldCatalog'
import { buildDurationLabel } from '../catalog/stepFieldCatalog'

export type ProcessStepActionOnProcess = {
  processId: number
}

export type ProcessStepActionOn = {
  ingredients: RecipeIngredient[]
  processes: ProcessStepActionOnProcess[]
}

export type ProcessStepFields = {
  action: StepActionId | ''
  customActionName: string
  actionOn: ProcessStepActionOn
  preparationStyleId: PreparationStyleId | ''
  customPreparationStyle: string
  flameLevelId: FlameLevelId | ''
  customFlameLevel: string
  temperature: string
  durationValue: string
  durationUnit: DurationUnitOption | ''
  notes: string
}

export type ProcessStepNodeData = {
  title: string
  step: ProcessStepFields
  sectionId?: string | null
  stepNumber?: number
}

export const createDefaultProcessStepFields = (): ProcessStepFields => ({
  action: '',
  customActionName: '',
  actionOn: { ingredients: [], processes: [] },
  preparationStyleId: '',
  customPreparationStyle: '',
  flameLevelId: '',
  customFlameLevel: '',
  temperature: '',
  durationValue: '',
  durationUnit: '',
  notes: '',
})

const asRecord = (value: unknown): Record<string, unknown> => (value && typeof value === 'object' ? (value as Record<string, unknown>) : {})

const asString = (value: unknown): string => (typeof value === 'string' ? value : '')

const asNumber = (value: unknown): number | null => {
  if (typeof value === 'number' && Number.isFinite(value)) return value
  if (typeof value === 'string' && value.trim()) {
    const parsed = Number(value)
    if (Number.isFinite(parsed)) return parsed
  }
  return null
}

const UNIT_VALUES = new Set(['KG', 'GRAM', 'LITER', 'ML', 'COUNT'])

const normalizeActionOnIngredient = (value: unknown): RecipeIngredient | null => {
  const raw = asRecord(value)
  const ingredientId = asNumber(raw.ingredientId)
  const quantity = asNumber(raw.quantity)
  const unit = asString(raw.unit)
  if (ingredientId == null || quantity == null || !UNIT_VALUES.has(unit)) return null

  return {
    ingredientId,
    quantity,
    unit: unit as RecipeIngredient['unit'],
    notes: typeof raw.notes === 'string' && raw.notes.trim() ? raw.notes : undefined,
    preparation: typeof raw.preparation === 'string' && raw.preparation.trim() ? raw.preparation : undefined,
  }
}

const normalizeActionOnProcess = (value: unknown): ProcessStepActionOnProcess | null => {
  const raw = asRecord(value)
  const processId = asNumber(raw.processId)
  if (processId == null) return null
  return { processId }
}

const normalizeActionOn = (value: unknown): ProcessStepActionOn => {
  const raw = asRecord(value)
  const ingredients = Array.isArray(raw.ingredients)
    ? raw.ingredients.map(normalizeActionOnIngredient).filter((entry): entry is RecipeIngredient => entry != null)
    : []
  const processes = Array.isArray(raw.processes)
    ? raw.processes.map(normalizeActionOnProcess).filter((entry): entry is ProcessStepActionOnProcess => entry != null)
    : []
  return { ingredients, processes }
}

export const normalizeProcessStepFields = (value: unknown): ProcessStepFields => {
  const raw = asRecord(value)
  return {
    action: resolveStepActionId(raw.action),
    customActionName: asString(raw.customActionName),
    actionOn: normalizeActionOn(raw.actionOn),
    preparationStyleId: resolvePreparationStyleId(raw.preparationStyleId),
    customPreparationStyle: asString(raw.customPreparationStyle),
    flameLevelId: resolveFlameLevelId(raw.flameLevelId),
    customFlameLevel: asString(raw.customFlameLevel),
    temperature: asString(raw.temperature),
    durationValue: asString(raw.durationValue),
    durationUnit: (raw.durationUnit === 'seconds' || raw.durationUnit === 'minutes' || raw.durationUnit === 'hours') ? raw.durationUnit : '',
    notes: asString(raw.notes),
  }
}

export const normalizeProcessStepNodeData = (value: unknown): ProcessStepNodeData => {
  const raw = asRecord(value)
  const step = normalizeProcessStepFields(raw.step)
  return {
    title: getProcessStepTitle(step),
    step,
    sectionId: typeof raw.sectionId === 'string' || raw.sectionId === null ? (raw.sectionId as string | null) : null,
    stepNumber: typeof raw.stepNumber === 'number' ? raw.stepNumber : undefined,
  }
}

export const createDefaultProcessStepNodeData = (): ProcessStepNodeData => ({
  title: getProcessStepTitle(createDefaultProcessStepFields()),
  step: createDefaultProcessStepFields(),
  sectionId: null,
})

export function getProcessStepTitle(step: ProcessStepFields): string {
  return getActionDisplayName(step.action, step.customActionName)
}

/** Effective duration label ("5 minutes"), derived rather than stored redundantly. */
export const getProcessStepDurationLabel = (step: ProcessStepFields) =>
  buildDurationLabel(step.durationValue, step.durationUnit)

/**
 * Applies a single unprefixed step field update (e.g. `action`, `temperature`) to a
 * ProcessStepNodeData value, mirroring FlowCanvas.helpers.ts's `applyStepOrConditionFieldUpdate`
 * but for the Action On step model — `actionOn` itself is updated separately (see
 * `withProcessStepActionOnIngredients`/`withProcessStepActionOnProcesses` below), not through this
 * generic field setter, since it's a structured list rather than a scalar.
 */
export const applyProcessStepFieldUpdate = (data: unknown, field: string, value: string): ProcessStepNodeData => {
  const normalized = normalizeProcessStepNodeData(data)
  const mergedStep: ProcessStepFields = { ...normalized.step, [field]: value }

  if (field === 'action' && value !== 'custom') {
    mergedStep.customActionName = ''
  }
  if (field === 'preparationStyleId' && value !== 'custom') {
    mergedStep.customPreparationStyle = ''
  }
  if (field === 'flameLevelId' && value !== 'custom') {
    mergedStep.customFlameLevel = ''
  }

  const reNormalized = normalizeProcessStepFields(mergedStep)
  return { ...normalized, step: reNormalized, title: getProcessStepTitle(reNormalized) }
}

export const withProcessStepActionOnIngredients = (data: unknown, ingredients: RecipeIngredient[]): ProcessStepNodeData => {
  const normalized = normalizeProcessStepNodeData(data)
  return { ...normalized, step: { ...normalized.step, actionOn: { ...normalized.step.actionOn, ingredients } } }
}

export const withProcessStepActionOnProcesses = (data: unknown, processIds: number[]): ProcessStepNodeData => {
  const normalized = normalizeProcessStepNodeData(data)
  return {
    ...normalized,
    step: { ...normalized.step, actionOn: { ...normalized.step.actionOn, processes: processIds.map((processId) => ({ processId })) } },
  }
}
