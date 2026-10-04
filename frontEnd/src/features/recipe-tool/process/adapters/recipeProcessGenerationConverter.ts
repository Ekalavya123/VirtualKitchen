/**
 * Converts an AI-generated recipe process structure (recipe/dto/Generated*.java via
 * RecipeProcessGenerationApi) into the same `Process[]` shape RecipeSessionContext already works
 * with — generating every React Flow presentation detail (node/edge ids, positions, dimensions)
 * the AI was deliberately never asked for. Subprocess `ref` strings are resolved
 * to the client-temporary process ids this conversion assigns — real ids only start existing once
 * RecipeSessionContext.saveAll() creates them.
 */

import { RECIPE_NODE_TYPES } from '../model/recipeNodeTypes'
import { normalizeConditionNodeData, type ConditionNodeData } from '../model/recipeConditionData'
import { normalizeRecipeStepNodeData, type RecipeStepNodeData } from '../model/recipeStepData'
import { parseDurationLabel } from '../../catalog/stepFieldCatalog'
import type { Process, ProcessEdge, ProcessNode } from '../../../../types/process'
import type { GeneratedRecipeProcess, GeneratedRecipeStep, RecipeProcessGenerationResult } from '../../../../types/recipe'

const STEP_SIZE = { width: 280, height: 160 }
const CONDITION_SIZE = { width: 190, height: 190 }

const HORIZONTAL_GAP = 380
const VERTICAL_GAP = 260
const ORIGIN_X = 120
const ORIGIN_Y = 80

/**
 * Picks fresh, mutually-unique negative ids for the processes this generation is about to create
 * (a real Process id from the backend is always a positive sequence number, so negative ids can
 * never collide with one) — starting below the lowest id already present anywhere in the current
 * session, so two generation runs in the same visit (before either is saved) don't collide either.
 */
const allocateTempIds = (count: number, existingIds: number[]): number[] => {
  const lowest = existingIds.length > 0 ? Math.min(0, ...existingIds) : 0
  const start = lowest - 1
  return Array.from({ length: count }, (_, index) => start - index)
}

const buildStepNode = (
  step: GeneratedRecipeStep,
  nodeId: string,
  refToProcessId: Map<string, number>,
  stepIdToNodeId: Map<string, string>,
  x: number,
  y: number,
): ProcessNode => {
  const repeatInterval = parseDurationLabel(step.repeatInterval ?? '')
  const data: RecipeStepNodeData = normalizeRecipeStepNodeData({
    step: {
      action: step.action ?? '',
      customActionName: step.customActionName ?? '',
      actionOn: {
        ingredients: (step.actionOn?.ingredients ?? []).map((ingredient) => ({
          ingredientId: ingredient.ingredientId,
          customIngredientName: ingredient.customIngredientName ?? '',
          quantity: ingredient.quantity,
          unit: ingredient.unit ?? '',
          preparationStyleId: ingredient.preparationStyle ?? '',
          customPreparationStyle: '',
        })),
        processes: (step.actionOn?.processes ?? [])
          .map((ref) => refToProcessId.get(ref))
          .filter((processId): processId is number => processId != null)
          .map((processId) => ({ processId })),
        // Generated `stepId` slugs resolve to this process's freshly assigned node ids; the backend
        // validator already guaranteed each points at an earlier STEP with an Expected Output.
        steps: (step.actionOn?.steps ?? [])
          .map((ref) => stepIdToNodeId.get(ref))
          .filter((stepId): stepId is string => stepId != null)
          .map((stepId) => ({ stepId })),
      },
      actionDescription: step.actionDescription ?? '',
      expectedOutput: step.expectedOutput ?? '',
      flameLevelId: step.flameLevel ?? '',
      customFlameLevel: '',
      // normalizeRecipeStepFields falls back to parsing the legacy free-text `temperature` when no value is given.
      temperatureValue: step.temperatureValue ?? '',
      temperatureUnit: step.temperatureUnit ?? '',
      temperature: step.temperature ?? '',
      ...parseDurationLabel(step.duration ?? ''),
      repeatIntervalValue: repeatInterval.durationValue,
      repeatIntervalUnit: repeatInterval.durationUnit,
    },
  })

  return {
    id: nodeId,
    kind: 'STEP',
    type: RECIPE_NODE_TYPES.step,
    data: data as unknown as Record<string, unknown>,
    position: { x, y },
    width: STEP_SIZE.width,
    height: STEP_SIZE.height,
    measured: STEP_SIZE,
    draggable: true,
    selectable: true,
    deletable: true,
  }
}

const buildConditionNode = (step: GeneratedRecipeStep, nodeId: string, x: number, y: number): ProcessNode => {
  const notes = [step.actionDescription, step.expectedOutput ? `Expected: ${step.expectedOutput}` : '']
    .filter(Boolean)
    .join(' ')
  const expectedResult = step.expectedResult === 'failure' ? 'failure' : 'success'

  const data: ConditionNodeData = normalizeConditionNodeData({
    title: step.title ?? '',
    condition: {
      question: step.title ?? '',
      expectedResult,
      successLabel: 'Yes',
      failureLabel: 'No',
      notes,
    },
    yesLabel: 'Yes',
    noLabel: 'No',
    description: notes,
  })

  return {
    id: nodeId,
    kind: 'CONDITION',
    type: RECIPE_NODE_TYPES.condition,
    data: data as unknown as Record<string, unknown>,
    position: { x, y },
    width: CONDITION_SIZE.width,
    height: CONDITION_SIZE.height,
    measured: CONDITION_SIZE,
    draggable: true,
    selectable: true,
    deletable: true,
  }
}

/**
 * Builds one process's nodes/edges from its ordered step list. Steps are already in execution
 * order (the AI was asked for an ordered list, not a graph — see RecipeProcessGenerationPromptBuilder),
 * so consecutive steps are connected linearly. A CONDITION's YES branch goes to the next node and
 * its NO branch back to the step before it (a "repeat until" loop) — the AI doesn't generate branch
 * targets itself (asking for real graph topology is a substantially harder and more error-prone
 * generation task than an ordered step list), so the prompt fixes that convention instead.
 */
const buildNodesAndEdges = (steps: GeneratedRecipeStep[], refToProcessId: Map<string, number>): { nodes: ProcessNode[]; edges: ProcessEdge[] } => {
  const columns = Math.max(1, Math.ceil(Math.sqrt(Math.max(1, steps.length))))

  // Node ids are assigned up front so a step can reference an earlier step's output by id.
  const nodeIds = steps.map(() => crypto.randomUUID())
  const stepIdToNodeId = new Map<string, string>()
  steps.forEach((step, index) => {
    if (step.stepId) stepIdToNodeId.set(step.stepId, nodeIds[index])
  })

  const nodes = steps.map((step, index) => {
    const col = index % columns
    const row = Math.floor(index / columns)
    const x = ORIGIN_X + col * HORIZONTAL_GAP
    const y = ORIGIN_Y + row * VERTICAL_GAP

    return step.nodeType === 'CONDITION'
      ? buildConditionNode(step, nodeIds[index], x, y)
      : buildStepNode(step, nodeIds[index], refToProcessId, stepIdToNodeId, x, y)
  })

  const edges: ProcessEdge[] = []
  nodes.forEach((source, i) => {
    const next = nodes[i + 1]
    if (source.kind !== 'CONDITION') {
      if (next) edges.push({ id: crypto.randomUUID(), source: source.id, target: next.id })
      return
    }
    // YES continues to the next node; NO loops back to the step being checked (the nearest STEP
    // before the condition) so it's repeated until the check passes. The prompt asks for every
    // condition to be phrased that way and placed right after the step it checks.
    const checkedStep = nodes.slice(0, i).reverse().find((node) => node.kind === 'STEP')
    if (next) edges.push({ id: crypto.randomUUID(), source: source.id, target: next.id, sourceHandle: 'condition-yes', label: 'Yes' })
    const noTarget = checkedStep ?? next
    if (noTarget) edges.push({ id: crypto.randomUUID(), source: source.id, target: noTarget.id, sourceHandle: 'condition-no', label: 'No' })
  })

  return { nodes, edges }
}

export type ConvertedGeneration = {
  /** Every generated process (MAIN first, then subprocesses), ready to load into RecipeSessionContext via addProcess/updateProcess. */
  processes: Process[]
  /** The id (temporary, or the existing MAIN's real id when replacing it) to select/display after loading. */
  mainProcessId: number
}

/**
 * @param existingMain pass the recipe's current MAIN process to replace its content in place
 *   (keeping its real id) instead of creating a second MAIN — the caller decides whether that's
 *   appropriate (with the user's confirmation) before calling this.
 * @param existingSessionIds every process id already in the current session, so temporary ids
 *   never collide with an unsaved process from an earlier generation run.
 */
export const convertGeneratedResultToProcesses = (
  recipeId: number,
  result: RecipeProcessGenerationResult,
  existingMain: Process | null,
  existingSessionIds: number[],
): ConvertedGeneration => {
  const subprocesses = result.subprocesses ?? []
  const tempIds = allocateTempIds(subprocesses.length + (existingMain ? 0 : 1), existingSessionIds)

  let tempIdCursor = 0
  const mainProcessId = existingMain ? existingMain.id : tempIds[tempIdCursor++]

  // 1:1 with `subprocesses` by position — every generated subprocess gets its own temp id
  // regardless of whether it declared a (valid, non-blank, unique) ref; only ones that did are
  // addressable from a step's actionOn.processes.
  const subprocessIds: number[] = subprocesses.map(() => tempIds[tempIdCursor++])
  const refToProcessId = new Map<string, number>()
  subprocesses.forEach((subprocess, index) => {
    if (subprocess.ref) refToProcessId.set(subprocess.ref, subprocessIds[index])
  })

  const buildProcess = (generated: GeneratedRecipeProcess, id: number, type: Process['type']): Process => {
    const { nodes, edges } = buildNodesAndEdges(generated.steps ?? [], refToProcessId)
    return {
      id,
      type,
      recipeId,
      name: generated.name || (type === 'MAIN' ? 'Main Process' : 'Subprocess'),
      nodes,
      edges,
    }
  }

  const processes: Process[] = [
    buildProcess(result.mainProcess, mainProcessId, 'MAIN'),
    ...subprocesses.map((subprocess, index) => buildProcess(subprocess, subprocessIds[index], 'SUBPROCESS')),
  ]

  return { processes, mainProcessId }
}
