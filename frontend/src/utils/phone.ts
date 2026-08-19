/** Formats digits as the user types into a Brazilian phone number: (XX) XXXXX-XXXX for an
 * 11-digit mobile, (XX) XXXX-XXXX for a 10-digit landline. Never rejects input - just reflects
 * whatever digits are typed so far, capped at 11. */
export function formatBrazilianPhone(value: string): string {
  const digits = value.replace(/\D/g, '').slice(0, 11)
  if (digits.length === 0) return ''
  if (digits.length <= 2) return `(${digits}`
  const ddd = digits.slice(0, 2)
  const rest = digits.slice(2)
  if (rest.length <= 4) return `(${ddd}) ${rest}`
  const splitAt = digits.length <= 10 ? 4 : 5
  return `(${ddd}) ${rest.slice(0, splitAt)}-${rest.slice(splitAt)}`
}

/** Builds a wa.me link from a Brazilian phone number stored in any format (formatted, raw, with
 * or without the country code) - strips everything but digits and adds the 55 prefix only if it's
 * not already there, so a number stored either way still produces a valid link. */
export function buildWhatsAppUrl(phone: string, message?: string) {
  const digits = phone.replace(/\D/g, '')
  const withCountryCode = digits.startsWith('55') ? digits : `55${digits}`
  const query = message ? `?text=${encodeURIComponent(message)}` : ''
  return `https://wa.me/${withCountryCode}${query}`
}
