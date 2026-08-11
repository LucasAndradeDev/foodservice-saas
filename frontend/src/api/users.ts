import type { UserRole } from '../auth/types'
import { http } from './http'

export interface StaffMember {
  id: string
  restaurantId: string
  name: string
  email: string
  role: UserRole
  active: boolean
}

export interface CreateUserPayload {
  name: string
  email: string
  role: UserRole
}

export interface UpdateUserPayload {
  name?: string
  role?: UserRole
  active?: boolean
}

export function listUsers(active?: boolean) {
  return http.get<StaffMember[]>('/users', { params: { active } }).then((res) => res.data)
}

export function createUser(payload: CreateUserPayload) {
  return http.post<StaffMember>('/users', payload).then((res) => res.data)
}

export function updateUser(id: string, payload: UpdateUserPayload) {
  return http.put<StaffMember>(`/users/${id}`, payload).then((res) => res.data)
}

export function sendPasswordResetLink(id: string) {
  return http.post<void>(`/users/${id}/password-reset-link`).then((res) => res.data)
}
