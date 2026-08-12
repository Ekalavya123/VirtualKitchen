import { Outlet } from 'react-router-dom'
import type { User } from '../../types/User'
import KitchenNavbar from './KitchenNavbar'
import './KitchenPage.css'

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
  return (
    <div className="kitchen-page">
      <KitchenNavbar
        user={user}
        kitchen={kitchen}
        onLogout={onLogout}
      />

      <div className="kitchen-body">
        <Outlet />
        <footer className="kitchen-footer">
          <div className="footer-content">
            <p>&copy; 2024 Virtual Kitchen. All rights reserved.</p>
            <div className="footer-links">
              <a href="#privacy">Privacy Policy</a>
              <span>•</span>
              <a href="#terms">Terms of Service</a>
              <span>•</span>
              <a href="#contact">Contact Us</a>
            </div>
          </div>
        </footer>
      </div>
    </div>
  )
}