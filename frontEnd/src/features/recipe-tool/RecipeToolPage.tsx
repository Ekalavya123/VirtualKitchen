import { useCallback, useEffect, useState } from 'react'
import { Navigate } from 'react-router-dom'
import { RecipeApi, RecipeDetailApi } from '../../api'
import type { NutritionInfo, RecipeDetail } from '../../types/recipe'
import { RecipeSessionProvider } from './session/RecipeSessionContext'
import './editor/styles/RecipeProcessCanvas.css'
import RecipeToolNavbar from './components/RecipeToolNavbar'
import RecipeEditorView from './editor/components/RecipeEditorView'
import RecipeViewPage from './view/components/RecipeViewPage'
import { recipeToolPath, type RecipeToolView } from './recipeToolRoutes'
import { useStartRecipeOrder } from '../recipe-order/useStartRecipeOrder'
import EditRecipeDetailsModal, { type RecipeDetailsDraft } from '../recipes/components/EditRecipeDetailsModal'
import { useConfirm } from '../../shared/components/confirm/confirmContext'
import { useNotifications } from '../../shared/components/notifications/NotificationProvider'
import { browserStorage, clearRecovery } from './session/persistence/recoveryStore'
import { clearRecipeDrafts } from '../../shared/drafts/draftStore'

type RecipeToolPageProps = {
  recipeId: number
  currentUserId: number
  /** From the route; undefined on the bare `/tool` URL, which lands on the owner's editor or a reader's Recipe Process. */
  view?: RecipeToolView
  onBack: () => void
}

/**
 * The Recipe Tool page: an internal navbar (compact recipe metadata + the
 * two tool tabs) above a full-height content area showing one of two views
 * of the same recipe, each its own route (see recipeToolRoutes.ts):
 * - Recipe Editor — the React Flow process editor (RecipeEditorView), where
 *   the recipe is built and saved;
 * - Recipe Process — the human-readable recipe (ingredients, steps,
 *   nutrition) derived from that same process data (RecipeViewPage).
 * Both read the one RecipeSessionProvider below, so there's a single source
 * of truth and the Recipe Process always reflects the editor's latest content.
 */
export default function RecipeToolPage({ recipeId, currentUserId, view, onBack }: RecipeToolPageProps) {
  const [recipe, setRecipe] = useState<RecipeDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const { startOrder, orderingRecipeId } = useStartRecipeOrder()
  const confirm = useConfirm()
  const { notifySuccess, notifyError } = useNotifications()
  const [editingDetails, setEditingDetails] = useState(false)
  // The editor mounts on first visit and then stays mounted (hidden while reading the Recipe
  // Process), so switching tabs never loses its canvas state — undo history, selection, the open
  // process. Not mounted up front: React Flow shouldn't initialise inside a hidden container.
  const [editorMounted, setEditorMounted] = useState(view === 'editor')
  if (view === 'editor' && !editorMounted) setEditorMounted(true)

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
            // Not fatal: the Recipe Editor tab still offers "Create Main Process" / AI generation.
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

  // --- owner actions (the header's ⋯ menu) ---

  const saveDetails = async (details: RecipeDetailsDraft) => {
    const updated = await RecipeApi.updateRecipe(recipeId, currentUserId, details)
    setRecipe((current) => (current ? { ...current, name: updated.name, description: updated.description } : current))
    setEditingDetails(false)
    notifySuccess('Recipe details saved.')
  }

  const toggleVisibility = async () => {
    if (!recipe) return
    const makingPublic = recipe.visibility !== 'PUBLIC'
    const confirmed = await confirm({
      eyebrow: makingPublic ? 'Make public' : 'Make private',
      title: makingPublic ? 'Make this recipe public?' : 'Make this recipe private?',
      message: makingPublic
        ? 'Anyone browsing Global Recipes will be able to view this recipe and add a copy of it to their own kitchen. You can make it private again at any time.'
        : 'This recipe will be removed from Global Recipes and other users will no longer be able to find or add it. Copies already added to other kitchens will not be affected.',
      confirmLabel: makingPublic ? 'Make public' : 'Make private',
      tone: 'default',
    })
    if (!confirmed) return
    try {
      const updated = await RecipeApi.updateVisibility(recipeId, currentUserId, makingPublic ? 'PUBLIC' : 'PRIVATE')
      setRecipe((current) => (current ? { ...current, visibility: updated.visibility } : current))
      notifySuccess(makingPublic ? 'Recipe is now public.' : 'Recipe is now private.')
    } catch (error) {
      notifyError(error instanceof Error ? error.message : 'Unable to update recipe visibility')
    }
  }

  const deleteRecipe = async () => {
    if (!recipe) return
    const confirmed = await confirm({
      eyebrow: 'Delete recipe',
      title: `Delete "${recipe.name}"?`,
      message:
        'The recipe, its processes and step images will be permanently removed from your kitchen. Orders already placed keep their own copy. This cannot be undone.',
      confirmLabel: 'Delete recipe',
    })
    if (!confirmed) return
    try {
      await RecipeApi.deleteRecipe(recipeId, currentUserId)
      // Unsaved Recipe Tool changes left on this device for a deleted recipe can never be recovered.
      clearRecovery(browserStorage(), recipeId)
      clearRecipeDrafts(recipeId)
      notifySuccess('Recipe deleted.')
      onBack()
    } catch (error) {
      notifyError(error instanceof Error ? error.message : 'Unable to delete recipe')
    }
  }

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

  if (!view) {
    return <Navigate to={recipeToolPath(recipeId, isOwner ? 'editor' : 'process')} replace />
  }

  return (
    // Readers can look around the editor, but only the owner's edits are autosaved/kept for recovery.
    <RecipeSessionProvider recipeId={recipeId} canEdit={isOwner}>
      {/* `flow-canvas-container` strips .kitchen-body's ambient page padding/scroll (see
          KitchenLayout.css) so the editor's canvas can use the full available area edge-to-edge;
          the Recipe Process manages its own padding/scroll. */}
      <div className="flow-canvas-container flex h-full w-full flex-col" style={{ background: 'var(--flow-surface-muted)' }}>
        <RecipeToolNavbar
          recipe={recipe}
          onBack={onBack}
          onOrderRecipe={isOwner ? () => void startOrder(recipe.id, recipe.nutrition?.servings) : undefined}
          ordering={orderingRecipeId === recipe.id}
          onEditDetails={isOwner ? () => setEditingDetails(true) : undefined}
          onToggleVisibility={isOwner ? () => void toggleVisibility() : undefined}
          onDeleteRecipe={isOwner ? () => void deleteRecipe() : undefined}
        />

        <EditRecipeDetailsModal
          recipe={editingDetails ? recipe : null}
          onSave={saveDetails}
          onClose={() => setEditingDetails(false)}
        />

        {editorMounted && (
          <div className="min-h-0 flex-1" style={{ display: view === 'editor' ? 'flex' : 'none', flexDirection: 'column' }}>
            <RecipeEditorView recipeId={recipeId} recipeName={recipe.name} isOwner={isOwner} onMainProcessChanged={handleMainProcessChanged} />
          </div>
        )}

        {view === 'process' && (
          <RecipeViewPage recipe={recipe} isOwner={isOwner} onNutritionSaved={handleNutritionSaved} />
        )}
      </div>
    </RecipeSessionProvider>
  )
}
