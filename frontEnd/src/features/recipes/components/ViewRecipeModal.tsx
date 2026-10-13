import type { Recipe } from '../../../api'

export default function ViewRecipeModal({
  recipe,
  onClose,
}: {
  recipe: Recipe | null
  onClose: () => void
}) {
  if (!recipe) {
    return null
  }

  return (
    <div
      className="recipe-modal-backdrop"
      onMouseDown={e => {
        if (e.target === e.currentTarget) {
          onClose()
        }
      }}
    >
      <div
        className="recipe-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="view-recipe-title"
      >
        <div className="recipe-modal-header">
          <div>
            <div className="mb-1 text-[0.7rem] font-bold uppercase tracking-[0.14em] text-[var(--flow-accent)]">
              Global Recipe
            </div>

            <h2
              id="view-recipe-title"
              className="text-xl font-bold text-[var(--flow-text)]"
            >
              {recipe.name}
            </h2>
          </div>

          <button
            onClick={onClose}
            className="recipe-modal-close"
            aria-label="Close"
          >
            ✕
          </button>
        </div>

        <div className="recipe-modal-body">
          <p className="text-sm text-[var(--flow-text-muted)]">
            {recipe.description || 'No description provided for this recipe.'}
          </p>
        </div>

        <div className="recipe-modal-footer">
          <button
            onClick={onClose}
            className="recipe-secondary-button"
          >
            Close
          </button>
        </div>
      </div>
    </div>
  )
}
