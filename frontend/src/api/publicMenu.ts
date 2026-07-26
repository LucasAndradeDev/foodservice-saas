import { http } from './http'

export interface PublicMenuProduct {
  id: string
  name: string
  description: string | null
  imageUrl: string | null
  price: number
  soldOut: boolean
}

export interface PublicMenuCategory {
  id: string
  name: string
  products: PublicMenuProduct[]
}

export interface PublicMenuTable {
  id: string
  number: number
  hasDeliveredItems: boolean
}

export interface PublicMenu {
  restaurantName: string
  logo: string | null
  primaryColor: string | null
  categories: PublicMenuCategory[]
  table: PublicMenuTable | null
}

export interface PublicOrderItemPayload {
  productId: string
  quantity: number
  observation?: string
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
