/**
 * Turning saved processes into a flow file and a flow back into a recipe, through the editor's own
 * save path — so a recipe built from a flow is exactly what saving it in the app would produce, with
 * no AI call.
 */

import type { ApiClient } from './api'
import type { Process } from '../../../frontEnd/src/types/process'
import { createPersistedState, persistSnapshot, remapProcessIds } from '../../../frontEnd/src/features/recipe-tool/session/persistence/snapshotPersistence'
import { remapActionOnProcessRefs } from '../../../frontEnd/src/features/recipe-tool/model/subprocessReferences'
import { FLOW_VERSION, stripVisuals, type FlowDoc, type FlowProcess } from './flowDoc.ts'
import { persistenceApi } from './stages/generate'

/**
 * Gives every process a temporary negative id (MAIN first) and rewrites the references between them
 * to match. A flow then never carries ids of the recipe it came from, and can't collide with the ids
 * of the recipe it is built into.
 */
function withTemporaryIds(processes: Process[]): Process[] {
  const ordered = [...processes].sort((a, b) => Number(b.type === 'MAIN') - Number(a.type === 'MAIN'))
  // Two passes, so a file whose ids are already -1, -2, … in another order can't map onto itself halfway.
  const parked = new Map(ordered.map((process, index) => [process.id, -1_000_000 - index]))
  const temporary = new Map(ordered.map((_, index) => [-1_000_000 - index, -(index + 1)]))
  return remapProcessIds(remapProcessIds(ordered, parked, remapActionOnProcessRefs), temporary, remapActionOnProcessRefs)
}

/** The flow of a recipe as it was saved: its processes without images, ids made temporary. */
export function toFlowDoc(
  recipe: string, recipeText: string, textModel: string | null, processes: Process[], warnings: string[], repairs: string[],
): FlowDoc {
  const portable = stripVisuals(withTemporaryIds(processes)).map((process): FlowProcess => ({
    id: process.id,
    type: process.type,
    name: process.name,
    description: process.description,
    nodes: process.nodes,
    edges: process.edges,
    viewport: process.viewport,
  }))
  return {
    version: FLOW_VERSION, recipe, recipeText, textModel, generatedAt: new Date().toISOString(),
    warnings, repairs, processes: portable,
  }
}

type RecipeDetail = { processRevision?: number | null }

/**
 * Builds the flow into an existing (fresh) recipe and returns its saved processes, with real ids.
 * Step ids are kept, so the same step has the same id in every recipe built from one flow.
 */
export async function importFlow(api: ApiClient, recipeId: number, flow: FlowDoc): Promise<Process[]> {
  const processesPath = `/api/v1/recipes/${recipeId}/processes`
  const detail = await api.get<RecipeDetail>(`/api/v1/recipes/${recipeId}`)
  const existing = await api.get<Process[]>(processesPath)
  const snapshot = withTemporaryIds(flow.processes.map((process) => ({ ...process, recipeId }) as unknown as Process))
  const state = createPersistedState(existing, detail.processRevision ?? 0)
  await persistSnapshot(snapshot, state, persistenceApi(api, recipeId), remapActionOnProcessRefs, () => {})
  return api.get<Process[]>(processesPath)
}

/** Creates a recipe in the app from a flow file. */
export async function createRecipeFromFlow(api: ApiClient, flow: FlowDoc, name: string): Promise<{ recipeId: number; processes: Process[] }> {
  const created = await api.post<{ id: number }>('/api/v1/process-templates', {
    name: name.slice(0, 120),
    description: 'Imported by tools/ai-bench',
  })
  return { recipeId: created.id, processes: await importFlow(api, created.id, flow) }
}
