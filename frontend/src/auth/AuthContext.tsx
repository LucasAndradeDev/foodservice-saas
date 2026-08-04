import { useQueryClient } from '@tanstack/react-query'
import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import {
  getMe,
  login as loginRequest,
  registerRestaurant as registerRestaurantRequest,
  resendVerificationEmail as resendVerificationEmailRequest,
  resetPassword as resetPasswordRequest,
  type AuthResponse,
  type RegisterRestaurantPayload,
} from '../api/auth'
import { AUTH_LOGOUT_EVENT } from '../api/http'
import {
  clearStoredAuth,
  getStoredAuth,
  setStoredAuth,
  updateStoredRestaurant,
  updateStoredUser,
  type StoredRestaurant,
  type StoredUser,
} from './tokenStorage'

interface AuthContextValue {
  user: StoredUser | null
  restaurant: StoredRestaurant | null
  isAuthenticated: boolean
  login: (email: string, password: string) => Promise<void>
  registerRestaurant: (payload: RegisterRestaurantPayload) => Promise<void>
  resetPassword: (token: string, newPassword: string) => Promise<void>
  updateRestaurant: (restaurant: Partial<StoredRestaurant>) => void
  refreshUser: () => Promise<void>
  resendVerificationEmail: () => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const initial = getStoredAuth()
  const [user, setUser] = useState<StoredUser | null>(initial?.user ?? null)
  const [restaurant, setRestaurant] = useState<StoredRestaurant | null>(initial?.restaurant ?? null)
  const queryClient = useQueryClient()

  useEffect(() => {
    function handleForcedLogout() {
      queryClient.clear()
      setUser(null)
      setRestaurant(null)
    }
    window.addEventListener(AUTH_LOGOUT_EVENT, handleForcedLogout)
    return () => window.removeEventListener(AUTH_LOGOUT_EVENT, handleForcedLogout)
  }, [queryClient])

  function applyAuthResponse(response: AuthResponse) {
    // Clear any cached data from a previous session/restaurant before switching —
    // query keys aren't scoped by restaurantId, so stale cross-tenant data would
    // otherwise flash briefly on the first visit to each screen.
    queryClient.clear()
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

  async function resetPassword(token: string, newPassword: string) {
    applyAuthResponse(await resetPasswordRequest(token, newPassword))
  }

  function logout() {
    queryClient.clear()
    clearStoredAuth()
    setUser(null)
    setRestaurant(null)
  }

  function updateRestaurant(partial: Partial<StoredRestaurant>) {
    const updated = updateStoredRestaurant(partial)
    if (updated) setRestaurant(updated)
  }

  async function refreshUser() {
    const response = await getMe()
    const updated = updateStoredUser(response.user)
    if (updated) setUser(updated)
  }

  async function resendVerificationEmail() {
    await resendVerificationEmailRequest()
  }

  return (
    <AuthContext.Provider
      value={{
        user,
        restaurant,
        isAuthenticated: user !== null,
        login,
        registerRestaurant,
        resetPassword,
        updateRestaurant,
        refreshUser,
        resendVerificationEmail,
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
