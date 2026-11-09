/** How a new recipe starts: AI drafts the flow from a description, or the user builds it on a blank canvas. */
export type CreateRecipeMode = 'ai' | 'blank'

/** What the create-recipe dialog keeps as a draft until the recipe is created. */
export type CreateRecipeDraft = {
  title: string
  description: string
  mode: CreateRecipeMode
  /** The dish described for AI Recipe Creation (only used in "ai" mode). */
  recipeText: string
}

export const EMPTY_CREATE_RECIPE_DRAFT: CreateRecipeDraft = { title: '', description: '', mode: 'ai', recipeText: '' }
