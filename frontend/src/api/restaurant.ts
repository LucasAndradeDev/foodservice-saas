import { http } from './http'

export interface Restaurant {
  id: string
  name: string
  tradeName: string | null
  slug: string | null
  cnpj: string | null
  phone: string | null
  address: string | null
  // Structured address (task 26.5 follow-up), same shape as the customer delivery address -
  // additive alongside `address` above, which stays as the display/backfill fallback.
  street: string | null
  number: string | null
  complement: string | null
  neighborhood: string | null
  city: string | null
  zipCode: string | null
  // Geocoded from address above - null means distance-based delivery pricing is unavailable
  // (falls back to bairro/DeliveryZone), whether because address hasn't geocoded yet or is blank.
  latitude: number | null
  longitude: number | null
  deliveryBaseFee: number | null
  deliveryFeePerKm: number | null
  maxDeliveryDistanceKm: number | null
  logo: string | null
  tableCount: number | null
  active: boolean
  autoPrintKitchenTickets: boolean
  kitchenWarningThresholdMinutes: number
  kitchenCriticalThresholdMinutes: number
  tableForgottenWarningThresholdMinutes: number
  tableForgottenCriticalThresholdMinutes: number
  serviceChargeEnabled: boolean
  serviceChargePercentage: number
}

export interface UpdateRestaurantPayload {
  tradeName?: string
  slug?: string
  logo?: string
  phone?: string
  address?: string
  street?: string
  number?: string
  complement?: string
  neighborhood?: string
  city?: string
  zipCode?: string
  cnpj?: string
  autoPrintKitchenTickets?: boolean
  kitchenWarningThresholdMinutes?: number
  kitchenCriticalThresholdMinutes?: number
  tableForgottenWarningThresholdMinutes?: number
  tableForgottenCriticalThresholdMinutes?: number
  serviceChargeEnabled?: boolean
  serviceChargePercentage?: number
  deliveryBaseFee?: number
  deliveryFeePerKm?: number
  maxDeliveryDistanceKm?: number
}

export function getMyRestaurant() {
  return http.get<Restaurant>('/restaurants/me').then((res) => res.data)
}

export function updateMyRestaurant(payload: UpdateRestaurantPayload) {
  return http.put<Restaurant>('/restaurants/me', payload).then((res) => res.data)
}

export function uploadRestaurantLogo(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return http.post<{ url: string }>('/restaurants/upload-logo', formData).then((res) => res.data.url)
}
