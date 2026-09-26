import type { Node } from '@xyflow/react'

/**
 * React Flow node `type` strings of the Recipe Process canvas. These values are persisted on every
 * ProcessNode (`ProcessNode.type`), so they must never change — `'processStepNode'` predates the
 * Recipe naming and is kept as-is for that reason.
 */
export const RECIPE_NODE_TYPES = {
  step: 'processStepNode',
  condition: 'conditionNode',
} as const

export type RecipeNodeType = (typeof RECIPE_NODE_TYPES)[keyof typeof RECIPE_NODE_TYPES]

export const isRecipeStepNode = (node: Pick<Node, 'type'>) => node.type === RECIPE_NODE_TYPES.step
export const isRecipeConditionNode = (node: Pick<Node, 'type'>) => node.type === RECIPE_NODE_TYPES.condition
