import type { RecipeDetail } from '../../../../types/recipe'
import { formatTotalMinutes, type RecipePresentation } from '../model/recipePresentation'
import RecipeImage from './RecipeImage'
import { scrollToAnchor } from './scrollToAnchor'

type RecipeHeroProps = {
  recipe: RecipeDetail
  presentation: RecipePresentation
  sectionAnchors: { id: string; label: string }[]
}

const plural = (count: number, word: string) => `${count} ${word}${count === 1 ? '' : 's'}`

/**
 * The recipe's header: name, description and at-a-glance facts. Every fact is read from existing
 * data (the recipe itself, its steps, its nutrition) and only shown when it's actually there.
 * There's no recipe-level photo in the model, so the hero uses the generated image of the main
 * process's last illustrated step — usually the finished dish.
 */
export default function RecipeHero({ recipe, presentation, sectionAnchors }: RecipeHeroProps) {
  const servings = recipe.nutrition?.servings

  return (
    <header className="rp-hero">
      <div className="rp-hero-text">
        <span className="rp-eyebrow">
          Recipe
          {recipe.visibility && (
            <span className="rp-visibility">{recipe.visibility === 'PUBLIC' ? '🌐 Public' : '🔒 Private'}</span>
          )}
        </span>
        <h1 className="rp-hero-title">{recipe.name}</h1>
        {recipe.description && <p className="rp-hero-description">{recipe.description}</p>}

        <div className="rp-hero-stats">
          {presentation.totalMinutes != null && (
            <span className="rp-stat" title="Total of every timed step">⏱️ <strong>{formatTotalMinutes(presentation.totalMinutes)}</strong></span>
          )}
          {servings != null && (
            <span className="rp-stat">🍽️ <strong>{servings}</strong> {servings === 1 ? 'serving' : 'servings'}</span>
          )}
          {presentation.stepCount > 0 && <span className="rp-stat">👣 {plural(presentation.stepCount, 'step')}</span>}
          {presentation.ingredients.length > 0 && (
            <span className="rp-stat">🥕 {plural(presentation.ingredients.length, 'ingredient')}</span>
          )}
        </div>

        <nav className="rp-jump-nav" aria-label="Recipe sections">
          {sectionAnchors.map((anchor) => (
            <button key={anchor.id} type="button" className="rp-jump-link" onClick={() => scrollToAnchor(anchor.id)}>
              {anchor.label}
            </button>
          ))}
        </nav>
      </div>

      <div className="rp-hero-media">
        <RecipeImage src={presentation.heroImageUrl} alt={recipe.name} fallbackIcon="🍲" />
      </div>
    </header>
  )
}
