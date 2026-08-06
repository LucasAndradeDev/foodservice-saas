import { Navigate, Outlet } from 'react-router-dom'
import { useAdminAuth } from './AdminAuthContext'

export function AdminProtectedRoute() {
  const { isAuthenticated } = useAdminAuth()

  if (!isAuthenticated) {
    return <Navigate to="/admin/login" replace />
  }

  return <Outlet />
}
