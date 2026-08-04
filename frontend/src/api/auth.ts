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

export function forgotPassword(email: string) {
  return http.post<void>('/auth/forgot-password', { email }).then((res) => res.data)
}

export function resetPassword(token: string, newPassword: string) {
  return http.post<AuthResponse>('/auth/reset-password', { token, newPassword }).then((res) => res.data)
}

export function getMe() {
  return http.get<AuthResponse>('/auth/me').then((res) => res.data)
}

export function verifyEmail(token: string) {
  return http.post<void>('/auth/verify-email', { token }).then((res) => res.data)
}

export function resendVerificationEmail() {
  return http.post<void>('/auth/resend-verification-email').then((res) => res.data)
}
