import { useQueryClient } from '@tanstack/react-query'
import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import {
  getMe,
  login as loginRequest,
  logout as logoutRequest,
  registerRestaurant as registerRestaurantRequest,
  resendVerificationEmail as resendVerificationEmailRequest,
  resetPassword as resetPasswordRequest,
  type AuthResponse,
  type RegisterRestaurantPayload,
} from '../api/auth'
import { AUTH_LOGOUT_EVENT, refreshAccessToken } from '../api/http'
import {
  clearStoredProfile,
  getStoredProfile,
  setAccessToken,
  setStoredProfile,
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
  const initial = getStoredProfile()
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

  useEffect(() => {
    // The access token is memory-only, so it's gone on every page load - warm it back up here
    // using the httpOnly refresh cookie so the first real request doesn't have to eat a
    // 401-then-retry round trip first. Only bother when there's a cached profile to begin with,
    // so a genuinely logged-out visitor on a public page doesn't fire a pointless request. Not
    // gating rendering on this: the cached profile below is shown optimistically, and the normal
    // 401 => refresh => retry flow in api/http.ts is the real safety net if this fails silently
    // or a request races ahead of it.
    if (!initial) return
    refreshAccessToken()
      .then(setAccessToken)
      .catch(() => {
        clearStoredProfile()
        setUser(null)
        setRestaurant(null)
      })
    // Intentionally runs once on mount only.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  function applyAuthResponse(response: AuthResponse) {
    // Clear any cached data from a previous session/restaurant before switching —
    // query keys aren't scoped by restaurantId, so stale cross-tenant data would
    // otherwise flash briefly on the first visit to each screen.
    queryClient.clear()
    setAccessToken(response.accessToken)
    setStoredProfile({ user: response.user, restaurant: response.restaurant })
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
    // Revoke the refresh-token cookie server-side - best-effort, since local logout must succeed
    // regardless of whether the network call does (e.g. offline, token already expired).
    logoutRequest().catch(() => {})
    queryClient.clear()
    setAccessToken(null)
    clearStoredProfile()
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
