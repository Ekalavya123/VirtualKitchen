import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import '../../../../styles/flow-ui.css'
import '../styles/recipe-view.css'
import type { NutritionInfo, RecipeDetail } from '../../../../types/recipe'
import { useRecipeSession } from '../../session/sessionContext'
import { useReadinessGate } from '../../session/useRecipeReadiness'
import type { ReadinessIssue } from '../../model/recipeReadiness'
import { recipeToolPath } from '../../recipeToolRoutes'
import { buildRecipePresentation } from '../model/recipeView'
import { buildCookModeSlides } from '../model/cookModeSlides'
import RecipeVisualizationSlideshow from '../../walkthrough/RecipeVisualizationSlideshow'
import RecipeHero from './RecipeHero'
import RecipeIngredientsList from './RecipeIngredientsList'
import RecipeNutritionPanel from './RecipeNutritionPanel'
import RecipeStepsTimeline from './RecipeStepsTimeline'

type RecipeViewPageProps = {
  recipe: RecipeDetail
  isOwner: boolean
  onNutritionSaved: (nutrition: NutritionInfo | null) => void
  /** Opens an issue that blocks cook mode in the editor (owners only). */
  onShowIssue?: (issue: ReadinessIssue) => void
  /** Opens one step in the editor (owners only). */
  onOpenStep?: (processId: number, nodeId: string) => void
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
export default function RecipeViewPage({ recipe, isOwner, onNutritionSaved, onShowIssue, onOpenStep }: RecipeViewPageProps) {
  const session = useRecipeSession()
  const readinessGate = useReadinessGate(isOwner ? onShowIssue : undefined)

  // `session` changes identity on every process edit (its `version` bumps), which is exactly
  // when this needs re-deriving.
  const presentation = useMemo(
    () => (session && !session.loading ? buildRecipePresentation(session.getProcesses()) : null),
    [session],
  )

  // Cook mode: the whole recipe, one step at a time, narrated. It needs the whole structure (preparations
  // feeding one connected main process), and narration is generated from the *saved* steps — the gate saves
  // the owner's pending edits first, then refuses (explaining why) while the recipe has blockers.
  const [cookMode, setCookMode] = useState<{ ready: Promise<boolean> } | null>(null)
  const openCookMode = async () => {
    if (!(await readinessGate('cooking'))) return
    setCookMode({ ready: Promise.resolve(true) })
  }

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
            {hasSteps && (
              <button type="button" className="rp-button" onClick={() => void openCookMode()} title="Follow the recipe one step at a time, with narration">
                <span aria-hidden>▶</span> Cook mode
              </button>
            )}
          </div>
          {hasSteps ? (
            <RecipeStepsTimeline sections={presentation.sections} onOpenStep={isOwner ? onOpenStep : undefined} />
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

      {cookMode && (
        <RecipeVisualizationSlideshow
          steps={buildCookModeSlides(presentation.sections)}
          recipeId={recipe.id}
          processId={null}
          narrationReady={cookMode.ready}
          heading="👩‍🍳 Cook mode"
          onClose={() => setCookMode(null)}
        />
      )}
    </div>
  )
}
