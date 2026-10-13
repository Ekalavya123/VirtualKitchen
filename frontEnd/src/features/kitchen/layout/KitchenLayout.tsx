import { Outlet, useNavigate } from 'react-router-dom'
import type { User } from '../../../types/User'
import AppFooter from '../../../shared/components/AppFooter'
import KitchenNavbar from './KitchenNavbar'
import './KitchenLayout.css'

interface Kitchen {
  id: number
  name: string
  ownerId: number
}

interface KitchenPageProps {
  user: User
  kitchen: Kitchen
  onLogout: () => void
}

export default function KitchenLayout({
  user,
  kitchen,
  onLogout,
}: KitchenPageProps) {
  const navigate = useNavigate()

  // Navigate before clearing auth state so the /kitchen route guard
  // doesn't win the race and redirect to /auth instead of home.
  const handleLogoutAndGoHome = () => {
    navigate('/')
    onLogout()
  }
  return (
    <div className="kitchen-page">
      <KitchenNavbar
        user={user}
        kitchen={kitchen}
        onLogout={handleLogoutAndGoHome}
      />

      <div className="kitchen-body">
        <div className="kitchen-content">
          <Outlet />
        </div>
        <AppFooter />
      </div>
    </div>
  )
}