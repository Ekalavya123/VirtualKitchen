import { useEffect, useState } from 'react'
import {
  BrowserRouter,
  Navigate,
  Route,
  Routes,
  useLocation,
  useNavigate,
  useParams,
} from 'react-router-dom'
import Auth from '../features/auth/Auth'
import KitchenLayout from '../features/kitchen/KitchenPage'
import InventoryView from '../features/kitchen/InventoryView'
import InventoryShopView from '../features/kitchen/InventoryShopView'
import OrderHistoryView from '../features/kitchen/OrderHistoryView'
import RecipeHomePage from '../features/flow-editor/RecipeHomePage'
import FlowEditor from '../features/flow-editor/FlowEditor'
import ProcessEditor from '../features/flow-editor/components/process/ProcessEditor'
import ProcessListPage from '../features/flow-editor/components/process/ProcessListPage'
import RecipeToolPage from '../features/recipe-tool/RecipeToolPage'
import HomePage from '../features/HomePage'
import type { User } from '../types/User'
import type { ProcessBreadcrumbEntry } from '../types/process'
import { AuthenticationApi, KitchenApi } from '../api'
import { clearStoredToken, isAuthenticated } from '../shared/auth/session'
import '../App.css'

interface Kitchen {
  id: number
  name: string
  ownerId: number
}

type RecipeRouteState = {
  recipeTitle?: string
}

function ShopRoute({ userId }: { userId: number }) {
  const navigate = useNavigate()

  return (
    <InventoryShopView
      userId={userId}
      onOrderPlaced={() => navigate('/kitchen/orders')}
    />
  )
}

function RecipesRoute({ userId }: { userId: number }) {
  const navigate = useNavigate()

  return (
    <RecipeHomePage
      userId={userId}
      onCreateRecipe={(recipeId, title) => {
        navigate(`/kitchen/recipes/${recipeId}`, {
          state: { recipeTitle: title } satisfies RecipeRouteState,
        })
      }}
      onOpenRecipeTool={(recipeId) => navigate(`/kitchen/recipes/${recipeId}/tool`)}
    />
  )
}

function RecipeToolRoute({ currentUserId }: { currentUserId: number }) {
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
    <RecipeToolPage
      // Fresh mount per recipe (fresh initial state) rather than resetting in place — mirrors
      // ProcessEditorRoute's own `key` below.
      key={recipeId}
      recipeId={recipeId}
      currentUserId={currentUserId}
      onBack={() => navigate('/kitchen/recipes')}
    />
  )
}

function RecipeEditorRoute() {
  const navigate = useNavigate()
  const location = useLocation()
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

  const state = location.state as RecipeRouteState | null

  return (
    <FlowEditor
      recipeId={recipeId}
      recipeTitle={state?.recipeTitle}
      onBackToRecipes={() =>
        navigate('/kitchen/recipes')
      }
    />
  )
}

type ProcessRouteState = {
  /** Ancestors from the recipe's process list down to this process's parent, root-first. */
  breadcrumb?: ProcessBreadcrumbEntry[]
}

function ProcessListRoute({ currentUserId }: { currentUserId: number }) {
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
    <ProcessListPage
      recipeId={recipeId}
      currentUserId={currentUserId}
      onOpenProcess={(processId) => navigate(`/kitchen/recipes/${recipeId}/process/${processId}`)}
      onBack={() => navigate(`/kitchen/recipes/${recipeId}/tool`)}
    />
  )
}

function ProcessEditorRoute() {
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

  // The navigation trail lives here, as router state, rather than in ProcessCanvas or any app-level
  // store — "normal route/navigation state" per the brief. Each entry is an ancestor process this
  // editor was reached through; the process currently open is not included (ProcessCanvas already
  // knows its own name once loaded).
  const breadcrumb = (location.state as ProcessRouteState | null)?.breadcrumb ?? []

  const goToProcess = (targetProcessId: number, nextBreadcrumb: ProcessBreadcrumbEntry[]) => {
    navigate(`/kitchen/recipes/${recipeId}/process/${targetProcessId}`, {
      state: { breadcrumb: nextBreadcrumb } satisfies ProcessRouteState,
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
    <ProcessEditor
      // Force a fresh mount per process — including when navigating into/out of a subprocess —
      // so ProcessCanvas's load effect and initial-viewport state always start clean rather than
      // trying to reset themselves in place.
      key={`${recipeId}:${processId}`}
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
            element={<RecipeEditorRoute />}
          />

          {/* New Recipe Tool (Phase 6): recipe summary/ingredients/nutrition/process, linking into
              the Phase 4+5 Process Builder rather than duplicating it. */}
          <Route
            path="recipes/:recipeId/tool"
            element={
              currentUser ? (
                <RecipeToolRoute currentUserId={currentUser.id} />
              ) : null
            }
          />
          <Route
            path="recipes/:recipeId/processes"
            element={
              currentUser ? (
                <ProcessListRoute currentUserId={currentUser.id} />
              ) : null
            }
          />
          <Route
            path="recipes/:recipeId/process/:processId"
            element={<ProcessEditorRoute />}
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
