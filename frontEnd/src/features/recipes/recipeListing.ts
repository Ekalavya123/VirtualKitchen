/** Searching and sorting a list of recipes (My Recipes and Discover share it). Pure, so it is unit-tested. */

export type RecipeSort = 'newest' | 'oldest' | 'name'

export const RECIPE_SORT_OPTIONS: Array<{ value: RecipeSort; label: string }> = [
  { value: 'newest', label: 'Newest first' },
  { value: 'oldest', label: 'Oldest first' },
  { value: 'name', label: 'Name (A–Z)' },
]

export const isRecipeSort = (value: string | null | undefined): value is RecipeSort =>
  value === 'newest' || value === 'oldest' || value === 'name'

type Listable = { id: number; name: string; description?: string | null; createdAt?: string | null }

const time = (value: string | null | undefined) => {
  const parsed = value ? Date.parse(value) : Number.NaN
  return Number.isFinite(parsed) ? parsed : null
}

/** Recipes whose name or description contains every word of `query` (case-insensitive). */
export const searchRecipes = <T extends Listable>(recipes: T[], query: string): T[] => {
  const words = query.trim().toLowerCase().split(/\s+/).filter(Boolean)
  if (words.length === 0) return recipes
  return recipes.filter((recipe) => {
    const haystack = `${recipe.name} ${recipe.description ?? ''}`.toLowerCase()
    return words.every((word) => haystack.includes(word))
  })
}

/** A sorted copy. Undated recipes go last for date sorts; ties keep their original order. */
export const sortRecipes = <T extends Listable>(recipes: T[], sort: RecipeSort): T[] => {
  const indexed = recipes.map((recipe, index) => ({ recipe, index, time: time(recipe.createdAt) }))
  indexed.sort((a, b) => {
    if (sort === 'name') {
      return a.recipe.name.localeCompare(b.recipe.name, undefined, { sensitivity: 'base' }) || a.index - b.index
    }
    if (a.time == null || b.time == null) {
      if (a.time == null && b.time == null) return a.index - b.index
      return a.time == null ? 1 : -1
    }
    return (sort === 'newest' ? b.time - a.time : a.time - b.time) || a.index - b.index
  })
  return indexed.map((entry) => entry.recipe)
}
