import { useEffect, useState, type ReactNode } from 'react'
import { Navigate, useLocation, useNavigate, useParams } from 'react-router-dom'
import { RecipeDetailApi } from '../../api'
import { useStartRecipeOrder } from '../../features/recipe-order'
import {
  isRecipeToolView,
  RecipeProcessEditor,
  RecipeProcessListPage,
  RecipeSessionProvider,
  RecipeToolPage,
  recipeToolPath,
} from '../../features/recipe-tool'
import RecipeLibraryPage from '../../features/recipes/RecipeLibraryPage'
import type { RecipeProcessBreadcrumbEntry } from '../../types/recipe'
import { useSignedIn } from '../session/signedIn'

const toLibrary = <Navigate to="/kitchen/recipes" replace />

/**
 * The standalone process pages edit a recipe, so only its owner gets them; anyone else reading a public recipe
 * is sent to its read view (the Recipe Process), the same as the Recipe Tool's editor tab does.
 */
function OwnerOnly({ recipeId, children }: { recipeId: number; children: ReactNode }) {
  const { user } = useSignedIn()
  const [owner, setOwner] = useState<{ recipeId: number; isOwner: boolean } | null>(null)

  useEffect(() => {
    let cancelled = false
    RecipeDetailApi.getRecipeDetail(recipeId)
      .then((recipe) => { if (!cancelled) setOwner({ recipeId, isOwner: recipe.createdBy === user.id }) })
      // A recipe that can't be loaded is left to the page, which shows its own error.
      .catch(() => { if (!cancelled) setOwner({ recipeId, isOwner: true }) })
    return () => { cancelled = true }
  }, [recipeId, user.id])

  if (owner?.recipeId !== recipeId) {
    return (
      <div className="flex h-full w-full items-center justify-center" style={{ color: 'var(--flow-text-muted)' }}>
        Loading recipe…
      </div>
    )
  }
  if (!owner.isOwner) return <Navigate to={recipeToolPath(recipeId, 'process')} replace />
  return children
}

/** The recipe library; opening a recipe shows it in read mode (Recipe Process), a just-created one opens in the editor. */
export function RecipesRoute() {
  const { user } = useSignedIn()
  const navigate = useNavigate()
  return (
    <RecipeLibraryPage
      userId={user.id}
      onOpenRecipe={(recipeId) => navigate(recipeToolPath(recipeId, 'process'))}
      onCreateRecipe={(recipeId) => navigate(recipeToolPath(recipeId, 'editor'))}
    />
  )
}

/** The Recipe Tool: the Recipe Editor (`tool/editor`) and the Recipe Process (`tool/process`). */
export function RecipeToolRoute() {
  const { user } = useSignedIn()
  const navigate = useNavigate()
  const { startOrder, orderingRecipeId } = useStartRecipeOrder()
  const { recipeId: recipeIdParam, view } = useParams()
  const recipeId = Number(recipeIdParam)

  if (!Number.isFinite(recipeId)) return toLibrary

  // An unknown view segment falls back to the bare tool URL, which picks the default view.
  if (view !== undefined && !isRecipeToolView(view)) {
    return <Navigate to={recipeToolPath(recipeId)} replace />
  }

  return (
    <RecipeToolPage
      // Fresh mount per recipe (fresh initial state) rather than resetting in place — mirrors
      // RecipeProcessEditorRoute's own `key` below.
      key={recipeId}
      recipeId={recipeId}
      currentUserId={user.id}
      view={view}
      onBack={() => navigate('/kitchen/recipes')}
      onOrderRecipe={(id, servings) => void startOrder(id, servings)}
      orderingRecipeId={orderingRecipeId}
    />
  )
}

type RecipeProcessRouteState = {
  /** Ancestors from the recipe's process list down to this process's parent, root-first. */
  breadcrumb?: RecipeProcessBreadcrumbEntry[]
}

/** A recipe's processes, each opening in the standalone process editor. */
export function RecipeProcessListRoute() {
  const { user } = useSignedIn()
  const navigate = useNavigate()
  const { recipeId: recipeIdParam } = useParams()
  const recipeId = Number(recipeIdParam)

  if (!Number.isFinite(recipeId)) return toLibrary

  return (
    <OwnerOnly recipeId={recipeId}>
      <RecipeProcessListPage
        recipeId={recipeId}
        currentUserId={user.id}
        onOpenProcess={(processId) => navigate(`/kitchen/recipes/${recipeId}/process/${processId}`)}
        onBack={() => navigate(recipeToolPath(recipeId, 'editor'))}
      />
    </OwnerOnly>
  )
}

/** The standalone editor for one process, with a breadcrumb back through the processes it was opened from. */
export function RecipeProcessEditorRoute() {
  const navigate = useNavigate()
  const location = useLocation()
  const { recipeId: recipeIdParam, processId: processIdParam } = useParams()
  const recipeId = Number(recipeIdParam)
  const processId = Number(processIdParam)

  if (!Number.isFinite(recipeId) || !Number.isFinite(processId)) return toLibrary

  // The navigation trail lives here, as router state, rather than in RecipeProcessCanvas or any app-level
  // store. Each entry is an ancestor process this editor was reached through; the process currently open is
  // not included (RecipeProcessCanvas already knows its own name once loaded).
  const breadcrumb = (location.state as RecipeProcessRouteState | null)?.breadcrumb ?? []

  const goToProcess = (targetProcessId: number, nextBreadcrumb: RecipeProcessBreadcrumbEntry[]) => {
    navigate(`/kitchen/recipes/${recipeId}/process/${targetProcessId}`, {
      state: { breadcrumb: nextBreadcrumb } satisfies RecipeProcessRouteState,
    })
  }

  const goToList = () => navigate(`/kitchen/recipes/${recipeId}/processes`)

  const goBack = () => {
    if (breadcrumb.length === 0) {
      goToList()
      return
    }
    const parent = breadcrumb[breadcrumb.length - 1]
    goToProcess(parent.processId, breadcrumb.slice(0, -1))
  }

  return (
    // Keyed only by recipeId (not processId): navigating into/out of a subprocess here is the same
    // "one recipe, one editing session" model as the embedded Recipe Tool tab — RecipeProcessCanvas itself
    // handles switching which process is displayed without losing another process's unsaved edits or
    // re-fetching what's already loaded. A different recipe id (a real route change) still gets a
    // fresh session; the same recipeId across a process-to-process navigation does not remount.
    <OwnerOnly recipeId={recipeId}>
      <RecipeSessionProvider key={recipeId} recipeId={recipeId}>
        <RecipeProcessEditor
          recipeId={recipeId}
          processId={processId}
          breadcrumbAncestors={breadcrumb}
          onNavigateToList={goToList}
          onNavigateToAncestor={(index) => goToProcess(breadcrumb[index].processId, breadcrumb.slice(0, index))}
          onOpenSubprocess={(subprocessId, currentProcessName) =>
            goToProcess(subprocessId, [...breadcrumb, { processId, name: currentProcessName }])
          }
          onBack={goBack}
        />
      </RecipeSessionProvider>
    </OwnerOnly>
  )
}
