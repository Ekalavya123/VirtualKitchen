/**
 * React Flow-facing node/edge/viewport payload shapes the Recipe Process canvas loads from and
 * saves back to a Process (see adapters/recipeProcessCanvasAdapter.ts). These describe the canvas
 * graph itself, not any cooking semantics — a node's `data` is the step/condition data bag.
 */

import type { RecipeStepNodeData } from './recipeStepData'
import type { ConditionNodeData } from './recipeConditionData'

export type FlowNodeData = RecipeStepNodeData | ConditionNodeData | Record<string, unknown>

export type FlowNodePayload = {
  id: string
  type?: string
  position?: { x: number; y: number }
  data?: FlowNodeData
  measured?: unknown
  width?: number
  height?: number
  parentId?: string
  extent?: unknown
  draggable?: boolean
  selectable?: boolean
  deletable?: boolean
  connectable?: boolean
  style?: Record<string, unknown>
}

export type FlowEdgePayload = {
  id: string
  source: string
  target: string
  sourceHandle?: string | null
  targetHandle?: string | null
  type?: string
  animated?: boolean
  style?: Record<string, unknown>
  data?: Record<string, unknown>
  label?: string | null
}

export type FlowViewport = {
  x: number
  y: number
  zoom: number
}

export interface FlowData {
  nodes: FlowNodePayload[]
  edges: FlowEdgePayload[]
  viewport?: FlowViewport
}
