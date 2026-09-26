import RecipeConditionNode from './RecipeConditionNode'
import RecipeStepNode from './RecipeStepNode'
import { RECIPE_NODE_TYPES } from '../model/recipeNodeTypes'

/** React Flow `nodeTypes` map for the Recipe Process canvas. */
export const recipeNodeComponents = {
  [RECIPE_NODE_TYPES.condition]: RecipeConditionNode,
  [RECIPE_NODE_TYPES.step]: RecipeStepNode,
}
