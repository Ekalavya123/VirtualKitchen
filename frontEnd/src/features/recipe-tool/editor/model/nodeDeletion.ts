import type { Edge, Node } from '@xyflow/react'
import type { ConfirmOptions } from '../../../../shared/components/confirm/confirmContext'
import { normalizeConditionNodeData } from '../../model/recipeConditionData'
import { isRecipeStepNode } from '../../model/recipeNodeTypes'
import { normalizeRecipeStepNodeData } from '../../model/recipeStepData'

/**
 * The confirmation shown before deleting a node, saying what goes with it: its connections and, for a
 * step, the references other steps make to its output. Null when the node isn't on the canvas.
 */
export const describeNodeDeletion = (nodes: Node[], edges: Edge[], id: string): ConfirmOptions | null => {
  const node = nodes.find((candidate) => candidate.id === id)
  if (!node) return null
  const connections = edges.filter((edge) => edge.source === id || edge.target === id).length
  const connectionsText = connections > 0 ? ` and its ${connections} connection${connections === 1 ? '' : 's'}` : ''

  if (isRecipeStepNode(node)) {
    const stepNumber = nodes.filter(isRecipeStepNode).indexOf(node) + 1
    const title = normalizeRecipeStepNodeData(node.data).title || 'Untitled step'
    // Other steps using this step's output lose that reference (see the canvas's deleteNode).
    const usedBy = nodes.filter((other) => other.id !== id && isRecipeStepNode(other)
      && normalizeRecipeStepNodeData(other.data).step.actionOn.steps.some((entry) => entry.stepId === id)).length
    return {
      eyebrow: 'Delete step',
      title: `Delete step ${stepNumber} · ${title}?`,
      message: `This step${connectionsText} will be removed from the process, with its Action On, settings and visualization.`
        + (usedBy > 0 ? ` ${usedBy} other step${usedBy === 1 ? ' uses' : 's use'} its output — that reference will be removed too.` : '')
        + ' You can Undo this.',
      confirmLabel: 'Delete step',
    }
  }

  const title = normalizeConditionNodeData(node.data).title || 'Untitled condition'
  return {
    eyebrow: 'Delete condition',
    title: `Delete condition “${title}”?`,
    message: `This condition${connectionsText} (its Yes/No branches) will be removed from the process. You can Undo this.`,
    confirmLabel: 'Delete condition',
  }
}
