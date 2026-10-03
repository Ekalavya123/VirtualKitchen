/**
 * Pure helpers for swapping a whole snapshot into the Recipe session — from undo/redo history or
 * from a local recovery snapshot — without losing things that were never user edits.
 */

import type { Process, ProcessNode } from '../../../types/process'

/** STEP data keys written by Generate Visuals (nested by the editor, flat by the backend job). */
const VISUALIZATION_KEYS = ['visualization', 'visualizationAssetId', 'imagePrompt', 'imageUrl'] as const

const carryVisualization = (restored: ProcessNode, current: ProcessNode | undefined): ProcessNode => {
  if (!current?.data || restored.kind !== 'STEP') return restored
  const carried: Record<string, unknown> = {}
  for (const key of VISUALIZATION_KEYS) {
    if (current.data[key] !== undefined && current.data[key] !== restored.data?.[key]) carried[key] = current.data[key]
  }
  return Object.keys(carried).length === 0 ? restored : { ...restored, data: { ...restored.data, ...carried } }
}

/**
 * The state to install when undo/redo restores `snapshot` over `current`: the snapshot's content,
 * except that each process keeps its current viewport (panning isn't an undoable edit) and each
 * STEP keeps images generated since (the visuals job already stored them on the backend; undo
 * must not quietly delete them again).
 */
export const restoreSnapshot = (snapshot: Process[], current: Process[]): Process[] => {
  const currentById = new Map(current.map((process) => [process.id, process]))
  return snapshot.map((process) => {
    const live = currentById.get(process.id)
    if (!live || live === process) return process
    const liveNodes = new Map(live.nodes.map((node) => [node.id, node]))
    let nodesChanged = false
    const nodes = process.nodes.map((node) => {
      const next = carryVisualization(node, liveNodes.get(node.id))
      if (next !== node) nodesChanged = true
      return next
    })
    const viewportChanged = live.viewport !== process.viewport
    if (!nodesChanged && !viewportChanged) return process
    return { ...process, nodes: nodesChanged ? nodes : process.nodes, viewport: live.viewport }
  })
}

/**
 * The session to install when the user recovers unsaved local changes: the recovered processes,
 * plus any backend process the recovery never knew about (created elsewhere after it was written)
 * — those must not be deleted just because the recovered snapshot lacks them. Processes the
 * recovery knew existed and deliberately removed stay removed.
 */
export const mergeRecoveredProcesses = (recovered: Process[], recoveredPersistedIds: number[], backend: Process[]): Process[] => {
  const known = new Set([...recoveredPersistedIds, ...recovered.map((process) => process.id)])
  return [...recovered, ...backend.filter((process) => !known.has(process.id))]
}
