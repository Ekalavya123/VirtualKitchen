import stepCatalogsData from './stepCatalogs.data.json'
import {
  CUSTOM_ACTION_ID,
  getStepActionById,
  type ActionCategory,
  type FieldRequirement,
  type StepActionId,
  type StepSchemaFieldKey,
} from './actionCatalog'
import { getIngredientPreparationStyleSets, type IngredientId } from './ingredientCatalog'
import { getStylesOfSets, type PreparationStyleId } from './preparationStyleCatalog'
import { getTemperatureFieldLabel } from './stepFieldCatalog'

export type { StepSchemaFieldKey, FieldRequirement }

export type StepSchemaFieldConfig = {
  key: StepSchemaFieldKey
  label: string
  requirement: FieldRequirement
}

export type StepActionSchema = {
  category: ActionCategory
  fields: readonly StepSchemaFieldConfig[]
}

const FIELD_LABELS = new Map<string, string>(stepCatalogsData.fieldDefinitions.map((field) => [field.id, field.label]))

/**
 * The fields an action declares (the same per-action rules the backend AI validator enforces — see
 * docs/recipe-vocabulary-v2.md, section F). No action selected yet behaves like the custom action,
 * so nothing is hidden before a choice is made.
 */
export const getStepActionSchema = (action: StepActionId | ''): StepActionSchema => {
  const definition = getStepActionById(action || CUSTOM_ACTION_ID)
  const fields: StepSchemaFieldConfig[] = definition.fields.map((key) => ({
    key,
    label: key === 'temperature' ? getTemperatureFieldLabel(definition.temperatureContext) : FIELD_LABELS.get(key) ?? key,
    requirement: definition.fieldRequirements[key] ?? 'optional',
  }))

  return { category: definition.category, fields }
}

export const isStepFieldEnabled = (action: StepActionId | '', field: StepSchemaFieldKey) =>
  getStepActionSchema(action).fields.some((config) => config.key === field)

export const getStepFieldRequirement = (action: StepActionId | '', field: StepSchemaFieldKey): FieldRequirement | null =>
  getStepActionSchema(action).fields.find((config) => config.key === field)?.requirement ?? null

/** Step-level fields (not per-ingredient) an action may carry — used to drop stale values when the action changes. */
export const STEP_LEVEL_FIELD_KEYS: readonly StepSchemaFieldKey[] = stepCatalogsData.fieldDefinitions
  .filter((field) => field.scope === 'step')
  .map((field) => field.id as StepSchemaFieldKey)

/**
 * Preparation styles offered for one ingredient on one step: the action's sets intersected with the
 * ingredient's (its category's, unless it overrides them). A custom ingredient gets the action's full list.
 * Empty when the action takes no preparation style, or nothing it offers fits this ingredient.
 */
export const getAllowedPreparationStyles = (action: StepActionId | '', ingredientId: IngredientId | ''): PreparationStyleId[] => {
  if (!isStepFieldEnabled(action, 'preparationStyleId')) return []
  const forAction = getStylesOfSets(getStepActionById(action || CUSTOM_ACTION_ID).preparationStyleSets)
  const ingredientSets = getIngredientPreparationStyleSets(ingredientId)
  if (ingredientSets == null) return forAction
  const forIngredient = new Set(getStylesOfSets(ingredientSets))
  return forAction.filter((style) => forIngredient.has(style))
}
