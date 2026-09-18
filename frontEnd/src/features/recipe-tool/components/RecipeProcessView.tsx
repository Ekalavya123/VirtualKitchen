import { useCallback, useEffect, useState } from 'react'
import '../../flow-editor/styles/flow-editor.css'
import { ProcessApi, RecipeDetailApi } from '../../../api'
import type { Process, ProcessBreadcrumbEntry } from '../../../types/process'
import { useNotifications } from '../../../shared/components/notifications/NotificationProvider'
import ProcessEditor from '../../flow-editor/components/process/ProcessEditor'
import ProcessListSidebar from './ProcessListSidebar'

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
 * on opening a subprocess (a PROCESS node's double-click still navigates,
 * but by updating local state here instead of the URL). This reuses
 * ProcessEditor/ProcessCanvas exactly as built in Phase 4/5 — only how it's
 * hosted changes (local state instead of route params), not the canvas,
 * properties panel, or save logic themselves.
 */
export default function RecipeProcessView({ recipeId, isOwner, onMainProcessChanged }: RecipeProcessViewProps) {
  const { notifyError, notifySuccess } = useNotifications()
  const [processes, setProcesses] = useState<Process[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [selectedProcessId, setSelectedProcessId] = useState<number | null>(null)
  const [ancestorTrail, setAncestorTrail] = useState<ProcessBreadcrumbEntry[]>([])
  const [creatingMainProcess, setCreatingMainProcess] = useState(false)

  const loadProcesses = useCallback((preferredProcessId?: number) => {
    return ProcessApi.listByRecipe(recipeId)
      .then((list) => {
        setProcesses(list)
        setLoadError(null)
        setSelectedProcessId((current) => {
          if (preferredProcessId != null && list.some((process) => process.id === preferredProcessId)) return preferredProcessId
          if (current != null && list.some((process) => process.id === current)) return current
          const main = list.find((process) => process.type === 'MAIN')
          return main ? main.id : (list[0]?.id ?? null)
        })
        return list
      })
      .catch((error) => {
        setLoadError(error instanceof Error ? error.message : 'Unable to load this recipe\'s processes')
        return []
      })
  }, [recipeId])

  useEffect(() => {
    let cancelled = false
    // No sync setLoading(true)/setAncestorTrail([]) here — both already match their initial
    // useState values, and this effect's only dep (recipeId, via loadProcesses) is stable for the
    // lifetime of this component instance: RecipeToolPage stays mounted across tab switches and is
    // itself remounted (not updated in place) when the recipe changes.
    loadProcesses().finally(() => {
      if (!cancelled) setLoading(false)
    })
    return () => {
      cancelled = true
    }
  }, [loadProcesses])

  const handleCreateMainProcess = useCallback(async () => {
    setCreatingMainProcess(true)
    try {
      const created = await RecipeDetailApi.createMainProcess(recipeId)
      await loadProcesses(created.id)
      onMainProcessChanged(created.id)
      notifySuccess('Main process created')
    } catch (error) {
      notifyError(error instanceof Error ? error.message : 'Unable to create the main process')
    } finally {
      setCreatingMainProcess(false)
    }
  }, [recipeId, loadProcesses, onMainProcessChanged, notifySuccess, notifyError])

  const handleCreateSubprocess = useCallback(async (name: string, description: string) => {
    const created = await ProcessApi.create(recipeId, { type: 'SUBPROCESS', name, description: description || undefined })
    await loadProcesses(created.id)
    notifySuccess('Subprocess created')
  }, [recipeId, loadProcesses, notifySuccess])

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

  if (loading) {
    return (
      <div className="flex h-full w-full items-center justify-center" style={{ color: 'var(--flow-text-muted)' }}>
        Loading processes…
      </div>
    )
  }

  if (loadError) {
    return (
      <div className="flex h-full w-full items-center justify-center" style={{ color: '#9f1239' }}>
        {loadError}
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
        <div className="flex min-w-0 flex-1 items-center justify-center" style={{ color: 'var(--flow-text-muted)', fontSize: 13 }}>
          {isOwner ? 'Create a main process to get started.' : 'This recipe has no processes yet.'}
        </div>
      </div>
    )
  }

  return (
    <ProcessEditor
      key={`${recipeId}:${selectedProcessId}`}
      recipeId={recipeId}
      processId={selectedProcessId}
      breadcrumbAncestors={ancestorTrail}
      onNavigateToList={handleNavigateToList}
      onNavigateToAncestor={handleNavigateToAncestor}
      onOpenSubprocess={handleOpenSubprocess}
      sidebarHeader={processListSidebar}
    />
  )
}
