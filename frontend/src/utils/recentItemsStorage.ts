const STORAGE_KEY_PREFIX = 'restaurant_saas_recent_products_'
const MAX_RECENT_ITEMS = 8

function storageKey(restaurantId: string) {
  return `${STORAGE_KEY_PREFIX}${restaurantId}`
}

export function getRecentProductIds(restaurantId: string): string[] {
  const raw = localStorage.getItem(storageKey(restaurantId))
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed.filter((id) => typeof id === 'string') : []
  } catch {
    return []
  }
}

export function addRecentProductId(restaurantId: string, productId: string): void {
  const current = getRecentProductIds(restaurantId)
  const next = [productId, ...current.filter((id) => id !== productId)].slice(0, MAX_RECENT_ITEMS)
  localStorage.setItem(storageKey(restaurantId), JSON.stringify(next))
}
