import { describe, expect, it } from 'vitest'
import { buildWhatsAppUrl, formatBrazilianPhone } from './phone'

describe('formatBrazilianPhone', () => {
  it('returns empty string for no digits', () => {
    expect(formatBrazilianPhone('')).toBe('')
  })

  it('formats a partial DDD while typing', () => {
    expect(formatBrazilianPhone('11')).toBe('(11')
  })

  it('formats a landline (10 digits)', () => {
    expect(formatBrazilianPhone('1133334444')).toBe('(11) 3333-4444')
  })

  it('formats a mobile (11 digits)', () => {
    expect(formatBrazilianPhone('11987654321')).toBe('(11) 98765-4321')
  })

  it('strips non-digit characters and caps at 11 digits', () => {
    expect(formatBrazilianPhone('(11) 98765-4321999')).toBe('(11) 98765-4321')
  })
})

describe('buildWhatsAppUrl', () => {
  it('adds the country code when missing', () => {
    expect(buildWhatsAppUrl('11987654321')).toBe('https://wa.me/5511987654321')
  })

  it('keeps an existing country code as-is', () => {
    expect(buildWhatsAppUrl('5511987654321')).toBe('https://wa.me/5511987654321')
  })

  it('strips formatting before building the link', () => {
    expect(buildWhatsAppUrl('(11) 98765-4321')).toBe('https://wa.me/5511987654321')
  })

  it('appends an encoded message when provided', () => {
    expect(buildWhatsAppUrl('11987654321', 'Olá, tudo bem?')).toBe(
      'https://wa.me/5511987654321?text=Ol%C3%A1%2C%20tudo%20bem%3F',
    )
  })
})
