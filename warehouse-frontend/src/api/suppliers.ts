import { http } from './http'

export interface Supplier {
  id: string
  name: string
  contact: string | null
  active: boolean
}

export interface SupplierPayload {
  name: string
  contact?: string
}

export function listSuppliers(active?: boolean) {
  return http.get<Supplier[]>('/suppliers', { params: { active } }).then((res) => res.data)
}

export function createSupplier(payload: SupplierPayload) {
  return http.post<Supplier>('/suppliers', payload).then((res) => res.data)
}

export function updateSupplier(id: string, payload: Partial<SupplierPayload & { active: boolean }>) {
  return http.put<Supplier>(`/suppliers/${id}`, payload).then((res) => res.data)
}
