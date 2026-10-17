/**
 * Where a prepared component is used: STEP nodes reference the Preparation process (one of its steps' outputs, by
 * `stepId`) — or, in data saved before the single Preparation, a whole subprocess — only through their Action On
 * (`actionOn.processes`). Deleting a process or a preparation step first strips those references, so no saved
 * step is ever left pointing at something that no longer exists — the same way deleting a step strips other
 * steps' step-output references to it.
 */

import type { Process, ProcessNode } from '../../../types/process'
import { normalizeRecipeStepNodeData, withRecipeStepActionOnProcesses, type RecipeStepActionOnProcess } from './recipeStepData'

/** Matches a reference to `processId` — to any of its steps, or only to `stepId` when given. */
const matches = (entry: RecipeStepActionOnProcess, processId: number, stepId?: string) =>
  entry.processId === processId && (stepId == null || entry.stepId === stepId)

const referencesProcess = (data: unknown, processId: number, stepId?: string) =>
  normalizeRecipeStepNodeData(data).step.actionOn.processes.some((entry) => matches(entry, processId, stepId))

/** How many STEP nodes, across `processes`, use `processId` (or only its step `stepId`) in their Action On. */
export const countSubprocessReferences = (processes: Process[], processId: number, stepId?: string) =>
  processes.reduce((count, process) => count + process.nodes
    .filter((node) => node.kind === 'STEP' && referencesProcess(node.data, processId, stepId)).length, 0)

/** The processes (and their steps) that use `processId`'s step `stepId` — for a deletion's confirmation. */
export const findPreparedOutputUses = (processes: Process[], processId: number, stepId: string) =>
  processes.flatMap((process) => process.nodes
    .filter((node) => node.kind === 'STEP' && referencesProcess(node.data, processId, stepId))
    .map((node) => ({ process, node })))

/**
 * `process` with every STEP's Action On reference to `processId` (or only to its step `stepId`) removed —
 * `process` itself when none had one.
 */
export const withoutSubprocessReferences = (process: Process, processId: number, stepId?: string): Process => {
  if (!process.nodes.some((node) => node.kind === 'STEP' && referencesProcess(node.data, processId, stepId))) return process
  return {
    ...process,
    nodes: process.nodes.map((node) => {
      if (node.kind !== 'STEP' || !referencesProcess(node.data, processId, stepId)) return node
      const remaining = normalizeRecipeStepNodeData(node.data).step.actionOn.processes
        .filter((entry) => !matches(entry, processId, stepId))
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
 * Rewrites every STEP node's Action On references (`processId`) through `idMap` — used whenever a save creates
 * processes for real (temporary/undone ids -> real ids), so any step that referenced one of them, in any
 * process, points at the real id. Step ids are kept. Returns `nodes` itself when nothing referenced a remapped id.
 */
export const remapActionOnProcessRefs = (nodes: ProcessNode[], idMap: Map<number, number>): ProcessNode[] => {
  let anyChanged = false
  const next = nodes.map((node) => {
    if (node.kind !== 'STEP') return node

    const { step } = normalizeRecipeStepNodeData(node.data)
    const references = step.actionOn.processes
    if (references.length === 0) return node

    const remapped = references.map((entry) => ({ ...entry, processId: idMap.get(entry.processId) ?? entry.processId }))
    const changed = remapped.some((entry, index) => entry.processId !== references[index].processId)
    if (!changed) return node

    anyChanged = true
    return { ...node, data: withRecipeStepActionOnProcesses(node.data, remapped) as unknown as Record<string, unknown> }
  })
  return anyChanged ? next : nodes
}
