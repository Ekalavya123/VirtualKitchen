import { useRef, useState } from 'react'
import '../../styles/recipe-tool.css'
import '../styles/RecipeEditorToolbar.css'
import type { Process } from '../../../../types/process'
import { isPendingProcessId } from '../../context/RecipeSessionContext'
import { useDraft } from '../../../../shared/drafts/useDraft'
import DraftRestoredNote from '../../../../shared/drafts/DraftRestoredNote'
import AddNodeMenu from './AddNodeMenu'
import { useProcessSidebarActions } from '../context/ProcessSidebarActionsContext'

type RecipeProcessSidebarProps = {
  processes: Process[]
  selectedProcessId: number | null
  isOwner: boolean
  onSelect: (processId: number) => void
  onCreateMainProcess: () => void
  creatingMainProcess: boolean
  onCreateSubprocess: (name: string, description: string) => Promise<void>
  /** Where the new-subprocess form keeps its draft (see shared/drafts). */
  newSubprocessDraftKey?: string
}

const sectionLabel = 'px-1 text-[0.68rem] font-bold uppercase tracking-wide'

/**
 * "Recipe Processes": the Recipe Editor's process navigator (MAIN pinned above SUBPROCESSes) with
 * the editor's one "+ Add" menu (Step / Condition / Subprocess) at the top, and a "+" on the
 * Subprocesses heading that opens the same new-subprocess form. Distinct from the
 * standalone RecipeProcessListPage, which is a full page. Selecting a process here just updates
 * local state in RecipeEditorView — it never navigates to a different route ("keep the user inside
 * the Recipe Tool"). Subprocess creation reuses the existing ProcessApi (via `onCreateSubprocess`).
 */
export default function RecipeProcessSidebar({
  processes,
  selectedProcessId,
  isOwner,
  onSelect,
  onCreateMainProcess,
  creatingMainProcess,
  onCreateSubprocess,
  newSubprocessDraftKey,
}: RecipeProcessSidebarProps) {
  // Adding a step/condition and collapsing come from the canvas hosting this sidebar (none without one).
  const { onAddStep, onAddCondition, onCollapse } = useProcessSidebarActions() ?? {}
  // The new-subprocess form keeps what was typed until the subprocess is created (closing the form
  // or leaving the recipe doesn't lose it); a form with an unfinished draft opens again by itself.
  const draft = useDraft(newSubprocessDraftKey ?? null, { name: '', description: '' })
  const { name, description } = draft.value
  const setName = (value: string) => draft.setValue((current) => ({ ...current, name: value }))
  const setDescription = (value: string) => draft.setValue((current) => ({ ...current, description: value }))
  const [showCreateForm, setShowCreateForm] = useState(() => draft.restored)
  const [creating, setCreating] = useState(false)
  const [createError, setCreateError] = useState<string | null>(null)
  const nameInputRef = useRef<HTMLInputElement | null>(null)

  const mainProcess = processes.find((process) => process.type === 'MAIN') ?? null
  const subprocesses = processes.filter((process) => process.type === 'SUBPROCESS')

  const handleCreate = async () => {
    if (!name.trim()) {
      setCreateError('Name is required')
      return
    }
    setCreating(true)
    setCreateError(null)
    try {
      await onCreateSubprocess(name.trim(), description.trim())
      draft.clear()
      setShowCreateForm(false)
    } catch (error) {
      setCreateError(error instanceof Error ? error.message : 'Unable to create this subprocess')
    } finally {
      setCreating(false)
    }
  }

  // Opening the form from "+ Add → Subprocess" puts the cursor straight in its name field, once rendered.
  const openCreateForm = () => {
    setShowCreateForm(true)
    requestAnimationFrame(() => nameInputRef.current?.focus())
  }

  const renderCard = (process: Process) => {
    const selected = process.id === selectedProcessId
    const stepCount = process.nodes.filter((node) => node.kind === 'STEP').length
    return (
      <button
        key={process.id}
        type="button"
        onClick={() => onSelect(process.id)}
        aria-current={selected ? 'true' : undefined}
        style={{
          width: '100%',
          textAlign: 'left',
          padding: '10px 12px',
          borderRadius: 10,
          border: `1px solid ${selected ? 'var(--flow-accent)' : 'var(--flow-border)'}`,
          background: selected ? 'var(--flow-accent-soft)' : 'var(--flow-surface)',
          cursor: 'pointer',
          display: 'flex',
          alignItems: 'center',
          gap: 8,
        }}
      >
        <span aria-hidden style={{ fontSize: 14, flexShrink: 0 }}>{process.type === 'MAIN' ? '👑' : '🧩'}</span>
        <div style={{ minWidth: 0, flex: 1 }}>
          <div style={{ fontSize: 12.5, fontWeight: 700, color: 'var(--flow-text)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {process.name}
          </div>
          <div style={{ fontSize: 10.5, color: 'var(--flow-text-muted)' }}>
            {process.type === 'MAIN' ? 'Main Process' : 'Subprocess'} · {stepCount} step{stepCount === 1 ? '' : 's'}
          </div>
        </div>
        {isPendingProcessId(process.id) && (
          <span
            title="Generated — not saved yet"
            style={{ flexShrink: 0, fontSize: 9.5, fontWeight: 700, padding: '2px 6px', borderRadius: 999, background: 'var(--flow-magic-soft)', border: '1px solid var(--flow-magic-border)', color: 'var(--flow-magic)' }}
          >
            NEW
          </span>
        )}
      </button>
    )
  }

  return (
    <div className="flex h-full flex-col">
      <div className="flex flex-shrink-0 items-center justify-between gap-2 border-b border-[var(--flow-border)] py-2.5 pr-2 pl-3.5">
        <div style={{ fontSize: 12.5, fontWeight: 700, color: 'var(--flow-text)' }}>Recipe Processes</div>
        {onCollapse && (
          <button
            type="button"
            onClick={onCollapse}
            aria-label="Collapse sidebar"
            title="Collapse sidebar"
            className="recipe-topbar-button"
            style={{ height: 28, padding: '0 8px' }}
          >
            «
          </button>
        )}
      </div>

      {(onAddStep || onAddCondition || isOwner) && (
        <div className="flex-shrink-0 border-b border-[var(--flow-border)] p-2.5">
          <AddNodeMenu
            onAddStep={onAddStep}
            onAddCondition={onAddCondition}
            onAddSubprocess={isOwner ? openCreateForm : undefined}
            buttonClassName="recipe-sidebar-add"
            placement="bottom-start"
          />
        </div>
      )}

      {isOwner && showCreateForm && (
        <div className="flex flex-shrink-0 flex-col gap-2 border-b border-[var(--flow-border)] p-3" role="group" aria-label="New subprocess">
          <div style={{ fontSize: 11.5, fontWeight: 700, color: 'var(--flow-text)' }}>New subprocess</div>
          {draft.restored && (name.trim() || description.trim()) && <DraftRestoredNote onDiscard={draft.clear} />}
          <input
            ref={nameInputRef}
            className="flow-properties-input"
            value={name}
            onChange={(e) => setName(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') void handleCreate()
              if (e.key === 'Escape') setShowCreateForm(false)
            }}
            placeholder="Subprocess name"
          />
          <textarea
            className="flow-properties-textarea"
            rows={2}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="Description (optional)"
          />
          {createError && <div style={{ fontSize: 11, color: '#dc2626', fontWeight: 600 }}>{createError}</div>}
          <div style={{ display: 'flex', gap: 6 }}>
            <button
              type="button"
              onClick={() => void handleCreate()}
              disabled={creating}
              style={{ padding: '6px 12px', borderRadius: 7, border: '1px solid var(--flow-accent)', background: 'var(--flow-accent)', color: 'white', fontSize: 11.5, fontWeight: 700, cursor: creating ? 'wait' : 'pointer' }}
            >
              {creating ? 'Creating…' : 'Create Subprocess'}
            </button>
            <button
              type="button"
              onClick={() => setShowCreateForm(false)}
              style={{ padding: '6px 12px', borderRadius: 7, border: '1px solid var(--flow-border)', background: 'var(--flow-surface)', color: 'var(--flow-text-muted)', fontSize: 11.5, fontWeight: 700, cursor: 'pointer' }}
            >
              Cancel
            </button>
          </div>
        </div>
      )}

      <div className="flex flex-1 flex-col gap-2 overflow-y-auto p-2.5">
        <div className={sectionLabel} style={{ color: 'var(--flow-text-subtle)' }}>Main Process</div>
        {mainProcess ? (
          renderCard(mainProcess)
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6, padding: '10px 12px', borderRadius: 10, border: '1px dashed var(--flow-border)' }}>
            <span style={{ fontSize: 11.5, color: 'var(--flow-text-muted)' }}>No main process yet.</span>
            {isOwner && (
              <button
                type="button"
                onClick={onCreateMainProcess}
                disabled={creatingMainProcess}
                style={{ alignSelf: 'flex-start', padding: '5px 10px', borderRadius: 7, border: '1px solid var(--flow-accent)', background: 'var(--flow-accent)', color: 'white', fontSize: 11, fontWeight: 700, cursor: creatingMainProcess ? 'wait' : 'pointer' }}
              >
                {creatingMainProcess ? 'Creating…' : 'Create Main Process'}
              </button>
            )}
          </div>
        )}

        <div className="mt-2 flex items-center justify-between gap-2">
          <div className={sectionLabel} style={{ color: 'var(--flow-text-subtle)' }}>
            Subprocesses{subprocesses.length > 0 ? ` (${subprocesses.length})` : ''}
          </div>
          {isOwner && (
            <button
              type="button"
              onClick={openCreateForm}
              aria-label="Add subprocess"
              title="Add subprocess"
              className="recipe-sidebar-section-add"
            >
              +
            </button>
          )}
        </div>
        {subprocesses.length === 0 ? (
          <div style={{ fontSize: 11.5, color: 'var(--flow-text-subtle)', padding: '0 4px' }}>No subprocesses yet.</div>
        ) : (
          subprocesses.map(renderCard)
        )}
      </div>

    </div>
  )
}
