import stepCatalogsData from './stepCatalogs.data.json'
import {
  buildAliasLookup,
  getCatalogDisplayName,
  resolveCatalogId,
  resolveCatalogInput,
  type CatalogInputResolution,
} from './catalogSelectionUtils'

// Ids are plain strings: the catalog (stepCatalogs.data.json, shared with the backend AI
// validator) is the only list of valid values — a literal union here would be a second copy.
export type ActionCategory = string
export type StepActionId = string

/** Per-action fields (see the catalog's `fieldDefinitions`): quantity/unitId/preparationStyleId are per Action On ingredient, the rest per step. */
export type StepSchemaFieldKey =
  | 'quantity'
  | 'unitId'
  | 'preparationStyleId'
  | 'temperature'
  | 'flameLevelId'
  | 'duration'
  | 'repeatInterval'

export type FieldRequirement = 'required' | 'recommended' | 'optional'

/** What an action may act on: Action On ingredients, subprocess outputs, both, or nothing (e.g. Preheat). */
export type ActionTarget = 'INGREDIENT' | 'PROCESS' | 'BOTH' | 'NONE'

export type ActionCategoryDefinition = {
  id: ActionCategory
  label: string
  icon: string
}

export type StepActionDefinition = {
  id: StepActionId
  displayName: string
  icon: string
  category: ActionCategory
  description: string
  aliases: readonly string[]
  actionOn: ActionTarget
  actionOnRequired: boolean
  multipleIngredients: boolean
  fieldRequirements: Readonly<Partial<Record<StepSchemaFieldKey, FieldRequirement>>>
  fields: readonly StepSchemaFieldKey[]
  preparationStyleSets: readonly string[]
  temperatureContext: string | null
  visualization: string
}

type RawActionEntry = {
  id: string
  displayName: string
  icon: string
  category: string
  description: string
  aliases?: string[]
  actionOn: string
  actionOnRequired: boolean
  multipleIngredients: boolean
  fields: Record<string, string>
  preparationStyleSets: string[]
  temperatureContext?: string
  visualization?: string
}

export const ACTION_CATEGORIES: readonly ActionCategoryDefinition[] = stepCatalogsData.actionCategories

export const ACTION_CATEGORY_ORDER: readonly ActionCategory[] = ACTION_CATEGORIES.map((category) => category.id)

export const STEP_ACTION_CATALOG: readonly StepActionDefinition[] = (
  stepCatalogsData.actions as RawActionEntry[]
).map((entry) => ({
  id: entry.id,
  displayName: entry.displayName,
  icon: entry.icon,
  category: entry.category,
  description: entry.description,
  aliases: entry.aliases ?? [],
  actionOn: entry.actionOn as ActionTarget,
  actionOnRequired: entry.actionOnRequired,
  multipleIngredients: entry.multipleIngredients,
  fieldRequirements: entry.fields as Partial<Record<StepSchemaFieldKey, FieldRequirement>>,
  fields: Object.keys(entry.fields) as StepSchemaFieldKey[],
  preparationStyleSets: entry.preparationStyleSets,
  temperatureContext: entry.temperatureContext ?? null,
  visualization: entry.visualization ?? '',
}))

export const CUSTOM_ACTION_ID: StepActionId = 'custom'

const catalogById = new Map<StepActionId, StepActionDefinition>(
  STEP_ACTION_CATALOG.map((entry) => [entry.id, entry])
)

const categoryById = new Map<ActionCategory, ActionCategoryDefinition>(ACTION_CATEGORIES.map((category) => [category.id, category]))

const actionAliasLookup = buildAliasLookup(
  STEP_ACTION_CATALOG.map((action) => ({ id: action.id, aliases: [action.id, action.displayName, ...action.aliases] }))
)

export const ACTIONS_BY_CATEGORY: Readonly<Record<ActionCategory, readonly StepActionDefinition[]>> =
  ACTION_CATEGORY_ORDER.reduce((accumulator, category) => {
    accumulator[category] = STEP_ACTION_CATALOG.filter((action) => action.category === category)
    return accumulator
  }, {} as Record<ActionCategory, readonly StepActionDefinition[]>)

export const getActionCategoryLabel = (category: ActionCategory) => categoryById.get(category)?.label ?? category

export const isStepActionId = (value: unknown): value is StepActionId =>
  typeof value === 'string' && catalogById.has(value)

// Falls back instead of crashing when `id` doesn't resolve (e.g. an action removed/renamed in
// stepCatalogs.data.json since a process was saved) — this is looked up during render (canvas node
// theming/labels, the Step Properties panel), so it must never throw.
const UNKNOWN_ACTION: StepActionDefinition = {
  id: CUSTOM_ACTION_ID,
  displayName: 'Unknown Action',
  icon: '❓',
  category: 'custom',
  description: '',
  aliases: [],
  actionOn: 'BOTH',
  actionOnRequired: false,
  multipleIngredients: true,
  fieldRequirements: {},
  fields: [],
  preparationStyleSets: [],
  temperatureContext: null,
  visualization: '',
}

export const getStepActionById = (id: StepActionId): StepActionDefinition =>
  catalogById.get(id) ?? UNKNOWN_ACTION

export const actionAllowsIngredients = (id: StepActionId | '') => {
  const target = getStepActionById(id || CUSTOM_ACTION_ID).actionOn
  return target === 'INGREDIENT' || target === 'BOTH'
}

export const actionAllowsProcesses = (id: StepActionId | '') => {
  const target = getStepActionById(id || CUSTOM_ACTION_ID).actionOn
  return target === 'PROCESS' || target === 'BOTH'
}

export const resolveStepActionId = (value: unknown): StepActionId | '' =>
  resolveCatalogId(actionAliasLookup, value)

/**
 * Resolves free-form action text (e.g. from AI generation or a legacy saved flow) against the
 * catalog. Unrecognized text is preserved as a custom action name instead of being dropped.
 */
export const resolveActionInput = (value: string): CatalogInputResolution<StepActionId> =>
  resolveCatalogInput(actionAliasLookup, CUSTOM_ACTION_ID, value)

export const getActionDisplayName = (id: StepActionId | '', customActionName = '') =>
  id
    ? getCatalogDisplayName(
        CUSTOM_ACTION_ID,
        id,
        customActionName,
        (actionId) => getStepActionById(actionId).displayName,
        'Custom Action'
      )
    : 'Select Action'

export const getActionIcon = (id: StepActionId | '') => (id ? getStepActionById(id).icon : 'ST')
