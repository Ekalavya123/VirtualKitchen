import stepCatalogsData from './stepCatalogs.data.json'
import {
  buildAliasLookup,
  getCatalogDisplayName,
  resolveCatalogId,
  resolveCatalogInput,
  type CatalogInputResolution,
} from './catalogSelectionUtils'

export const ACTION_CATEGORY_ORDER = [
  'Ingredient Operations',
  'Preparation Operations',
  'Cooking Operations',
  'Mixing Operations',
  'Waiting Operations',
  'Finish Operations',
  'Custom',
] as const

export type ActionCategory = (typeof ACTION_CATEGORY_ORDER)[number]

// The precise set of ids is data-driven (see stepCatalogs.data.json), but kept as an explicit
// literal union here so the rest of the app still gets autocomplete/exhaustiveness checking.
export type StepActionId =
  | 'add' | 'remove' | 'pour' | 'season'
  | 'cut' | 'chop' | 'slice' | 'dice'
  | 'heat' | 'boil' | 'fry' | 'bake'
  | 'stir' | 'mix' | 'whisk'
  | 'wait' | 'rest'
  | 'serve' | 'garnish'
  | 'custom'

export type StepSchemaFieldKey =
  | 'ingredientId'
  | 'quantity'
  | 'unitId'
  | 'preparationStyleId'
  | 'temperature'
  | 'flameLevelId'
  | 'duration'
  | 'repeatInterval'
  | 'notes'

export type StepActionDefinition = {
  id: StepActionId
  displayName: string
  icon: string
  category: ActionCategory
  fields: readonly StepSchemaFieldKey[]
  amountLabel: string
  unitLabel: string
}

const DEFAULT_AMOUNT_LABEL = 'Amount'
const DEFAULT_UNIT_LABEL = 'Unit'

type RawActionEntry = {
  id: string
  displayName: string
  icon: string
  category: string
  fields: string[]
  amountLabel?: string
  unitLabel?: string
}

export const STEP_ACTION_CATALOG: readonly StepActionDefinition[] = (
  stepCatalogsData.actions as RawActionEntry[]
).map((entry) => ({
  id: entry.id as StepActionId,
  displayName: entry.displayName,
  icon: entry.icon,
  category: entry.category as ActionCategory,
  fields: entry.fields as StepSchemaFieldKey[],
  amountLabel: entry.amountLabel ?? DEFAULT_AMOUNT_LABEL,
  unitLabel: entry.unitLabel ?? DEFAULT_UNIT_LABEL,
}))

export const CUSTOM_ACTION_ID: StepActionId = 'custom'

const catalogById = new Map<StepActionId, StepActionDefinition>(
  STEP_ACTION_CATALOG.map((entry) => [entry.id, entry])
)

const actionAliasLookup = buildAliasLookup(
  STEP_ACTION_CATALOG.map((action) => ({ id: action.id, aliases: [action.id, action.displayName] }))
)

export const ACTIONS_BY_CATEGORY: Readonly<Record<ActionCategory, readonly StepActionDefinition[]>> =
  ACTION_CATEGORY_ORDER.reduce((accumulator, category) => {
    accumulator[category] = STEP_ACTION_CATALOG.filter((action) => action.category === category)
    return accumulator
  }, {} as Record<ActionCategory, readonly StepActionDefinition[]>)

export const isStepActionId = (value: unknown): value is StepActionId =>
  typeof value === 'string' && catalogById.has(value as StepActionId)

export const getStepActionById = (id: StepActionId): StepActionDefinition =>
  catalogById.get(id) as StepActionDefinition

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
