/**
 * Reading order of recipe process graphs: the order a cook follows a process's nodes in, and the
 * order a recipe's processes are prepared in. Shared by the recipe view (which lays the recipe out
 * in this order) and the editor's AI adapters (which number and describe steps in it).
 */

import type { Process, ProcessEdge, ProcessNode } from '../../../types/process'
import { CONDITION_HANDLES } from './recipeNodeTypes'
import { normalizeRecipeStepNodeData } from './recipeStepData'


const YES_HANDLE = CONDITION_HANDLES.yes
const NO_HANDLE = CONDITION_HANDLES.no

export type OrderedProcess = {
  nodes: ProcessNode[]
  /** Edges that loop back to an earlier node (e.g. "not cooked yet → cook again"). */
  backEdges: Set<ProcessEdge>
  outgoing: Map<string, ProcessEdge[]>
  incoming: Map<string, ProcessEdge[]>
}

/**
 * Orders a process's nodes into reading order: dependencies first (a topological order of the graph
 * with loop-back edges removed), each branch kept together rather than interleaved, and canvas
 * position (top-to-bottom, left-to-right) breaking ties so the reader matches how the author laid
 * the flow out.
 */
export const orderProcessNodes = (process: Process): OrderedProcess => {
  const rank = new Map<string, number>()
  const byPosition = process.nodes
    .map((node, index) => ({ node, index }))
    .sort((a, b) =>
      (a.node.position?.y ?? 0) - (b.node.position?.y ?? 0)
      || (a.node.position?.x ?? 0) - (b.node.position?.x ?? 0)
      || a.index - b.index)
  byPosition.forEach(({ node }, index) => rank.set(node.id, index))

  const nodeById = new Map(process.nodes.map((node) => [node.id, node]))
  const outgoing = new Map<string, ProcessEdge[]>()
  const incoming = new Map<string, ProcessEdge[]>()
  for (const edge of process.edges) {
    if (!nodeById.has(edge.source) || !nodeById.has(edge.target)) continue
    outgoing.set(edge.source, [...(outgoing.get(edge.source) ?? []), edge])
    incoming.set(edge.target, [...(incoming.get(edge.target) ?? []), edge])
  }

  // A check's "yes" path reads first, then "no"; anything else follows canvas position.
  const handlePriority = (edge: ProcessEdge) => (edge.sourceHandle === YES_HANDLE ? 0 : edge.sourceHandle === NO_HANDLE ? 1 : 2)
  const childEdges = (nodeId: string) =>
    [...(outgoing.get(nodeId) ?? [])].sort((a, b) =>
      handlePriority(a) - handlePriority(b) || (rank.get(a.target) ?? 0) - (rank.get(b.target) ?? 0))

  const sortedIds = byPosition.map(({ node }) => node.id)

  // Pass 1: find loop-back edges with a DFS from the flow's starting points (nodes nothing leads
  // into), then from anything left over (e.g. a loop with no clear entry).
  const backEdges = new Set<ProcessEdge>()
  const state = new Map<string, 'active' | 'done'>()
  const classify = (nodeId: string) => {
    state.set(nodeId, 'active')
    for (const edge of childEdges(nodeId)) {
      const targetState = state.get(edge.target)
      if (targetState === 'active') backEdges.add(edge)
      else if (targetState == null) classify(edge.target)
    }
    state.set(nodeId, 'done')
  }
  const roots = sortedIds.filter((id) => (incoming.get(id) ?? []).length === 0)
  for (const id of [...roots, ...sortedIds]) if (!state.has(id)) classify(id)

  // Pass 2: reverse post-order over the remaining (acyclic) graph. Children and starting points are
  // visited in reverse of their desired order, so reversing the post-order puts them back in order
  // with each branch's steps contiguous.
  const forwardChildren = (nodeId: string) => childEdges(nodeId).filter((edge) => !backEdges.has(edge))
  const visited = new Set<string>()
  const postOrder: string[] = []
  const visit = (nodeId: string) => {
    visited.add(nodeId)
    for (const edge of [...forwardChildren(nodeId)].reverse()) if (!visited.has(edge.target)) visit(edge.target)
    postOrder.push(nodeId)
  }
  const sources = sortedIds.filter((id) => (incoming.get(id) ?? []).every((edge) => backEdges.has(edge)))
  for (const id of [...sources].reverse()) if (!visited.has(id)) visit(id)
  for (const id of [...sortedIds].reverse()) if (!visited.has(id)) visit(id)

  return {
    nodes: postOrder.reverse().map((id) => nodeById.get(id) as ProcessNode),
    backEdges,
    outgoing,
    incoming,
  }
}

/** A process's nodes in reading order (see orderProcessNodes) — also what AI edits number and describe. */
export const getProcessReadingOrder = (process: Process): ProcessNode[] => orderProcessNodes(process).nodes

/** Subprocesses a process's steps use, in the order its (ordered) steps first use them. */
const referencedSubprocessIds = (ordered: ProcessNode[]): number[] => {
  const ids: number[] = []
  for (const node of ordered) {
    if (node.kind !== 'STEP') continue
    for (const entry of normalizeRecipeStepNodeData(node.data).step.actionOn.processes) {
      if (!ids.includes(entry.processId)) ids.push(entry.processId)
    }
  }
  return ids
}

/**
 * Sections in cooking order: whatever a process uses is prepared before it (depth-first), so the
 * MAIN process — which brings everything together — reads last. Subprocesses nothing references
 * still appear (before MAIN), since they're part of the recipe's data.
 */
export const orderProcesses = (processes: Process[], orderedById: Map<number, OrderedProcess>): Process[] => {
  const byId = new Map(processes.map((process) => [process.id, process]))
  const result: Process[] = []
  const seen = new Set<number>()
  const visit = (process: Process) => {
    if (seen.has(process.id)) return
    seen.add(process.id)
    for (const id of referencedSubprocessIds(orderedById.get(process.id)?.nodes ?? [])) {
      const sub = byId.get(id)
      if (sub) visit(sub)
    }
    result.push(process)
  }

  const main = processes.find((process) => process.type === 'MAIN')
  if (!main) {
    processes.forEach(visit)
    return result
  }

  // MAIN's own dependencies in the order it uses them, then anything unreferenced, then MAIN.
  visit(main)
  const mainChain = result.splice(0, result.length)
  processes.forEach(visit)
  return [...mainChain.slice(0, -1), ...result, main]
}
