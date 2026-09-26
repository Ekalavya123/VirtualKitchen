import { useState } from 'react'
import '../../styles/recipe-tool.css'
import type { Process } from '../../../../types/process'
import { isPendingProcessId } from '../../context/RecipeSessionContext'

type RecipeProcessSidebarProps = {
  processes: Process[]
  selectedProcessId: number | null
  isOwner: boolean
  onSelect: (processId: number) => void
  onCreateMainProcess: () => void
  creatingMainProcess: boolean
  onCreateSubprocess: (name: string, description: string) => Promise<void>
  /** Opens the "Generate with AI" modal — omitted (button hidden) for a non-owner. */
  onOpenGenerate?: () => void
}

/**
 * Compact process list for the Recipe Tool's embedded "Recipe Process" tab
 * (distinct from the standalone
 * RecipeProcessListPage, which is a full page). Selecting a process here just
 * updates local state in RecipeProcessView — it never navigates to a
 * different route, per the brief ("keep the user inside the Recipe Tool").
 * Subprocess creation reuses the existing ProcessApi (via the parent's
 * `onCreateSubprocess`), matching Phase 5's own creation flow.
 */
export default function RecipeProcessSidebar({
  processes,
  selectedProcessId,
  isOwner,
  onSelect,
  onCreateMainProcess,
  creatingMainProcess,
  onCreateSubprocess,
  onOpenGenerate,
}: RecipeProcessSidebarProps) {
  const [showCreateForm, setShowCreateForm] = useState(false)
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [creating, setCreating] = useState(false)
  const [createError, setCreateError] = useState<string | null>(null)

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
      setName('')
      setDescription('')
      setShowCreateForm(false)
    } catch (error) {
      setCreateError(error instanceof Error ? error.message : 'Unable to create this subprocess')
    } finally {
      setCreating(false)
    }
  }

  const renderCard = (process: Process) => {
    const selected = process.id === selectedProcessId
    const stepCount = process.nodes.filter((node) => node.kind === 'STEP').length
    return (
      <button
        key={process.id}
        type="button"
        onClick={() => onSelect(process.id)}
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
      <div className="flex flex-shrink-0 items-center justify-between border-b border-[var(--flow-border)] px-3 py-3">
        <div style={{ fontSize: 12.5, fontWeight: 700, color: 'var(--flow-text)' }}>Processes ({processes.length})</div>
        {isOwner && (
          <div style={{ display: 'flex', gap: 6 }}>
            {onOpenGenerate && (
              <button
                type="button"
                onClick={onOpenGenerate}
                title="Generate with AI"
                style={{ padding: '4px 9px', borderRadius: 7, border: '1px solid var(--flow-magic-border)', background: 'var(--flow-magic-soft)', color: 'var(--flow-magic)', fontSize: 11, fontWeight: 700, cursor: 'pointer' }}
              >
                ✨ AI
              </button>
            )}
            <button
              type="button"
              onClick={() => setShowCreateForm((value) => !value)}
              style={{ padding: '4px 9px', borderRadius: 7, border: '1px solid var(--flow-border)', background: 'var(--flow-surface)', fontSize: 11, fontWeight: 700, cursor: 'pointer' }}
            >
              {showCreateForm ? 'Cancel' : '+ Subprocess'}
            </button>
          </div>
        )}
      </div>

      {isOwner && showCreateForm && (
        <div className="flex flex-shrink-0 flex-col gap-2 border-b border-[var(--flow-border)] p-3">
          <input
            className="flow-properties-input"
            value={name}
            onChange={(e) => setName(e.target.value)}
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
          <button
            type="button"
            onClick={() => void handleCreate()}
            disabled={creating}
            style={{ alignSelf: 'flex-start', padding: '6px 12px', borderRadius: 7, border: '1px solid var(--flow-accent)', background: 'var(--flow-accent)', color: 'white', fontSize: 11.5, fontWeight: 700, cursor: creating ? 'wait' : 'pointer' }}
          >
            {creating ? 'Creating…' : 'Create Subprocess'}
          </button>
        </div>
      )}

      <div className="flex flex-1 flex-col gap-2 overflow-y-auto p-2.5">
        <div className="px-1 text-[0.68rem] font-bold uppercase tracking-wide" style={{ color: 'var(--flow-text-subtle)' }}>Main Process</div>
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

        <div className="mt-2 px-1 text-[0.68rem] font-bold uppercase tracking-wide" style={{ color: 'var(--flow-text-subtle)' }}>
          Subprocesses ({subprocesses.length})
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
