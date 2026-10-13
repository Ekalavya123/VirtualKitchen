/**
 * Where a SUBPROCESS is used: STEP nodes reference subprocesses only through their Action On
 * (`actionOn.processes`), in any process of the recipe. Deleting a subprocess first strips those
 * references, so no saved step is ever left pointing at a process that no longer exists — the same
 * way deleting a step strips other steps' step-output references to it.
 */

import type { Process, ProcessNode } from '../../../types/process'
import { normalizeRecipeStepNodeData, withRecipeStepActionOnProcesses } from './recipeStepData'

const referencesProcess = (data: unknown, processId: number) =>
  normalizeRecipeStepNodeData(data).step.actionOn.processes.some((entry) => entry.processId === processId)

/** How many STEP nodes, across `processes`, use `processId` in their Action On. */
export const countSubprocessReferences = (processes: Process[], processId: number) =>
  processes.reduce((count, process) => count + process.nodes
    .filter((node) => node.kind === 'STEP' && referencesProcess(node.data, processId)).length, 0)

/** `process` with every STEP's Action On reference to `processId` removed — `process` itself when none had one. */
export const withoutSubprocessReferences = (process: Process, processId: number): Process => {
  if (!process.nodes.some((node) => node.kind === 'STEP' && referencesProcess(node.data, processId))) return process
  return {
    ...process,
    nodes: process.nodes.map((node) => {
      if (node.kind !== 'STEP' || !referencesProcess(node.data, processId)) return node
      const remaining = normalizeRecipeStepNodeData(node.data).step.actionOn.processes
        .map((entry) => entry.processId)
        .filter((id) => id !== processId)
      return { ...node, data: withRecipeStepActionOnProcesses(node.data, remaining) as unknown as Record<string, unknown> }
    }),
  }
}

/**
 * A process not yet created on the backend (e.g. from AI generation — see
 * recipeProcessGenerationConverter.ts) is held in the session under a temporary
 * negative id; a real Process id is always a positive sequence number, so
 * the two can never collide.
 */
export const isPendingProcessId = (id: number) => id < 0

/**
 * Rewrites every STEP node's Action On subprocess references (`processId`) through `idMap` — used
 * whenever a save creates processes for real (temporary/undone ids -> real ids), so any step that
 * referenced one of them, in any process, points at the real id. Returns `nodes` itself when
 * nothing referenced a remapped id.
 */
export const remapActionOnProcessRefs = (nodes: ProcessNode[], idMap: Map<number, number>): ProcessNode[] => {
  let anyChanged = false
  const next = nodes.map((node) => {
    if (node.kind !== 'STEP') return node

    const { step } = normalizeRecipeStepNodeData(node.data)
    const references = step.actionOn.processes
    if (references.length === 0) return node

    const remapped = references.map((entry) => idMap.get(entry.processId) ?? entry.processId)
    const changed = remapped.some((id, index) => id !== references[index].processId)
    if (!changed) return node

    anyChanged = true
    return { ...node, data: withRecipeStepActionOnProcesses(node.data, remapped) as unknown as Record<string, unknown> }
  })
  return anyChanged ? next : nodes
}
