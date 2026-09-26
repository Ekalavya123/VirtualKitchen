/**
 * STEP node data model for the *new* Process Builder (Recipe -> Process ->
 * ProcessNode), distinct from the legacy flow model's `StepNodeStructuredFields`
 * (types/recipeFlow.ts). The legacy shape stores a single ingredient picked
 * from a hardcoded local catalog; the Process model instead needs "Action On"
 * — a step's action can apply to *multiple* ingredients (from the app's UI
 * static ingredient catalog, catalog/ingredientCatalog.ts — not a backend
 * catalog API) and/or *multiple* subprocess references.
 *
 * This is stored as free-form JSON inside `ProcessNode.data` (already a
 * `Map<String,Object>` on the backend — see Process.java), so no backend
 * change is needed to support it. It intentionally does not reuse
 * StepNodeStructuredFields: reusing it would mean bolting a second,
 * conflicting ingredient model onto a shape already built around a single
 * ingredientId, which is exactly the "two independent sources of truth" the
 * brief says to avoid.
 */

import { getActionDisplayName, resolveStepActionId, type StepActionId } from '../catalog/actionCatalog'
import { isIngredientId, type IngredientId } from '../catalog/ingredientCatalog'
import {
  resolvePreparationStyleId,
  type PreparationStyleId,
} from '../catalog/preparationStyleCatalog'
import { resolveFlameLevelId, type FlameLevelId } from '../catalog/flameLevelCatalog'
import type { UnitType } from '../../../types/process'
import type { DurationUnitOption } from '../catalog/stepFieldCatalog'
import { buildDurationLabel } from '../catalog/stepFieldCatalog'

/**
 * One "On:" ingredient target of a step's Action On — sourced from the UI's
 * static ingredient catalog (catalog/ingredientCatalog.ts), identified by
 * its string id there (e.g. "onion"), not the numeric backend `Ingredient`
 * catalog used elsewhere (RecipeIngredient in types/process.ts). Backend
 * storage is identical either way (an opaque field inside the STEP's data
 * bag), so this is purely a frontend catalog-source choice.
 *
 * Preparation style is per-ingredient (e.g. Cut → onion: medium, tomato:
 * large), not a step-level field — the same action can be applied
 * differently to each of its Action On ingredients.
 */
export type ActionOnIngredient = {
  ingredientId: IngredientId
  quantity: number
  unit: UnitType
  notes?: string
  preparationStyleId?: PreparationStyleId | ''
  customPreparationStyle?: string
}

export type ProcessStepActionOnProcess = {
  processId: number
}

export type ProcessStepActionOn = {
  ingredients: ActionOnIngredient[]
  processes: ProcessStepActionOnProcess[]
}

export type ProcessStepFields = {
  action: StepActionId | ''
  customActionName: string
  actionOn: ProcessStepActionOn
  /** Natural-language description of what this step does — a core, always-shown Step property (not an Advanced Option). */
  actionDescription: string
  /** The expected result/state after this step completes — a core, always-shown Step property. */
  expectedOutput: string
  /** Advanced Options (optional, action-specific). */
  flameLevelId: FlameLevelId | ''
  customFlameLevel: string
  temperature: string
  durationValue: string
  durationUnit: DurationUnitOption | ''
}

export type ProcessStepVisualizationStatus = 'not_generated' | 'generated'

/**
 * A STEP's generated visualization image, one per step (never per-ingredient/per-operation — see
 * the V1 "one step -> one image" contract). Mirrors the legacy flow model's `StepVisualizationData`
 * (types/recipeFlow.ts) but is its own type since the two models are otherwise independent.
 */
export type ProcessStepVisualizationData = {
  assetId?: number
  imagePrompt?: string
  imageUrl?: string
  status: ProcessStepVisualizationStatus
}

export type ProcessStepNodeData = {
  title: string
  step: ProcessStepFields
  sectionId?: string | null
  stepNumber?: number
  visualization?: ProcessStepVisualizationData
}

export const createDefaultProcessStepFields = (): ProcessStepFields => ({
  action: '',
  customActionName: '',
  actionOn: { ingredients: [], processes: [] },
  actionDescription: '',
  expectedOutput: '',
  flameLevelId: '',
  customFlameLevel: '',
  temperature: '',
  durationValue: '',
  durationUnit: '',
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

const normalizeActionOnIngredient = (value: unknown): ActionOnIngredient | null => {
  const raw = asRecord(value)
  const ingredientId = isIngredientId(raw.ingredientId) ? raw.ingredientId : null
  const quantity = asNumber(raw.quantity)
  const unit = asString(raw.unit)
  if (ingredientId == null || quantity == null || !UNIT_VALUES.has(unit)) return null

  return {
    ingredientId,
    quantity,
    unit: unit as UnitType,
    notes: typeof raw.notes === 'string' && raw.notes.trim() ? raw.notes : undefined,
    preparationStyleId: resolvePreparationStyleId(raw.preparationStyleId),
    customPreparationStyle: asString(raw.customPreparationStyle),
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
    ? raw.ingredients.map(normalizeActionOnIngredient).filter((entry): entry is ActionOnIngredient => entry != null)
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
    actionDescription: asString(raw.actionDescription),
    expectedOutput: asString(raw.expectedOutput),
    flameLevelId: resolveFlameLevelId(raw.flameLevelId),
    customFlameLevel: asString(raw.customFlameLevel),
    temperature: asString(raw.temperature),
    durationValue: asString(raw.durationValue),
    durationUnit: (raw.durationUnit === 'seconds' || raw.durationUnit === 'minutes' || raw.durationUnit === 'hours') ? raw.durationUnit : '',
  }
}

/**
 * Reads a STEP's visualization state — nested under `visualization` when set by
 * `withProcessStepVisualization`, or (the backend's own write shape, see
 * `ProcessVisualizationService.attachResultsAndSave`) flat sibling fields
 * `visualizationAssetId`/`imagePrompt`/`imageUrl` directly on the node's data, mirroring the same
 * flat-field fallback the legacy flow model's `normalizeStepNodeData` already documents.
 */
const normalizeProcessStepVisualization = (raw: Record<string, unknown>): ProcessStepVisualizationData | undefined => {
  const nested = asRecord(raw.visualization)
  const assetId = asNumber(nested.assetId) ?? asNumber(raw.visualizationAssetId)
  const imageUrl = asString(nested.imageUrl) || asString(raw.imageUrl)
  const imagePrompt = asString(nested.imagePrompt) || asString(raw.imagePrompt)
  if (assetId == null && !imageUrl && !imagePrompt) return undefined

  return {
    assetId: assetId ?? undefined,
    imagePrompt: imagePrompt || undefined,
    imageUrl: imageUrl || undefined,
    status: imageUrl ? 'generated' : 'not_generated',
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
    visualization: normalizeProcessStepVisualization(raw),
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
  if (field === 'flameLevelId' && value !== 'custom') {
    mergedStep.customFlameLevel = ''
  }

  const reNormalized = normalizeProcessStepFields(mergedStep)
  return { ...normalized, step: reNormalized, title: getProcessStepTitle(reNormalized) }
}

export const withProcessStepActionOnIngredients = (data: unknown, ingredients: ActionOnIngredient[]): ProcessStepNodeData => {
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

/** Applies a freshly generated visualization result (from a visualization job's step result) onto a STEP's data. */
export const withProcessStepVisualization = (data: unknown, visualization: ProcessStepVisualizationData): ProcessStepNodeData => {
  const normalized = normalizeProcessStepNodeData(data)
  return { ...normalized, visualization }
}
