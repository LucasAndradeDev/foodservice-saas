import { http } from './http'

export type ItemStatus = 'PENDING' | 'PREPARING' | 'READY' | 'DELIVERED' | 'CANCELLED'
export type DiscountType = 'FIXED' | 'PERCENTAGE'

export interface OrderItemModifier {
  groupName: string
  optionName: string
  priceDelta: number
}

export interface ApplyDiscountPayload {
  discountType: DiscountType | null
  discountValue?: number
  reason?: string
}

export interface OrderItem {
  id: string
  productId: string
  productName: string
  quantity: number
  unitPrice: number
  observation: string | null
  status: ItemStatus
  modifiers: OrderItemModifier[]
  subtotal: number
  discountType: DiscountType | null
  discountValue: number | null
  discountAmount: number
  discountReason: string | null
  discountAppliedBy: string | null
  discountAppliedAt: string | null
  cancelledBy: string | null
  cancelledAt: string | null
  netSubtotal: number
  isComboHeader: boolean
  children: OrderItem[]
}

export interface Order {
  id: string
  restaurantId: string
  tabId: string
  createdAt: string
  items: OrderItem[]
  total: number
  printedAt: string | null
}

export interface SlotSelectionPayload {
  slotId: string
  selectedProductId: string
}

export interface CreateOrderItemPayload {
  productId: string
  quantity: number
  observation?: string
  selectedOptionIds?: string[]
  slotSelections?: SlotSelectionPayload[]
}

export function listOrders(tabId: string) {
  return http.get<Order[]>(`/tabs/${tabId}/orders`).then((res) => res.data)
}

export function createOrder(tabId: string, items: CreateOrderItemPayload[]) {
  return http.post<Order>(`/tabs/${tabId}/orders`, { items }).then((res) => res.data)
}

export function getOrder(id: string) {
  return http.get<Order>(`/orders/${id}`).then((res) => res.data)
}

export function markOrderPrinted(id: string) {
  return http.patch<Order>(`/orders/${id}/print`).then((res) => res.data)
}

export function applyItemDiscount(itemId: string, payload: ApplyDiscountPayload) {
  return http.patch<OrderItem>(`/order-items/${itemId}/discount`, payload).then((res) => res.data)
}

export function transferItems(itemIds: string[], targetTabId: string) {
  return http.post<OrderItem[]>('/order-items/transfer', { itemIds, targetTabId }).then((res) => res.data)
}
