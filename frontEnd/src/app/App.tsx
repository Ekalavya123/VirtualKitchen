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
import HomePage from '../features/HomePage'
import type { User } from '../types/User'
import { AuthApi, KitchenApi } from '../api'
import '../App.css'

interface Kitchen {
  id: number
  name: string
  ownerId: number
}

const SESSION_EMAIL_KEY = 'virtual-kitchen.session.email'

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

function HomeRoute({onLoginSuccess,}: { onLoginSuccess: (user: User, kitchen: Kitchen) => void}) {
  const navigate = useNavigate()
  const [showAuthModal, setShowAuthModal] = useState(false)

  const handleAuthSuccess = (user: User, kitchen: Kitchen) => {
    onLoginSuccess(user, kitchen)
    setShowAuthModal(false)
    navigate('/kitchen/inventory')
  }

  const isAuthenticated = Boolean(localStorage.getItem(SESSION_EMAIL_KEY)!== null)

  return (
    <>
      <HomePage
        onTryIt={() => {
          isAuthenticated ? navigate('/kitchen/recipes') : setShowAuthModal(true)
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
      const storedEmail = localStorage.getItem(SESSION_EMAIL_KEY)?.trim()

      if (!storedEmail) {
        setIsRestoringSession(false)
        return
      }

      try {
        const user = await AuthApi.getUserByEmail(storedEmail)
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
        localStorage.removeItem(SESSION_EMAIL_KEY)
        setCurrentUser(null)
        setCurrentKitchen(null)
      } finally {
        setIsRestoringSession(false)
      }
    }

    void restoreSession()
  }, [])

  const handleLoginSuccess = (user: User, kitchen: Kitchen) => {
    localStorage.setItem(SESSION_EMAIL_KEY, user.email)
    setCurrentUser(user)
    setCurrentKitchen(kitchen)
  }

  const handleLogout = () => {
    localStorage.removeItem(SESSION_EMAIL_KEY)
    setCurrentUser(null)
    setCurrentKitchen(null)
  }

  if (isRestoringSession) {
    return null
  }

  const isAuthenticated = Boolean(
    currentUser && currentKitchen,
  )

  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<HomeRoute onLoginSuccess={handleLoginSuccess} />} />
        <Route
          path="/auth"
          element={
            isAuthenticated ? (
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
            isAuthenticated && currentUser && currentKitchen ? (
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
                isAuthenticated
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
