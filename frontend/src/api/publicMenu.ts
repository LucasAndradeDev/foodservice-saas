import type { ItemStatus } from './orders'
import type { ModifierGroup } from './productModifiers'
import type { ComboComposition } from './combos'
import type { CardCharge, PixCharge } from './tabs'
import { http } from './http'

export interface PublicMenuProduct {
  id: string
  name: string
  description: string | null
  imageUrl: string | null
  galleryImageUrls: string[]
  type: 'SIMPLE' | 'COMBO'
  price: number
  discountedPrice: number | null
  soldOut: boolean
  availableNow: boolean
  featured: boolean
  bestseller: boolean
  estimatedWaitMinutes: number | null
  modifierGroups: ModifierGroup[]
  combo: ComboComposition | null
}

export interface PublicMenuCategory {
  id: string
  name: string
  bannerImageUrl: string | null
  products: PublicMenuProduct[]
}

export interface PublicMenuOrderItem {
  id: string
  productName: string
  quantity: number
  status: ItemStatus
}

export interface PublicMenuReorderModifier {
  groupName: string
  optionName: string
}

export interface PublicMenuReorderItem {
  productId: string
  productName: string
  quantity: number
  observation: string | null
  modifiers: PublicMenuReorderModifier[]
}

export type DiscountType = 'FIXED' | 'PERCENTAGE'

export interface PublicMenuTable {
  id: string
  number: number
  hasDeliveredItems: boolean
  orderItems: PublicMenuOrderItem[]
  lastOrderItems: PublicMenuReorderItem[]
  currentTabId: string | null
  pixConfigured: boolean
  cardConfigured: boolean
  discountAppliedLabel: string | null
  discountType: DiscountType | null
  discountValue: number | null
}

export interface PublicMenu {
  restaurantName: string
  logo: string | null
  categories: PublicMenuCategory[]
  table: PublicMenuTable | null
}

export interface PublicOrderItemPayload {
  productId: string
  quantity: number
  observation?: string
  selectedOptionIds?: string[]
  slotSelections?: { slotId: string; selectedProductId: string }[]
}

export function getPublicMenu(slug: string, tableId?: string) {
  return http
    .get<PublicMenu>(`/public/menu/${slug}`, { params: tableId ? { tableId } : undefined })
    .then((res) => res.data)
}

export function submitPublicOrder(slug: string, tableId: string, items: PublicOrderItemPayload[], customerPhone?: string) {
  return http
    .post(`/public/menu/${slug}/tables/${tableId}/orders`, { items, customerPhone: customerPhone || undefined })
    .then((res) => res.data)
}

export interface DeliveryOrderPayload {
  items: PublicOrderItemPayload[]
  customerName: string
  customerPhone: string
  street: string
  number: string
  complement?: string
  neighborhood: string
  city: string
  zipCode?: string
  referencePoint?: string
}

export interface DeliveryOrderResult {
  tabId: string
  accessToken: string
  deliveryFee: number
}

export function submitDeliveryOrder(slug: string, payload: DeliveryOrderPayload) {
  return http
    .post<DeliveryOrderResult>(`/public/menu/${slug}/delivery/orders`, payload)
    .then((res) => res.data)
}

export interface DeliveryFeeQuote {
  available: boolean
  fee: number | null
}

// Preview only - the fee actually charged is looked up again server-side when the order is
// created (submitDeliveryOrder), never trusted from this response.
export function getDeliveryFeeQuote(slug: string, neighborhood: string) {
  return http
    .get<DeliveryFeeQuote>(`/public/menu/${slug}/delivery/fee`, { params: { neighborhood } })
    .then((res) => res.data)
}

export interface CouponRedemption {
  discountAppliedLabel: string | null
  discountType: DiscountType | null
  discountValue: number | null
}

export function redeemCoupon(slug: string, tableId: string, code: string) {
  return http
    .post<CouponRedemption>(`/public/menu/${slug}/tables/${tableId}/coupon`, { code })
    .then((res) => res.data)
}

export function removeCoupon(slug: string, tableId: string) {
  return http.delete<CouponRedemption>(`/public/menu/${slug}/tables/${tableId}/coupon`).then((res) => res.data)
}

/** Freezes the table's open tab total and asks Woovi for a Pix QR code, same as the staff Caixa
 * flow (createPixCharge in api/tabs.ts) but reachable without authentication. Confirmation is
 * asynchronous - the digital menu already detects the tab closing via its own polling of
 * getPublicMenu, so the caller doesn't need to poll separately. */
export function createPublicPixCharge(slug: string, tableId: string) {
  return http.post<PixCharge>(`/public/menu/${slug}/tables/${tableId}/pix-charges`).then((res) => res.data)
}

/** Freezes the table's open tab total and asks Mercado Pago for a Checkout Pro preference, same
 * as the staff Caixa flow (createCardCharge in api/tabs.ts) but reachable without authentication.
 * Confirmation is asynchronous - the digital menu already detects the tab closing via its own
 * polling of getPublicMenu, so the caller doesn't need to poll separately. Unlike Pix, the
 * customer is redirected to initPointUrl (their own browser) rather than shown a QR code. */
export function createPublicCardCharge(slug: string, tableId: string) {
  return http.post<CardCharge>(`/public/menu/${slug}/tables/${tableId}/card-charges`).then((res) => res.data)
}
