import { http } from './http'
import type { ItemStatus, OrderItem, OrderItemModifier } from './orders'

export interface KitchenItem {
  id: string
  orderId: string
  tableNumbers: number[]
  productName: string
  quantity: number
  observation: string | null
  modifiers: OrderItemModifier[]
  status: ItemStatus
  createdAt: string
}

export function listKitchenQueue() {
  return http.get<KitchenItem[]>('/order-items').then((res) => res.data)
}

export function updateItemStatus(id: string, status: ItemStatus) {
  return http.patch<OrderItem>(`/order-items/${id}/status`, { status }).then((res) => res.data)
}
