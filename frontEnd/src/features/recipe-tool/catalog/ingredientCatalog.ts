import { useSyncExternalStore } from 'react'
import stepCatalogsData from './stepCatalogs.data.json'
import { IngredientCatalogApi } from '../../../api/recipeApi'
import { PIECE_UNIT_ID, UNIT_CATALOG, isUnitId, type UnitId } from './unitCatalog'
import { getCatalogDisplayName, resolveCatalogInput } from './catalogSelectionUtils'
import {
  CUSTOM_INGREDIENT_ID,
  findIngredientDefinition,
  getCatalogIngredients,
  getIngredientAliasLookup,
  getIngredientCatalogVersion,
  isIngredientCatalogLoaded,
  resolveIngredientAlias,
  setIngredientCatalog as replaceIngredientCatalog,
  subscribeIngredientCatalog,
  type IngredientCategory,
  type IngredientDefinition,
  type IngredientId,
} from './ingredientCatalogStore'
import type { GlobalIngredient } from '../../../api/recipeApi'

/*
 * The ingredient catalog of the step editor. Ingredients come from the database
 * (GET /api/v1/ingredients, held by ingredientCatalogStore.ts) and are referenced by `String(id)`;
 * their categories (labels, icons, preparation style sets) stay in the static stepCatalogs.data.json
 * alongside units, actions and preparation styles. The JSON's own `ingredients` array is only the
 * backend's seed data now and is deliberately not read here.
 *
 * Every getter is synchronous and reads the live store, so callers need the catalog loaded first:
 * the Recipe Tool and recipe-order routes render behind IngredientCatalogGate, and components that
 * list ingredients subscribe with useIngredientCatalog() to re-render when it (re)loads.
 */

export { CUSTOM_INGREDIENT_ID, isIngredientCatalogLoaded, subscribeIngredientCatalog }
export type { IngredientCategory, IngredientDefinition, IngredientId }

export type IngredientCategoryDefinition = {
  id: IngredientCategory
  label: string
  icon: string
  /** Preparation style sets that make sense for this category (intersected with the action's own sets). */
  preparationStyleSets: readonly string[]
}

export const INGREDIENT_CATEGORIES: readonly IngredientCategoryDefinition[] = stepCatalogsData.ingredientCategories

export const INGREDIENT_CATEGORY_ORDER: readonly IngredientCategory[] = INGREDIENT_CATEGORIES.map((category) => category.id)

const categoryById = new Map<IngredientCategory, IngredientCategoryDefinition>(
  INGREDIENT_CATEGORIES.map((category) => [category.id, category])
)

/** Installs catalog entries directly (tests, or a caller that already fetched them). */
export const setIngredientCatalog = (entries: readonly GlobalIngredient[]) =>
  replaceIngredientCatalog(entries, (category) => categoryById.get(category)?.icon)

let pendingLoad: Promise<void> | null = null

/**
 * Loads the catalog once per page session: concurrent callers share one request, later callers
 * resolve immediately. A failed load is not cached, so calling again retries.
 */
export const loadIngredientCatalog = (): Promise<void> => {
  if (isIngredientCatalogLoaded()) return Promise.resolve()
  if (!pendingLoad) {
    pendingLoad = IngredientCatalogApi.list()
      .then((entries) => setIngredientCatalog(Array.isArray(entries) ? entries : []))
      .finally(() => {
        pendingLoad = null
      })
  }
  return pendingLoad
}

/** Re-renders the caller whenever the catalog is (re)loaded; returns the catalog version. */
export const useIngredientCatalog = (): number =>
  useSyncExternalStore(subscribeIngredientCatalog, getIngredientCatalogVersion)

/** Every ingredient (custom last). */
export const getIngredientCatalog = (): readonly IngredientDefinition[] => getCatalogIngredients()

let groupedVersion = -1
let grouped: { order: readonly IngredientCategory[]; byCategory: Readonly<Record<IngredientCategory, readonly IngredientDefinition[]>> } = {
  order: [],
  byCategory: {},
}

const groupByCategory = () => {
  const version = getIngredientCatalogVersion()
  if (version === groupedVersion) return grouped
  const byCategory: Record<IngredientCategory, IngredientDefinition[]> = {}
  for (const ingredient of getCatalogIngredients()) {
    ;(byCategory[ingredient.category] ??= []).push(ingredient)
  }
  // Static category order first; a category only the database knows goes at the end.
  const known = INGREDIENT_CATEGORY_ORDER.filter((category) => byCategory[category]?.length)
  const extra = Object.keys(byCategory).filter((category) => !categoryById.has(category))
  grouped = { order: [...known, ...extra], byCategory }
  groupedVersion = version
  return grouped
}

/** Ingredients grouped by category (cached per catalog version). */
export const getIngredientsByCategory = (): Readonly<Record<IngredientCategory, readonly IngredientDefinition[]>> =>
  groupByCategory().byCategory

/** The categories that have ingredients, in display order. */
export const getIngredientCategoryOrder = (): readonly IngredientCategory[] => groupByCategory().order

export const getIngredientCategoryLabel = (category: IngredientCategory) => categoryById.get(category)?.label ?? category

export const isIngredientId = (value: unknown): value is IngredientId =>
  typeof value === 'string' && (value === CUSTOM_INGREDIENT_ID || findIngredientDefinition(value) != null)

// Falls back instead of crashing when `id` doesn't resolve (e.g. an ingredient deleted from the
// database, or the catalog not loaded yet) — this is looked up during render (canvas node labels,
// the Action On panel), so a missing entry must never throw.
const UNKNOWN_INGREDIENT: IngredientDefinition = {
  id: CUSTOM_INGREDIENT_ID, name: 'Unknown ingredient', category: 'other', icon: '❓', defaultUnit: PIECE_UNIT_ID, units: [], aliases: [],
}

export const getIngredientById = (id: IngredientId): IngredientDefinition =>
  findIngredientDefinition(id) ?? UNKNOWN_INGREDIENT

/** An id, name, alias or legacy catalog slug ("onion") -> the ingredient's id; '' when nothing matches. */
export const resolveIngredientId = (value: unknown): IngredientId | '' => resolveIngredientAlias(value)

export const resolveIngredientInput = (value: string): { ingredientId: IngredientId | ''; customIngredientName: string } => {
  const resolved = resolveCatalogInput(getIngredientAliasLookup(), CUSTOM_INGREDIENT_ID, value)
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
  const units = getIngredientById(ingredientId).units.filter(isUnitId)
  return units.length > 0 ? units : UNIT_CATALOG.map((unit) => unit.id)
}

/** Null for a custom/unknown ingredient, meaning "no ingredient-level restriction". */
export const getIngredientPreparationStyleSets = (ingredientId: IngredientId | ''): readonly string[] | null => {
  if (!ingredientId || ingredientId === CUSTOM_INGREDIENT_ID) return null
  const ingredient = findIngredientDefinition(ingredientId)
  if (!ingredient) return null
  return ingredient.preparationStyleSets ?? categoryById.get(ingredient.category)?.preparationStyleSets ?? []
}

export const getIngredientSearchValue = (ingredientId: IngredientId | '', customIngredientName = '') =>
  getIngredientDisplayName(ingredientId, customIngredientName)
