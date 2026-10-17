/**
 * Recipes saved before the single Preparation may have several SUBPROCESSes. This folds them into one Preparation
 * process — each old subprocess becoming one (or more) of its flows — and rewrites every use of them:
 * a whole-subprocess use `{ processId: old }` becomes a use of that subprocess's output step
 * `{ processId: kept, stepId }`, whose Expected Output takes the old subprocess's name if it had none. Uses from
 * inside the preparation itself are dropped (flows are independent). Node ids are kept, so step images and
 * step-output references stay intact.
 *
 * Pure (raw node data) so it runs under node --test; RecipeEditorView applies the result as one undoable edit.
 */

import type { Process, ProcessNode } from '../../../types/process'
import {
  asRecord,
  getPreparationFlows,
  processRefsOf,
  stepFieldsOf,
  type PreparedReference,
} from './preparationFlows.ts'

export type PreparationMerge = {
  /** The one Preparation, keeping the id of the subprocess it was built on. */
  preparation: Process
  /** The other subprocesses, now part of `preparation`. */
  removedIds: number[]
  /** Other processes (MAIN) whose uses were rewritten. */
  changed: Process[]
  /** Uses that couldn't be kept: from inside the preparation, or of a subprocess without any step. */
  droppedReferences: number
}

export const PREPARATION_NAME = 'Preparation'

const DEFAULT_SIZE = { width: 280, height: 160 }
const GRID = { columns: 3, gap: 200 }

const withStepFields = (node: ProcessNode, patch: (fields: Record<string, unknown>) => Record<string, unknown>): ProcessNode => {
  const data = asRecord(node.data)
  if (data.step && typeof data.step === 'object') return { ...node, data: { ...data, step: patch(asRecord(data.step)) } }
  return { ...node, data: patch(data) }
}

const withProcessRefs = (node: ProcessNode, references: PreparedReference[]) => withStepFields(node, (fields) => ({
  ...fields,
  actionOn: {
    ...asRecord(fields.actionOn),
    processes: references.map(({ processId, stepId }) => (stepId ? { processId, stepId } : { processId })),
  },
}))

type Box = { minX: number; minY: number; width: number; height: number }

const boundsOf = (nodes: ProcessNode[]): Box => {
  if (nodes.length === 0) return { minX: 0, minY: 0, width: 0, height: 0 }
  const rects = nodes.map((node) => ({
    x: node.position?.x ?? 0,
    y: node.position?.y ?? 0,
    width: node.width ?? node.measured?.width ?? DEFAULT_SIZE.width,
    height: node.height ?? node.measured?.height ?? DEFAULT_SIZE.height,
  }))
  const minX = Math.min(...rects.map((rect) => rect.x))
  const minY = Math.min(...rects.map((rect) => rect.y))
  return {
    minX,
    minY,
    width: Math.max(...rects.map((rect) => rect.x + rect.width)) - minX,
    height: Math.max(...rects.map((rect) => rect.y + rect.height)) - minY,
  }
}

/** Null when the recipe already has at most one SUBPROCESS. */
export const mergePreparations = (processes: readonly Process[]): PreparationMerge | null => {
  const preparations = processes.filter((process) => process.type === 'SUBPROCESS')
  if (preparations.length <= 1) return null

  // Keep a saved process (the oldest) when there is one, so its id — and anything keyed by it — survives.
  const saved = preparations.filter((process) => process.id > 0).sort((a, b) => a.id - b.id)
  const kept = saved[0] ?? preparations[0]
  const preparationIds = new Set(preparations.map((process) => process.id))

  // The step a whole-subprocess use now stands for: the output of the subprocess's last flow.
  const outputStepOf = new Map<number, string | null>()
  const namesToFill = new Map<string, string>()
  preparations.forEach((process) => {
    const flows = getPreparationFlows(process).filter((flow) => flow.output != null)
    const flow = flows[flows.length - 1]
    if (!flow?.output) {
      outputStepOf.set(process.id, null)
      return
    }
    outputStepOf.set(process.id, flow.output.id)
    if (!flow.outputName && process.name.trim()) namesToFill.set(flow.output.id, process.name.trim())
  })

  let droppedReferences = 0
  const rewrite = (nodes: ProcessNode[], insidePreparation: boolean) => nodes.map((node) => {
    let next = node
    if (node.kind === 'STEP') {
      const references = processRefsOf(stepFieldsOf(node))
      if (references.some((reference) => preparationIds.has(reference.processId))) {
        const remaining: PreparedReference[] = []
        references.forEach((reference) => {
          if (!preparationIds.has(reference.processId)) {
            remaining.push(reference)
            return
          }
          const stepId = reference.stepId ?? outputStepOf.get(reference.processId)
          if (insidePreparation || !stepId) {
            droppedReferences += 1
            return
          }
          remaining.push({ processId: kept.id, stepId })
        })
        next = withProcessRefs(next, remaining)
      }
    }
    const name = namesToFill.get(node.id)
    if (name) next = withStepFields(next, (fields) => ({ ...fields, expectedOutput: name }))
    return next
  })

  // Each old subprocess keeps its own arrangement, placed in a grid of up to three columns (Auto Arrange tidies it).
  const boxes = preparations.map((process) => boundsOf(process.nodes))
  const nodes: ProcessNode[] = []
  let rowTop = 0
  for (let start = 0; start < preparations.length; start += GRID.columns) {
    const row = preparations.slice(start, start + GRID.columns)
    let left = 0
    let rowHeight = 0
    row.forEach((process, offset) => {
      const box = boxes[start + offset]
      process.nodes.forEach((node) => nodes.push({
        ...node,
        position: { x: (node.position?.x ?? 0) - box.minX + left, y: (node.position?.y ?? 0) - box.minY + rowTop },
      }))
      left += box.width + GRID.gap
      rowHeight = Math.max(rowHeight, box.height)
    })
    rowTop += rowHeight + GRID.gap
  }

  const preparation: Process = {
    ...kept,
    name: PREPARATION_NAME,
    nodes: rewrite(nodes, true),
    edges: preparations.flatMap((process) => process.edges),
  }
  const changed = processes
    .filter((process) => !preparationIds.has(process.id))
    .flatMap((process) => {
      const rewritten = rewrite(process.nodes, false)
      return rewritten.some((node, index) => node !== process.nodes[index]) ? [{ ...process, nodes: rewritten }] : []
    })

  return {
    preparation,
    removedIds: preparations.filter((process) => process.id !== kept.id).map((process) => process.id),
    changed,
    droppedReferences,
  }
}
