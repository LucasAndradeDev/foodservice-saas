import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from './AuthContext'

// Guards the courier-only route subtree (/my-deliveries) - only reached after ProtectedRoute's
// auth check, so `user` is always set here.
export function RequireCourierRole() {
  const { user } = useAuth()

  if (user?.role !== 'COURIER') {
    return <Navigate to="/" replace />
  }

  return <Outlet />
}
