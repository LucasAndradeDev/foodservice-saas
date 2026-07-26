import { http } from './http'

export type TableRequestType = 'CALL_WAITER' | 'REQUEST_BILL'

export interface TableRequest {
  id: string
  tableId: string
  type: TableRequestType
  requestedAt: string
  acknowledgedAt: string | null
}

export function createTableRequest(slug: string, tableId: string, type: TableRequestType) {
  return http
    .post<TableRequest>(`/public/menu/${slug}/tables/${tableId}/requests`, { type })
    .then((res) => res.data)
}

export function listPendingTableRequests() {
  return http.get<TableRequest[]>('/table-requests').then((res) => res.data)
}

export function acknowledgeTableRequest(id: string) {
  return http.patch<TableRequest>(`/table-requests/${id}/acknowledge`).then((res) => res.data)
}
