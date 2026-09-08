import type { DeliveryAddressForm } from '../pages/publicMenu/utils'

const STORAGE_KEY_PREFIX = 'restaurant_saas_last_delivery_address_'

function storageKey(slug: string) {
  return `${STORAGE_KEY_PREFIX}${slug}`
}

export function saveLastDeliveryAddress(slug: string, address: DeliveryAddressForm): void {
  localStorage.setItem(storageKey(slug), JSON.stringify(address))
}

export function loadLastDeliveryAddress(slug: string): DeliveryAddressForm | null {
  const raw = localStorage.getItem(storageKey(slug))
  if (!raw) return null
  try {
    const parsed = JSON.parse(raw)
    return parsed && typeof parsed === 'object' ? parsed : null
  } catch {
    return null
  }
}
