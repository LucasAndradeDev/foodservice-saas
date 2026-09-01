import type { DeliveryDetails } from '../api/deliveries'

// Shared between DeliveryPage (staff operation screen) and MyDeliveriesPage (a courier's own
// restricted screen) - both render the same delivery address, just in different layouts.
export function formatAddressLines(delivery: DeliveryDetails) {
  const line1 = `${delivery.street}, ${delivery.number}${delivery.complement ? ` - ${delivery.complement}` : ''}`
  const line2 = [delivery.neighborhood, delivery.city].filter(Boolean).join(' - ')
  return { line1, line2 }
}

export function buildMapsUrl(delivery: DeliveryDetails) {
  const query = [`${delivery.street}, ${delivery.number}`, delivery.neighborhood, delivery.city]
    .filter(Boolean)
    .join(', ')
  return `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(query)}`
}
