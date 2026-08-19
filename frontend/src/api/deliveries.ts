import { http } from './http'

export type DeliveryStatus = 'SEPARATING' | 'OUT_FOR_DELIVERY' | 'DELIVERED'

export const DELIVERY_STATUS_LABELS: Record<DeliveryStatus, string> = {
  SEPARATING: 'Separando',
  OUT_FOR_DELIVERY: 'Saiu pra entrega',
  DELIVERED: 'Entregue',
}

export const DELIVERY_NEXT_STATUS: Partial<Record<DeliveryStatus, DeliveryStatus>> = {
  SEPARATING: 'OUT_FOR_DELIVERY',
  OUT_FOR_DELIVERY: 'DELIVERED',
}

export const DELIVERY_NEXT_STATUS_LABELS: Partial<Record<DeliveryStatus, string>> = {
  SEPARATING: 'Saiu pra entrega',
  OUT_FOR_DELIVERY: 'Marcar como entregue',
}

export const DELIVERY_STATUS_STYLES: Record<DeliveryStatus, string> = {
  SEPARATING: 'bg-teal-100 text-teal-700 dark:bg-teal-500/10 dark:text-teal-400',
  OUT_FOR_DELIVERY: 'bg-brand-100 text-brand-700 dark:bg-brand-500/10 dark:text-brand-400',
  DELIVERED: 'bg-sage-100 text-sage-700 dark:bg-sage-500/10 dark:text-sage-400',
}

export const DELIVERY_ACCENT_STYLES: Record<DeliveryStatus, string> = {
  SEPARATING: 'bg-teal-500',
  OUT_FOR_DELIVERY: 'bg-brand-500',
  DELIVERED: 'bg-sage-500',
}

// Customer-facing copy for the tracking page (DeliveryStatusPage) - warmer than the staff-facing
// DELIVERY_STATUS_LABELS badge text, since this is the one place a customer actually reads it.
export const DELIVERY_STATUS_MESSAGES: Record<DeliveryStatus, string> = {
  SEPARATING: 'O restaurante está preparando seu pedido.',
  OUT_FOR_DELIVERY: 'Seu pedido saiu pra entrega!',
  DELIVERED: 'Pedido entregue. Bom apetite!',
}

export interface DeliveryItem {
  productName: string
  quantity: number
  unitPrice: number
}

export interface DeliveryDetails {
  id: string
  tabId: string
  status: DeliveryStatus
  kitchenReady: boolean
  paid: boolean
  customerName: string
  customerPhone: string
  restaurantSlug: string
  restaurantName: string
  restaurantPhone: string | null
  street: string
  number: string
  complement: string | null
  neighborhood: string
  city: string
  zipCode: string | null
  referencePoint: string | null
  deliveryFee: number
  items: DeliveryItem[]
  billTotal: number | null
  createdAt: string
  updatedAt: string
}

export function listOpenDeliveries() {
  return http.get<DeliveryDetails[]>('/deliveries').then((res) => res.data)
}

export function updateDeliveryStatus(tabId: string, status: DeliveryStatus) {
  return http.patch<DeliveryDetails>(`/deliveries/${tabId}/status`, { status }).then((res) => res.data)
}
