import { http } from './http'

export interface WarehouseHandoffResponse {
  handoffUrl: string
}

export function startWarehouseHandoff() {
  return http.post<WarehouseHandoffResponse>('/warehouse/handoff').then((res) => res.data)
}
