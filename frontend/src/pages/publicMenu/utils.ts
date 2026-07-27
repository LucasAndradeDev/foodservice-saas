export type { SelectedModifier } from '../../utils/modifiers'
export { modifiersTotal, sameModifiers } from '../../utils/modifiers'
import type { SelectedModifier } from '../../utils/modifiers'

export interface CartItem {
  productId: string
  productName: string
  unitPrice: number
  quantity: number
  observation: string
  selectedModifiers: SelectedModifier[]
}

export const currencyFormatter = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

export function scrollToCategory(categoryId: string) {
  document.getElementById(`category-${categoryId}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}
