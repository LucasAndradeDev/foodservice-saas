import { http } from './http'

export type TableStatus = 'FREE' | 'OCCUPIED' | 'CLOSING'

export interface RestaurantTable {
  id: string
  restaurantId: string
  number: number
  status: TableStatus
  active: boolean
}

export interface TableFilters {
  status?: TableStatus
  active?: boolean
}

export function listTables(filters: TableFilters = {}) {
  return http.get<RestaurantTable[]>('/tables', { params: filters }).then((res) => res.data)
}

export function createTable(number?: number) {
  return http.post<RestaurantTable>('/tables', { number }).then((res) => res.data)
}

export function createTablesBulk(quantity: number) {
  return http.post<RestaurantTable[]>('/tables/bulk', { quantity }).then((res) => res.data)
}

export function updateTable(id: string, payload: { number?: number; active?: boolean }) {
  return http.put<RestaurantTable>(`/tables/${id}`, payload).then((res) => res.data)
}

export function updateTableStatus(id: string, status: TableStatus) {
  return http.patch<RestaurantTable>(`/tables/${id}/status`, { status }).then((res) => res.data)
}
