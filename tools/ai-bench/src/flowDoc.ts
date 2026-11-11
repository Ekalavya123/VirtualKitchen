/**
 * A recipe's flow as a file: the processes a text model generated for one recipe text, without any
 * images or audio. Written by every run that generates a flow; a recipe can be rebuilt from it with
 * no AI call (`bench import`, or a `"flow"` recipe entry in bench.json).
 *
 * Kept free of frontend imports so it can be read and tested on its own.
 */

import { readFileSync } from 'node:fs'

export const FLOW_VERSION = 1

/** The saved shape of one process; `id` is a temporary negative id, references between processes use it. */
export type FlowProcess = {
  id: number
  type: string
  name: string
  description?: string
  nodes: { id: string; kind?: string; data?: Record<string, unknown>; [key: string]: unknown }[]
  edges: unknown[]
  viewport?: unknown
  [key: string]: unknown
}

export type FlowDoc = {
  version: number
  /** The bench recipe id the flow was generated for. */
  recipe: string
  recipeText: string
  /** The model that generated it, or null when it is not known (a hand-made file). */
  textModel: string | null
  generatedAt: string
  /** The generation's warnings and repairs, kept so reused runs report the same quality signals. */
  warnings: string[]
  repairs: string[]
  processes: FlowProcess[]
}

/** Where a step's generated image lives on its node data: the editor's nested shape and the backend's flat one. */
export const VISUALIZATION_KEYS = ['visualization', 'visualizationAssetId', 'imagePrompt', 'imageUrl']

/** A copy of the processes with every generated image removed, so a recipe built from them starts unillustrated. */
export function stripVisuals<T extends { nodes: { data?: Record<string, unknown> }[] }>(processes: T[]): T[] {
  return processes.map((process) => ({
    ...process,
    nodes: process.nodes.map((node) => {
      if (!node.data || !VISUALIZATION_KEYS.some((key) => key in node.data!)) return node
      const data = { ...node.data }
      for (const key of VISUALIZATION_KEYS) delete data[key]
      return { ...node, data }
    }),
  }))
}

const fail = (where: string, message: string): never => {
  throw new Error(`flow file ${where}: ${message}`)
}

/** Checks a parsed flow file's shape; throws with the file's name on anything unusable. */
export function parseFlowDoc(raw: unknown, where: string): FlowDoc {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) fail(where, 'is not a JSON object')
  const value = raw as Record<string, unknown>
  if (value.version !== FLOW_VERSION) fail(where, `has version ${JSON.stringify(value.version)}, this bench reads version ${FLOW_VERSION}`)
  if (!Array.isArray(value.processes) || value.processes.length === 0) fail(where, 'has no "processes"')
  const processes = value.processes as FlowProcess[]
  processes.forEach((process, index) => {
    if (!process || typeof process !== 'object') fail(where, `processes[${index}] is not an object`)
    if (typeof process.id !== 'number') fail(where, `processes[${index}].id must be a number`)
    if (process.type !== 'MAIN' && process.type !== 'SUBPROCESS') fail(where, `processes[${index}].type must be MAIN or SUBPROCESS`)
    if (!Array.isArray(process.nodes) || !Array.isArray(process.edges)) fail(where, `processes[${index}] needs "nodes" and "edges"`)
  })
  if (processes.filter((process) => process.type === 'MAIN').length !== 1) fail(where, 'must have exactly one MAIN process')
  const strings = (field: unknown) => (Array.isArray(field) ? field.filter((entry): entry is string => typeof entry === 'string') : [])
  return {
    version: FLOW_VERSION,
    recipe: typeof value.recipe === 'string' ? value.recipe : '',
    recipeText: typeof value.recipeText === 'string' ? value.recipeText : '',
    textModel: typeof value.textModel === 'string' && value.textModel ? value.textModel : null,
    generatedAt: typeof value.generatedAt === 'string' ? value.generatedAt : '',
    warnings: strings(value.warnings),
    repairs: strings(value.repairs),
    // Images never travel with a flow, even if a file was edited by hand to carry some.
    processes: stripVisuals(processes),
  }
}

export function readFlowDoc(file: string): FlowDoc {
  let raw: unknown
  try {
    raw = JSON.parse(readFileSync(file, 'utf8'))
  } catch (error) {
    return fail(file, `cannot be read: ${(error as Error).message}`)
  }
  return parseFlowDoc(raw, file)
}
