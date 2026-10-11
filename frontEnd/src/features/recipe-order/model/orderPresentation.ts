/**
 * The recipe snapshot of an order, read through the Recipe Process presentation model
 * (recipe-tool/presentation/model/recipePresentation.ts) — the same reading order and step
 * numbering the Recipe Process page shows, so "step 3" means the same thing everywhere.
 */

import type { Process } from '../../../types/process'
import {
  buildRecipePresentation,
  type RecipePresentation,
  type RecipeTimelineStep,
} from '../../recipe-tool/presentation/model/recipePresentation'

export const EMPTY_PRESENTATION: RecipePresentation = { sections: [], ingredients: [], stepCount: 0 }

export const buildOrderPresentation = (processes: Process[] | null | undefined): RecipePresentation =>
  processes && processes.length > 0 ? buildRecipePresentation(processes) : EMPTY_PRESENTATION

/**
 * Every numbered step in reading order (checks left out) — index i is the order's
 * `currentStepIndex` i, which the backend counts over the same STEP nodes.
 */
export const flattenPresentationSteps = (presentation: RecipePresentation): RecipeTimelineStep[] =>
  presentation.sections.flatMap((section) =>
    section.items.filter((item): item is RecipeTimelineStep => item.kind === 'step'))
