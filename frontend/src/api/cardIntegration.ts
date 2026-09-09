import { http } from './http'

export interface CardIntegrationStatus {
  configured: boolean
}

export function getCardIntegrationStatus() {
  return http.get<CardIntegrationStatus>('/card-integration').then((res) => res.data)
}

export function saveCardIntegration(accessToken: string, webhookSecret: string) {
  return http.put('/card-integration', { accessToken, webhookSecret })
}

// Backstop for the async webhook (docs/CARD_PAYMENT.md - sandbox has shown signature flakiness
// for live_mode: true payments): CardPaymentReturnPage calls this the moment the paying browser
// lands back on our redirect, re-checking the charge directly against Mercado Pago. Safe to call
// even if the webhook already resolved it - a no-op on the backend either way.
export function verifyCardCharge(externalReference: string) {
  return http.post(`/public/payments/mercadopago/verify/${externalReference}`)
}

// The delivery order's tracking token never travels through Mercado Pago's back_url (see
// CardChargeService#buildDeliveryReturnUrl) - CardPaymentReturnPage resolves it here instead,
// keyed off the same externalReference, so it can redirect back to /delivery/status/:token.
export function resolveDeliveryReturnToken(externalReference: string) {
  return http
    .get<{ accessToken: string }>(`/public/payments/mercadopago/verify/${externalReference}/delivery-token`)
    .then((res) => res.data.accessToken || null)
}
