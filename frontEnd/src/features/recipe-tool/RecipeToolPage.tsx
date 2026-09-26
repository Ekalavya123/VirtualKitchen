import { useCallback, useEffect, useState } from 'react'
import { RecipeDetailApi } from '../../api'
import type { NutritionInfo, RecipeDetail } from '../../types/recipe'
import { RecipeSessionProvider } from './context/RecipeSessionContext'
import './process/styles/RecipeProcessCanvas.css'
import RecipeToolNavbar, { type RecipeToolView } from './components/RecipeToolNavbar'
import IngredientsSection from './components/IngredientsSection'
import NutritionSection from './components/NutritionSection'
import RecipeProcessView from './process/components/RecipeProcessView'

type RecipeToolPageProps = {
  recipeId: number
  currentUserId: number
  onBack: () => void
}

/**
 * The Recipe Tool page: an internal navbar (compact recipe metadata + the
 * three tool tabs) above a full-height content area that swaps between
 * Recipe Process / Ingredients / Nutrition — a single route, local view
 * state, no per-tab page navigation (see RecipeToolNavbar). Recipe Process
 * is the default and primary authoring surface; Ingredients is a read-only
 * view derived from it (see IngredientsSection); Nutrition stays its own,
 * independently editable model, unchanged from Phase 6.
 */
export default function RecipeToolPage({ recipeId, currentUserId, onBack }: RecipeToolPageProps) {
  const [recipe, setRecipe] = useState<RecipeDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [activeView, setActiveView] = useState<RecipeToolView>('RECIPE_PROCESS')

  useEffect(() => {
    let cancelled = false
    // No sync setLoading(true) here: `loading` already starts true (useState(true) above), and this
    // effect's deps are just [recipeId] — the caller (App.tsx's RecipeToolRoute) keys this component
    // by recipeId, so a different recipe means a fresh mount (fresh initial state) rather than this
    // effect re-running in place on an existing instance.
    RecipeDetailApi.getRecipeDetail(recipeId)
      .then(async (result) => {
        // Every recipe the owner opens gets its MAIN process before the session below loads, so
        // the tool always opens on MAIN. The endpoint is idempotent (safe to race, e.g. StrictMode's
        // double effect). Runs before RecipeSessionProvider mounts (it only mounts
        // once `loading` is false), so the session's single process load already includes it.
        if (result.mainProcessId == null && result.createdBy === currentUserId) {
          try {
            const main = await RecipeDetailApi.createMainProcess(recipeId)
            return { ...result, mainProcessId: main.id }
          } catch (error) {
            // Not fatal: the Recipe Process tab still offers "Create Main Process" / AI generation.
            console.error('Unable to prepare this recipe\'s main process:', error)
          }
        }
        return result
      })
      .then((result) => {
        if (!cancelled) {
          setRecipe(result)
          setLoadError(null)
        }
      })
      .catch((error) => {
        if (!cancelled) {
          setLoadError(error instanceof Error ? error.message : 'Unable to load this recipe')
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [recipeId, currentUserId])

  const isOwner = recipe != null && recipe.createdBy != null && recipe.createdBy === currentUserId

  const handleMainProcessChanged = useCallback((mainProcessId: number) => {
    setRecipe((current) => (current ? { ...current, mainProcessId } : current))
  }, [])

  const handleNutritionSaved = useCallback((nutrition: NutritionInfo | null) => {
    setRecipe((current) => (current ? { ...current, nutrition } : current))
  }, [])

  if (loading) {
    return (
      <div className="flex h-full w-full items-center justify-center" style={{ color: 'var(--flow-text-muted)' }}>
        Loading recipe…
      </div>
    )
  }

  if (loadError || !recipe) {
    return (
      <div className="flex h-full w-full flex-col items-center justify-center gap-3" style={{ color: 'var(--flow-text-muted)' }}>
        <div>{loadError ?? 'This recipe could not be loaded.'}</div>
        <button
          onClick={onBack}
          style={{ padding: '8px 14px', borderRadius: 8, border: '1px solid var(--flow-border)', background: 'var(--flow-surface)', cursor: 'pointer' }}
        >
          ← Back to Recipes
        </button>
      </div>
    )
  }

  return (
    <RecipeSessionProvider recipeId={recipeId}>
      {/* `flow-canvas-container` strips .kitchen-body's ambient page padding/scroll (see
          KitchenPage.css) so the Recipe Process tab's RecipeProcessCanvas can use the full available
          area edge-to-edge — applied unconditionally (not only while that
          tab is active) since Ingredients/Nutrition already manage their own padding/scroll below
          and all three tabs stay mounted together (see the display:none toggling below). */}
      <div className="flow-canvas-container flex h-full w-full flex-col" style={{ background: 'var(--flow-surface-muted)' }}>
        <RecipeToolNavbar recipe={recipe} activeView={activeView} onChangeView={setActiveView} onBack={onBack} />

        {/* All three views stay mounted the whole time (visibility toggled, not conditional
            rendering) — switching tabs must not lose an in-progress canvas edit or an unsaved
            nutrition/ingredient edit (verification requirement: switching tabs preserves state).
            The shared RecipeSessionProvider above is what makes MAIN + every SUBPROCESS one
            unified in-memory recipe snapshot — Ingredients reads it directly, and Recipe Process
            (RecipeProcessView/RecipeProcessCanvas) reads and writes the same processes, so switching
            between processes or tabs never loses an unsaved edit or re-fetches what's already
            loaded. */}
        <div className="min-h-0 flex-1" style={{ display: activeView === 'RECIPE_PROCESS' ? 'flex' : 'none', flexDirection: 'column' }}>
          <RecipeProcessView recipeId={recipeId} isOwner={isOwner} onMainProcessChanged={handleMainProcessChanged} />
        </div>

        <div className="min-h-0 flex-1 overflow-y-auto p-5" style={{ display: activeView === 'INGREDIENTS' ? 'block' : 'none' }}>
          <div className="mx-auto w-full max-w-3xl">
            <IngredientsSection />
          </div>
        </div>

        <div className="min-h-0 flex-1 overflow-y-auto p-5" style={{ display: activeView === 'NUTRITION' ? 'block' : 'none' }}>
          <div className="mx-auto w-full max-w-3xl">
            <NutritionSection recipeId={recipeId} nutrition={recipe.nutrition} isOwner={isOwner} onSaved={handleNutritionSaved} />
          </div>
        </div>
      </div>
    </RecipeSessionProvider>
  )
}
