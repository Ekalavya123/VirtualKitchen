import type { RecipeTab } from '../useRecipeLibrary'

export default function EmptyState({
  searching,
  tab,
  onCreate,
}: {
  searching: boolean
  tab: RecipeTab
  onCreate: () => void
}) {
  if (searching) {
    return (
      <div className="recipe-empty-state">
        <div className="recipe-empty-icon">🔍</div>

        <h3>No recipes found</h3>

        <p>
          Try searching with a different recipe name or description.
        </p>
      </div>
    )
  }

  if (tab === 'global') {
    return (
      <div className="recipe-empty-state">
        <div className="recipe-empty-icon">🌐</div>

        <h3>No public recipes yet</h3>

        <p>
          When other users publish recipes, they will show up here.
        </p>
      </div>
    )
  }

  return (
    <div className="recipe-empty-state">
      <div className="recipe-empty-icon">🍳</div>

      <h3>No recipes yet</h3>

      <p>
        Create your first recipe and start designing its cooking flow.
      </p>

      <button
        onClick={onCreate}
        className="recipe-primary-button mt-5"
      >
        Create your first recipe
        <span aria-hidden="true">→</span>
      </button>
    </div>
  )
}
