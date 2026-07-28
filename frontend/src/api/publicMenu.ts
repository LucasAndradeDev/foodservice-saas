import type { ItemStatus } from './orders'
import type { ModifierGroup } from './productModifiers'
import { http } from './http'

export interface PublicMenuProduct {
  id: string
  name: string
  description: string | null
  imageUrl: string | null
  price: number
  soldOut: boolean
  featured: boolean
  bestseller: boolean
  estimatedWaitMinutes: number | null
  modifierGroups: ModifierGroup[]
}

export interface PublicMenuCategory {
  id: string
  name: string
  products: PublicMenuProduct[]
}

export interface PublicMenuOrderItem {
  id: string
  productName: string
  quantity: number
  status: ItemStatus
}

export interface PublicMenuTable {
  id: string
  number: number
  hasDeliveredItems: boolean
  orderItems: PublicMenuOrderItem[]
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
}

export function getPublicMenu(slug: string, tableId?: string) {
  return http
    .get<PublicMenu>(`/public/menu/${slug}`, { params: tableId ? { tableId } : undefined })
    .then((res) => res.data)
}

export function submitPublicOrder(slug: string, tableId: string, items: PublicOrderItemPayload[]) {
  return http
    .post(`/public/menu/${slug}/tables/${tableId}/orders`, { items })
    .then((res) => res.data)
}
