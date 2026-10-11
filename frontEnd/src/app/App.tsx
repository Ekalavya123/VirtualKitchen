import { useEffect, useState } from 'react'
import {
  BrowserRouter,
  Navigate,
  Route,
  Routes,
  useLocation,
  useNavigate,
  useParams,
  useSearchParams,
} from 'react-router-dom'
import Auth from '../features/auth/Auth'
import KitchenLayout from '../features/kitchen/KitchenPage'
import InventoryView from '../features/kitchen/InventoryView'
import InventoryShopView from '../features/kitchen/InventoryShopView'
import OrderHistoryView from '../features/kitchen/OrderHistoryView'
import RecipeHomePage from '../features/recipes/RecipeHomePage'
import RecipeProcessEditor from '../features/recipe-tool/process/components/RecipeProcessEditor'
import RecipeProcessListPage from '../features/recipe-tool/process/components/RecipeProcessListPage'
import RecipeToolPage from '../features/recipe-tool/RecipeToolPage'
import { isRecipeToolView, recipeToolPath } from '../features/recipe-tool/recipeToolRoutes'
import { RecipeSessionProvider } from '../features/recipe-tool/context/RecipeSessionContext'
import IngredientCatalogGate from '../features/recipe-tool/catalog/IngredientCatalogGate'
import RecipeOrderPage from '../features/recipe-order/RecipeOrderPage'
import { recipeOrderPath } from '../features/recipe-order/model/orderView'
import HomePage from '../features/HomePage'
import type { User } from '../types/User'
import type { RecipeProcessBreadcrumbEntry } from '../types/recipe'
import { AuthenticationApi, KitchenApi, RecipeOrderApi } from '../api'
import { clearStoredToken, isAuthenticated } from '../shared/auth/session'
import { clearAllDrafts } from '../shared/drafts/draftStore'
import type { ShopResumeState } from '../shared/cart/cartStorage'
import '../App.css'

interface Kitchen {
  id: number
  name: string
  ownerId: number
}

function ShopRoute({ userId }: { userId: number }) {
  const navigate = useNavigate()
  const location = useLocation()
  const [searchParams] = useSearchParams()

  // `?resumeRecipeOrder=<id>`: shopping for a recipe order's missing ingredients.
  const resumeParam = Number(searchParams.get('resumeRecipeOrder'))
  const resumeOrderId = Number.isInteger(resumeParam) && resumeParam > 0 ? resumeParam : null
  const stateCode = (location.state as ShopResumeState | null)?.recipeOrderCode
  const [fetchedCode, setFetchedCode] = useState<{ orderId: number; code: string } | null>(null)

  useEffect(() => {
    // The code normally comes with the navigation; after a reload it's looked up.
    if (resumeOrderId == null || stateCode) return
    let cancelled = false
    RecipeOrderApi.get(resumeOrderId)
      .then((order) => {
        if (!cancelled) setFetchedCode({ orderId: resumeOrderId, code: order.orderCode })
      })
      .catch(() => {
        // The banner falls back to the order id.
      })
    return () => {
      cancelled = true
    }
  }, [resumeOrderId, stateCode])

  const resumeOrderCode = stateCode ?? (fetchedCode?.orderId === resumeOrderId ? fetchedCode.code : null)

  return (
    <InventoryShopView
      // Remount when the resume target changes, so the cart is re-read from storage.
      key={resumeOrderId ?? 'shop'}
      userId={userId}
      resumeRecipeOrder={resumeOrderId != null ? { orderId: resumeOrderId, orderCode: resumeOrderCode } : undefined}
      // Back to the order's ingredient check (which re-checks stock) instead of the order list.
      onOrderPlaced={() => navigate(resumeOrderId != null ? recipeOrderPath(resumeOrderId, 'ingredients') : '/kitchen/orders')}
    />
  )
}

function RecipeOrderRoute({ userId }: { userId: number }) {
  const { orderId: orderIdParam, view } = useParams()
  const orderId = Number(orderIdParam)

  if (!Number.isInteger(orderId) || orderId <= 0) {
    return (
      <Navigate
        to="/kitchen/orders"
        replace
      />
    )
  }

  // RecipeOrderPage itself validates `view` against the order's status (and redirects).
  return (
    <RecipeOrderPage
      key={orderId}
      orderId={orderId}
      view={view}
      userId={userId}
    />
  )
}

function RecipesRoute({ userId }: { userId: number }) {
  const navigate = useNavigate()

  // Opening (or just having created) a recipe goes straight to the Recipe Tool.
  return (
    <RecipeHomePage
      userId={userId}
      onCreateRecipe={(recipeId) => navigate(recipeToolPath(recipeId))}
    />
  )
}

function RecipeToolRoute({ currentUserId }: { currentUserId: number }) {
  const navigate = useNavigate()
  const { recipeId: recipeIdParam, view } = useParams()
  const recipeId = Number(recipeIdParam)

  if (!Number.isFinite(recipeId)) {
    return (
      <Navigate
        to="/kitchen/recipes"
        replace
      />
    )
  }

  // An unknown view segment falls back to the bare tool URL, which picks the default view.
  if (view !== undefined && !isRecipeToolView(view)) {
    return (
      <Navigate
        to={recipeToolPath(recipeId)}
        replace
      />
    )
  }

  return (
    <RecipeToolPage
      // Fresh mount per recipe (fresh initial state) rather than resetting in place — mirrors
      // RecipeProcessEditorRoute's own `key` below.
      key={recipeId}
      recipeId={recipeId}
      currentUserId={currentUserId}
      view={view}
      onBack={() => navigate('/kitchen/recipes')}
    />
  )
}

type RecipeProcessRouteState = {
  /** Ancestors from the recipe's process list down to this process's parent, root-first. */
  breadcrumb?: RecipeProcessBreadcrumbEntry[]
}

function RecipeProcessListRoute({ currentUserId }: { currentUserId: number }) {
  const navigate = useNavigate()
  const { recipeId: recipeIdParam } = useParams()
  const recipeId = Number(recipeIdParam)

  if (!Number.isFinite(recipeId)) {
    return (
      <Navigate
        to="/kitchen/recipes"
        replace
      />
    )
  }

  return (
    <RecipeProcessListPage
      recipeId={recipeId}
      currentUserId={currentUserId}
      onOpenProcess={(processId) => navigate(`/kitchen/recipes/${recipeId}/process/${processId}`)}
      onBack={() => navigate(recipeToolPath(recipeId, 'editor'))}
    />
  )
}

function RecipeProcessEditorRoute() {
  const navigate = useNavigate()
  const location = useLocation()
  const { recipeId: recipeIdParam, processId: processIdParam } = useParams()
  const recipeId = Number(recipeIdParam)
  const processId = Number(processIdParam)

  if (!Number.isFinite(recipeId) || !Number.isFinite(processId)) {
    return (
      <Navigate
        to="/kitchen/recipes"
        replace
      />
    )
  }

  // The navigation trail lives here, as router state, rather than in RecipeProcessCanvas or any app-level
  // store — "normal route/navigation state" per the brief. Each entry is an ancestor process this
  // editor was reached through; the process currently open is not included (RecipeProcessCanvas already
  // knows its own name once loaded).
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
  )
}

function HomeRoute({onLoginSuccess,}: { onLoginSuccess: (user: User, kitchen: Kitchen) => void}) {
  const navigate = useNavigate()
  const [showAuthModal, setShowAuthModal] = useState(false)

  const handleAuthSuccess = (user: User, kitchen: Kitchen) => {
    onLoginSuccess(user, kitchen)
    setShowAuthModal(false)
    navigate('/kitchen/inventory')
  }

  const hasSession = isAuthenticated()

  return (
    <>
      <HomePage
        onTryIt={() => {
          hasSession ? navigate('/kitchen/recipes') : setShowAuthModal(true)
        }}
        onLogin={() => setShowAuthModal(true)}
      />

      {showAuthModal && (
        <Auth
          onLoginSuccess={handleAuthSuccess}
          onClose={() => setShowAuthModal(false)}
        />
      )}
    </>
  )
}

function App() {
  const [currentUser, setCurrentUser] = useState<User | null>(null)
  const [currentKitchen, setCurrentKitchen] = useState<Kitchen | null>(null)
  const [isRestoringSession, setIsRestoringSession] = useState(true)
  const [inventoryFilter, setInventoryFilter] = useState<'ingredients' | 'equipment'>('ingredients')

  useEffect(() => {
    clearAllDrafts(undefined, { onlyExpired: true })

    const restoreSession = async () => {
      if (!isAuthenticated()) {
        setIsRestoringSession(false)
        return
      }

      try {
        const user = await AuthenticationApi.me()
        const kitchensData = await KitchenApi.getKitchenByOwnerId(user.id)
        const kitchens = Array.isArray(kitchensData) ? kitchensData : [kitchensData]
        const kitchen = kitchens[0]

        if (!kitchen) {
          throw new Error('No kitchen found for this user')
        }

        setCurrentUser(user)
        setCurrentKitchen(kitchen)
      } catch (error) {
        console.error('Session restore failed:', error)
        clearStoredToken()
        setCurrentUser(null)
        setCurrentKitchen(null)
      } finally {
        setIsRestoringSession(false)
      }
    }

    void restoreSession()
  }, [])

  const handleLoginSuccess = (user: User, kitchen: Kitchen) => {
    setCurrentUser(user)
    setCurrentKitchen(kitchen)
  }

  const handleLogout = () => {
    clearStoredToken()
    // Unsent drafts may hold recipe content; the next person on this browser shouldn't see them.
    clearAllDrafts()
    setCurrentUser(null)
    setCurrentKitchen(null)
  }

  if (isRestoringSession) {
    return null
  }

  const isLoggedIn = Boolean(
    currentUser && currentKitchen,
  )

  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<HomeRoute onLoginSuccess={handleLoginSuccess} />} />
        <Route
          path="/auth"
          element={
            isLoggedIn ? (
              <Navigate
                to="/kitchen/inventory"
                replace
              />
            ) : (
              <Auth onLoginSuccess={handleLoginSuccess} />
            )
          }
        />

        <Route
          path="/kitchen"
          element={
            isLoggedIn && currentUser && currentKitchen ? (
              <KitchenLayout
                user={currentUser}
                kitchen={currentKitchen}
                onLogout={handleLogout}
              />
            ) : (
              <Navigate
                to="/"
                replace
              />
            )
          }
        >
          <Route
            index
            element={
              <Navigate
                to="inventory"
                replace
              />
            }
          />

          <Route
            path="inventory"
            element={
              currentKitchen ? (
                <InventoryView
                  kitchenId={currentKitchen.id}
                  filter={inventoryFilter}
                  onFilterChange={setInventoryFilter}
                />
              ) : null
            }
          />

          <Route
            path="shop"
            element={
              currentUser ? (
                <ShopRoute userId={currentUser.id} />
              ) : null
            }
          />

          <Route
            path="recipes"
            element={
              currentUser ? (
                <RecipesRoute userId={currentUser.id} />
              ) : null
            }
          />

          <Route
            path="recipes/:recipeId"
            // Old links to a recipe (this path used to open a separate editor) land in the Recipe Tool.
            element={<Navigate to="tool" replace />}
          />

          {/* Recipe Tool: the Recipe Editor (tool/editor) and the Recipe Process (tool/process). */}
          <Route
            path="recipes/:recipeId/tool/:view?"
            element={
              currentUser ? (
                <IngredientCatalogGate>
                  <RecipeToolRoute currentUserId={currentUser.id} />
                </IngredientCatalogGate>
              ) : null
            }
          />
          <Route
            path="recipes/:recipeId/processes"
            element={
              currentUser ? (
                <IngredientCatalogGate>
                  <RecipeProcessListRoute currentUserId={currentUser.id} />
                </IngredientCatalogGate>
              ) : null
            }
          />
          <Route
            path="recipes/:recipeId/process/:processId"
            element={
              <IngredientCatalogGate>
                <RecipeProcessEditorRoute />
              </IngredientCatalogGate>
            }
          />

          {/* Recipe orders: confirm -> ingredients -> payment -> receipt -> track (see features/recipe-order). */}
          <Route
            path="recipe-orders/:orderId/:view?"
            element={
              currentUser ? (
                <IngredientCatalogGate>
                  <RecipeOrderRoute userId={currentUser.id} />
                </IngredientCatalogGate>
              ) : null
            }
          />

          <Route
            path="orders"
            element={
              currentUser ? (
                <OrderHistoryView
                  userId={currentUser.id}
                />
              ) : null
            }
          />
        </Route>

        <Route
          path="*"
          element={
            <Navigate
              to={
                isLoggedIn
                  ? '/kitchen/inventory'
                  : '/auth'
              }
              replace
            />
          }
        />
      </Routes>
    </BrowserRouter>
  )
}

export default App
