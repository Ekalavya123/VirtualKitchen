import { useEffect, useState } from 'react'
import type { Recipe } from '../../../api'
import recipeIcon from '../../../assets/kitchen/recipeIcon.png'

type OrderRecipeModalProps = {
  open: boolean
  recipes: Recipe[]
  loading: boolean
  /** The recipe whose order is being created. */
  orderingRecipeId: number | null
  onOrder: (recipe: Recipe) => void
  onCreateRecipe: () => void
  onClose: () => void
}

/** "Order recipe": choose one of your recipes, then continue to its order's confirm page. */
export default function OrderRecipeModal({
  open,
  recipes,
  loading,
  orderingRecipeId,
  onOrder,
  onCreateRecipe,
  onClose,
}: OrderRecipeModalProps) {
  const [query, setQuery] = useState('')
  const [selectedId, setSelectedId] = useState<number | null>(null)

  useEffect(() => {
    if (!open) return
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && orderingRecipeId == null) onClose()
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [open, orderingRecipeId, onClose])

  if (!open) return null

  const busy = orderingRecipeId != null
  const search = query.trim().toLowerCase()
  const matches = search
    ? recipes.filter(recipe =>
        recipe.name.toLowerCase().includes(search) || recipe.description?.toLowerCase().includes(search))
    : recipes
  const selected = recipes.find(recipe => recipe.id === selectedId) ?? null

  const close = () => {
    if (busy) return
    setQuery('')
    setSelectedId(null)
    onClose()
  }

  return (
    <div
      className="recipe-modal-backdrop"
      onMouseDown={event => {
        if (event.target === event.currentTarget) close()
      }}
    >
      <div className="recipe-modal order-recipe-modal" role="dialog" aria-modal="true" aria-labelledby="order-recipe-title">
        <div className="recipe-modal-header">
          <div>
            <div className="mb-1 text-[0.7rem] font-bold uppercase tracking-[0.14em] text-[var(--flow-accent)]">
              Order recipe
            </div>
            <h2 id="order-recipe-title" className="text-xl font-bold text-[var(--flow-text)]">
              Which recipe would you like prepared?
            </h2>
            <p className="mt-1 text-sm text-[var(--flow-text-muted)]">
              Pick one of your recipes. Next you'll confirm servings and the delivery address.
            </p>
          </div>

          <button onClick={close} disabled={busy} className="recipe-modal-close" aria-label="Close">
            ✕
          </button>
        </div>

        <div className="recipe-modal-body order-recipe-body">
          {loading ? (
            <p className="text-sm text-[var(--flow-text-muted)]">Loading your recipes…</p>
          ) : recipes.length === 0 ? (
            <div className="order-recipe-empty">
              <p>You don't have any recipes to order yet.</p>
              <button type="button" className="recipe-primary-button" onClick={() => { close(); onCreateRecipe() }}>
                + Create recipe
              </button>
            </div>
          ) : (
            <>
              <input
                value={query}
                onChange={event => setQuery(event.target.value)}
                placeholder="Search your recipes..."
                className="recipe-search-input order-recipe-search"
                aria-label="Search your recipes"
                autoFocus
              />

              <div className="order-recipe-list" role="radiogroup" aria-label="Your recipes">
                {matches.length === 0 ? (
                  <p className="text-sm text-[var(--flow-text-muted)]">No recipes match "{query}".</p>
                ) : (
                  matches.map(recipe => (
                    <label
                      key={recipe.id}
                      className={`order-recipe-option${recipe.id === selectedId ? ' selected' : ''}`}
                    >
                      <input
                        type="radio"
                        name="order-recipe"
                        checked={recipe.id === selectedId}
                        onChange={() => setSelectedId(recipe.id)}
                        disabled={busy}
                      />
                      <span className="order-recipe-thumb" aria-hidden="true">
                        {recipe.thumbnailUrl ? (
                          <img src={recipe.thumbnailUrl} alt="" loading="lazy" />
                        ) : (
                          <img src={recipeIcon} alt="" className="order-recipe-thumb-icon" />
                        )}
                      </span>
                      <span className="order-recipe-text">
                        <span className="order-recipe-name">{recipe.name}</span>
                        {recipe.description?.trim() && (
                          <span className="order-recipe-description">{recipe.description}</span>
                        )}
                      </span>
                    </label>
                  ))
                )}
              </div>
            </>
          )}
        </div>

        {recipes.length > 0 && (
          <div className="recipe-modal-footer">
            <button onClick={close} disabled={busy} className="recipe-secondary-button">
              Cancel
            </button>
            <button
              onClick={() => selected && onOrder(selected)}
              disabled={!selected || busy}
              className="recipe-primary-button"
            >
              {busy ? 'Starting order…' : 'Continue to order →'}
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
