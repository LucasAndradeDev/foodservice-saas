import type { UserRole } from './types'

export interface StoredUser {
  id: string
  restaurantId: string
  name: string
  email: string
  role: UserRole
  active: boolean
  emailVerified: boolean
}

export interface StoredRestaurant {
  id: string
  name: string
  tradeName: string | null
  logo: string | null
  paymentDueDate: string | null
}

interface StoredProfile {
  user: StoredUser
  restaurant: StoredRestaurant
}

const PROFILE_STORAGE_KEY = 'restaurant_saas_profile'

// Every entry written under this key before the httpOnly-cookie migration held the raw
// access/refresh tokens in the clear. Purge it once on load so a browser that already has this
// app open doesn't keep carrying live credentials in localStorage indefinitely.
const LEGACY_AUTH_STORAGE_KEY = 'restaurant_saas_auth'
localStorage.removeItem(LEGACY_AUTH_STORAGE_KEY)

// The access token lives in memory only, never in localStorage/sessionStorage - an XSS payload
// that runs a one-off `localStorage.getItem(...)` can no longer walk off with it. It's still
// reachable by a payload that runs *while* the app is live (e.g. by monkey-patching fetch), but
// that's a meaningfully smaller and harder-to-pull-off attack than exfiltrating a persisted
// value. The refresh token is stronger still: it never reaches JS at all, see ../api/http.ts.
let accessToken: string | null = null

export function getAccessToken(): string | null {
  return accessToken
}

export function setAccessToken(token: string | null): void {
  accessToken = token
}

export function getStoredProfile(): StoredProfile | null {
  const raw = localStorage.getItem(PROFILE_STORAGE_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as StoredProfile
  } catch {
    return null
  }
}

export function setStoredProfile(profile: StoredProfile): void {
  localStorage.setItem(PROFILE_STORAGE_KEY, JSON.stringify(profile))
}

export function updateStoredRestaurant(restaurant: Partial<StoredRestaurant>): StoredRestaurant | null {
  const current = getStoredProfile()
  if (!current) return null
  const updated = { ...current.restaurant, ...restaurant }
  setStoredProfile({ ...current, restaurant: updated })
  return updated
}

export function updateStoredUser(user: Partial<StoredUser>): StoredUser | null {
  const current = getStoredProfile()
  if (!current) return null
  const updated = { ...current.user, ...user }
  setStoredProfile({ ...current, user: updated })
  return updated
}

export function clearStoredProfile(): void {
  localStorage.removeItem(PROFILE_STORAGE_KEY)
}
