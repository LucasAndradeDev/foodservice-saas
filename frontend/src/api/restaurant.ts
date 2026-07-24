import { http } from './http'

export interface Restaurant {
  id: string
  name: string
  tradeName: string | null
  slug: string | null
  cnpj: string | null
  phone: string | null
  address: string | null
  logo: string | null
  primaryColor: string | null
  tableCount: number | null
  active: boolean
  autoPrintKitchenTickets: boolean
}

export interface UpdateRestaurantPayload {
  tradeName?: string
  slug?: string
  logo?: string
  primaryColor?: string
  phone?: string
  address?: string
  cnpj?: string
  autoPrintKitchenTickets?: boolean
}

export function getMyRestaurant() {
  return http.get<Restaurant>('/restaurants/me').then((res) => res.data)
}

export function updateMyRestaurant(payload: UpdateRestaurantPayload) {
  return http.put<Restaurant>('/restaurants/me', payload).then((res) => res.data)
}
