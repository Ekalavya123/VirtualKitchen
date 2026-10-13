import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { RecipeProcessVisualizationStepResult } from '../../../../types/recipe'
import { errorMessage } from '../../../../shared/errors'
import { isTrackedJobActive, visualsJobKey } from '../../jobs/jobTracker'
import { useJobTracker, useTrackedJob } from '../../jobs/useJobTracker'

export type GenerateVisualsStatus = { type: 'success' | 'error' | 'info'; text: string }

/**
 * "Generate Visuals" for one process: starts (or joins the already-running) per-step visualization job —
 * one generated image per STEP node — and hands each step's result to `applyStep` as soon as it shows up,
 * including, on (re)mount, every step a job already finished while this process wasn't open. The job itself
 * is followed by the app-level JobTracker (keyed by process), so it keeps running, and its progress stays
 * visible, across process switches, page navigation and reloads; only the start request is local.
 */
export function useProcessVisuals({ recipeId, processId, stepCount, applyStep }: {
  recipeId: number
  processId: number
  stepCount: number
  applyStep: (step: RecipeProcessVisualizationStepResult) => void
}) {
  const jobTracker = useJobTracker()
  const visualsJob = useTrackedJob(visualsJobKey(processId))
  const [starting, setStarting] = useState(false)
  const generating = starting || isTrackedJobActive(visualsJob)
  const [message, setMessage] = useState<GenerateVisualsStatus | null>(null)

  // Guards `generate` against setState-after-unmount — the canvas remounts fresh per process, so a start
  // request still in flight when switching processes must not touch this instance's state once it's gone.
  // Reset on every (re-)mount rather than declared once: StrictMode's dev-only mount->cleanup->mount cycle
  // would otherwise latch this to true even though the component is actually mounted.
  const unmountedRef = useRef(false)
  useEffect(() => {
    unmountedRef.current = false
    return () => {
      unmountedRef.current = true
    }
  }, [])

  const generate = useCallback(async () => {
    if (generating) return
    if (stepCount === 0) {
      setMessage({ type: 'error', text: 'No steps to visualize' })
      return
    }
    setMessage(null)
    setStarting(true)
    try {
      await jobTracker.startVisuals(recipeId, processId)
    } catch (error) {
      if (!unmountedRef.current) {
        setMessage({ type: 'error', text: errorMessage(error, 'Unable to generate visuals right now.') })
      }
    } finally {
      if (!unmountedRef.current) setStarting(false)
    }
  }, [generating, stepCount, jobTracker, recipeId, processId])

  const appliedStepsRef = useRef(new Set<string>())
  useEffect(() => {
    if (!visualsJob) return
    for (const step of visualsJob.job.steps) {
      const appliedKey = `${visualsJob.job.jobId}:${step.stepId}`
      if (appliedStepsRef.current.has(appliedKey)) continue
      applyStep(step)
      appliedStepsRef.current.add(appliedKey)
    }
  }, [visualsJob, applyStep])

  const status = useMemo<GenerateVisualsStatus | null>(() => {
    if (message) return message
    if (!visualsJob) return null
    const { job } = visualsJob
    if (visualsJob.pollError) return { type: 'error', text: `Lost track of visuals generation: ${visualsJob.pollError}` }
    if (isTrackedJobActive(visualsJob)) {
      const progress = `Generating visuals… ${job.completedSteps}/${job.totalSteps} steps`
      return { type: 'info', text: visualsJob.connectionLost ? `${progress} (connection lost, retrying…)` : progress }
    }
    const successCount = job.steps.filter((step) => step.success).length
    if (job.status === 'COMPLETED') return { type: 'success', text: `Generated visuals for all ${job.totalSteps} steps` }
    if (job.status === 'COMPLETED_WITH_ERRORS') return { type: 'error', text: `Generated ${successCount}/${job.totalSteps} steps — ${job.totalSteps - successCount} failed` }
    return { type: 'error', text: job.errorMessage || 'Unable to generate visuals right now.' }
  }, [message, visualsJob])

  const progressLabel = visualsJob && isTrackedJobActive(visualsJob)
    ? `Generating… ${visualsJob.job.completedSteps}/${visualsJob.job.totalSteps}`
    : undefined

  return { generating, generate, status, progressLabel }
}
