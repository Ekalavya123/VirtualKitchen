import type { RecipeAiTaskSelection, RecipeAiTaskType } from '../../../../types/recipeAiWorkflow'

/**
 * "What would you like AI to create?" — which tasks are ticked. "Complete Recipe Experience" is not a
 * task of its own: it is ticked exactly when every task is, ticking it selects them all and unticking
 * it clears them, so it can never add a task twice.
 */
export type WorkflowTaskSelection = Record<RecipeAiTaskType, boolean>

/** Display and dependency order (the process first; visuals and narration follow from it). */
export const WORKFLOW_TASK_ORDER: readonly RecipeAiTaskType[] = ['PROCESS', 'VISUALS', 'NARRATION']

export const createWorkflowTaskSelection = (selectAll = true): WorkflowTaskSelection => ({
  PROCESS: selectAll,
  VISUALS: selectAll,
  NARRATION: selectAll,
})

export const isCompleteExperience = (selection: WorkflowTaskSelection) =>
  WORKFLOW_TASK_ORDER.every((task) => selection[task])

export const toggleWorkflowTask = (selection: WorkflowTaskSelection, task: RecipeAiTaskType): WorkflowTaskSelection => ({
  ...selection,
  [task]: !selection[task],
})

export const setCompleteExperience = (_selection: WorkflowTaskSelection, checked: boolean): WorkflowTaskSelection =>
  createWorkflowTaskSelection(checked)

/** The ticked tasks, each once, in {@link WORKFLOW_TASK_ORDER}. */
export const selectedWorkflowTasks = (selection: WorkflowTaskSelection): RecipeAiTaskType[] =>
  WORKFLOW_TASK_ORDER.filter((task) => selection[task])

/** The request shape the backend expects (it normalises again, so duplicates are impossible either way). */
export const toTaskSelectionRequest = (selection: WorkflowTaskSelection): RecipeAiTaskSelection => ({
  tasks: selectedWorkflowTasks(selection),
  completeExperience: isCompleteExperience(selection),
})

/**
 * Why "Create Recipe" can't run yet, or null when it can. Visuals and narration on their own work on
 * the recipe's existing process, so they need one with steps; generating a process needs recipe text.
 */
export const workflowSelectionProblem = (
  selection: WorkflowTaskSelection,
  context: { recipeText: string; existingStepCount: number },
): string | null => {
  const tasks = selectedWorkflowTasks(selection)
  if (tasks.length === 0) return 'Select at least one thing for AI to create.'
  if (selection.PROCESS) {
    return context.recipeText.trim() ? null : 'Describe your recipe so AI can create its process.'
  }
  return context.existingStepCount > 0 ? null : 'This recipe has no steps yet. Select Recipe Process to create them first.'
}
