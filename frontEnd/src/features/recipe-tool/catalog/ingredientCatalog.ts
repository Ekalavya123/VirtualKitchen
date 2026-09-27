import stepCatalogsData from './stepCatalogs.data.json'
import { PIECE_UNIT_ID, UNIT_CATALOG, isUnitId, type UnitId } from './unitCatalog'
import {
  buildAliasLookup,
  getCatalogDisplayName,
  resolveCatalogId,
  resolveCatalogInput,
} from './catalogSelectionUtils'

export type IngredientCategory = string

/** An ingredient id from stepCatalogs.data.json (e.g. "onion"); the catalog is the only list of valid values. */
export type IngredientId = string

export type IngredientCategoryDefinition = {
  id: IngredientCategory
  label: string
  icon: string
  /** Preparation style sets that make sense for this category (intersected with the action's own sets). */
  preparationStyleSets: readonly string[]
}

export type IngredientDefinition = {
  id: IngredientId
  name: string
  category: IngredientCategory
  icon: string
  defaultUnit: UnitId
  /** Default unit first, then reasonable alternatives. */
  units: readonly UnitId[]
  /** Search/interpretation only — never stored. */
  aliases: readonly string[]
  /** Overrides the category's preparation style sets when present. */
  preparationStyleSets?: readonly string[]
}

type RawIngredientEntry = {
  id: string
  name: string
  category: string
  icon: string
  defaultUnit: string
  units: string[]
  aliases?: string[]
  preparationStyleSets?: string[]
}

export const INGREDIENT_CATEGORIES: readonly IngredientCategoryDefinition[] = stepCatalogsData.ingredientCategories

export const INGREDIENT_CATEGORY_ORDER: readonly IngredientCategory[] = INGREDIENT_CATEGORIES.map((category) => category.id)

export const INGREDIENT_CATALOG: readonly IngredientDefinition[] = (
  stepCatalogsData.ingredients as RawIngredientEntry[]
).map((entry) => ({
  id: entry.id,
  name: entry.name,
  category: entry.category,
  icon: entry.icon,
  defaultUnit: entry.defaultUnit,
  units: entry.units,
  aliases: entry.aliases ?? [],
  preparationStyleSets: entry.preparationStyleSets,
}))

export const CUSTOM_INGREDIENT_ID: IngredientId = 'custom'

const ingredientById = new Map<IngredientId, IngredientDefinition>(
  INGREDIENT_CATALOG.map((ingredient) => [ingredient.id, ingredient])
)

const categoryById = new Map<IngredientCategory, IngredientCategoryDefinition>(
  INGREDIENT_CATEGORIES.map((category) => [category.id, category])
)

const ingredientAliasLookup = buildAliasLookup(
  INGREDIENT_CATALOG.map((ingredient) => ({ id: ingredient.id, aliases: [ingredient.id, ingredient.name, ...ingredient.aliases] }))
)

export const INGREDIENTS_BY_CATEGORY: Readonly<Record<IngredientCategory, readonly IngredientDefinition[]>> =
  INGREDIENT_CATEGORY_ORDER.reduce((accumulator, category) => {
    accumulator[category] = INGREDIENT_CATALOG.filter((ingredient) => ingredient.category === category)
    return accumulator
  }, {} as Record<IngredientCategory, readonly IngredientDefinition[]>)

export const getIngredientCategoryLabel = (category: IngredientCategory) => categoryById.get(category)?.label ?? category

export const isIngredientId = (value: unknown): value is IngredientId =>
  typeof value === 'string' && ingredientById.has(value)

// Falls back instead of crashing when `id` doesn't resolve (e.g. data saved under an older
// ingredient catalog, or before the catalog had this entry) — this is looked up during render
// (canvas node labels, the Action On panel), so a missing entry must never throw.
const UNKNOWN_INGREDIENT: IngredientDefinition = {
  id: CUSTOM_INGREDIENT_ID, name: 'Unknown ingredient', category: 'other', icon: '❓', defaultUnit: PIECE_UNIT_ID, units: [], aliases: [],
}

export const getIngredientById = (id: IngredientId): IngredientDefinition =>
  ingredientById.get(id) ?? UNKNOWN_INGREDIENT

export const resolveIngredientId = (value: unknown): IngredientId | '' =>
  resolveCatalogId(ingredientAliasLookup, value)

export const resolveIngredientInput = (value: string): { ingredientId: IngredientId | ''; customIngredientName: string } => {
  const resolved = resolveCatalogInput(ingredientAliasLookup, CUSTOM_INGREDIENT_ID, value)
  return { ingredientId: resolved.id, customIngredientName: resolved.customValue }
}

export const getIngredientDisplayName = (ingredientId: IngredientId | '', customIngredientName = '') =>
  getCatalogDisplayName(
    CUSTOM_INGREDIENT_ID,
    ingredientId,
    customIngredientName,
    (id) => getIngredientById(id).name,
    'Custom Ingredient'
  )

export const getIngredientDefaultUnit = (ingredientId: IngredientId | ''): UnitId => {
  if (!ingredientId || ingredientId === CUSTOM_INGREDIENT_ID) return PIECE_UNIT_ID
  const unit = getIngredientById(ingredientId).defaultUnit
  return isUnitId(unit) ? unit : PIECE_UNIT_ID
}

/** The units offered first for this ingredient (its default, then its alternatives); every unit for a custom ingredient. */
export const getIngredientUnitIds = (ingredientId: IngredientId | ''): readonly UnitId[] => {
  if (!ingredientId || ingredientId === CUSTOM_INGREDIENT_ID) return UNIT_CATALOG.map((unit) => unit.id)
  const units = getIngredientById(ingredientId).units
  return units.length > 0 ? units : UNIT_CATALOG.map((unit) => unit.id)
}

/** Null for a custom/unknown ingredient, meaning "no ingredient-level restriction". */
export const getIngredientPreparationStyleSets = (ingredientId: IngredientId | ''): readonly string[] | null => {
  if (!ingredientId || ingredientId === CUSTOM_INGREDIENT_ID || !ingredientById.has(ingredientId)) return null
  const ingredient = getIngredientById(ingredientId)
  return ingredient.preparationStyleSets ?? categoryById.get(ingredient.category)?.preparationStyleSets ?? []
}

export const getIngredientSearchValue = (ingredientId: IngredientId | '', customIngredientName = '') =>
  getIngredientDisplayName(ingredientId, customIngredientName)
