import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { adminLogin, adminResetPassword } from '../api/admin'
import { ADMIN_LOGOUT_EVENT } from './adminHttp'
import { clearAdminToken, getAdminToken, setAdminToken } from './adminTokenStorage'

interface AdminAuthContextValue {
  isAuthenticated: boolean
  login: (username: string, password: string) => Promise<void>
  resetPassword: (token: string, newPassword: string) => Promise<void>
  logout: () => void
}

const AdminAuthContext = createContext<AdminAuthContextValue | undefined>(undefined)

export function AdminAuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(getAdminToken())

  useEffect(() => {
    function handleForcedLogout() {
      setToken(null)
    }
    window.addEventListener(ADMIN_LOGOUT_EVENT, handleForcedLogout)
    return () => window.removeEventListener(ADMIN_LOGOUT_EVENT, handleForcedLogout)
  }, [])

  async function login(username: string, password: string) {
    const response = await adminLogin(username, password)
    setAdminToken(response.accessToken)
    setToken(response.accessToken)
  }

  async function resetPassword(token: string, newPassword: string) {
    const response = await adminResetPassword(token, newPassword)
    setAdminToken(response.accessToken)
    setToken(response.accessToken)
  }

  function logout() {
    clearAdminToken()
    setToken(null)
  }

  return (
    <AdminAuthContext.Provider value={{ isAuthenticated: token !== null, login, resetPassword, logout }}>
      {children}
    </AdminAuthContext.Provider>
  )
}

export function useAdminAuth(): AdminAuthContextValue {
  const context = useContext(AdminAuthContext)
  if (!context) {
    throw new Error('useAdminAuth must be used within an AdminAuthProvider')
  }
  return context
}
