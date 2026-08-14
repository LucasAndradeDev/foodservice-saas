import { useQueryClient } from '@tanstack/react-query'
import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { exchangeSsoToken } from '../api/auth'
import { AUTH_LOGOUT_EVENT } from '../api/http'
import {
  clearStoredSession,
  getAccessToken,
  getStoredSession,
  setAccessToken,
  setStoredSession,
  type StoredSession,
} from './sessionStorage'

interface AuthContextValue {
  restaurantName: string | null
  isAuthenticated: boolean
  exchangeHandoffToken: (token: string) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  // A stored session with no token (e.g. a previous logout mid-write) shouldn't read as
  // authenticated - both must be present.
  const initial = getAccessToken() ? getStoredSession() : null
  const [session, setSession] = useState<StoredSession | null>(initial)
  const queryClient = useQueryClient()

  useEffect(() => {
    function handleForcedLogout() {
      queryClient.clear()
      setSession(null)
    }
    window.addEventListener(AUTH_LOGOUT_EVENT, handleForcedLogout)
    return () => window.removeEventListener(AUTH_LOGOUT_EVENT, handleForcedLogout)
  }, [queryClient])

  async function exchangeHandoffToken(token: string) {
    const response = await exchangeSsoToken(token)
    queryClient.clear()
    setAccessToken(response.accessToken)
    const newSession: StoredSession = { restaurantName: response.restaurantName }
    setStoredSession(newSession)
    setSession(newSession)
  }

  function logout() {
    queryClient.clear()
    setAccessToken(null)
    clearStoredSession()
    setSession(null)
  }

  return (
    <AuthContext.Provider
      value={{
        restaurantName: session?.restaurantName ?? null,
        isAuthenticated: session !== null,
        exchangeHandoffToken,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}
