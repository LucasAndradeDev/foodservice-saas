export type { SelectedModifier } from '../../utils/modifiers'
export { modifiersTotal, sameModifiers } from '../../utils/modifiers'
import type { SelectedModifier } from '../../utils/modifiers'
import type { SelectedComboSlot } from '../../utils/combos'

export interface CartItem {
  productId: string
  productName: string
  unitPrice: number
  quantity: number
  observation: string
  selectedModifiers: SelectedModifier[]
  comboSelections?: SelectedComboSlot[]
}

export interface DeliveryAddressForm {
  customerName: string
  customerPhone: string
  street: string
  number: string
  complement: string
  neighborhood: string
  city: string
  zipCode: string
  referencePoint: string
}

export function emptyDeliveryAddress(): DeliveryAddressForm {
  return {
    customerName: '',
    customerPhone: '',
    street: '',
    number: '',
    complement: '',
    neighborhood: '',
    city: '',
    zipCode: '',
    referencePoint: '',
  }
}

// Only the fields a delivery actually can't happen without - complement/referencePoint stay
// optional, same spirit as observation being optional on a cart item.
const REQUIRED_DELIVERY_FIELDS: (keyof DeliveryAddressForm)[] = [
  'customerName',
  'customerPhone',
  'street',
  'number',
  'neighborhood',
  'city',
]

export function isDeliveryAddressComplete(address: DeliveryAddressForm) {
  return REQUIRED_DELIVERY_FIELDS.every((field) => address[field].trim().length > 0)
}

export const currencyFormatter = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

export function scrollToCategory(categoryId: string) {
  document.getElementById(`category-${categoryId}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

/** Rounds to cents, avoiding binary floating-point artifacts (e.g. 51.3 + 38.9 === 90.19999999999999 in JS). */
export function roundCurrency(value: number) {
  return Math.round(value * 100) / 100
}

/** Mirrors Tab.getDiscountAmount on the backend: capped fixed/percentage discount over a base amount. */
export function computeDiscountAmount(
  discountType: 'FIXED' | 'PERCENTAGE' | null,
  discountValue: number | null,
  baseAmount: number,
) {
  if (!discountType || !discountValue) return 0
  const amount = discountType === 'PERCENTAGE' ? roundCurrency((baseAmount * discountValue) / 100) : discountValue
  return Math.min(amount, baseAmount)
}
