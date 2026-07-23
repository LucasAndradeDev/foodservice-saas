import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import {
  login as loginRequest,
  registerRestaurant as registerRestaurantRequest,
  type AuthResponse,
  type RegisterRestaurantPayload,
} from '../api/auth'
import { AUTH_LOGOUT_EVENT } from '../api/http'
import {
  clearStoredAuth,
  getStoredAuth,
  setStoredAuth,
  type StoredRestaurant,
  type StoredUser,
} from './tokenStorage'

interface AuthContextValue {
  user: StoredUser | null
  restaurant: StoredRestaurant | null
  isAuthenticated: boolean
  login: (email: string, password: string) => Promise<void>
  registerRestaurant: (payload: RegisterRestaurantPayload) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const initial = getStoredAuth()
  const [user, setUser] = useState<StoredUser | null>(initial?.user ?? null)
  const [restaurant, setRestaurant] = useState<StoredRestaurant | null>(initial?.restaurant ?? null)

  useEffect(() => {
    function handleForcedLogout() {
      setUser(null)
      setRestaurant(null)
    }
    window.addEventListener(AUTH_LOGOUT_EVENT, handleForcedLogout)
    return () => window.removeEventListener(AUTH_LOGOUT_EVENT, handleForcedLogout)
  }, [])

  function applyAuthResponse(response: AuthResponse) {
    setStoredAuth({
      accessToken: response.accessToken,
      refreshToken: response.refreshToken,
      user: response.user,
      restaurant: response.restaurant,
    })
    setUser(response.user)
    setRestaurant(response.restaurant)
  }

  async function login(email: string, password: string) {
    applyAuthResponse(await loginRequest(email, password))
  }

  async function registerRestaurant(payload: RegisterRestaurantPayload) {
    applyAuthResponse(await registerRestaurantRequest(payload))
  }

  function logout() {
    clearStoredAuth()
    setUser(null)
    setRestaurant(null)
  }

  return (
    <AuthContext.Provider
      value={{ user, restaurant, isAuthenticated: user !== null, login, registerRestaurant, logout }}
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
