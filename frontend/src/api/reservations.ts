import { http } from './http'

export type ReservationStatus = 'SCHEDULED' | 'SEATED' | 'CANCELLED' | 'NO_SHOW'

export const RESERVATION_STATUS_LABELS: Record<ReservationStatus, string> = {
  SCHEDULED: 'Agendada',
  SEATED: 'Sentou',
  CANCELLED: 'Cancelada',
  NO_SHOW: 'Não compareceu',
}

export interface ReservationTableSummary {
  id: string
  number: number
}

export interface Reservation {
  id: string
  restaurantId: string
  customerName: string
  customerPhone: string
  note: string | null
  partySize: number
  reservationTime: string
  status: ReservationStatus
  accessToken: string
  tabId: string | null
  tables: ReservationTableSummary[]
  createdAt: string
}

export interface CreateReservationPayload {
  customerName: string
  customerPhone: string
  note?: string
  partySize: number
  reservationTime: string
  /** Omit to let the system auto-assign the smallest table (or best pair) with enough capacity. */
  tableIds?: string[]
}

export function listReservations(date: string) {
  return http.get<Reservation[]>('/reservations', { params: { date } }).then((res) => res.data)
}

export function createReservation(payload: CreateReservationPayload) {
  return http.post<Reservation>('/reservations', payload).then((res) => res.data)
}

export function checkInReservation(id: string) {
  return http.patch<Reservation>(`/reservations/${id}/check-in`).then((res) => res.data)
}

export function cancelReservation(id: string) {
  return http.patch<Reservation>(`/reservations/${id}/cancel`).then((res) => res.data)
}

export interface PublicCreateReservationPayload {
  customerName: string
  customerPhone: string
  note?: string
  partySize: number
  reservationTime: string
}

export function createPublicReservation(slug: string, payload: PublicCreateReservationPayload) {
  return http.post<Reservation>(`/public/menu/${slug}/reservations`, payload).then((res) => res.data)
}

export function getReservationByToken(token: string) {
  return http.get<Reservation>(`/public/reservations/${token}`).then((res) => res.data)
}

export function cancelReservationByToken(token: string) {
  return http.delete<Reservation>(`/public/reservations/${token}`).then((res) => res.data)
}
