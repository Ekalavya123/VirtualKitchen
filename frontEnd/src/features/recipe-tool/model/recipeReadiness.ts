/**
 * Full-recipe readiness: whether a recipe's processes form the Preparation + Main structure that whole-recipe
 * operations (cook mode, visualizing/narrating everything, ordering) rely on.
 *
 * - The MAIN process is one connected cooking sequence; its steps have an action and an instruction.
 * - The recipe's one SUBPROCESS is its Preparation: independent flows, each naming its output with its final
 *   step's Expected Output, which main steps use by that step's id (see preparationFlows.ts).
 *
 * This is deliberately separate from saving: an incomplete recipe always saves. Issues are BLOCKERs (the
 * operation can't run) or WARNINGs (shown, never blocking). The same rules run on the backend
 * (recipe/validation/RecipeReadinessEvaluator.java) to gate the operations server-side; both are tested
 * against the shared cases in backend/src/test/resources/readiness/readiness-cases.json.
 *
 * Self-contained on purpose (raw node data, no catalog imports) so it runs anywhere, including node --test;
 * pass `isKnownAction` to also reject an action id the catalog doesn't know.
 */

import type { Process, ProcessNode } from '../../../types/process'
import {
  asText,
  componentsOf,
  expectedOutputOf,
  getPreparationFlows,
  ingredientsOf,
  processRefsOf,
  stepFieldsOf,
  stepRefsOf,
} from './preparationFlows.ts'
import { ingredientAnnotationId, preparedAnnotationId, toPlainText, unbackedAnnotations } from './stepAnnotations.ts'

export type ReadinessSeverity = 'BLOCKER' | 'WARNING'

export type ReadinessIssueCode =
  | 'MAIN_MISSING'
  | 'MAIN_EMPTY'
  | 'MAIN_DISCONNECTED'
  | 'MAIN_MULTIPLE_STARTS'
  | 'STEP_INCOMPLETE'
  | 'PREP_MULTIPLE_PROCESSES'
  | 'PREP_OUTPUT_MISSING'
  | 'PREP_MULTIPLE_ENDS'
  | 'PREP_EMPTY'
  | 'PREP_OUTPUT_UNUSED'
  | 'PREP_REFERENCES_PROCESS'
  | 'REF_DANGLING'
  | 'CUSTOM_INGREDIENT'
  | 'CUSTOM_UNIT'
  | 'ANNOTATION_DANGLING'

export type ReadinessIssue = {
  code: ReadinessIssueCode
  severity: ReadinessSeverity
  /** The process to open to fix it; null for a recipe-wide issue (no main process). */
  processId: number | null
  /** The node to select, when the issue is about one step. */
  nodeId: string | null
  message: string
}

export type RecipeReadiness = {
  issues: ReadinessIssue[]
  blockers: ReadinessIssue[]
  warnings: ReadinessIssue[]
  /** No blockers: whole-recipe operations may run. */
  ready: boolean
}

export type ReadinessOptions = {
  /** Rejects an action id the catalog doesn't know (a missing action is always rejected). */
  isKnownAction?: (actionId: string) => boolean
}

const CUSTOM_ID = 'custom'

/** A step's instruction as prose (annotations read as their labels). */
const instructionOf = (fields: Record<string, unknown>) =>
  (typeof fields.actionDescription === 'string' ? toPlainText(fields.actionDescription).trim() : '')

/** "“Fry the onions”", or the step's position when it has no description yet. */
const describeNode = (process: Process, node: ProcessNode) => {
  const description = instructionOf(stepFieldsOf(node)) || asText((node.data ?? {}).title)
  if (description) return `“${description.length > 48 ? `${description.slice(0, 45)}…` : description}”`
  const index = process.nodes.filter((candidate) => candidate.kind === node.kind).indexOf(node)
  return node.kind === 'CONDITION' ? `check ${index + 1}` : `step ${index + 1}`
}

const processLabel = (process: Process) => (process.type === 'MAIN' ? 'the main process' : 'Preparation')

/** Every component except the main one (the largest; the earliest on a tie). */
const strayComponents = (process: Process): ProcessNode[][] => {
  const components = componentsOf(process)
  if (components.length <= 1) return []
  const mainComponent = components.reduce((best, candidate) => (candidate.length > best.length ? candidate : best))
  return components.filter((component) => component !== mainComponent)
}

/** Nodes nothing leads into — more than one means independent starting points. */
const startNodesOf = (process: Process): ProcessNode[] => {
  const targets = new Set(process.edges.filter((edge) => edge.source !== edge.target).map((edge) => edge.target))
  return process.nodes.filter((node) => !targets.has(node.id))
}

const stepsOf = (process: Process) => process.nodes.filter((node) => node.kind === 'STEP')

export const evaluateRecipeReadiness = (processes: readonly Process[], options: ReadinessOptions = {}): RecipeReadiness => {
  const issues: ReadinessIssue[] = []
  const add = (code: ReadinessIssueCode, severity: ReadinessSeverity, processId: number | null, nodeId: string | null, message: string) =>
    issues.push({ code, severity, processId, nodeId, message })

  const main = processes.find((process) => process.type === 'MAIN') ?? null
  const preparations = processes.filter((process) => process.type === 'SUBPROCESS')
  const ordered = main ? [main, ...preparations] : preparations
  const byId = new Map(processes.map((process) => [process.id, process]))

  // What MAIN steps use: whole processes (legacy) and individual preparation steps.
  const wholeUsedByMain = new Set<number>()
  const stepsUsedByMain = new Set<string>()
  if (main) {
    stepsOf(main).forEach((node) => processRefsOf(stepFieldsOf(node)).forEach((reference) => {
      if (reference.stepId) stepsUsedByMain.add(`${reference.processId}:${reference.stepId}`)
      else wholeUsedByMain.add(reference.processId)
    }))
  }
  const usedAnywhere = new Set<number>()
  processes.forEach((process) => stepsOf(process).forEach((node) =>
    processRefsOf(stepFieldsOf(node)).forEach((reference) => usedAnywhere.add(reference.processId))))

  if (!main) {
    add('MAIN_MISSING', 'BLOCKER', null, null, 'The recipe has no main process.')
  } else {
    if (stepsOf(main).length === 0) {
      add('MAIN_EMPTY', 'BLOCKER', main.id, null, 'The main process has no steps yet.')
    }
    const stray = strayComponents(main)
    stray.forEach((component) => {
      const first = component[0]
      add('MAIN_DISCONNECTED', 'BLOCKER', main.id, first.id,
        `${describeNode(main, first)}${component.length > 1 ? ` (and ${component.length - 1} more)` : ''} is not connected to the rest of the main process. Connect it, or move it into Preparation.`)
    })
    const starts = startNodesOf(main)
    if (stray.length === 0 && starts.length > 1) {
      add('MAIN_MULTIPLE_STARTS', 'WARNING', main.id, starts[1].id,
        `The main process starts in ${starts.length} places. Work done independently before cooking belongs in Preparation.`)
    }
  }

  preparations.slice(1).forEach((extra) => {
    add('PREP_MULTIPLE_PROCESSES', 'WARNING', extra.id, null,
      `“${extra.name.trim() || 'Untitled'}” is a separate preparation process; a recipe has one Preparation with several flows.`)
  })

  preparations.forEach((preparation) => {
    if (stepsOf(preparation).length === 0) {
      const referenced = usedAnywhere.has(preparation.id)
      add('PREP_EMPTY', referenced ? 'BLOCKER' : 'WARNING', preparation.id, null,
        `Preparation has no steps${referenced ? ', but a step uses something from it' : ''}.`)
      return
    }
    getPreparationFlows(preparation).forEach((flow) => {
      const anchor = flow.output ?? flow.nodes[0]
      if (!flow.outputName) {
        add('PREP_OUTPUT_MISSING', 'BLOCKER', preparation.id, anchor.id,
          `A preparation flow has no named output: give ${flow.output ? describeNode(preparation, flow.output) : 'its last step'} an Expected Output (e.g. “Chopped Onions”).`)
      }
      if (flow.endSteps.length > 1) {
        add('PREP_MULTIPLE_ENDS', 'WARNING', preparation.id, flow.endSteps[1].id,
          `A preparation flow ends in ${flow.endSteps.length} steps; it should end in the one step that makes its output.`)
      }
      const used = wholeUsedByMain.has(preparation.id)
        || flow.nodes.some((node) => stepsUsedByMain.has(`${preparation.id}:${node.id}`))
      if (main && flow.outputName && !used) {
        add('PREP_OUTPUT_UNUSED', 'WARNING', preparation.id, anchor.id, `No main step uses “${flow.outputName}” yet.`)
      }
    })
  })

  const customIngredients = new Set<string>()
  const customUnits = new Set<string>()
  ordered.forEach((process) => {
    const nodesById = new Map(process.nodes.map((node) => [node.id, node]))
    process.nodes.forEach((node) => {
      if (node.kind !== 'STEP') return
      const fields = stepFieldsOf(node)
      const label = `${describeNode(process, node)} in ${processLabel(process)}`

      const action = asText(fields.action)
      if (!action) {
        add('STEP_INCOMPLETE', 'BLOCKER', process.id, node.id, `${label} has no action.`)
      } else if (action === CUSTOM_ID && !asText(fields.customActionName)) {
        add('STEP_INCOMPLETE', 'BLOCKER', process.id, node.id, `${label} uses a custom action without a name.`)
      } else if (action !== CUSTOM_ID && options.isKnownAction && !options.isKnownAction(action)) {
        add('STEP_INCOMPLETE', 'BLOCKER', process.id, node.id, `${label} has an unknown action “${action}”.`)
      } else if (process.type === 'MAIN' && !instructionOf(fields)) {
        add('STEP_INCOMPLETE', 'BLOCKER', process.id, node.id, `${label} has no instruction (Action Description).`)
      }

      processRefsOf(fields).forEach((reference) => {
        const target = byId.get(reference.processId)
        // A process can't use itself whole; a Preparation step using another of its steps is only a warning (below).
        if (!target || target.type !== 'SUBPROCESS' || (target.id === process.id && !reference.stepId)) {
          add('REF_DANGLING', 'BLOCKER', process.id, node.id, `${label} uses a prepared component that no longer exists.`)
          return
        }
        if (reference.stepId) {
          const source = target.nodes.find((candidate) => candidate.id === reference.stepId)
          if (!source || source.kind !== 'STEP' || !expectedOutputOf(source)) {
            add('REF_DANGLING', 'BLOCKER', process.id, node.id, `${label} uses a prepared component whose preparation step is gone or has no Expected Output.`)
            return
          }
        }
        if (process.type === 'SUBPROCESS') {
          add('PREP_REFERENCES_PROCESS', 'WARNING', process.id, node.id,
            `${label} uses a prepared component. Preparation flows are independent: only main steps use their outputs.`)
        }
      })

      stepRefsOf(fields).forEach((stepId) => {
        const source = nodesById.get(stepId)
        if (!source || source.kind !== 'STEP' || source.id === node.id || !expectedOutputOf(source)) {
          add('REF_DANGLING', 'BLOCKER', process.id, node.id, `${label} uses the output of a step that no longer provides one.`)
        }
      })

      const unbacked = typeof fields.actionDescription === 'string'
        ? unbackedAnnotations(fields.actionDescription, {
          ingredient: ingredientsOf(fields).map(ingredientAnnotationId),
          prepared: processRefsOf(fields).map(preparedAnnotationId),
          step: stepRefsOf(fields),
        })
        : []
      if (unbacked.length > 0) {
        add('ANNOTATION_DANGLING', 'WARNING', process.id, node.id,
          `${label} mentions ${unbacked.map((annotation) => `“${annotation.label}”`).join(', ')}, which the step no longer uses.`)
      }

      ingredientsOf(fields).forEach((ingredient) => {
        if (asText(ingredient.ingredientId) === CUSTOM_ID) {
          const ingredientName = asText(ingredient.customIngredientName)
          const key = ingredientName.toLowerCase()
          if (!customIngredients.has(key)) {
            customIngredients.add(key)
            add('CUSTOM_INGREDIENT', 'WARNING', process.id, node.id,
              `“${ingredientName || 'Unnamed ingredient'}” isn't in the ingredient catalog; it will need matching to a shop item when ordering.`)
          }
        }
        if (asText(ingredient.unit) === CUSTOM_ID) {
          const unitName = asText(ingredient.customUnit)
          const key = unitName.toLowerCase()
          if (!customUnits.has(key)) {
            customUnits.add(key)
            add('CUSTOM_UNIT', 'WARNING', process.id, node.id,
              `The unit “${unitName || 'custom'}” can't be converted for ordering; give an amount in a standard unit when you order.`)
          }
        }
      })
    })
  })

  const blockers = issues.filter((issue) => issue.severity === 'BLOCKER')
  const warnings = issues.filter((issue) => issue.severity === 'WARNING')
  return { issues, blockers, warnings, ready: blockers.length === 0 }
}
