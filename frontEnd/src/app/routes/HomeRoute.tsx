import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import Auth from '../../features/auth/Auth'
import HomePage from '../../features/home/HomePage'
import type { Kitchen } from '../../types/kitchen'
import type { User } from '../../types/User'

/** The landing page; "Try it" and "Log in" open the sign-in dialog unless the visitor is already signed in. */
export default function HomeRoute({ signedIn, onLoginSuccess }: {
  signedIn: boolean
  onLoginSuccess: (user: User, kitchen: Kitchen) => void
}) {
  const navigate = useNavigate()
  const [showAuthModal, setShowAuthModal] = useState(false)

  const handleAuthSuccess = (user: User, kitchen: Kitchen) => {
    onLoginSuccess(user, kitchen)
    setShowAuthModal(false)
    navigate('/kitchen/inventory')
  }

  const tryIt = () => {
    if (signedIn) {
      navigate('/kitchen/recipes')
    } else {
      setShowAuthModal(true)
    }
  }

  return (
    <>
      <HomePage onTryIt={tryIt} onLogin={() => setShowAuthModal(true)} />
      {showAuthModal && <Auth onLoginSuccess={handleAuthSuccess} onClose={() => setShowAuthModal(false)} />}
    </>
  )
}
