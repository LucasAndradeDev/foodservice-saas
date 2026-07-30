import { http } from './http'

export interface DiningArea {
  id: string
  restaurantId: string
  name: string
  displayOrder: number
}

export interface DiningAreaPayload {
  name: string
}

export function listDiningAreas() {
  return http.get<DiningArea[]>('/dining-areas').then((res) => res.data)
}

export function createDiningArea(payload: DiningAreaPayload) {
  return http.post<DiningArea>('/dining-areas', payload).then((res) => res.data)
}

export function updateDiningArea(id: string, payload: DiningAreaPayload) {
  return http.put<DiningArea>(`/dining-areas/${id}`, payload).then((res) => res.data)
}

export function deleteDiningArea(id: string) {
  return http.delete<void>(`/dining-areas/${id}`).then((res) => res.data)
}

export function reorderDiningAreas(orderedIds: string[]) {
  return http.put<DiningArea[]>('/dining-areas/reorder', { orderedIds }).then((res) => res.data)
}
