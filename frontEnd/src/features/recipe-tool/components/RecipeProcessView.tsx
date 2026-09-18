import { useCallback, useEffect, useMemo, useState } from 'react'
import '../../flow-editor/styles/flow-editor.css'
import { ProcessApi, ProcessGenerationApi, RecipeDetailApi } from '../../../api'
import type { ProcessBreadcrumbEntry, ProcessGenerationJobStatus } from '../../../types/process'
import { useNotifications } from '../../../shared/components/notifications/NotificationProvider'
import ProcessEditor from '../../flow-editor/components/process/ProcessEditor'
import ProcessListSidebar from './ProcessListSidebar'
import ProcessGenerationModal from './ProcessGenerationModal'
import { useRecipeSession } from '../context/RecipeSessionContext'
import { convertGeneratedResultToProcesses } from '../../flow-editor/adapters/processGenerationConverter'

const GENERATION_JOB_POLL_MS = 2000

const GENERATION_STAGE_LABELS: Record<string, string> = {
  QUEUED: 'Queued…',
  BUILDING_PROMPT: 'Reading your recipe…',
  CALLING_MODEL: 'Asking the AI to structure it…',
  VALIDATING_RESPONSE: 'Checking the result…',
  RETRYING: 'Retrying with feedback…',
  COMPLETED: 'Done',
}

const sleep = (ms: number) => new Promise<void>((resolve) => window.setTimeout(resolve, ms))

const isTerminalStatus = (status: ProcessGenerationJobStatus) => status === 'COMPLETED' || status === 'FAILED'

type RecipeProcessViewProps = {
  recipeId: number
  isOwner: boolean
  /** Bumped whenever the MAIN process is created/changes here, so RecipeToolPage's own recipe state (mainProcessId) stays in sync without a second fetch. */
  onMainProcessChanged: (mainProcessId: number) => void
}

/**
 * The Recipe Tool's "Recipe Process" tab: a process list (MAIN pinned above
 * SUBPROCESSes) alongside the actual Process Builder canvas for whichever
 * process is selected — all inside one tab, no route change on selection or
 * on opening a subprocess. Every process's data comes from the shared
 * RecipeSessionContext (loaded once by RecipeToolPage) rather than a fetch
 * of its own — "selected process" here is purely a *view* concern (which
 * already-loaded process the canvas currently displays), not a data-loading
 * concern, which is why ProcessEditor/ProcessCanvas below is never
 * remounted (no `key`) when the selection changes: switching processes is
 * navigation within one recipe editing session, not opening a new flow.
 */
export default function RecipeProcessView({ recipeId, isOwner, onMainProcessChanged }: RecipeProcessViewProps) {
  const { notifyError, notifySuccess } = useNotifications()
  const session = useRecipeSession()
  const processes = useMemo(() => session?.getProcesses() ?? [], [session])
  const [selectedProcessId, setSelectedProcessId] = useState<number | null>(null)
  const [ancestorTrail, setAncestorTrail] = useState<ProcessBreadcrumbEntry[]>([])
  const [creatingMainProcess, setCreatingMainProcess] = useState(false)
  const [showGenerationModal, setShowGenerationModal] = useState(false)
  const [generating, setGenerating] = useState(false)
  const [generationProgress, setGenerationProgress] = useState<{ percent: number; stageLabel: string } | null>(null)

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
   * Generates a MAIN process (+ subprocesses where meaningful) from free-form recipe text and
   * loads the result straight into the current in-memory Recipe session — never persisted until
   * the user explicitly Saves (see RecipeSessionContext.saveAll, which creates any still-pending
   * generated process for real at that point). Replaces the recipe's existing MAIN content in
   * place (same real id, so its own unsaved edits are what get overwritten, deliberately, since the
   * user just confirmed this in the modal) rather than adding a second MAIN; every generated
   * subprocess is new and simply added alongside whatever subprocesses already existed.
   */
  const runGeneration = useCallback(async (recipeText: string) => {
    if (!session) return
    setGenerating(true)
    setGenerationProgress({ percent: 0, stageLabel: GENERATION_STAGE_LABELS.QUEUED })
    try {
      const clientRequestId = crypto.randomUUID()
      let job = await ProcessGenerationApi.startJob(recipeId, { recipeText, clientRequestId })
      setGenerationProgress({ percent: job.progressPercent, stageLabel: GENERATION_STAGE_LABELS[job.stage] ?? job.stage })

      while (!isTerminalStatus(job.status)) {
        await sleep(GENERATION_JOB_POLL_MS)
        job = await ProcessGenerationApi.getJobStatus(recipeId, job.jobId)
        setGenerationProgress({ percent: job.progressPercent, stageLabel: GENERATION_STAGE_LABELS[job.stage] ?? job.stage })
      }

      if (job.status !== 'COMPLETED' || !job.result) {
        throw new Error(job.errorMessage || 'Unable to generate this recipe right now.')
      }

      const { processes: generated, mainProcessId } = convertGeneratedResultToProcesses(
        recipeId, job.result, existingMain, session.getProcesses().map((process) => process.id))

      generated.forEach((process) => session.addProcess(process))
      setAncestorTrail([])
      setSelectedProcessId(mainProcessId)
      if (!existingMain) onMainProcessChanged(mainProcessId)

      setShowGenerationModal(false)
      notifySuccess('Recipe generated — review it, then Save when you\'re happy with it.')
    } finally {
      setGenerating(false)
      setGenerationProgress(null)
    }
  }, [recipeId, session, existingMain, onMainProcessChanged, notifySuccess])

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
    <ProcessListSidebar
      processes={processes}
      selectedProcessId={selectedProcessId}
      isOwner={isOwner}
      onSelect={handleSelectFromSidebar}
      onCreateMainProcess={() => void handleCreateMainProcess()}
      creatingMainProcess={creatingMainProcess}
      onCreateSubprocess={handleCreateSubprocess}
      onOpenGenerate={isOwner ? () => setShowGenerationModal(true) : undefined}
    />
  )

  const generationModal = showGenerationModal && (
    <ProcessGenerationModal
      onClose={() => setShowGenerationModal(false)}
      onGenerate={runGeneration}
      isGenerating={generating}
      progress={generationProgress}
      willReplaceMain={existingMain != null}
    />
  )

  // Normally ProcessEditor/ProcessCanvas hosts the process list itself (as `sidebarHeader`, split
  // 50/50 with Tool Options in one combined column — see ProcessCanvas.tsx). Without a process to
  // open yet, there's no ProcessCanvas to host it in, so this is the one place the list still needs
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
              ✨ Generate with AI
            </button>
          )}
        </div>
        {generationModal}
      </div>
    )
  }

  return (
    <>
      <ProcessEditor
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
