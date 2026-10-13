import DraftRestoredNote from '../../../shared/drafts/DraftRestoredNote'

type CreateRecipeModalProps = {
  open: boolean
  title: string
  description: string
  loading: boolean
  error: string | null
  onTitleChange: (value: string) => void
  onDescriptionChange: (value: string) => void
  onCreate: () => void
  onClose: () => void
  /** The fields were refilled from an unfinished earlier attempt; `onClearDraft` starts over. */
  draftRestored: boolean
  onClearDraft: () => void
}

export default function CreateRecipeModal({
  open,
  title,
  description,
  loading,
  error,
  onTitleChange,
  onDescriptionChange,
  onCreate,
  onClose,
  draftRestored,
  onClearDraft,
}: CreateRecipeModalProps) {
  if (!open) {
    return null
  }

  return (
    <div
      className="recipe-modal-backdrop"
      onMouseDown={e => {
        if (e.target === e.currentTarget && !loading) {
          onClose()
        }
      }}
    >
      <div
        className="recipe-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="create-recipe-title"
      >
        <div className="recipe-modal-header">
          <div>
            <div className="mb-1 text-[0.7rem] font-bold uppercase tracking-[0.14em] text-[var(--flow-accent)]">
              Virtual Kitchen
            </div>

            <h2
              id="create-recipe-title"
              className="text-xl font-bold text-[var(--flow-text)]"
            >
              Create a recipe
            </h2>

            <p className="mt-1 text-sm text-[var(--flow-text-muted)]">
              Start with the basics and build the cooking flow next.
            </p>
          </div>

          <button
            onClick={onClose}
            disabled={loading}
            className="recipe-modal-close"
            aria-label="Close"
          >
            ✕
          </button>
        </div>

        <div className="recipe-modal-body">
          {draftRestored && (title.trim() || description.trim()) && (
            <div className="mb-3"><DraftRestoredNote onDiscard={onClearDraft} /></div>
          )}
          <label className="block">
            <div className="mb-1.5 text-xs font-bold text-[var(--flow-text)]">
              Recipe name
            </div>

            <input
              autoFocus
              value={title}
              onChange={e => onTitleChange(e.target.value)}
              onKeyDown={e => {
                if (e.key === 'Enter' && !e.shiftKey) {
                  e.preventDefault()
                  onCreate()
                }
              }}
              placeholder="e.g. Spaghetti Aglio e Olio"
              className="flow-editor-input"
            />
          </label>

          <label className="mt-4 block">
            <div className="mb-1.5 text-xs font-bold text-[var(--flow-text)]">
              Description
              <span className="ml-1 font-normal text-[var(--flow-text-subtle)]">
                optional
              </span>
            </div>

            <textarea
              value={description}
              onChange={e => onDescriptionChange(e.target.value)}
              placeholder="Briefly describe the recipe..."
              rows={4}
              className="flow-editor-input resize-none"
            />
          </label>

          {error && (
            <div className="recipe-form-error">
              {error}
            </div>
          )}
        </div>

        <div className="recipe-modal-footer">
          <button
            onClick={onClose}
            disabled={loading}
            className="recipe-secondary-button"
          >
            Cancel
          </button>

          <button
            onClick={onCreate}
            disabled={loading}
            className="recipe-primary-button"
          >
            {loading ? 'Creating...' : 'Create recipe'}
            {!loading && <span aria-hidden="true">→</span>}
          </button>
        </div>
      </div>
    </div>
  )
}
