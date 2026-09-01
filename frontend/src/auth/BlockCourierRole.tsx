import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from './AuthContext'

// Guards the whole restaurant-management route subtree (AppLayout + the print routes) - a
// courier has no reason to be there and, per the backend's narrow SecurityConfig allow-list for
// COURIER, most of what it renders would 403 anyway. Sends them to their own screen instead.
export function BlockCourierRole() {
  const { user } = useAuth()

  if (user?.role === 'COURIER') {
    return <Navigate to="/my-deliveries" replace />
  }

  return <Outlet />
}
