/**
 * The runtime ingredient catalog: the backend's `ingredients` collection (GET /api/v1/ingredients)
 * is the one list of ingredients a recipe step can reference, by `String(id)` (e.g. "42"). This
 * module only holds and indexes that list — loading it is ingredientCatalog.ts's job, and the
 * synchronous getters every step-editing module uses are built on top of it there.
 *
 * Kept free of runtime imports other than the pure selection helpers (with explicit `.ts`
 * extensions) so node:test can import it directly.
 */

import type { GlobalIngredient } from '../../../api/recipeApi'
import { buildAliasLookup, resolveCatalogId } from './catalogSelectionUtils.ts'

export type IngredientCategory = string

/** `String(id)` of a DB ingredient, or "custom". The loaded catalog is the only list of valid values. */
export type IngredientId = string

export type IngredientDefinition = {
  id: IngredientId
  name: string
  category: IngredientCategory
  icon: string
  /** The DB image of the ingredient, when one has been generated. */
  imageUrl?: string
  /** A recipe unit id (catalog/unitCatalog.ts). */
  defaultUnit: string
  /** Default unit first, then reasonable alternatives; empty means "no ingredient-specific units". */
  units: readonly string[]
  /** Search/interpretation only — never stored. Includes the legacy catalog slug (e.g. "onion"). */
  aliases: readonly string[]
  /** Overrides the category's preparation style sets when present. */
  preparationStyleSets?: readonly string[]
}

export const CUSTOM_INGREDIENT_ID: IngredientId = 'custom'

const PIECE_UNIT = 'piece'
const FALLBACK_ICON = '🥕'

/**
 * The "custom" pseudo-ingredient (a free-text name on the step). It isn't a DB entry, so it's always
 * part of the catalog regardless of what the API returned. Its units are left empty: the getters
 * offer every unit for it.
 */
export const CUSTOM_INGREDIENT: IngredientDefinition = {
  id: CUSTOM_INGREDIENT_ID,
  name: 'Custom Ingredient',
  category: 'other',
  icon: '✨',
  defaultUnit: PIECE_UNIT,
  units: [],
  aliases: ['custom'],
}

/** The backend's shop/inventory UnitType, as a recipe unit id. */
const RECIPE_UNIT_FOR_UNIT_TYPE: Record<string, string> = {
  KG: 'kg',
  GRAM: 'g',
  LITER: 'l',
  ML: 'ml',
  COUNT: PIECE_UNIT,
}

const nonBlank = (values: readonly (string | null | undefined)[] | null | undefined): string[] =>
  (values ?? []).filter((value): value is string => typeof value === 'string' && value.trim() !== '')

/** One API entry as a step-editing catalog definition. `categoryIcon` is used when the entry has no icon of its own. */
export const toIngredientDefinition = (entry: GlobalIngredient, categoryIcon?: string): IngredientDefinition => {
  const units = nonBlank(entry.recipeUnits)
  const category = entry.category?.trim() || 'other'
  return {
    id: String(entry.id),
    name: entry.name,
    category,
    icon: entry.icon?.trim() || categoryIcon || FALLBACK_ICON,
    imageUrl: entry.imageUrl || undefined,
    defaultUnit: units[0] ?? (entry.defaultUnit ? RECIPE_UNIT_FOR_UNIT_TYPE[entry.defaultUnit] : undefined) ?? PIECE_UNIT,
    units,
    aliases: [...nonBlank([entry.catalogSlug]), ...nonBlank(entry.aliases)],
    preparationStyleSets: entry.preparationStyleSets ? nonBlank(entry.preparationStyleSets) : undefined,
  }
}

type CatalogState = {
  loaded: boolean
  /** Bumped on every replacement, so subscribers (useSyncExternalStore) re-render. */
  version: number
  /** In API order (the backend returns catalog display order), custom last. */
  ingredients: readonly IngredientDefinition[]
  byId: Map<IngredientId, IngredientDefinition>
  aliasLookup: Map<string, IngredientId>
}

const indexCatalog = (ingredients: readonly IngredientDefinition[]) => ({
  byId: new Map(ingredients.map((ingredient) => [ingredient.id, ingredient])),
  // Ids resolve to themselves; names and aliases (legacy slugs included) resolve to their entry.
  aliasLookup: buildAliasLookup(
    ingredients.map((ingredient) => ({ id: ingredient.id, aliases: [ingredient.id, ingredient.name, ...ingredient.aliases] }))
  ),
})

let state: CatalogState = { loaded: false, version: 0, ingredients: [CUSTOM_INGREDIENT], ...indexCatalog([CUSTOM_INGREDIENT]) }
const listeners = new Set<() => void>()

/**
 * Replaces the catalog with the given API entries (plus the custom pseudo-ingredient) and notifies
 * subscribers. Also how tests install fixture entries.
 */
export const setIngredientCatalog = (
  entries: readonly GlobalIngredient[],
  categoryIcon: (category: string) => string | undefined = () => undefined,
): void => {
  const definitions = entries
    .filter((entry) => entry && entry.id != null && typeof entry.name === 'string')
    .map((entry) => toIngredientDefinition(entry, categoryIcon(entry.category?.trim() || 'other')))
  const ingredients = [...definitions, CUSTOM_INGREDIENT]
  state = { loaded: true, version: state.version + 1, ingredients, ...indexCatalog(ingredients) }
  listeners.forEach((listener) => listener())
}

export const subscribeIngredientCatalog = (listener: () => void): (() => void) => {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

export const getIngredientCatalogVersion = (): number => state.version

/** False until the first successful load (or setIngredientCatalog call). */
export const isIngredientCatalogLoaded = (): boolean => state.loaded

/** Every ingredient, the custom entry last. */
export const getCatalogIngredients = (): readonly IngredientDefinition[] => state.ingredients

export const findIngredientDefinition = (id: IngredientId): IngredientDefinition | undefined => state.byId.get(id)

/** An id, name, alias or legacy slug ("onion") -> the entry's id; '' when nothing matches. */
export const resolveIngredientAlias = (value: unknown): IngredientId | '' => resolveCatalogId(state.aliasLookup, value)

export const getIngredientAliasLookup = (): Map<string, IngredientId> => state.aliasLookup
