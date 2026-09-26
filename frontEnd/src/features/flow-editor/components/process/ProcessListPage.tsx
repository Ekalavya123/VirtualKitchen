import { useCallback, useEffect, useState } from 'react'
import '../../styles/flow-editor.css'
import { ProcessApi, RecipeDetailApi } from '../../../../api'
import type { Process } from '../../../../types/process'
import { useNotifications } from '../../../../shared/components/notifications/NotificationProvider'
import ProcessSummaryCard from './ProcessSummaryCard'

type ProcessListPageProps = {
  recipeId: number
  /** Used only to derive whether the viewer owns this recipe (hides create controls otherwise) — the backend remains authoritative regardless. */
  currentUserId: number | null
  onOpenProcess: (processId: number) => void
  onBack?: () => void
}

/**
 * Recipe-scoped list of a recipe's processes (its MAIN process plus every
 * SUBPROCESS document), with a way to create a new subprocess and to
 * bootstrap the MAIN process if the recipe doesn't have one yet. Reachable
 * both directly by URL and from the Recipe Tool page's "Manage Processes"
 * link — this is the one place subprocess creation actually happens; the
 * Recipe Tool page only links here rather than reimplementing it.
 */
export default function ProcessListPage({ recipeId, currentUserId, onOpenProcess, onBack }: ProcessListPageProps) {
  const { notifyError, notifySuccess } = useNotifications()
  const [processes, setProcesses] = useState<Process[]>([])
  const [isOwner, setIsOwner] = useState(false)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [creatingMain, setCreatingMain] = useState(false)

  const [showCreateForm, setShowCreateForm] = useState(false)
  const [newName, setNewName] = useState('')
  const [newDescription, setNewDescription] = useState('')
  const [creating, setCreating] = useState(false)
  const [createError, setCreateError] = useState<string | null>(null)

  const loadProcesses = useCallback(() => {
    Promise.all([ProcessApi.listByRecipe(recipeId), RecipeDetailApi.getRecipeDetail(recipeId)])
      .then(([processList, recipe]) => {
        setProcesses(processList)
        setIsOwner(currentUserId != null && recipe.createdBy != null && recipe.createdBy === currentUserId)
        setLoadError(null)
      })
      .catch((error) => {
        setLoadError(error instanceof Error ? error.message : 'Unable to load this recipe\'s processes')
      })
      .finally(() => setLoading(false))
  }, [recipeId, currentUserId])

  useEffect(() => {
    loadProcesses()
  }, [loadProcesses])

  const mainProcess = processes.find((process) => process.type === 'MAIN') ?? null
  const subprocesses = processes.filter((process) => process.type === 'SUBPROCESS')

  const handleCreateMainProcess = async () => {
    setCreatingMain(true)
    try {
      const created = await RecipeDetailApi.createMainProcess(recipeId)
      notifySuccess('Main process created')
      onOpenProcess(created.id)
    } catch (error) {
      notifyError(error instanceof Error ? error.message : 'Unable to create the main process')
    } finally {
      setCreatingMain(false)
    }
  }

  const handleCreateSubprocess = async () => {
    if (!newName.trim()) {
      setCreateError('Name is required')
      return
    }

    setCreating(true)
    setCreateError(null)
    try {
      await ProcessApi.create(recipeId, {
        type: 'SUBPROCESS',
        name: newName.trim(),
        description: newDescription.trim() || undefined,
      })
      setNewName('')
      setNewDescription('')
      setShowCreateForm(false)
      notifySuccess('Subprocess created')
      loadProcesses()
    } catch (error) {
      setCreateError(error instanceof Error ? error.message : 'Unable to create this subprocess')
    } finally {
      setCreating(false)
    }
  }

  if (loading) {
    return (
      <div className="flex h-full w-full items-center justify-center" style={{ color: 'var(--flow-text-muted)' }}>
        Loading processes…
      </div>
    )
  }

  return (
    <div className="flex h-full w-full flex-col" style={{ background: 'var(--flow-surface-muted)' }}>
      <div className="flex h-[3.75rem] flex-shrink-0 items-center justify-between border-b border-[var(--flow-border)] bg-[var(--flow-surface)] px-4">
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          {onBack && (
            <button
              onClick={onBack}
              style={{ padding: '8px 10px', borderRadius: 8, border: '1px solid var(--flow-border)', background: 'var(--flow-surface)', cursor: 'pointer', fontSize: 12, fontWeight: 600 }}
            >
              ← Back
            </button>
          )}
          <div style={{ fontWeight: 700, fontSize: 14, color: 'var(--flow-text)' }}>Recipe Processes</div>
        </div>
      </div>

      <div className="flex-1 overflow-y-auto p-5" style={{ maxWidth: 760, margin: '0 auto', width: '100%' }}>
        {loadError && (
          <div className="mb-4 rounded-lg px-3 py-2 text-sm" style={{ border: '1px solid #fda4af', background: '#fff5f5', color: '#9f1239' }}>
            {loadError}
          </div>
        )}

        <div className="flow-editor-section-heading mb-2">Main Process</div>
        {mainProcess ? (
          <ProcessSummaryCard process={mainProcess} onOpen={() => onOpenProcess(mainProcess.id)} />
        ) : (
          <div
            className="flex items-center justify-between rounded-xl border px-4 py-3"
            style={{ border: '1px dashed var(--flow-border)', background: 'var(--flow-surface)' }}
          >
            <span style={{ fontSize: 13, color: 'var(--flow-text-muted)' }}>This recipe doesn't have a main process yet.</span>
            {isOwner && (
              <button
                type="button"
                onClick={handleCreateMainProcess}
                disabled={creatingMain}
                style={{ padding: '6px 12px', borderRadius: 8, border: '1px solid var(--flow-accent)', background: 'var(--flow-accent)', color: 'white', fontSize: 12, fontWeight: 700, cursor: creatingMain ? 'wait' : 'pointer' }}
              >
                {creatingMain ? 'Creating…' : 'Create Main Process'}
              </button>
            )}
          </div>
        )}

        <div className="mt-6 mb-2 flex items-center justify-between">
          <div className="flow-editor-section-heading">Subprocesses</div>
          {isOwner && (
            <button
              type="button"
              onClick={() => setShowCreateForm((value) => !value)}
              style={{ padding: '5px 10px', borderRadius: 8, border: '1px solid var(--flow-border)', background: 'var(--flow-surface)', fontSize: 12, fontWeight: 700, cursor: 'pointer' }}
            >
              {showCreateForm ? 'Cancel' : '+ New Subprocess'}
            </button>
          )}
        </div>

        {isOwner && showCreateForm && (
          <div className="mb-4 flex flex-col gap-2 rounded-xl border p-3" style={{ border: '1px solid var(--flow-border)', background: 'var(--flow-surface)' }}>
            <input
              className="flow-properties-input"
              value={newName}
              onChange={(e) => setNewName(e.target.value)}
              placeholder="Subprocess name (e.g. Prepare Chicken)"
            />
            <textarea
              className="flow-properties-textarea"
              rows={2}
              value={newDescription}
              onChange={(e) => setNewDescription(e.target.value)}
              placeholder="Description (optional)"
            />
            {createError && <div style={{ fontSize: 12, color: '#dc2626', fontWeight: 600 }}>{createError}</div>}
            <button
              type="button"
              onClick={handleCreateSubprocess}
              disabled={creating}
              style={{ alignSelf: 'flex-start', padding: '6px 14px', borderRadius: 8, border: '1px solid var(--flow-accent)', background: 'var(--flow-accent)', color: 'white', fontSize: 12, fontWeight: 700, cursor: creating ? 'wait' : 'pointer' }}
            >
              {creating ? 'Creating…' : 'Create Subprocess'}
            </button>
          </div>
        )}

        {subprocesses.length === 0 ? (
          <div style={{ fontSize: 13, color: 'var(--flow-text-subtle)' }}>No subprocesses yet.</div>
        ) : (
          <div className="flex flex-col gap-3">
            {subprocesses.map((process) => (
              <ProcessSummaryCard key={process.id} process={process} onOpen={() => onOpenProcess(process.id)} />
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
