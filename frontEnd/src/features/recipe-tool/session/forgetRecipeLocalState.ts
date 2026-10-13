import { clearRecipeDrafts } from '../../../shared/drafts/draftStore'
import { browserStorage, clearRecovery } from './persistence/recoveryStore'

/** Drops what the browser keeps for a recipe (its crash-recovery copy and form drafts), e.g. once it's deleted. */
export const forgetRecipeLocalState = (recipeId: number) => {
  clearRecovery(browserStorage(), recipeId)
  clearRecipeDrafts(recipeId)
}
