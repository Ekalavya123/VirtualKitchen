/**
 * The structure behind the recipe's written explanation (the Recipe Process page and cook mode), derived only from
 * the stored graph — the same for AI-generated and hand-built recipes, never a second copy of the instructions:
 *
 * - Preparation: one card per flow, titled from the flow's named output ("Prepare Marinated Chicken"), its steps in
 *   cooking order.
 * - Main process: one ordered list, where a step that follows straight on from the previous one along an edge
 *   "continues" it. Nothing is said that the graph doesn't establish (e.g. whether it's the same pan).
 *
 * Pure (raw node data; the reading order is passed in) so it runs under node --test.
 */

import type { Process, ProcessEdge } from '../../../../types/process'
import { getPreparationFlows } from '../../model/preparationFlows.ts'
import { CONDITION_HANDLES } from '../../model/recipeNodeTypes.ts'

export type ExplainedFlow = {
  /** 0-based position among the flows, in reading order. */
  index: number
  /** The flow's node ids in cooking (reading) order. */
  nodeIds: string[]
  /** The step whose Expected Output names the flow; null for a flow without a step. */
  outputNodeId: string | null
  /** '' when the final step has no Expected Output yet. */
  outputName: string
  title: string
}

/** "Prepare Marinated Chicken"; a flow without a named output is only numbered (never given an invented name). */
export const preparationFlowTitle = (outputName: string, index: number) =>
  (outputName.trim() ? `Prepare ${outputName.trim()}` : `Preparation flow ${index + 1}`)

/**
 * The Preparation's flows, each with its nodes in reading order, ordered by where each flow starts in that order.
 * `readingOrder` is the process's node ids in reading order (see processReadingOrder.ts).
 */
export const explainPreparationFlows = (preparation: Pick<Process, 'nodes' | 'edges'>, readingOrder: readonly string[]): ExplainedFlow[] => {
  const position = new Map(readingOrder.map((id, index) => [id, index]))
  const at = (id: string) => position.get(id) ?? Number.MAX_SAFE_INTEGER
  return getPreparationFlows(preparation)
    .map((flow) => ({ flow, nodeIds: flow.nodes.map((node) => node.id).sort((a, b) => at(a) - at(b)) }))
    .sort((a, b) => at(a.nodeIds[0]) - at(b.nodeIds[0]))
    .map(({ flow, nodeIds }, index) => ({
      index,
      nodeIds,
      outputNodeId: flow.output?.id ?? null,
      outputName: flow.outputName,
      title: preparationFlowTitle(flow.outputName, index),
    }))
}

/** A check's "No" edge loops back; it never carries the cooking forward. */
const carriesForward = (edge: ProcessEdge) => edge.sourceHandle !== CONDITION_HANDLES.no && edge.source !== edge.target

/**
 * The nodes that follow straight on from the item before them in reading order (an edge leads from it to them):
 * the cooking carries on from there. The first item, and an item reached only from elsewhere, don't.
 */
export const nodesContinuingPrevious = (process: Pick<Process, 'edges'>, readingOrder: readonly string[]): Set<string> => {
  const continuing = new Set<string>()
  readingOrder.forEach((id, index) => {
    if (index === 0) return
    const previous = readingOrder[index - 1]
    if (process.edges.some((edge) => edge.source === previous && edge.target === id && carriesForward(edge))) continuing.add(id)
  })
  return continuing
}
