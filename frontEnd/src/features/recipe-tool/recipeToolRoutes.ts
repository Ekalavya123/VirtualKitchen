/**
 * The Recipe Tool's two views, each its own route (`/kitchen/recipes/:recipeId/tool/:view`):
 * the Recipe Editor (build the process graph) and the Recipe Process (read and cook the recipe).
 */
export const RECIPE_TOOL_VIEWS = ['editor', 'process'] as const

export type RecipeToolView = (typeof RECIPE_TOOL_VIEWS)[number]

export const isRecipeToolView = (value: unknown): value is RecipeToolView =>
  typeof value === 'string' && (RECIPE_TOOL_VIEWS as readonly string[]).includes(value)

export const recipeToolPath = (recipeId: number, view?: RecipeToolView) =>
  view ? `/kitchen/recipes/${recipeId}/tool/${view}` : `/kitchen/recipes/${recipeId}/tool`
