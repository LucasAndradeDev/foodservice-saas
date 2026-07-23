import { http } from './http'
import type { StoredRestaurant, StoredUser } from '../auth/tokenStorage'

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  user: StoredUser
  restaurant: StoredRestaurant
}

export function login(email: string, password: string) {
  return http.post<AuthResponse>('/auth/login', { email, password }).then((res) => res.data)
}

export interface RegisterRestaurantPayload {
  restaurantName: string
  cnpj?: string
  phone?: string
  address?: string
  ownerName: string
  ownerEmail: string
  ownerPassword: string
}

export function registerRestaurant(payload: RegisterRestaurantPayload) {
  return http.post<AuthResponse>('/auth/register-restaurant', payload).then((res) => res.data)
}
