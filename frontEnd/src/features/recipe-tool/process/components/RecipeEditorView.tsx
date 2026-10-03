import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import '../../styles/recipe-tool.css'
import { ProcessApi, RecipeProcessGenerationApi, RecipeDetailApi } from '../../../../api'
import type { RecipeProcessBreadcrumbEntry } from '../../../../types/recipe'
import { useNotifications } from '../../../../shared/components/notifications/NotificationProvider'
import RecipeProcessEditor from './RecipeProcessEditor'
import RecipeProcessSidebar from './RecipeProcessSidebar'
import RecipeProcessGenerationModal from './RecipeProcessGenerationModal'
import { useRecipeSession } from '../../context/RecipeSessionContext'
import { convertGeneratedResultToProcesses } from '../adapters/recipeProcessGenerationConverter'
import { generationJobKey, isTrackedJobActive } from '../../context/jobTracker'
import { useJobTracker, useTrackedJob } from '../../context/useJobTracker'

const GENERATION_STAGE_LABELS: Record<string, string> = {
  QUEUED: 'Queued…',
  BUILDING_PROMPT: 'Reading your recipe…',
  CALLING_MODEL: 'Asking the AI to structure it…',
  VALIDATING_RESPONSE: 'Checking the result…',
  RETRYING: 'Retrying with feedback…',
  COMPLETED: 'Done',
}


type RecipeEditorViewProps = {
  recipeId: number
  isOwner: boolean
  /** Bumped whenever the MAIN process is created/changes here, so RecipeToolPage's own recipe state (mainProcessId) stays in sync without a second fetch. */
  onMainProcessChanged: (mainProcessId: number) => void
}

/**
 * The Recipe Tool's "Recipe Editor" tab: a process list (MAIN pinned above
 * SUBPROCESSes) alongside the actual Recipe Process canvas for whichever
 * process is selected — all inside one tab, no route change on selection or
 * on opening a subprocess. Every process's data comes from the shared
 * RecipeSessionContext (loaded once by RecipeToolPage) rather than a fetch
 * of its own — "selected process" here is purely a *view* concern (which
 * already-loaded process the canvas currently displays), not a data-loading
 * concern, which is why RecipeProcessEditor/RecipeProcessCanvas below is never
 * remounted (no `key`) when the selection changes: switching processes is
 * navigation within one recipe editing session, not opening a new flow.
 */
export default function RecipeEditorView({ recipeId, isOwner, onMainProcessChanged }: RecipeEditorViewProps) {
  const { notifyError, notifySuccess } = useNotifications()
  const session = useRecipeSession()
  const processes = useMemo(() => session?.getProcesses() ?? [], [session])
  const [selectedProcessId, setSelectedProcessId] = useState<number | null>(null)
  const [ancestorTrail, setAncestorTrail] = useState<RecipeProcessBreadcrumbEntry[]>([])
  const [creatingMainProcess, setCreatingMainProcess] = useState(false)
  const [showGenerationModal, setShowGenerationModal] = useState(false)
  // AI generation state lives in the app-level JobTracker (keyed by recipe), so it survives
  // leaving the recipe tool and reloads; only the start request itself is local.
  const jobTracker = useJobTracker()
  const generationJob = useTrackedJob(generationJobKey(recipeId))
  const [startingGeneration, setStartingGeneration] = useState(false)
  const generating = startingGeneration || isTrackedJobActive(generationJob)
  const generationProgress = useMemo(() => {
    if (startingGeneration && !isTrackedJobActive(generationJob)) return { percent: 0, stageLabel: GENERATION_STAGE_LABELS.QUEUED }
    if (!generationJob || !isTrackedJobActive(generationJob)) return null
    const { job } = generationJob
    const stageLabel = GENERATION_STAGE_LABELS[job.stage] ?? job.stage
    return { percent: job.progressPercent, stageLabel: generationJob.connectionLost ? `${stageLabel} (connection lost, retrying…)` : stageLabel }
  }, [startingGeneration, generationJob])
  const generationError = generationJob && !isTrackedJobActive(generationJob)
    ? (generationJob.pollError ?? (generationJob.job.status === 'FAILED' ? (generationJob.job.errorMessage || 'Unable to generate this recipe right now.') : null))
    : null
  const [editorRevision, setEditorRevision] = useState(0)

  // Defaults the selection to MAIN (or the first process) once the session has loaded, and keeps
  // it pinned to whatever's currently selected otherwise — including across every later session
  // mutation (edits, saves), since this only actually changes state when the current selection no
  // longer resolves (e.g. it was just deleted).
  useEffect(() => {
    if (!session || session.loading) return
    setSelectedProcessId((current) => {
      if (current != null && session.getProcess(current)) return current
      const list = session.getProcesses()
      const main = list.find((process) => process.type === 'MAIN')
      return main ? main.id : (list[0]?.id ?? null)
    })
  }, [session])

  const handleCreateMainProcess = useCallback(async () => {
    if (!session) return
    setCreatingMainProcess(true)
    try {
      const created = await RecipeDetailApi.createMainProcess(recipeId)
      session.addProcess(created)
      setSelectedProcessId(created.id)
      onMainProcessChanged(created.id)
      notifySuccess('Main process created')
    } catch (error) {
      notifyError(error instanceof Error ? error.message : 'Unable to create the main process')
    } finally {
      setCreatingMainProcess(false)
    }
  }, [recipeId, session, onMainProcessChanged, notifySuccess, notifyError])

  const handleCreateSubprocess = useCallback(async (name: string, description: string) => {
    if (!session) return
    const created = await ProcessApi.create(recipeId, { type: 'SUBPROCESS', name, description: description || undefined })
    session.addProcess(created)
    setSelectedProcessId(created.id)
    notifySuccess('Subprocess created')
  }, [recipeId, session, notifySuccess])

  const existingMain = processes.find((process) => process.type === 'MAIN') ?? null

  /**
   * Starts (or joins the already-running) AI generation of a MAIN process (+ subprocesses where
   * meaningful) from free-form recipe text. The job runs in the background and is followed by the
   * app-level JobTracker, so the user can close the modal, switch processes or leave the tool; the
   * effect below loads the result once it's ready, whenever that is.
   */
  const runGeneration = useCallback(async (recipeText: string) => {
    if (generating) return
    setStartingGeneration(true)
    try {
      await jobTracker.startGeneration(recipeId, recipeText)
    } finally {
      setStartingGeneration(false)
    }
  }, [generating, jobTracker, recipeId])

  /**
   * Loads a completed generation's result straight into the current in-memory Recipe session —
   * never persisted until the user explicitly Saves (see RecipeSessionContext.saveAll, which creates
   * any still-pending generated process for real at that point). Replaces the recipe's existing
   * MAIN content in place (same real id, so its own unsaved edits are what get overwritten,
   * deliberately, since the user confirmed this in the modal) rather than adding a second MAIN;
   * every generated subprocess is new and simply added alongside whatever subprocesses already
   * existed. Also covers a job that finished while the user was away (rediscovered on load).
   */
  const appliedGenerationJobIdsRef = useRef(new Set<string>())
  useEffect(() => {
    if (!session || session.loading || !generationJob) return
    const { job } = generationJob
    if (job.status !== 'COMPLETED' || !job.result || appliedGenerationJobIdsRef.current.has(job.jobId)) return
    appliedGenerationJobIdsRef.current.add(job.jobId)

    const { processes: generated, mainProcessId } = convertGeneratedResultToProcesses(
      recipeId, job.result, existingMain, session.getProcesses().map((process) => process.id))

    generated.forEach((process) => session.addProcess(process))
    setAncestorTrail([])
    setSelectedProcessId(mainProcessId)
    // Re-seed React Flow when generation replaces an existing MAIN process without changing its
    // id. Normal edits must stay mounted so their local selection and history are preserved.
    setEditorRevision((revision) => revision + 1)
    if (!existingMain) onMainProcessChanged(mainProcessId)

    setShowGenerationModal(false)
    jobTracker.dismiss(generationJobKey(recipeId))
    RecipeProcessGenerationApi.markApplied(recipeId, job.jobId)
      .catch((error) => console.error('Unable to mark the generated recipe as applied:', error))
    notifySuccess('AI recipe process ready — review it, then Save when you\'re happy with it.')
  }, [session, generationJob, recipeId, existingMain, onMainProcessChanged, jobTracker, notifySuccess])

  const handleSelectFromSidebar = useCallback((processId: number) => {
    setAncestorTrail([])
    setSelectedProcessId(processId)
  }, [])

  const handleNavigateToList = useCallback(() => {
    setAncestorTrail([])
    setSelectedProcessId((current) => {
      const main = processes.find((process) => process.type === 'MAIN')
      return main ? main.id : current
    })
  }, [processes])

  const handleNavigateToAncestor = useCallback((index: number) => {
    setAncestorTrail((trail) => {
      const target = trail[index]
      if (target) setSelectedProcessId(target.processId)
      return trail.slice(0, index)
    })
  }, [])

  const handleOpenSubprocess = useCallback((subprocessId: number, currentProcessName: string) => {
    setSelectedProcessId((current) => {
      if (current != null) {
        setAncestorTrail((trail) => [...trail, { processId: current, name: currentProcessName }])
      }
      return subprocessId
    })
  }, [])

  if (!session || session.loading) {
    return (
      <div className="flex h-full w-full items-center justify-center" style={{ color: 'var(--flow-text-muted)' }}>
        Loading processes…
      </div>
    )
  }

  if (session.loadError) {
    return (
      <div className="flex h-full w-full items-center justify-center" style={{ color: '#9f1239' }}>
        {session.loadError}
      </div>
    )
  }

  const processListSidebar = (
    <RecipeProcessSidebar
      processes={processes}
      selectedProcessId={selectedProcessId}
      isOwner={isOwner}
      onSelect={handleSelectFromSidebar}
      onCreateMainProcess={() => void handleCreateMainProcess()}
      creatingMainProcess={creatingMainProcess}
      onCreateSubprocess={handleCreateSubprocess}
      onOpenGenerate={isOwner ? () => setShowGenerationModal(true) : undefined}
      generationProgress={generationProgress}
    />
  )

  const generationModal = showGenerationModal && (
    <RecipeProcessGenerationModal
      onClose={() => setShowGenerationModal(false)}
      onGenerate={runGeneration}
      isGenerating={generating}
      progress={generationProgress}
      jobError={generationError}
      willReplaceMain={existingMain != null}
    />
  )

  // Normally RecipeProcessEditor/RecipeProcessCanvas hosts the process list itself (as `sidebarHeader`, split
  // 50/50 with Tool Options in one combined column — see RecipeProcessCanvas.tsx). Without a process to
  // open yet, there's no RecipeProcessCanvas to host it in, so this is the one place the list still needs
  // its own column.
  if (selectedProcessId == null) {
    return (
      <div className="flex h-full w-full">
        <div style={{ width: 260, flexShrink: 0, borderRight: '1px solid var(--flow-border)', overflow: 'hidden' }}>
          {processListSidebar}
        </div>
        <div className="flex min-w-0 flex-1 flex-col items-center justify-center gap-3" style={{ color: 'var(--flow-text-muted)', fontSize: 13 }}>
          <div>{isOwner ? 'Create a main process to get started.' : 'This recipe has no processes yet.'}</div>
          {isOwner && (
            <button
              type="button"
              onClick={() => setShowGenerationModal(true)}
              style={{ padding: '8px 16px', borderRadius: 8, border: '1px solid var(--flow-accent)', background: 'var(--flow-accent)', color: 'white', fontSize: 12.5, fontWeight: 700, cursor: 'pointer' }}
            >
              {generationProgress ? `✨ Generating… ${generationProgress.percent}%` : '✨ Generate with AI'}
            </button>
          )}
        </div>
        {generationModal}
      </div>
    )
  }

  return (
    <>
      <RecipeProcessEditor
        key={`${selectedProcessId}-${editorRevision}`}
        recipeId={recipeId}
        processId={selectedProcessId}
        breadcrumbAncestors={ancestorTrail}
        onNavigateToList={handleNavigateToList}
        onNavigateToAncestor={handleNavigateToAncestor}
        onOpenSubprocess={handleOpenSubprocess}
        sidebarHeader={processListSidebar}
      />
      {generationModal}
    </>
  )
}
