/**
 * The recipe tool's public API: everything the app shell and other features use. Code outside
 * recipe-tool imports from here, never from its folders directly.
 */
import './view/styles/recipe-view.css'

export { default as RecipeToolPage } from './page/RecipeToolPage'
export { default as EditRecipeDetailsModal, type RecipeDetailsDraft } from './page/EditRecipeDetailsModal'
export { default as RecipeProcessEditor } from './editor/components/RecipeProcessEditor'
export { default as RecipeProcessListPage } from './process-list/RecipeProcessListPage'
export { RecipeSessionProvider } from './session/RecipeSessionProvider'
export { forgetRecipeLocalState } from './session/forgetRecipeLocalState'
export { JobTrackerProvider } from './jobs/JobTrackerContext'
export { isRecipeToolView, recipeToolPath, type RecipeToolView } from './recipeToolRoutes'

// The read-only recipe view, reused by recipe orders to show what is being cooked.
export { default as RecipeImage } from './view/components/RecipeImage'
export {
  buildRecipePresentation,
  formatTotalMinutes,
  type RecipePresentation,
  type RecipeTimelineStep,
} from './view/model/recipeView'
