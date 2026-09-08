import { AxiosError } from 'axios'
import { describe, expect, it } from 'vitest'
import { paymentErrorMessage } from './paymentErrorMessage'

function errorWithStatus(status: number) {
  return new AxiosError('Request failed', String(status), undefined, undefined, {
    status,
    data: {},
    statusText: '',
    headers: {},
    config: {} as never,
  })
}

describe('paymentErrorMessage', () => {
  it('reports nothing delivered yet for a 400 on a table charge', () => {
    expect(paymentErrorMessage(errorWithStatus(400), 'card', false)).toBe('Ainda não há nada entregue na mesa pra pagar.')
  })

  it('reports the order-specific message for a 400 on a delivery charge', () => {
    expect(paymentErrorMessage(errorWithStatus(400), 'pix', true)).toBe('Não foi possível gerar a cobrança pra esse pedido.')
  })

  it('tells a table customer to call the waiter when the gateway is not configured (403)', () => {
    expect(paymentErrorMessage(errorWithStatus(403), 'card', false)).toBe('Pagamento com cartão não está configurado. Chame o garçom.')
  })

  it('never tells a delivery customer to call a waiter, even when the gateway is not configured (403)', () => {
    const message = paymentErrorMessage(errorWithStatus(403), 'pix', true)
    expect(message).not.toContain('garçom')
    expect(message).toBe('Pagamento com Pix ainda não está disponível pra esse restaurante. Tente pelo outro método ou entre em contato com o restaurante.')
  })

  it('falls back to the dine-in gateway-failure message on an unrecognized status (e.g. 502)', () => {
    expect(paymentErrorMessage(errorWithStatus(502), 'card', false)).toBe('Não foi possível gerar a cobrança no cartão. Chame o garçom.')
  })

  it('never tells a delivery customer to call a waiter on a gateway failure (502)', () => {
    const message = paymentErrorMessage(errorWithStatus(502), 'pix', true)
    expect(message).not.toContain('garçom')
    expect(message).toBe('Não foi possível gerar a cobrança Pix. Tente de novo em instantes.')
  })

  it('treats a non-Axios error the same as an unrecognized status', () => {
    expect(paymentErrorMessage(new Error('boom'), 'card', false)).toBe('Não foi possível gerar a cobrança no cartão. Chame o garçom.')
  })
})
