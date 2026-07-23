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
  primaryColor: string | null
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

export function clearStoredAuth(): void {
  localStorage.removeItem(STORAGE_KEY)
}
