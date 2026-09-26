import { MarkerType, type Edge, type Node } from '@xyflow/react'
import { RECIPE_NODE_TYPES } from '../model/recipeNodeTypes'
import {
  createDefaultConditionFields,
  getConditionNodeTitle,
  normalizeConditionNodeData,
} from '../model/recipeConditionData'
import type { FlowData, FlowEdgePayload, FlowNodePayload } from '../model/canvasGraph'

export type EdgeKind = 'step' | 'yes' | 'no' | 'parallel'

const EDGE_COLORS: Record<EdgeKind, string> = {
  step: '#94a3b8',
  yes: '#16a34a',
  no: '#dc2626',
  parallel: '#7c3aed',
}

const getEdgeKind = (sourceHandle?: string | null, targetHandle?: string | null): EdgeKind => {
  if (sourceHandle === 'condition-yes') return 'yes'
  if (sourceHandle === 'condition-no') return 'no'
  if (String(sourceHandle ?? '').startsWith('parallel-') || String(targetHandle ?? '').startsWith('parallel-')) {
    return 'parallel'
  }
  return 'step'
}

const getDefaultEdgeLabel = (kind: EdgeKind) => {
  if (kind === 'yes') return 'Yes'
  if (kind === 'no') return 'No'
  if (kind === 'parallel') return 'Parallel'
  return undefined
}

export const getFlowEdgePresentation = (
  sourceHandle?: string | null,
  targetHandle?: string | null,
  label?: string | null,
) => {
  const kind = getEdgeKind(sourceHandle, targetHandle)
  const color = EDGE_COLORS[kind]

  return {
    type: 'smoothstep',
    animated: false,
    label: label ?? getDefaultEdgeLabel(kind),
    labelStyle: { fill: color, fontWeight: 700, fontSize: 11 },
    labelBgStyle: {
      fill: kind === 'yes'
        ? '#f0fdf4'
        : kind === 'no'
          ? '#fff5f5'
          : kind === 'parallel'
            ? '#f5f3ff'
            : 'transparent',
    },
    style: { stroke: color, strokeWidth: 1.5 },
    markerEnd: { type: MarkerType.ArrowClosed, color, width: 16, height: 16 },
  }
}

export const createConditionNode = (id: string, position: { x: number; y: number }): Node => ({
  id,
  type: RECIPE_NODE_TYPES.condition,
  position,
  draggable: true,
  selectable: true,
  connectable: true,
  style: { width: 190, height: 190 },
  data: {
    title: getConditionNodeTitle(''),
    condition: createDefaultConditionFields(),
    sectionId: null,
  },
})

/** Loads one node payload onto the canvas: CONDITION data is normalized, STEP data is passed through as stored. */
export const normalizeFlowNode = (node: FlowNodePayload): Node => {
  const baseData = node.data ?? {}
  const nodeType = node.type ?? RECIPE_NODE_TYPES.step
  const normalizedData = node.type === RECIPE_NODE_TYPES.condition
    ? normalizeConditionNodeData(baseData)
    : baseData

  // Explicit width/height (not just `measured`) are required so custom node components render at the
  // previously resized size immediately, instead of falling back to their default dimensions.
  const measured = node.measured as Node['measured']
  const width = node.width ?? measured?.width
  const height = node.height ?? measured?.height

  return {
    id: node.id,
    type: nodeType,
    position: node.position ?? { x: 0, y: 0 },
    data: normalizedData,
    measured,
    width,
    height,
    parentId: node.parentId,
    extent: node.extent as Node['extent'],
    draggable: node.draggable !== false,
    selectable: node.selectable !== false,
    deletable: node.deletable !== false,
    connectable: node.connectable !== false,
    style: node.style as Node['style'],
  }
}

export const createFlowDataPayload = (nodes: Node[], edges: Edge[], viewport?: FlowData['viewport']): FlowData => {
  const normalizedNodes: FlowNodePayload[] = nodes.map((node) => ({
    id: node.id,
    type: node.type,
    position: node.position,
    measured: node.measured,
    width: node.width ?? node.measured?.width,
    height: node.height ?? node.measured?.height,
    parentId: node.parentId,
    extent: node.extent,
    draggable: node.draggable,
    selectable: node.selectable,
    deletable: node.deletable,
    style: node.style as Record<string, unknown>,
    data: node.type === RECIPE_NODE_TYPES.condition
      ? normalizeConditionNodeData(node.data)
      : (node.data as Record<string, unknown>),
  }))

  const normalizedEdges: FlowEdgePayload[] = edges.map((edge) => ({
    id: edge.id,
    source: edge.source,
    target: edge.target,
    sourceHandle: edge.sourceHandle,
    targetHandle: edge.targetHandle,
    type: edge.type,
    animated: edge.animated,
    style: edge.style as Record<string, unknown>,
    data: edge.data as Record<string, unknown>,
    label: typeof edge.label === 'string' ? edge.label : null,
  }))

  return {
    nodes: normalizedNodes,
    edges: normalizedEdges,
    viewport,
  }
}

export const normalizeFlowEdges = (edges: FlowData['edges']): Edge[] =>
  (edges ?? []).map((edge) => {
    const presentation = getFlowEdgePresentation(edge.sourceHandle, edge.targetHandle, edge.label)

    return {
      id: edge.id,
      source: edge.source,
      target: edge.target,
      sourceHandle: edge.sourceHandle,
      targetHandle: edge.targetHandle,
      ...presentation,
      type: edge.type ?? presentation.type,
      animated: edge.animated ?? presentation.animated,
      style: edge.style ?? presentation.style,
      data: edge.data,
      label: edge.label ?? presentation.label,
    }
  })

/** Applies a single `condition.<field>` update to one CONDITION node, returning the updated node (or the same node unchanged for any other field). Pure — does not touch edges (a condition's yes/no edge-label sync is the caller's responsibility). */
export const applyConditionFieldUpdate = (node: Node, field: string, value: string): Node => {
  if (!field.startsWith('condition.')) return node

  const conditionField = field.slice(10)
  const normalized = normalizeConditionNodeData(node.data)
  const mergedCondition = {
    ...normalized.condition,
    [conditionField]: value,
  }

  const finalized = normalizeConditionNodeData({
    ...normalized,
    condition: mergedCondition,
  })

  return {
    ...node,
    data: {
      ...finalized,
      title: getConditionNodeTitle(finalized.condition.question),
      description: finalized.condition.notes,
      yesLabel: finalized.condition.successLabel,
      noLabel: finalized.condition.failureLabel,
    },
  }
}
