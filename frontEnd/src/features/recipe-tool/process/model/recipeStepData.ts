/**
 * Recipe STEP node data model of the Recipe Process (Recipe -> Process ->
 * ProcessNode): an Action plus its "Action On" targets — a step's action can
 * apply to *multiple* ingredients (from the app's UI static ingredient
 * catalog, catalog/ingredientCatalog.ts — not a backend catalog API) and/or
 * *multiple* subprocess references — and its cooking properties (flame,
 * temperature, duration).
 *
 * This is stored as free-form JSON inside `ProcessNode.data` (a
 * `Map<String,Object>` on the backend — see Process.java).
 */

import { getActionDisplayName, resolveStepActionId, type StepActionId } from '../../catalog/actionCatalog'
import { isIngredientId, type IngredientId } from '../../catalog/ingredientCatalog'
import {
  resolvePreparationStyleId,
  type PreparationStyleId,
} from '../../catalog/preparationStyleCatalog'
import { resolveFlameLevelId, type FlameLevelId } from '../../catalog/flameLevelCatalog'
import type { UnitType } from '../../../../types/recipe'
import type { DurationUnitOption } from '../../catalog/stepFieldCatalog'
import { buildDurationLabel } from '../../catalog/stepFieldCatalog'

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

export type RecipeStepActionOnProcess = {
  processId: number
}

export type RecipeStepActionOn = {
  ingredients: ActionOnIngredient[]
  processes: RecipeStepActionOnProcess[]
}

export type RecipeStepFields = {
  action: StepActionId | ''
  customActionName: string
  actionOn: RecipeStepActionOn
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

export type RecipeStepVisualizationStatus = 'not_generated' | 'generated'

/**
 * A STEP's generated visualization image, one per step (never per-ingredient/per-operation — see
 * the V1 "one step -> one image" contract).
 */
export type RecipeStepVisualizationData = {
  assetId?: number
  imagePrompt?: string
  imageUrl?: string
  status: RecipeStepVisualizationStatus
}

export type RecipeStepNodeData = {
  title: string
  step: RecipeStepFields
  sectionId?: string | null
  stepNumber?: number
  visualization?: RecipeStepVisualizationData
}

export const createDefaultRecipeStepFields = (): RecipeStepFields => ({
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

const normalizeActionOnProcess = (value: unknown): RecipeStepActionOnProcess | null => {
  const raw = asRecord(value)
  const processId = asNumber(raw.processId)
  if (processId == null) return null
  return { processId }
}

const normalizeActionOn = (value: unknown): RecipeStepActionOn => {
  const raw = asRecord(value)
  const ingredients = Array.isArray(raw.ingredients)
    ? raw.ingredients.map(normalizeActionOnIngredient).filter((entry): entry is ActionOnIngredient => entry != null)
    : []
  const processes = Array.isArray(raw.processes)
    ? raw.processes.map(normalizeActionOnProcess).filter((entry): entry is RecipeStepActionOnProcess => entry != null)
    : []
  return { ingredients, processes }
}

export const normalizeRecipeStepFields = (value: unknown): RecipeStepFields => {
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
 * `withRecipeStepVisualization`, or (the backend's own write shape, see
 * `RecipeProcessVisualizationService.attachResultsAndSave`) flat sibling fields
 * `visualizationAssetId`/`imagePrompt`/`imageUrl` directly on the node's data.
 */
const normalizeRecipeStepVisualization = (raw: Record<string, unknown>): RecipeStepVisualizationData | undefined => {
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

export const normalizeRecipeStepNodeData = (value: unknown): RecipeStepNodeData => {
  const raw = asRecord(value)
  const step = normalizeRecipeStepFields(raw.step)
  return {
    title: getRecipeStepTitle(step),
    step,
    sectionId: typeof raw.sectionId === 'string' || raw.sectionId === null ? (raw.sectionId as string | null) : null,
    stepNumber: typeof raw.stepNumber === 'number' ? raw.stepNumber : undefined,
    visualization: normalizeRecipeStepVisualization(raw),
  }
}

export const createDefaultRecipeStepNodeData = (): RecipeStepNodeData => ({
  title: getRecipeStepTitle(createDefaultRecipeStepFields()),
  step: createDefaultRecipeStepFields(),
  sectionId: null,
})

export function getRecipeStepTitle(step: RecipeStepFields): string {
  return getActionDisplayName(step.action, step.customActionName)
}

/** Effective duration label ("5 minutes"), derived rather than stored redundantly. */
export const getRecipeStepDurationLabel = (step: RecipeStepFields) =>
  buildDurationLabel(step.durationValue, step.durationUnit)

/**
 * Applies a single unprefixed step field update (e.g. `action`, `temperature`) to a
 * RecipeStepNodeData value (the STEP counterpart of recipeProcessCanvas.helpers.ts's
 * `applyConditionFieldUpdate`) — `actionOn` itself is updated separately (see
 * `withRecipeStepActionOnIngredients`/`withRecipeStepActionOnProcesses` below), not through this
 * generic field setter, since it's a structured list rather than a scalar.
 */
export const applyRecipeStepFieldUpdate = (data: unknown, field: string, value: string): RecipeStepNodeData => {
  const normalized = normalizeRecipeStepNodeData(data)
  const mergedStep: RecipeStepFields = { ...normalized.step, [field]: value }

  if (field === 'action' && value !== 'custom') {
    mergedStep.customActionName = ''
  }
  if (field === 'flameLevelId' && value !== 'custom') {
    mergedStep.customFlameLevel = ''
  }

  const reNormalized = normalizeRecipeStepFields(mergedStep)
  return { ...normalized, step: reNormalized, title: getRecipeStepTitle(reNormalized) }
}

export const withRecipeStepActionOnIngredients = (data: unknown, ingredients: ActionOnIngredient[]): RecipeStepNodeData => {
  const normalized = normalizeRecipeStepNodeData(data)
  return { ...normalized, step: { ...normalized.step, actionOn: { ...normalized.step.actionOn, ingredients } } }
}

export const withRecipeStepActionOnProcesses = (data: unknown, processIds: number[]): RecipeStepNodeData => {
  const normalized = normalizeRecipeStepNodeData(data)
  return {
    ...normalized,
    step: { ...normalized.step, actionOn: { ...normalized.step.actionOn, processes: processIds.map((processId) => ({ processId })) } },
  }
}

/** Applies a freshly generated visualization result (from a visualization job's step result) onto a STEP's data. */
export const withRecipeStepVisualization = (data: unknown, visualization: RecipeStepVisualizationData): RecipeStepNodeData => {
  const normalized = normalizeRecipeStepNodeData(data)
  return { ...normalized, visualization }
}
