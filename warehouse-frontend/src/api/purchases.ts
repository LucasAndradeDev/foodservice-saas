import { http } from './http'

export interface PurchaseItem {
  id: string
  ingredientId: string
  ingredientName: string
  quantity: number
  unitCost: number | null
}

export interface Purchase {
  id: string
  supplierId: string
  supplierName: string
  purchasedAt: string
  createdAt: string
  items: PurchaseItem[]
}

export interface CreatePurchaseItemPayload {
  ingredientId: string
  quantity: number
  unitCost?: number
}

export interface CreatePurchasePayload {
  supplierId: string
  items: CreatePurchaseItemPayload[]
}

export function listPurchases() {
  return http.get<Purchase[]>('/purchases').then((res) => res.data)
}

export function createPurchase(payload: CreatePurchasePayload) {
  return http.post<Purchase>('/purchases', payload).then((res) => res.data)
}
