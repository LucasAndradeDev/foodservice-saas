import type { CartItem, DeliveryAddressForm } from '../pages/publicMenu/utils'
import type { OrderMode } from '../pages/publicMenu/OrderModeToggle'

const STORAGE_KEY_PREFIX = 'restaurant_saas_public_order_'

export interface PublicOrderState {
  cart: CartItem[]
  deliveryAddress: DeliveryAddressForm
  orderMode: OrderMode
}

// Keyed by slug+table (not slug alone) so scanning two different tables of the same restaurant
// on one device doesn't leak one table's cart into the other. Delivery-only access (no tableId
// in the URL) falls back to a fixed suffix.
function storageKey(slug: string, tableId: string | undefined) {
  return `${STORAGE_KEY_PREFIX}${slug}_${tableId ?? 'delivery'}`
}

export function loadPublicOrderState(slug: string, tableId: string | undefined): Partial<PublicOrderState> | null {
  const raw = localStorage.getItem(storageKey(slug, tableId))
  if (!raw) return null
  try {
    const parsed = JSON.parse(raw)
    return parsed && typeof parsed === 'object' ? parsed : null
  } catch {
    return null
  }
}

export function savePublicOrderState(slug: string, tableId: string | undefined, state: PublicOrderState): void {
  localStorage.setItem(storageKey(slug, tableId), JSON.stringify(state))
}
