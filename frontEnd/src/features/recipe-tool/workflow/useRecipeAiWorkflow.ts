import { useCallback, useState } from 'react'
import { httpStatusOf } from '../../../api'
import { RecipeAiWorkflowApi } from '../../../api/recipeAiWorkflowApi'
import type { RecipeAiTaskType, RecipeAiWorkflowCreateRequest } from '../../../types/recipeAiWorkflow'
import { useRecipeSession } from '../context/RecipeSessionContext'
import { workflowJobKey } from '../context/jobTracker'
import { useJobTracker, useTrackedJob } from '../context/useJobTracker'

export type WorkflowAction = 'start' | 'approve' | 'retry' | 'discard' | 'dismiss'

/**
 * AI Recipe Creation for one recipe: the workflow's live state (followed by the app-level
 * JobTracker, so it survives closing the modal, navigating and reloading) plus its actions.
 *
 * Approval is where the editor and the workflow meet: the session is saved first, and the revision
 * that save produced goes with the approval, so the visuals and narration are created from exactly
 * the recipe process the user approved — never from the original AI output or an older save.
 */
export function useRecipeAiWorkflow(recipeId: number) {
  const tracker = useJobTracker()
  const session = useRecipeSession()
  const tracked = useTrackedJob(workflowJobKey(recipeId))
  const [busy, setBusy] = useState<WorkflowAction | null>(null)
  const [error, setError] = useState<string | null>(null)

  const run = useCallback(async (action: WorkflowAction, work: () => Promise<void>) => {
    setBusy(action)
    setError(null)
    try {
      await work()
      return true
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Something went wrong. Please try again.')
      return false
    } finally {
      setBusy(null)
    }
  }, [])

  const start = useCallback((request: RecipeAiWorkflowCreateRequest) => run('start', async () => {
    await tracker.startWorkflow(recipeId, request)
  }), [run, tracker, recipeId])

  const approve = useCallback(() => run('approve', async () => {
    const workflow = tracked?.job
    if (!workflow || !session) return
    // Approval is a decision about what the user sees now: make sure that is what the backend holds.
    const saved = await session.saveNow()
    if (!saved.ok) {
      throw new Error(`Your latest changes couldn't be saved, so nothing was started. ${saved.error ?? 'Please try again.'}`)
    }
    try {
      tracker.applyWorkflow(recipeId, await RecipeAiWorkflowApi.approve(recipeId, workflow.workflowId, session.getPersistedRevision()))
    } catch (caught) {
      if (httpStatusOf(caught) === 409) {
        // Typically an edit that saved in between: the user's next click approves that version.
        tracker.refresh(workflowJobKey(recipeId))
        throw new Error(caught instanceof Error ? caught.message : 'The recipe changed while approving. Please approve again.', { cause: caught })
      }
      throw caught
    }
  }), [run, tracked, session, tracker, recipeId])

  const retry = useCallback((task: RecipeAiTaskType) => run('retry', async () => {
    const workflow = tracked?.job
    if (!workflow) return
    tracker.applyWorkflow(recipeId, await RecipeAiWorkflowApi.retryTask(recipeId, workflow.workflowId, task))
  }), [run, tracked, tracker, recipeId])

  const discard = useCallback(() => run('discard', async () => {
    const workflow = tracked?.job
    if (!workflow) return
    await RecipeAiWorkflowApi.discard(recipeId, workflow.workflowId)
    tracker.dismiss(workflowJobKey(recipeId))
  }), [run, tracked, tracker, recipeId])

  /** Closes a finished workflow's summary for good (it isn't offered again on reload). */
  const dismiss = useCallback(() => run('dismiss', async () => {
    const workflow = tracked?.job
    if (!workflow) return
    tracker.dismiss(workflowJobKey(recipeId))
    await RecipeAiWorkflowApi.dismiss(recipeId, workflow.workflowId)
  }), [run, tracked, tracker, recipeId])

  return {
    workflow: tracked?.job ?? null,
    connectionLost: tracked?.connectionLost ?? false,
    pollError: tracked?.pollError ?? null,
    busy,
    error,
    clearError: () => setError(null),
    start,
    approve,
    retry,
    discard,
    dismiss,
  }
}
