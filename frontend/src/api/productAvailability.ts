import { http } from './http'

export type DayOfWeek =
  | 'MONDAY'
  | 'TUESDAY'
  | 'WEDNESDAY'
  | 'THURSDAY'
  | 'FRIDAY'
  | 'SATURDAY'
  | 'SUNDAY'

export interface AvailabilityWindow {
  id: string
  dayOfWeek: DayOfWeek | null
  startTime: string
  endTime: string
}

export interface AvailabilityWindowPayload {
  dayOfWeek: DayOfWeek | null
  startTime: string
  endTime: string
}

export function listAvailabilityWindows(productId: string) {
  return http.get<AvailabilityWindow[]>(`/products/${productId}/availability-windows`).then((res) => res.data)
}

export function createAvailabilityWindow(productId: string, payload: AvailabilityWindowPayload) {
  return http.post<AvailabilityWindow>(`/products/${productId}/availability-windows`, payload).then((res) => res.data)
}

export function updateAvailabilityWindow(productId: string, windowId: string, payload: AvailabilityWindowPayload) {
  return http
    .put<AvailabilityWindow>(`/products/${productId}/availability-windows/${windowId}`, payload)
    .then((res) => res.data)
}

export function deleteAvailabilityWindow(productId: string, windowId: string) {
  return http.delete<void>(`/products/${productId}/availability-windows/${windowId}`).then((res) => res.data)
}
