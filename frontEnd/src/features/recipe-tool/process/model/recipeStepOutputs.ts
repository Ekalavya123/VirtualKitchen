/**
 * Step-output references: a STEP's Action On may use an earlier STEP's Expected Output (e.g. step 1
 * "boiled eggs" -> step 2 "Fry boiled eggs + onions"). References are stored by the source step's
 * node id (recipeStepData.ts `actionOn.steps`); everything here is derived from the process's own
 * nodes/edges — there is no separate dependency model.
 *
 * "Earlier" is defined by the existing graph: step A is available to step B when an edge path leads
 * from A to B. When each can reach the other (a loop through a CONDITION), node order breaks the tie,
 * so only one direction is ever valid. The backend ProcessValidator applies the identical rule.
 */

import { isRecipeStepNode } from './recipeNodeTypes'
import { normalizeRecipeStepNodeData } from './recipeStepData'

type GraphNode = { id: string; type?: string; data?: unknown }
type GraphEdge = { source: string; target: string }

export type StepOutputSource = {
  stepId: string
  /** The source step's current Expected Output — the label shown wherever it's referenced. */
  label: string
  /** 1-based position among STEP nodes, for "from step N" hints. */
  stepNumber: number
}

export type StepOutputGraph = {
  /** Every STEP node (CONDITION nodes are never sources), in node order. */
  steps: readonly StepOutputSource[]
  /** Node id -> index in the process's node list (tie-breaker for cycles). */
  nodeIndex: ReadonlyMap<string, number>
  /** Node id -> ids of the nodes it has an edge to. */
  successors: ReadonlyMap<string, readonly string[]>
}

export const EMPTY_STEP_OUTPUT_GRAPH: StepOutputGraph = { steps: [], nodeIndex: new Map(), successors: new Map() }

export const buildStepOutputGraph = (nodes: readonly GraphNode[], edges: readonly GraphEdge[]): StepOutputGraph => {
  const steps: StepOutputSource[] = []
  const nodeIndex = new Map<string, number>()
  nodes.forEach((node, index) => {
    nodeIndex.set(node.id, index)
    if (isRecipeStepNode(node)) {
      steps.push({ stepId: node.id, label: normalizeRecipeStepNodeData(node.data).step.expectedOutput.trim(), stepNumber: steps.length + 1 })
    }
  })

  const successors = new Map<string, string[]>()
  for (const edge of edges) {
    const list = successors.get(edge.source)
    if (list) list.push(edge.target)
    else successors.set(edge.source, [edge.target])
  }
  return { steps, nodeIndex, successors }
}

const reaches = (graph: StepOutputGraph, from: string, to: string): boolean => {
  const seen = new Set<string>([from])
  const queue = [...(graph.successors.get(from) ?? [])]
  while (queue.length > 0) {
    const current = queue.shift() as string
    if (current === to) return true
    if (seen.has(current)) continue
    seen.add(current)
    queue.push(...(graph.successors.get(current) ?? []))
  }
  return false
}

const findStep = (graph: StepOutputGraph, stepId: string) => graph.steps.find((step) => step.stepId === stepId)

export type StepOutputReferenceProblem = 'self' | 'missing' | 'not-a-step' | 'empty-output' | 'not-before'

/** Null when `sourceId`'s output may be used by `consumerId`; otherwise why not. */
export const getStepOutputReferenceProblem = (graph: StepOutputGraph, sourceId: string, consumerId: string): StepOutputReferenceProblem | null => {
  if (sourceId === consumerId) return 'self'
  const source = findStep(graph, sourceId)
  if (!source) return graph.nodeIndex.has(sourceId) ? 'not-a-step' : 'missing'
  if (!source.label) return 'empty-output'
  if (!reaches(graph, sourceId, consumerId)) return 'not-before'
  if (reaches(graph, consumerId, sourceId) && (graph.nodeIndex.get(sourceId) ?? 0) > (graph.nodeIndex.get(consumerId) ?? 0)) return 'not-before'
  return null
}

/** The step outputs `consumerId` can pick from, in step order. */
export const getAvailableStepOutputs = (graph: StepOutputGraph, consumerId: string): StepOutputSource[] =>
  graph.steps.filter((step) => getStepOutputReferenceProblem(graph, step.stepId, consumerId) == null)

/** Current Expected Output of a referenced step, resolved live — '' when the step no longer exists or has none. */
export const getStepOutputLabel = (graph: StepOutputGraph, stepId: string) => findStep(graph, stepId)?.label ?? ''

export const STEP_OUTPUT_PROBLEM_LABELS: Record<StepOutputReferenceProblem, string> = {
  'self': 'a step cannot use its own output',
  'missing': 'the referenced step no longer exists',
  'not-a-step': 'only a STEP has an output (this is a CONDITION)',
  'empty-output': 'the referenced step has no Expected Output',
  'not-before': 'the referenced step is not connected before this one',
}
