import { useMemo } from 'react'
import { Link } from 'react-router-dom'
import '../../styles/recipe-tool.css'
import '../styles/recipe-process.css'
import type { NutritionInfo, RecipeDetail } from '../../../../types/recipe'
import { useRecipeSession } from '../../context/RecipeSessionContext'
import { recipeToolPath } from '../../recipeToolRoutes'
import { buildRecipePresentation } from '../model/recipePresentation'
import RecipeHero from './RecipeHero'
import RecipeIngredientsList from './RecipeIngredientsList'
import RecipeNutritionPanel from './RecipeNutritionPanel'
import RecipeStepsTimeline from './RecipeStepsTimeline'

type RecipeProcessPageProps = {
  recipe: RecipeDetail
  isOwner: boolean
  onNutritionSaved: (nutrition: NutritionInfo | null) => void
}

const ANCHORS = {
  ingredients: 'recipe-ingredients',
  steps: 'recipe-steps',
  nutrition: 'recipe-nutrition',
}

/**
 * The Recipe Process: the recipe as someone would cook it — hero, ingredients, the steps in order
 * (with checks and their outcomes), then nutrition. Entirely derived from the same in-memory
 * recipe session the Recipe Editor edits (see buildRecipePresentation), so it always reflects the
 * editor's latest content, including edits that haven't been saved yet.
 */
export default function RecipeProcessPage({ recipe, isOwner, onNutritionSaved }: RecipeProcessPageProps) {
  const session = useRecipeSession()

  // `session` changes identity on every process edit (its `version` bumps), which is exactly
  // when this needs re-deriving.
  const presentation = useMemo(
    () => (session && !session.loading ? buildRecipePresentation(session.getProcesses()) : null),
    [session],
  )

  if (session?.loadError) {
    return <div className="rp-page"><div className="rp-state">{session.loadError}</div></div>
  }

  if (!presentation) {
    return <div className="rp-page"><div className="rp-state">Loading recipe…</div></div>
  }

  const hasSteps = presentation.sections.length > 0

  return (
    <div className="rp-page">
      <div className="rp-container">
        {session?.anyDirty && (
          <div className="rp-draft-banner" role="status">
            <span aria-hidden>✏️</span>
            Showing unsaved changes from the Recipe Editor.
          </div>
        )}

        <RecipeHero
          recipe={recipe}
          presentation={presentation}
          sectionAnchors={[
            { id: ANCHORS.ingredients, label: '🥕 Ingredients' },
            { id: ANCHORS.steps, label: '👩‍🍳 Steps' },
            { id: ANCHORS.nutrition, label: '📊 Nutrition' },
          ]}
        />

        <section id={ANCHORS.ingredients} className="rp-section" aria-labelledby={`${ANCHORS.ingredients}-title`}>
          <div className="rp-section-header">
            <h2 id={`${ANCHORS.ingredients}-title`} className="rp-section-title">
              <span className="rp-section-title-icon" aria-hidden>🥕</span>
              Ingredients
            </h2>
            {presentation.ingredients.length > 0 && (
              <p className="rp-section-subtitle">Everything you'll need, totalled across all steps.</p>
            )}
          </div>
          <RecipeIngredientsList ingredients={presentation.ingredients} />
        </section>

        <section id={ANCHORS.steps} className="rp-section" aria-labelledby={`${ANCHORS.steps}-title`}>
          <div className="rp-section-header">
            <h2 id={`${ANCHORS.steps}-title`} className="rp-section-title">
              <span className="rp-section-title-icon" aria-hidden>👩‍🍳</span>
              Recipe Steps
            </h2>
          </div>
          {hasSteps ? (
            <RecipeStepsTimeline sections={presentation.sections} />
          ) : (
            <div className="rp-empty-card">
              <span className="rp-empty-card-icon" aria-hidden>📝</span>
              This recipe doesn't have any steps yet.
              {isOwner && (
                <Link className="rp-button" to={recipeToolPath(recipe.id, 'editor')}>Open the Recipe Editor</Link>
              )}
            </div>
          )}
        </section>

        <section id={ANCHORS.nutrition} className="rp-section" aria-labelledby={`${ANCHORS.nutrition}-title`}>
          <div className="rp-section-header">
            <h2 id={`${ANCHORS.nutrition}-title`} className="rp-section-title">
              <span className="rp-section-title-icon" aria-hidden>📊</span>
              Nutrition
            </h2>
          </div>
          <RecipeNutritionPanel
            recipeId={recipe.id}
            nutrition={recipe.nutrition}
            isOwner={isOwner}
            onSaved={onNutritionSaved}
          />
        </section>
      </div>
    </div>
  )
}
