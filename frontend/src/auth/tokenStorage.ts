import type { UserRole } from './types'

export interface StoredUser {
  id: string
  restaurantId: string
  name: string
  email: string
  role: UserRole
  active: boolean
}

export interface StoredRestaurant {
  id: string
  name: string
  tradeName: string | null
  logo: string | null
}

interface StoredAuth {
  accessToken: string
  refreshToken: string
  user: StoredUser
  restaurant: StoredRestaurant
}

const STORAGE_KEY = 'restaurant_saas_auth'

export function getStoredAuth(): StoredAuth | null {
  const raw = localStorage.getItem(STORAGE_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as StoredAuth
  } catch {
    return null
  }
}

export function setStoredAuth(auth: StoredAuth): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(auth))
}

export function updateStoredTokens(accessToken: string, refreshToken: string): void {
  const current = getStoredAuth()
  if (!current) return
  setStoredAuth({ ...current, accessToken, refreshToken })
}

export function updateStoredRestaurant(restaurant: Partial<StoredRestaurant>): StoredRestaurant | null {
  const current = getStoredAuth()
  if (!current) return null
  const updated = { ...current.restaurant, ...restaurant }
  setStoredAuth({ ...current, restaurant: updated })
  return updated
}

export function clearStoredAuth(): void {
  localStorage.removeItem(STORAGE_KEY)
}
