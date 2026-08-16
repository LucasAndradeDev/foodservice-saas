import { http } from './http'

export interface DeliveryZone {
  id: string
  restaurantId: string
  neighborhood: string
  fee: number
  active: boolean
}

export interface CreateDeliveryZonePayload {
  neighborhood: string
  fee: number
}

export interface UpdateDeliveryZonePayload {
  neighborhood: string
  fee: number
  active: boolean
}

export function listDeliveryZones() {
  return http.get<DeliveryZone[]>('/delivery-zones').then((res) => res.data)
}

export function createDeliveryZone(payload: CreateDeliveryZonePayload) {
  return http.post<DeliveryZone>('/delivery-zones', payload).then((res) => res.data)
}

export function updateDeliveryZone(id: string, payload: UpdateDeliveryZonePayload) {
  return http.put<DeliveryZone>(`/delivery-zones/${id}`, payload).then((res) => res.data)
}

export function deleteDeliveryZone(id: string) {
  return http.delete<void>(`/delivery-zones/${id}`).then((res) => res.data)
}
