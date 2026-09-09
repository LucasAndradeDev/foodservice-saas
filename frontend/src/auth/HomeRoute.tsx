import { Navigate } from 'react-router-dom'
import { LandingPage } from '../pages/LandingPage'
import { useAuth } from './AuthContext'

// The root path is the public landing page for anonymous visitors, and a straight redirect to
// the dashboard for anyone already signed in - so a returning user never has to click through
// marketing copy to reach their restaurant.
export function HomeRoute() {
  const { isAuthenticated } = useAuth()

  if (isAuthenticated) {
    return <Navigate to="/dashboard" replace />
  }

  return <LandingPage />
}
