import { useState } from 'react'
import type { Recipe } from '../../../api'
import recipeIcon from '../../../assets/kitchen/recipeIcon.png'
import ActionMenu, { type ActionMenuItem } from '../../../shared/components/ActionMenu'
import { formatDate } from '../../../shared/format/date'
import type { RecipeTab } from '../useRecipeLibrary'

type RecipeCardProps = {
  recipe: Recipe
  variant: RecipeTab
  highlighted?: boolean
  onOpen: () => void
  onEditDetails?: () => void
  onDelete?: () => void
  onToggleVisibility?: () => void
  onAddToMyRecipes?: () => void
}

export default function RecipeCard({
  recipe,
  variant,
  highlighted,
  onOpen,
  onEditDetails,
  onDelete,
  onToggleVisibility,
  onAddToMyRecipes,
}: RecipeCardProps) {
  // Falls back to the recipe icon when there is no thumbnail or it fails to load.
  const [failedThumbnail, setFailedThumbnail] = useState<string | null>(null)
  const showThumbnail = Boolean(recipe.thumbnailUrl) && recipe.thumbnailUrl !== failedThumbnail
  const isPublic = recipe.visibility === 'PUBLIC'
  const visibilityHint = isPublic
    ? 'Public: anyone can find it in Global Recipes. Click to make it private.'
    : 'Private: only you can see it. Click to make it public.'

  // My Recipes: the card's actions live in its "⋯" menu (visibility has its own icon).
  const menuItems: ActionMenuItem[] = variant === 'mine'
    ? [
        ...(onEditDetails ? [{ label: '✏️ Edit name & description', onSelect: onEditDetails }] : []),
        ...(onDelete ? [{ label: '🗑️ Delete recipe', onSelect: onDelete, danger: true }] : []),
      ]
    : []

  return (
    <article className={`recipe-card group${highlighted ? ' recipe-card-highlighted' : ''}`}>
      {/* The whole card opens the recipe (Global Recipes: its preview). */}
      <button
        onClick={onOpen}
        className="recipe-card-content"
        aria-label={`Open ${recipe.name}`}
      >
        <div className="recipe-card-image">
          {showThumbnail ? (
            <img
              src={recipe.thumbnailUrl!}
              alt={recipe.name}
              className="recipe-card-thumbnail"
              loading="lazy"
              onError={() => setFailedThumbnail(recipe.thumbnailUrl ?? null)}
            />
          ) : (
            <div className="recipe-card-image-placeholder">
              <img src={recipeIcon} alt="Recipe" className="recipe-card-logo" />
            </div>
          )}
        </div>

        <div className="recipe-card-body">
          <h3 className="line-clamp-1 text-base font-bold text-[var(--flow-text)]">
            {recipe.name}
          </h3>

          {recipe.description?.trim() && (
            <p className="recipe-card-description">
              {recipe.description}
            </p>
          )}

          {recipe.createdAt && (
            <span className="recipe-card-meta">
              {formatDate(recipe.createdAt)}
            </span>
          )}
        </div>
      </button>

      {/* Overlays on the image corners — siblings of the open-button, so they never open the recipe. */}
      {variant === 'mine' && onToggleVisibility && (
        <button
          type="button"
          onClick={onToggleVisibility}
          className="recipe-card-visibility"
          aria-label={visibilityHint}
          data-tooltip={isPublic ? 'Public · click to make private' : 'Private · click to make public'}
        >
          {isPublic ? '🌐' : '🔒'}
        </button>
      )}

      {menuItems.length > 0 && (
        <ActionMenu
          items={menuItems}
          label={`More actions for ${recipe.name}`}
          className="recipe-card-actions"
        />
      )}

      {variant === 'global' && (
        <div className="recipe-card-footer">
          <button
            onClick={onAddToMyRecipes}
            className="recipe-card-order-button"
          >
            + Add to My Recipes
          </button>
        </div>
      )}
    </article>
  )
}
