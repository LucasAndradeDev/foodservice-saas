import type { UserRole } from '../auth/types'
import { http } from './http'

export type CourierVehicleType = 'MOTORCYCLE' | 'BICYCLE' | 'CAR' | 'ON_FOOT'

export const COURIER_VEHICLE_TYPE_LABELS: Record<CourierVehicleType, string> = {
  MOTORCYCLE: 'Moto',
  BICYCLE: 'Bicicleta',
  CAR: 'Carro',
  ON_FOOT: 'A pé',
}

export interface StaffMember {
  id: string
  restaurantId: string
  name: string
  email: string
  role: UserRole
  active: boolean
  // Only populated for role = COURIER.
  phone?: string | null
  vehicleType?: CourierVehicleType | null
  notes?: string | null
}

export interface CreateUserPayload {
  name: string
  email: string
  role: UserRole
  phone?: string
  vehicleType?: CourierVehicleType
  notes?: string
}

export interface UpdateUserPayload {
  name?: string
  role?: UserRole
  active?: boolean
  phone?: string
  vehicleType?: CourierVehicleType
  notes?: string
}

export function listUsers(params?: { active?: boolean; role?: UserRole }) {
  return http.get<StaffMember[]>('/users', { params }).then((res) => res.data)
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
