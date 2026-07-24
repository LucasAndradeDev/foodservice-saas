import { http } from './http'

export interface PublicMenuProduct {
  id: string
  name: string
  description: string | null
  imageUrl: string | null
  price: number
}

export interface PublicMenuCategory {
  id: string
  name: string
  products: PublicMenuProduct[]
}

export interface PublicMenu {
  restaurantName: string
  logo: string | null
  primaryColor: string | null
  categories: PublicMenuCategory[]
}

export function getPublicMenu(slug: string) {
  return http.get<PublicMenu>(`/public/menu/${slug}`).then((res) => res.data)
}
