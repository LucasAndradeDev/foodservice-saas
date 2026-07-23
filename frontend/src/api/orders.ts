import { http } from './http'

export type ItemStatus = 'PENDING' | 'PREPARING' | 'READY' | 'DELIVERED' | 'CANCELLED'

export interface OrderItem {
  id: string
  productId: string
  productName: string
  quantity: number
  unitPrice: number
  observation: string | null
  status: ItemStatus
  subtotal: number
}

export interface Order {
  id: string
  restaurantId: string
  tabId: string
  createdAt: string
  items: OrderItem[]
  total: number
}

export interface CreateOrderItemPayload {
  productId: string
  quantity: number
  observation?: string
}

export function listOrders(tabId: string) {
  return http.get<Order[]>(`/tabs/${tabId}/orders`).then((res) => res.data)
}

export function createOrder(tabId: string, items: CreateOrderItemPayload[]) {
  return http.post<Order>(`/tabs/${tabId}/orders`, { items }).then((res) => res.data)
}
