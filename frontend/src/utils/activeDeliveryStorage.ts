const STORAGE_KEY_PREFIX = 'restaurant_saas_active_delivery_'

export interface ActiveDeliveryOrder {
  token: string
  createdAt: number
}

function storageKey(slug: string) {
  return `${STORAGE_KEY_PREFIX}${slug}`
}

export function saveActiveDeliveryOrder(slug: string, token: string): void {
  localStorage.setItem(storageKey(slug), JSON.stringify({ token, createdAt: Date.now() }))
}

export function loadActiveDeliveryOrder(slug: string): ActiveDeliveryOrder | null {
  const raw = localStorage.getItem(storageKey(slug))
  if (!raw) return null
  try {
    const parsed = JSON.parse(raw)
    return parsed && typeof parsed.token === 'string' ? parsed : null
  } catch {
    return null
  }
}

export function clearActiveDeliveryOrder(slug: string): void {
  localStorage.removeItem(storageKey(slug))
}
