/**
 * Bridges the semantic `Process` domain model (types/process.ts) and the
 * flow editor's existing React Flow-facing shapes (types/recipeFlow.ts:
 * FlowNodePayload/FlowEdgePayload/FlowData), so a Process can be loaded into
 * a React Flow canvas using the *existing* node components (RecipeStepNode,
 * ConditionNode, ProcessStepNode) and saved back, without teaching the
 * domain types anything about React Flow.
 *
 * A Process graph only ever contains STEP and CONDITION nodes — a subprocess
 * is never embedded as a node inside another process's graph. It is instead
 * referenced by id from a STEP's own "Action On" data (model/processStepData.ts);
 * the referenced process's own `name` is its summary. There used to be a
 * PROCESS-kind React Flow node for this (features/flow-editor/nodes/ProcessNode.tsx,
 * now removed) — that concept has been replaced entirely by Action On.
 */

import type { Node } from '@xyflow/react'
import { FLOW_NODE_TYPES } from '../model/flowNodeModel'
import type { FlowData, FlowEdgePayload, FlowNodePayload, FlowViewport } from '../../../types/recipeFlow'
import { createDefaultProcessStepNodeData } from '../model/processStepData'
import type {
  Process,
  ProcessEdge,
  ProcessMeasured,
  ProcessNode as ProcessNodeModel,
  ProcessNodeKind,
  ProcessUpdateRequest,
  ProcessViewport,
} from '../../../types/process'

/** Creates a new, empty STEP-kind canvas node using the Process Builder's "Action On" step model (model/processStepData.ts) — not the legacy StepNodeStructuredFields. */
export const createProcessStepNode = (id: string, position: { x: number; y: number }): Node => ({
  id,
  type: FLOW_NODE_TYPES.processStep,
  position,
  draggable: true,
  selectable: true,
  connectable: true,
  style: { width: 280, height: 160 },
  data: createDefaultProcessStepNodeData(),
})

const NODE_TYPE_BY_KIND: Record<ProcessNodeKind, string> = {
  // A node persisted with type `recipeStepNode` from before this change still round-trips fine
  // (its own `type` is preserved on load — see processNodeToFlowNode below — so it keeps
  // rendering via the legacy RecipeStepNode/PropertiesPanel).
  STEP: FLOW_NODE_TYPES.processStep,
  CONDITION: FLOW_NODE_TYPES.condition,
}

const kindForFlowNodeType = (type?: string): ProcessNodeKind =>
  type === FLOW_NODE_TYPES.condition ? 'CONDITION' : 'STEP'

/** Converts one backend ProcessNode into a React Flow node payload. */
export const processNodeToFlowNode = (node: ProcessNodeModel): FlowNodePayload => {
  const type = node.type || NODE_TYPE_BY_KIND[node.kind]

  return {
    id: node.id,
    type,
    position: { x: node.position?.x ?? 0, y: node.position?.y ?? 0 },
    data: (node.data ?? {}) as FlowNodePayload['data'],
    measured: node.measured,
    width: node.width,
    height: node.height,
    parentId: node.parentId,
    extent: node.extent,
    draggable: node.draggable,
    selectable: node.selectable,
    deletable: node.deletable,
  }
}

/** Converts one backend ProcessEdge into a React Flow edge payload. */
export const processEdgeToFlowEdge = (edge: ProcessEdge): FlowEdgePayload => ({
  id: edge.id,
  source: edge.source,
  target: edge.target,
  sourceHandle: edge.sourceHandle,
  targetHandle: edge.targetHandle,
  type: edge.type,
  animated: edge.animated,
  style: edge.style,
  data: edge.data,
  label: edge.label,
})

const processViewportToFlowViewport = (viewport?: ProcessViewport): FlowViewport | undefined => {
  if (!viewport) return undefined
  return { x: viewport.x ?? 0, y: viewport.y ?? 0, zoom: viewport.zoom ?? 1 }
}

/** Converts a whole Process into React Flow-ready FlowData (nodes/edges/viewport). */
export const processToFlowData = (process: Process): FlowData => ({
  nodes: process.nodes.map(processNodeToFlowNode),
  edges: process.edges.map(processEdgeToFlowEdge),
  viewport: processViewportToFlowViewport(process.viewport),
})

/**
 * Converts a React Flow node payload back into a backend ProcessNode. Kind
 * is derived from `node.type` (set by createRecipeStepNode/createConditionNode/
 * createProcessStepNode, or preserved on load) rather than trusting anything
 * inside `data`, exactly like the legacy flow model's own normalizeFlowNode.
 */
export const flowNodeToProcessNode = (node: FlowNodePayload): ProcessNodeModel => ({
  id: node.id,
  kind: kindForFlowNodeType(node.type),
  data: (node.data as Record<string, unknown> | undefined) ?? {},
  type: node.type,
  position: node.position,
  measured: node.measured as ProcessMeasured | undefined,
  width: node.width,
  height: node.height,
  parentId: node.parentId,
  extent: typeof node.extent === 'string' ? node.extent : undefined,
  draggable: node.draggable,
  selectable: node.selectable,
  deletable: node.deletable,
})

/** Converts a React Flow edge payload back into a backend ProcessEdge. */
export const flowEdgeToProcessEdge = (edge: FlowEdgePayload): ProcessEdge => ({
  id: edge.id,
  source: edge.source,
  target: edge.target,
  label: edge.label,
  sourceHandle: edge.sourceHandle,
  targetHandle: edge.targetHandle,
  type: edge.type,
  animated: edge.animated,
  style: edge.style,
  data: edge.data,
})

const flowViewportToProcessViewport = (viewport?: FlowViewport): ProcessViewport | undefined => {
  if (!viewport) return undefined
  return { x: viewport.x, y: viewport.y, zoom: viewport.zoom }
}

/**
 * Builds a `PUT /api/v1/recipes/{recipeId}/processes/{processId}` request
 * body from the canvas's current nodes/edges/viewport (already normalized
 * into FlowData, e.g. via FlowCanvas.helpers.ts's createFlowDataPayload)
 * plus the process's own name/description (which the canvas doesn't own).
 */
export const buildProcessUpdateRequest = (
  name: string,
  description: string | undefined,
  flowData: FlowData,
): ProcessUpdateRequest => ({
  name,
  description,
  nodes: flowData.nodes.map(flowNodeToProcessNode),
  edges: flowData.edges.map(flowEdgeToProcessEdge),
  viewport: flowViewportToProcessViewport(flowData.viewport),
})
