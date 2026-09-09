import { isAxiosError } from 'axios'
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

// Ids of tables already booked around this time (respecting the restaurant's block-before/after
// thresholds) - powers the manual table picker for parties too large to auto-assign.
export function listBlockedTables(reservationTime: string) {
  return http.get<string[]>('/reservations/blocked-tables', { params: { reservationTime } }).then((res) => res.data)
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

// Branches by HTTP status rather than passing the backend's own message through - that message is
// in English (project convention). 403 is genuinely "no table available" (ReservationService);
// 429 is the per-phone/IP rate limit; a 400 (bad party size, a past date slipping past
// DateTimePicker's own guard, etc.) used to get the same "no table available" text, hiding what
// was actually wrong (finding #8, 2026-09-07 review). Shared by both the customer-facing and
// staff-facing reservation forms - a past-time submission is the same class of mistake either way.
export function reservationErrorMessage(error: unknown): string {
  const status = isAxiosError(error) ? error.response?.status : undefined

  if (status === 429) {
    return 'Muitas tentativas. Aguarde alguns minutos e tente de novo.'
  }
  if (status === 400) {
    return 'Não foi possível fazer a reserva. Confira o número de pessoas e o horário escolhido.'
  }
  return 'Não há mesa disponível para esse horário e número de pessoas. Tente outro horário.'
}
