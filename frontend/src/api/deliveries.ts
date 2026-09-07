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
  // Staff-facing transparency only (task 26.5) - null for orders priced by neighborhood.
  deliveryDistanceKm: number | null
  courierId: string | null
  courierName: string | null
  // Null unless OUT_FOR_DELIVERY and the courier has reported a position recently - never needs
  // staleness handling on this side, the backend already only sends it when it's worth showing.
  // Rounded to ~100-150m on this (public) endpoint; exact on the authenticated staff/courier ones.
  courierLatitude: number | null
  courierLongitude: number | null
  // Live route-based ETA, refreshed server-side at most once a minute - same null conditions as
  // courierLatitude/Longitude above, plus whenever the order was priced by neighborhood (no
  // geocoded customer point to route to) or no routing provider was available.
  etaMinutes: number | null
  items: DeliveryItem[]
  billTotal: number | null
  createdAt: string
  updatedAt: string
}

export interface AssignableCourier {
  id: string
  name: string
  active: boolean
}

export interface CourierLiveLocation {
  id: string
  name: string
  latitude: number
  longitude: number
  // False while they have a delivery currently OUT_FOR_DELIVERY with them - backs the
  // "Livres/Todos" filter on the staff map.
  available: boolean
}

export function listOpenDeliveries() {
  return http.get<DeliveryDetails[]>('/deliveries').then((res) => res.data)
}

// A courier's own restricted screen (MyDeliveriesPage) - only their own out-for-delivery orders.
export function listMyDeliveries() {
  return http.get<DeliveryDetails[]>('/deliveries/mine').then((res) => res.data)
}

export function updateDeliveryStatus(tabId: string, status: DeliveryStatus) {
  return http.patch<DeliveryDetails>(`/deliveries/${tabId}/status`, { status }).then((res) => res.data)
}

// courierId null unassigns the current courier - the DeliveryPage dropdown always offers a
// "sem entregador" option that calls this the same way as picking a real one.
export function assignCourier(tabId: string, courierId: string | null) {
  return http.patch<DeliveryDetails>(`/deliveries/${tabId}/courier`, { courierId }).then((res) => res.data)
}

// Narrower than listUsers({role:'COURIER'}) - reachable by every role that can assign a courier
// (WAITER/KITCHEN/CASHIER included), so it deliberately doesn't return an email/phone back.
export function listAssignableCouriers() {
  return http.get<AssignableCourier[]>('/deliveries/couriers').then((res) => res.data)
}

// Sent periodically by the courier's own browser while /my-deliveries is open, whenever logged
// in - not gated on having an active delivery, so the staff map below can show who's free/nearby.
export function updateMyLocation(latitude: number, longitude: number) {
  return http.patch<void>('/deliveries/mine/location', { latitude, longitude }).then((res) => res.data)
}

// The staff dispatch map - every courier who has reported a position recently, whether or not
// they're currently carrying a delivery. Exact coordinates (authenticated, unlike the fuzzed ones
// on DeliveryDetails.courierLatitude/courierLongitude).
export function listLiveCouriers() {
  return http.get<CourierLiveLocation[]>('/deliveries/couriers/live').then((res) => res.data)
}
