import { isAxiosError } from 'axios'

export type PublicPaymentMethod = 'card' | 'pix'

/** How each method's dine-in generic error text has always read - kept for backwards-compatible
 * default (non-delivery, non-4xx) messages. */
const GATEWAY_LABEL: Record<PublicPaymentMethod, string> = {
  card: 'no cartão',
  pix: 'Pix',
}

const CONFIG_LABEL: Record<PublicPaymentMethod, string> = {
  card: 'cartão',
  pix: 'Pix',
}

/** Shared by CardPaymentModal and PixPaymentModal: turns a failed charge-creation request into
 * customer-facing text, distinguishing both by HTTP status (why it failed) and by context (mesa vs.
 * delivery). A delivery order has no waiter standing by, so the dine-in default of "Chame o garçom"
 * never appears there - a 403 (restaurant hasn't connected this gateway) and anything else
 * (typically a 502 gateway failure) each get their own delivery-appropriate text instead. */
export function paymentErrorMessage(error: unknown, method: PublicPaymentMethod, isDelivery: boolean): string {
  const status = isAxiosError(error) ? error.response?.status : undefined

  if (status === 400) {
    return isDelivery ? 'Não foi possível gerar a cobrança pra esse pedido.' : 'Ainda não há nada entregue na mesa pra pagar.'
  }

  if (status === 403) {
    return isDelivery
      ? `Pagamento com ${CONFIG_LABEL[method]} ainda não está disponível pra esse restaurante. Tente pelo outro método ou entre em contato com o restaurante.`
      : `Pagamento com ${CONFIG_LABEL[method]} não está configurado. Chame o garçom.`
  }

  return isDelivery
    ? `Não foi possível gerar a cobrança ${GATEWAY_LABEL[method]}. Tente de novo em instantes.`
    : `Não foi possível gerar a cobrança ${GATEWAY_LABEL[method]}. Chame o garçom.`
}
