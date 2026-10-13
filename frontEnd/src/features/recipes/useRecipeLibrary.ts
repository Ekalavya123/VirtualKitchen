import { useEffect } from 'react'
import { RecipeApi, type Recipe } from '../../api'
import { forgetRecipeLocalState, type RecipeDetailsDraft } from '../recipe-tool'
import { useConfirm } from '../../shared/components/confirm/confirmContext'
import { useNotifications } from '../../shared/components/notifications/notificationContext'
import { errorMessage } from '../../shared/errors'
import { useApiData } from '../../shared/hooks/useApiData'

export type RecipeTab = 'mine' | 'global'

const NO_RECIPES: Recipe[] = []

/**
 * The recipe library's data and actions: the user's own recipes, the public ones (loaded once the Global tab
 * is first opened), and create/delete/edit/visibility/copy. Failures are reported as toasts; each action
 * resolves to whether it succeeded so the page can close its dialog.
 */
export function useRecipeLibrary(userId: number, activeTab: RecipeTab) {
  const { notifySuccess, notifyError } = useNotifications()
  const confirm = useConfirm()

  const mine = useApiData(`mine-${userId}`, async () => (await RecipeApi.getRecipesByUserId(userId)) ?? [])
  const global = useApiData(activeTab === 'global' ? `global-${userId}` : null,
    async () => (await RecipeApi.getGlobalRecipes(userId)) ?? [])

  useEffect(() => {
    if (mine.error) notifyError(mine.error)
  }, [mine.error, notifyError])
  useEffect(() => {
    if (global.error) notifyError(global.error)
  }, [global.error, notifyError])

  // The update endpoints don't resolve thumbnails, so an updated recipe keeps the one already shown.
  const replaceRecipe = (updated: Recipe) =>
    mine.mutate((recipes) => recipes.map((item) => (item.id === updated.id ? { ...item, ...updated, thumbnailUrl: item.thumbnailUrl } : item)))

  /** Creates a recipe and returns its id; throws (with the backend's message) on failure. */
  const createRecipe = async (name: string, description: string): Promise<number> => {
    const result = await RecipeApi.createRecipe({ name, description, createdBy: userId })
    if (!result.id) throw new Error('Recipe created but no id returned')
    return result.id
  }

  const deleteRecipe = async (recipe: Recipe) => {
    const confirmed = await confirm({
      eyebrow: 'Delete recipe',
      title: `Delete "${recipe.name}"?`,
      message:
        'The recipe, its processes and step images will be permanently removed from your kitchen. Orders already placed keep their own copy. This cannot be undone.',
      confirmLabel: 'Delete recipe',
    })
    if (!confirmed) return
    try {
      await RecipeApi.deleteRecipe(recipe.id, userId)
      // Unsaved Recipe Tool changes left on this device for a deleted recipe can never be recovered.
      forgetRecipeLocalState(recipe.id)
      mine.mutate((recipes) => recipes.filter((item) => item.id !== recipe.id))
      notifySuccess('Recipe deleted.')
    } catch (error) {
      notifyError(errorMessage(error, 'Unable to delete recipe'))
    }
  }

  /** Saves a recipe's name and description; throws on failure so the edit dialog can show the error. */
  const saveDetails = async (recipe: Recipe, details: RecipeDetailsDraft) => {
    replaceRecipe(await RecipeApi.updateRecipe(recipe.id, userId, details))
    notifySuccess('Recipe details saved.')
  }

  const toggleVisibility = async (recipe: Recipe): Promise<boolean> => {
    const nextVisibility = recipe.visibility === 'PUBLIC' ? 'PRIVATE' : 'PUBLIC'
    try {
      replaceRecipe(await RecipeApi.updateVisibility(recipe.id, userId, nextVisibility))
      notifySuccess(nextVisibility === 'PUBLIC' ? 'Recipe is now public.' : 'Recipe is now private.')
      return true
    } catch (error) {
      notifyError(errorMessage(error, 'Unable to update recipe visibility'))
      return false
    }
  }

  /** Copies a public recipe into the user's kitchen; returns the copy's id, or null when it failed. */
  const addToMyRecipes = async (recipe: Recipe): Promise<number | null> => {
    try {
      const copy = await RecipeApi.copyRecipe(recipe.id, userId)
      mine.reload()
      notifySuccess('Recipe added to your kitchen.')
      return copy.id
    } catch (error) {
      notifyError(errorMessage(error, 'Unable to add recipe to My Recipes'))
      return null
    }
  }

  return {
    recipes: mine.data ?? NO_RECIPES,
    recipesLoading: mine.loading,
    globalRecipes: global.data ?? NO_RECIPES,
    globalRecipesLoading: global.loading,
    createRecipe,
    deleteRecipe,
    saveDetails,
    toggleVisibility,
    addToMyRecipes,
  }
}
