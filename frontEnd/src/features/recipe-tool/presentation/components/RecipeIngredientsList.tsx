import type { RecipeIngredientLine } from '../model/recipePresentation'

type RecipeIngredientsListProps = {
  ingredients: RecipeIngredientLine[]
}

/**
 * Everything the recipe uses, totalled across all of its steps — derived from the steps
 * themselves, so there's no separate ingredient list to keep in sync.
 */
export default function RecipeIngredientsList({ ingredients }: RecipeIngredientsListProps) {
  if (ingredients.length === 0) {
    return (
      <div className="rp-empty-card">
        <span className="rp-empty-card-icon" aria-hidden>🧺</span>
        No ingredients yet — they appear here as soon as a step uses one.
      </div>
    )
  }

  return (
    <ul className="rp-ingredients">
      {ingredients.map((ingredient) => (
        <li key={ingredient.key} className="rp-ingredient">
          <span className="rp-ingredient-icon" aria-hidden>{ingredient.icon}</span>
          <span className="rp-ingredient-text">
            <span className="rp-ingredient-name">{ingredient.name}</span>
            {ingredient.amount && <span className="rp-ingredient-amount">{ingredient.amount}</span>}
            {ingredient.preparations.length > 0 && (
              <span className="rp-ingredient-prep">{ingredient.preparations.join(', ')}</span>
            )}
          </span>
        </li>
      ))}
    </ul>
  )
}
