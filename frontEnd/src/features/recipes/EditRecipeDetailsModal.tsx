import { useEffect, useState } from 'react'
import '../recipe-tool/styles/recipe-tool.css'

export type RecipeDetailsDraft = { name: string; description: string }

type EditRecipeDetailsModalProps = {
  /** The recipe being edited; null hides the dialog. */
  recipe: { name: string; description?: string | null } | null
  onSave: (details: RecipeDetailsDraft) => Promise<void>
  onClose: () => void
}

const MAX_NAME = 120
const MAX_DESCRIPTION = 1000

/**
 * Edit a recipe's name and description — from the recipe card's ⋯ menu and from the Recipe Tool
 * header. `onSave` does the request; while it runs the dialog can't be closed, and a failure is
 * shown in the dialog so nothing typed is lost.
 */
export default function EditRecipeDetailsModal({ recipe, onSave, onClose }: EditRecipeDetailsModalProps) {
  if (!recipe) return null
  return <EditRecipeDetailsForm key={`${recipe.name}\u0000${recipe.description ?? ''}`} recipe={recipe} onSave={onSave} onClose={onClose} />
}

function EditRecipeDetailsForm({
  recipe,
  onSave,
  onClose,
}: {
  recipe: { name: string; description?: string | null }
  onSave: (details: RecipeDetailsDraft) => Promise<void>
  onClose: () => void
}) {
  const [name, setName] = useState(recipe.name)
  const [description, setDescription] = useState(recipe.description ?? '')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const trimmedName = name.trim()
  const unchanged = trimmedName === recipe.name.trim() && description.trim() === (recipe.description ?? '').trim()

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !saving) onClose()
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [saving, onClose])

  const save = async () => {
    if (!trimmedName) {
      setError('The recipe name is required.')
      return
    }
    if (unchanged) {
      onClose()
      return
    }
    setSaving(true)
    setError(null)
    try {
      await onSave({ name: trimmedName, description: description.trim() })
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to save the recipe details')
      setSaving(false)
    }
  }

  return (
    <div
      className="recipe-modal-backdrop"
      onMouseDown={event => {
        if (event.target === event.currentTarget && !saving) onClose()
      }}
    >
      <div className="recipe-modal" role="dialog" aria-modal="true" aria-labelledby="edit-recipe-title">
        <div className="recipe-modal-header">
          <div>
            <div className="mb-1 text-[0.7rem] font-bold uppercase tracking-[0.14em] text-[var(--flow-accent)]">
              Edit recipe
            </div>
            <h2 id="edit-recipe-title" className="text-xl font-bold text-[var(--flow-text)]">
              Name and description
            </h2>
          </div>

          <button onClick={onClose} disabled={saving} className="recipe-modal-close" aria-label="Close">
            ✕
          </button>
        </div>

        <div className="recipe-modal-body">
          <label className="block">
            <div className="mb-1.5 text-xs font-bold text-[var(--flow-text)]">Recipe name</div>
            <input
              autoFocus
              value={name}
              maxLength={MAX_NAME}
              onChange={event => setName(event.target.value)}
              onKeyDown={event => {
                if (event.key === 'Enter') {
                  event.preventDefault()
                  void save()
                }
              }}
              className="flow-editor-input"
            />
          </label>

          <label className="mt-4 block">
            <div className="mb-1.5 text-xs font-bold text-[var(--flow-text)]">
              Description
              <span className="ml-1 font-normal text-[var(--flow-text-subtle)]">optional</span>
            </div>
            <textarea
              value={description}
              maxLength={MAX_DESCRIPTION}
              onChange={event => setDescription(event.target.value)}
              placeholder="Briefly describe the recipe..."
              rows={4}
              className="flow-editor-input resize-none"
            />
          </label>

          {error && <div className="recipe-form-error">{error}</div>}
        </div>

        <div className="recipe-modal-footer">
          <button onClick={onClose} disabled={saving} className="recipe-secondary-button">
            Cancel
          </button>
          <button onClick={() => void save()} disabled={saving || !trimmedName} className="recipe-primary-button">
            {saving ? 'Saving…' : 'Save changes'}
          </button>
        </div>
      </div>
    </div>
  )
}
