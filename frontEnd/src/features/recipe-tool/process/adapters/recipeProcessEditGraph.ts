/**
 * Structural graph changes behind AI edits (see recipeProcessEditApplier.ts): inserting a node into
 * the flow after another, detaching a node while keeping the flow connected, and finding a free spot
 * on the canvas for an inserted node. Pure functions over the persisted Process node/edge shapes —
 * node *data* is the applier's concern — so the rewiring rules can be tested on their own.
 *
 * "The flow after a node" means its ordinary outgoing edges; for a CONDITION that is its Yes branch.
 * A condition's No edge (the "repeat until" loop back to the step it checks) is never treated as
 * the way forward.
 */

import type { ProcessEdge, ProcessNode } from '../../../../types/process'

export const EDIT_START = 'START'

const YES_HANDLE = 'condition-yes'
const NO_HANDLE = 'condition-no'

const DEFAULT_SIZE = { width: 280, height: 160 }
const ROW_GAP = 260
const NUDGE = 60
const MAX_NUDGES = 30

export type ProcessGraph = { nodes: ProcessNode[]; edges: ProcessEdge[] }

const isCondition = (node: ProcessNode | undefined) => node?.kind === 'CONDITION'

/** Edges that carry the flow forward out of `nodeId` (everything but a condition's No branch). */
const forwardEdges = (edges: ProcessEdge[], nodeId: string) =>
  edges.filter((edge) => edge.source === nodeId && edge.sourceHandle !== NO_HANDLE)

const positionOf = (node: ProcessNode | undefined, fallback = { x: 120, y: 80 }) =>
  node?.position ? { x: node.position.x ?? fallback.x, y: node.position.y ?? fallback.y } : fallback

const sizeOf = (node: Pick<ProcessNode, 'width' | 'height' | 'measured'>) => ({
  width: node.width ?? node.measured?.width ?? DEFAULT_SIZE.width,
  height: node.height ?? node.measured?.height ?? DEFAULT_SIZE.height,
})

const overlaps = (a: { x: number; y: number; width: number; height: number }, b: { x: number; y: number; width: number; height: number }) =>
  a.x < b.x + b.width && b.x < a.x + a.width && a.y < b.y + b.height && b.y < a.y + a.height

/**
 * The first position at or below `preferred` where a node of `size` doesn't overlap any of `nodes`
 * (nudged down step by step; gives up and returns the last try rather than looping forever).
 */
export const findFreePosition = (
  nodes: ProcessNode[], preferred: { x: number; y: number }, size: { width: number; height: number },
): { x: number; y: number } => {
  const boxes = nodes.map((node) => ({ ...positionOf(node, { x: 0, y: 0 }), ...sizeOf(node) }))
  let candidate = { ...preferred }
  for (let attempt = 0; attempt < MAX_NUDGES; attempt += 1) {
    if (!boxes.some((box) => overlaps({ ...candidate, ...size }, box))) return candidate
    candidate = { x: candidate.x, y: candidate.y + NUDGE }
  }
  return candidate
}

/**
 * Where a node inserted after `anchorId` should go: between the anchor and the node that followed
 * it (or one row below the anchor at the end of the flow; above the first node for START).
 */
export const preferredInsertPosition = (graph: ProcessGraph, anchorId: string, firstNodeId: string | null): { x: number; y: number } => {
  const byId = new Map(graph.nodes.map((node) => [node.id, node]))
  if (anchorId === EDIT_START) {
    const at = positionOf(firstNodeId ? byId.get(firstNodeId) : undefined)
    return { x: at.x, y: at.y - ROW_GAP }
  }
  const at = positionOf(byId.get(anchorId))
  const next = forwardEdges(graph.edges, anchorId).map((edge) => byId.get(edge.target)).find((node) => node?.position)
  if (next) {
    const to = positionOf(next)
    return { x: Math.round((at.x + to.x) / 2) + 40, y: Math.round((at.y + to.y) / 2) + 40 }
  }
  return { x: at.x, y: at.y + ROW_GAP }
}

/**
 * Adds `node` to the flow right after `anchorId` (or before `firstNodeId` for START): the anchor's
 * forward edges are redirected into the new node, which then leads to wherever they used to go.
 * The redirected edge keeps its id, handle and label, so a condition's Yes branch stays a Yes branch.
 */
export const insertNodeAfter = (
  graph: ProcessGraph, node: ProcessNode, anchorId: string, firstNodeId: string | null, newEdgeId: () => string,
): ProcessGraph => {
  const nodes = [...graph.nodes, node]
  if (anchorId === EDIT_START) {
    const edges = firstNodeId ? [...graph.edges, { id: newEdgeId(), source: node.id, target: firstNodeId }] : graph.edges
    return { nodes, edges }
  }

  const anchor = graph.nodes.find((candidate) => candidate.id === anchorId)
  const outgoing = forwardEdges(graph.edges, anchorId)
  const followers = [...new Set(outgoing.map((edge) => edge.target))]
  const [kept, ...dropped] = outgoing
  const droppedIds = new Set(dropped.map((edge) => edge.id))

  const edges = graph.edges
    .filter((edge) => !droppedIds.has(edge.id))
    .map((edge) => (kept && edge.id === kept.id ? { ...edge, target: node.id } : edge))
  if (!kept) {
    edges.push(isCondition(anchor)
      ? { id: newEdgeId(), source: anchorId, target: node.id, sourceHandle: YES_HANDLE, label: 'Yes' }
      : { id: newEdgeId(), source: anchorId, target: node.id })
  }
  for (const target of followers) {
    edges.push(isCondition(node)
      ? { id: newEdgeId(), source: node.id, target, sourceHandle: YES_HANDLE, label: 'Yes' }
      : { id: newEdgeId(), source: node.id, target })
  }
  if (isCondition(node) && anchor && !isCondition(anchor)) {
    // The convention for a new check: No goes back to the step it checks, which repeats.
    edges.push({ id: newEdgeId(), source: node.id, target: anchorId, sourceHandle: NO_HANDLE, label: 'No' })
  }
  return { nodes, edges }
}

/**
 * Takes `nodeId` out of the flow without breaking it: every node that led into it now leads to
 * whatever it led to (keeping the incoming edge's handle/label), and all of its own edges go. The
 * node itself is removed from `nodes` too.
 */
export const detachNode = (graph: ProcessGraph, nodeId: string, newEdgeId: () => string): ProcessGraph => {
  const incoming = graph.edges.filter((edge) => edge.target === nodeId && edge.source !== nodeId)
  const followers = [...new Set(forwardEdges(graph.edges, nodeId).map((edge) => edge.target))].filter((target) => target !== nodeId)

  const edges = graph.edges.filter((edge) => edge.source !== nodeId && edge.target !== nodeId)
  const exists = (source: string, target: string, sourceHandle?: string | null) =>
    edges.some((edge) => edge.source === source && edge.target === target && (edge.sourceHandle ?? null) === (sourceHandle ?? null))

  for (const edge of incoming) {
    for (const target of followers) {
      if (target === edge.source || exists(edge.source, target, edge.sourceHandle)) continue
      edges.push({ ...edge, id: newEdgeId(), target })
    }
  }
  return { nodes: graph.nodes.filter((node) => node.id !== nodeId), edges }
}
