/**
 * Recipe STEP node data model of the Recipe Process (Recipe -> Process ->
 * ProcessNode): an Action plus its "Action On" targets — a step's action can
 * apply to *multiple* ingredients (from the app's UI static ingredient
 * catalog, catalog/ingredientCatalog.ts — not a backend catalog API) and/or
 * *multiple* subprocess references — and its cooking properties (flame,
 * temperature, duration, repeat interval). Which of those fields apply is
 * decided per action by the shared catalog (catalog/actionSchemaCatalog.ts).
 *
 * This is stored as free-form JSON inside `ProcessNode.data` (a
 * `Map<String,Object>` on the backend — see Process.java). Data saved before
 * catalog v2 (Process `UnitType` units, free-text temperature) is mapped onto
 * the new shape on read, and written back in the new shape on the next save.
 */

import { CUSTOM_ACTION_ID, getActionDisplayName, resolveStepActionId, type StepActionId } from '../../catalog/actionCatalog'
import { STEP_LEVEL_FIELD_KEYS, isStepFieldEnabled, type StepSchemaFieldKey } from '../../catalog/actionSchemaCatalog'
import { CUSTOM_INGREDIENT_ID, getIngredientDisplayName, isIngredientId, type IngredientId } from '../../catalog/ingredientCatalog'
import {
  resolvePreparationStyleId,
  type PreparationStyleId,
} from '../../catalog/preparationStyleCatalog'
import { resolveFlameLevelId, type FlameLevelId } from '../../catalog/flameLevelCatalog'
import { resolveUnitId, type UnitId } from '../../catalog/unitCatalog'
import {
  buildDurationLabel,
  buildTemperatureLabel,
  isDurationUnitOption,
  parseTemperatureLabel,
  resolveTemperatureUnit,
  type DurationUnitOption,
  type TemperatureUnitOption,
} from '../../catalog/stepFieldCatalog'

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
  /** Only meaningful when `ingredientId` is the catalog's "custom" entry. */
  customIngredientName?: string
  /** Null when no amount applies (not stated, or a non-numeric unit such as "to-taste"). */
  quantity: number | null
  /** A catalog unit id (unitCatalog.ts); '' when none. */
  unit: UnitId | ''
  notes?: string
  preparationStyleId?: PreparationStyleId | ''
  customPreparationStyle?: string
}

export type RecipeStepActionOnProcess = {
  processId: number
}

/**
 * A reference to an earlier STEP's Expected Output in the same process, by that step's node id —
 * never by the output text, so editing the source step's Expected Output (e.g. "boiled eggs" ->
 * "soft-boiled eggs") is reflected everywhere it's used. Which steps are valid sources is decided
 * by the process graph (see recipeStepOutputs.ts).
 */
export type RecipeStepActionOnStep = {
  stepId: string
}

export type RecipeStepActionOn = {
  ingredients: ActionOnIngredient[]
  processes: RecipeStepActionOnProcess[]
  steps: RecipeStepActionOnStep[]
}

export type RecipeStepFields = {
  action: StepActionId | ''
  customActionName: string
  actionOn: RecipeStepActionOn
  /** Natural-language description of what this step does — a core, always-shown Step property (not an Advanced Option). */
  actionDescription: string
  /** The expected result/state after this step completes — a core, always-shown Step property. */
  expectedOutput: string
  /** Advanced Options (optional, action-specific — see actionSchemaCatalog). */
  flameLevelId: FlameLevelId | ''
  customFlameLevel: string
  /** Numeric text; what it measures (oven/oil/liquid/…) comes from the action's temperatureContext. */
  temperatureValue: string
  temperatureUnit: TemperatureUnitOption | ''
  durationValue: string
  durationUnit: DurationUnitOption | ''
  /** How often the action repeats during the step (e.g. stir every 2 minutes). */
  repeatIntervalValue: string
  repeatIntervalUnit: DurationUnitOption | ''
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
  actionOn: { ingredients: [], processes: [], steps: [] },
  actionDescription: '',
  expectedOutput: '',
  flameLevelId: '',
  customFlameLevel: '',
  temperatureValue: '',
  temperatureUnit: '',
  durationValue: '',
  durationUnit: '',
  repeatIntervalValue: '',
  repeatIntervalUnit: '',
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

// Only an unknown ingredient drops the entry — a missing quantity or an unrecognized unit is kept
// (as null / '') rather than silently discarding the ingredient, as the pre-catalog normalizer did.
const normalizeActionOnIngredient = (value: unknown): ActionOnIngredient | null => {
  const raw = asRecord(value)
  const ingredientId = isIngredientId(raw.ingredientId) ? raw.ingredientId : null
  if (ingredientId == null) return null

  return {
    ingredientId,
    customIngredientName: ingredientId === CUSTOM_INGREDIENT_ID ? asString(raw.customIngredientName) : undefined,
    quantity: asNumber(raw.quantity),
    // resolveUnitId also maps legacy Process UnitType values (COUNT/GRAM/KG/ML/LITER) via unit aliases.
    unit: resolveUnitId(raw.unit),
    notes: typeof raw.notes === 'string' && raw.notes.trim() ? raw.notes : undefined,
    preparationStyleId: resolvePreparationStyleId(raw.preparationStyleId),
    customPreparationStyle: asString(raw.customPreparationStyle),
  }
}

export const getActionOnIngredientDisplayName = (entry: Pick<ActionOnIngredient, 'ingredientId' | 'customIngredientName'>) =>
  getIngredientDisplayName(entry.ingredientId, entry.customIngredientName ?? '')

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
  // Absent on steps saved before step-output references existed — reads as an empty list.
  const stepIds = Array.isArray(raw.steps)
    ? raw.steps.map((entry) => asString(asRecord(entry).stepId).trim()).filter(Boolean)
    : []
  const steps = [...new Set(stepIds)].map((stepId) => ({ stepId }))
  return { ingredients, processes, steps }
}

/** temperatureValue/temperatureUnit, else a legacy free-text `temperature` ("180 C") parsed into them. */
const normalizeTemperature = (raw: Record<string, unknown>): Pick<RecipeStepFields, 'temperatureValue' | 'temperatureUnit'> => {
  const value = asString(raw.temperatureValue) || (typeof raw.temperatureValue === 'number' ? String(raw.temperatureValue) : '')
  if (value) return { temperatureValue: value, temperatureUnit: resolveTemperatureUnit(raw.temperatureUnit) }
  return parseTemperatureLabel(asString(raw.temperature))
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
    ...normalizeTemperature(raw),
    durationValue: asString(raw.durationValue),
    durationUnit: isDurationUnitOption(raw.durationUnit) ? raw.durationUnit : '',
    repeatIntervalValue: asString(raw.repeatIntervalValue),
    repeatIntervalUnit: isDurationUnitOption(raw.repeatIntervalUnit) ? raw.repeatIntervalUnit : '',
  }
}

/** The step fields each step-level schema key controls — cleared when a newly chosen action doesn't declare that key. */
const STEP_FIELDS_BY_SCHEMA_KEY: Partial<Record<StepSchemaFieldKey, readonly (keyof RecipeStepFields)[]>> = {
  temperature: ['temperatureValue', 'temperatureUnit'],
  flameLevelId: ['flameLevelId', 'customFlameLevel'],
  duration: ['durationValue', 'durationUnit'],
  repeatInterval: ['repeatIntervalValue', 'repeatIntervalUnit'],
}

const clearFieldsNotDeclaredByAction = (step: RecipeStepFields): RecipeStepFields => {
  if (!step.action || step.action === CUSTOM_ACTION_ID) return step
  const cleared: RecipeStepFields = { ...step }
  for (const key of STEP_LEVEL_FIELD_KEYS) {
    if (isStepFieldEnabled(step.action, key)) continue
    for (const field of STEP_FIELDS_BY_SCHEMA_KEY[key] ?? []) Object.assign(cleared, { [field]: '' })
  }
  return cleared
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

/** "every 2 minutes", or '' when unset. */
export const getRecipeStepRepeatIntervalLabel = (step: RecipeStepFields) => {
  const label = buildDurationLabel(step.repeatIntervalValue, step.repeatIntervalUnit)
  return label ? `every ${label}` : ''
}

/** "180 °C", or '' when unset. */
export const getRecipeStepTemperatureLabel = (step: RecipeStepFields) =>
  buildTemperatureLabel(step.temperatureValue, step.temperatureUnit)

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

  const normalizedStep = normalizeRecipeStepFields(mergedStep)
  // Switching action drops advanced values the new action doesn't declare (e.g. a temperature
  // left over from Bake after changing to Chop), so hidden fields never linger in saved data.
  const reNormalized = field === 'action' ? clearFieldsNotDeclaredByAction(normalizedStep) : normalizedStep
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

export const withRecipeStepActionOnSteps = (data: unknown, stepIds: string[]): RecipeStepNodeData => {
  const normalized = normalizeRecipeStepNodeData(data)
  return {
    ...normalized,
    step: { ...normalized.step, actionOn: { ...normalized.step.actionOn, steps: stepIds.map((stepId) => ({ stepId })) } },
  }
}

/**
 * Human-readable one-liner of a step, built from the same action display name and Action On
 * target names the rest of the UI shows — e.g. "Fry marinated chicken + fried onions". Subprocess
 * and step-output names come from the caller (they live in other documents / other nodes).
 */
export const getRecipeStepActionSummary = (
  step: RecipeStepFields,
  resolve: { subprocessName: (processId: number) => string; stepOutputLabel: (stepId: string) => string },
) => {
  const targets = [
    ...step.actionOn.steps.map((entry) => resolve.stepOutputLabel(entry.stepId)),
    ...step.actionOn.ingredients.map(getActionOnIngredientDisplayName),
    ...step.actionOn.processes.map((entry) => resolve.subprocessName(entry.processId)),
  ].filter(Boolean)
  const action = getRecipeStepTitle(step)
  return targets.length > 0 ? `${action} ${targets.join(' + ')}` : action
}

/** Applies a freshly generated visualization result (from a visualization job's step result) onto a STEP's data. */
export const withRecipeStepVisualization = (data: unknown, visualization: RecipeStepVisualizationData): RecipeStepNodeData => {
  const normalized = normalizeRecipeStepNodeData(data)
  return { ...normalized, visualization }
}
