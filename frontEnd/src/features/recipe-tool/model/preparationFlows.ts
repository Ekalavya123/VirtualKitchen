/**
 * The Preparation process: the recipe's one SUBPROCESS, holding every independent preparation flow. A flow is a
 * connected group of nodes (1..n steps); its named output is its final STEP's Expected Output ("Marinated
 * Chicken"), and main steps use it by that step's node id — `actionOn.processes: [{ processId, stepId }]`. Any
 * other preparation step with an Expected Output can be used too (an intermediate result).
 *
 * Pure (raw node data, no catalog imports) so it runs anywhere, including node --test; readiness
 * (recipeReadiness.ts), the editor's pickers and the legacy merge (mergePreparations.ts) build on it.
 */

import type { Process, ProcessEdge, ProcessNode } from '../../../types/process'
import { CONDITION_HANDLES } from './recipeNodeTypes.ts'

// --- raw node data (a STEP's fields live under `data.step`, or flat on `data` in older data) ---

export const asRecord = (value: unknown): Record<string, unknown> =>
  (value && typeof value === 'object' && !Array.isArray(value) ? (value as Record<string, unknown>) : {})
export const asArray = (value: unknown): unknown[] => (Array.isArray(value) ? value : [])
export const asText = (value: unknown): string => (typeof value === 'string' ? value.trim() : '')

export const stepFieldsOf = (node: ProcessNode) => {
  const data = asRecord(node.data)
  return asRecord(data.step && typeof data.step === 'object' ? data.step : data)
}

/** A step's uses of another process's output: the whole process (legacy) or one of its steps. */
export type PreparedReference = { processId: number; stepId?: string }

export const processRefsOf = (fields: Record<string, unknown>): PreparedReference[] =>
  asArray(asRecord(fields.actionOn).processes).flatMap((entry) => {
    const raw = asRecord(entry)
    const id = typeof raw.processId === 'number' ? raw.processId : typeof raw.processId === 'string' && raw.processId.trim() ? Number(raw.processId) : NaN
    if (!Number.isFinite(id)) return []
    const stepId = asText(raw.stepId)
    return [stepId ? { processId: id, stepId } : { processId: id }]
  })

export const stepRefsOf = (fields: Record<string, unknown>): string[] =>
  asArray(asRecord(fields.actionOn).steps).map((entry) => asText(asRecord(entry).stepId)).filter(Boolean)

export const ingredientsOf = (fields: Record<string, unknown>) => asArray(asRecord(fields.actionOn).ingredients).map(asRecord)

export const expectedOutputOf = (node: ProcessNode) => asText(stepFieldsOf(node).expectedOutput)

// --- graph ---

/** Weakly connected components of a process graph, each as its nodes in the process's node order. */
export const componentsOf = (process: Pick<Process, 'nodes' | 'edges'>): ProcessNode[][] => {
  const parent = new Map(process.nodes.map((node) => [node.id, node.id]))
  const find = (id: string): string => {
    let root = id
    while (parent.get(root) !== root) root = parent.get(root) as string
    parent.set(id, root)
    return root
  }
  process.edges.forEach((edge) => {
    if (!parent.has(edge.source) || !parent.has(edge.target)) return
    parent.set(find(edge.source), find(edge.target))
  })
  const groups = new Map<string, ProcessNode[]>()
  process.nodes.forEach((node) => {
    const root = find(node.id)
    groups.set(root, [...(groups.get(root) ?? []), node])
  })
  return [...groups.values()]
}

/** A check's "No" edge leads back to the step it repeats — it never moves a flow forward. */
const isForward = (edge: ProcessEdge) => edge.sourceHandle !== CONDITION_HANDLES.no && edge.source !== edge.target

/**
 * The STEPs a flow ends with: those from which no other STEP can be reached going forward (a trailing check
 * doesn't count as a continuation). A well-formed flow has exactly one.
 */
const endStepsOf = (nodes: ProcessNode[], edges: readonly ProcessEdge[]): ProcessNode[] => {
  const ids = new Set(nodes.map((node) => node.id))
  const byId = new Map(nodes.map((node) => [node.id, node]))
  const forward = new Map<string, string[]>()
  edges.forEach((edge) => {
    if (!ids.has(edge.source) || !ids.has(edge.target) || !isForward(edge)) return
    forward.set(edge.source, [...(forward.get(edge.source) ?? []), edge.target])
  })
  return nodes.filter((node) => {
    if (node.kind !== 'STEP') return false
    const seen = new Set([node.id])
    const queue = [...(forward.get(node.id) ?? [])]
    while (queue.length > 0) {
      const next = queue.shift() as string
      if (seen.has(next)) continue
      seen.add(next)
      if (byId.get(next)?.kind === 'STEP') return false
      queue.push(...(forward.get(next) ?? []))
    }
    return true
  })
}

export type PreparationFlow = {
  /** The flow's nodes, in the process's node order. */
  nodes: ProcessNode[]
  /** STEPs the flow ends with — one when well formed. */
  endSteps: ProcessNode[]
  /** The step whose Expected Output is the flow's named output (null for a flow without a STEP). */
  output: ProcessNode | null
  /** That output's name; '' when the final step has no Expected Output yet. */
  outputName: string
}

/** The recipe's Preparation process (its first SUBPROCESS), or null when it has none yet. */
export const getPreparationProcess = <T extends Pick<Process, 'type'>>(processes: readonly T[]): T | null =>
  processes.find((process) => process.type === 'SUBPROCESS') ?? null

/** Every flow of a Preparation process, ordered by where its first node sits in the process. */
export const getPreparationFlows = (preparation: Pick<Process, 'nodes' | 'edges'>): PreparationFlow[] =>
  componentsOf(preparation).map((nodes) => {
    const endSteps = endStepsOf(nodes, preparation.edges)
    const output = endSteps.find((step) => expectedOutputOf(step)) ?? endSteps[0] ?? null
    return { nodes, endSteps, output, outputName: output ? expectedOutputOf(output) : '' }
  })

/** One thing a main step can use from the Preparation: a flow's output first, then its intermediate results. */
export type PreparedOutput = {
  processId: number
  stepId: string
  label: string
  /** The flow's final, named output (rather than an intermediate result). */
  isFlowOutput: boolean
  /** 1-based, for "Flow 2" style hints. */
  flowNumber: number
}

export const getPreparedOutputs = (processes: readonly Process[]): PreparedOutput[] => {
  const preparation = getPreparationProcess(processes)
  if (!preparation) return []
  return getPreparationFlows(preparation).flatMap((flow, index) => {
    const outputs: PreparedOutput[] = []
    if (flow.output && flow.outputName) {
      outputs.push({ processId: preparation.id, stepId: flow.output.id, label: flow.outputName, isFlowOutput: true, flowNumber: index + 1 })
    }
    flow.nodes.forEach((node) => {
      if (node.kind !== 'STEP' || node.id === flow.output?.id) return
      const label = expectedOutputOf(node)
      if (label) outputs.push({ processId: preparation.id, stepId: node.id, label, isFlowOutput: false, flowNumber: index + 1 })
    })
    return outputs
  })
}

export type PreparedReferenceProblem = 'missing-process' | 'not-preparation' | 'missing-step' | 'not-step' | 'no-output'

/**
 * What a main step's use of a prepared component shows ("Marinated Chicken"), or why it can't be resolved. A
 * legacy whole-process reference shows the process's name.
 */
export const describePreparedReference = (
  processes: readonly Process[], reference: PreparedReference,
): { label: string; problem: PreparedReferenceProblem | null } => {
  const process = processes.find((candidate) => candidate.id === reference.processId)
  if (!process) return { label: '', problem: 'missing-process' }
  if (process.type !== 'SUBPROCESS') return { label: process.name, problem: 'not-preparation' }
  if (!reference.stepId) return { label: process.name, problem: null }
  const node = process.nodes.find((candidate) => candidate.id === reference.stepId)
  if (!node) return { label: '', problem: 'missing-step' }
  if (node.kind !== 'STEP') return { label: '', problem: 'not-step' }
  const label = expectedOutputOf(node)
  return label ? { label, problem: null } : { label: '', problem: 'no-output' }
}

export const PREPARED_REFERENCE_PROBLEM_LABELS: Record<PreparedReferenceProblem, string> = {
  'missing-process': 'Prepared component no longer exists',
  'not-preparation': 'Not a preparation output',
  'missing-step': 'Preparation step was deleted',
  'not-step': 'Not a preparation step',
  'no-output': 'Preparation step has no Expected Output',
}

/** Stable key of a reference, for lists and comparisons. */
export const preparedReferenceKey = (reference: PreparedReference) => `${reference.processId}:${reference.stepId ?? ''}`

/**
 * A reference as one string, for the AI edit wire format (which lists `uses` as strings): "12" for a whole
 * process, "12:<stepId>" for one of its steps. Node ids are UUIDs, so they never contain ":".
 */
export const encodePreparedReference = (reference: PreparedReference) =>
  (reference.stepId ? `${reference.processId}:${reference.stepId}` : String(reference.processId))

export const decodePreparedReference = (value: string): PreparedReference | null => {
  const [processPart, ...stepParts] = value.split(':')
  const processId = Number(processPart)
  if (!processPart.trim() || !Number.isFinite(processId)) return null
  const stepId = stepParts.join(':').trim()
  return stepId ? { processId, stepId } : { processId }
}
