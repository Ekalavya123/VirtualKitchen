import stepCatalogsData from './stepCatalogs.data.json'
import { resolveUnitId } from './unitCatalog'
import {
  buildAliasLookup,
  getCatalogDisplayName,
  resolveCatalogId,
  resolveCatalogInput,
} from './catalogSelectionUtils'

export const INGREDIENT_CATEGORY_ORDER = [
  'Liquid',
  'Pantry',
  'Produce',
  'Protein',
  'Dairy',
  'Other',
] as const

export type IngredientCategory = (typeof INGREDIENT_CATEGORY_ORDER)[number]

// The precise set of ids is data-driven (see stepCatalogs.data.json), but kept as an explicit
// literal union here so the rest of the app still gets autocomplete/exhaustiveness checking.
export type IngredientId =
  | 'water' | 'oil' | 'salt' | 'sugar' | 'rice'
  | 'onion' | 'tomato' | 'garlic' | 'ginger' | 'chili' | 'potato' | 'carrot' | 'capsicum'
  | 'egg' | 'milk' | 'butter' | 'chicken' | 'paneer'
  | 'custom'

export type IngredientDefinition = {
  id: IngredientId
  name: string
  category: IngredientCategory
  icon: string
  defaultUnit: string
}

type RawIngredientEntry = {
  id: string
  name: string
  category: string
  icon: string
  defaultUnit: string
}

export const INGREDIENT_CATALOG: readonly IngredientDefinition[] = (
  stepCatalogsData.ingredients as RawIngredientEntry[]
).map((entry) => ({
  id: entry.id as IngredientId,
  name: entry.name,
  category: entry.category as IngredientCategory,
  icon: entry.icon,
  defaultUnit: entry.defaultUnit,
}))

export const CUSTOM_INGREDIENT_ID: IngredientId = 'custom'

const ingredientById = new Map<IngredientId, IngredientDefinition>(
  INGREDIENT_CATALOG.map((ingredient) => [ingredient.id, ingredient])
)

const ingredientAliasLookup = buildAliasLookup(
  INGREDIENT_CATALOG.map((ingredient) => ({ id: ingredient.id, aliases: [ingredient.id, ingredient.name] }))
)

export const INGREDIENTS_BY_CATEGORY: Readonly<Record<IngredientCategory, readonly IngredientDefinition[]>> =
  INGREDIENT_CATEGORY_ORDER.reduce((accumulator, category) => {
    accumulator[category] = INGREDIENT_CATALOG.filter((ingredient) => ingredient.category === category)
    return accumulator
  }, {} as Record<IngredientCategory, readonly IngredientDefinition[]>)

export const isIngredientId = (value: unknown): value is IngredientId =>
  typeof value === 'string' && ingredientById.has(value as IngredientId)

export const getIngredientById = (id: IngredientId): IngredientDefinition =>
  ingredientById.get(id) as IngredientDefinition

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

export const getIngredientDefaultUnit = (ingredientId: IngredientId | '') => {
  if (!ingredientId || ingredientId === CUSTOM_INGREDIENT_ID) return ''
  const resolved = resolveUnitId(getIngredientById(ingredientId).defaultUnit)
  return resolved || ''
}

export const getIngredientSearchValue = (ingredientId: IngredientId | '', customIngredientName = '') =>
  getIngredientDisplayName(ingredientId, customIngredientName)
