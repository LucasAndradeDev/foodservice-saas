import type { ComboComposition } from '../api/combos'

export interface SelectedComboSlot {
  slotId: string
  slotName: string
  productId: string
  productName: string
  unitPrice: number
  quantity: number
}

export function computeComboUnitPrice(
  composition: ComboComposition,
  slotSelections: Record<string, string>,
): number {
  const fixedTotal = composition.fixedItems.reduce((sum, item) => sum + item.unitPrice * item.quantity, 0)
  const slotsTotal = composition.slots.reduce((sum, slot) => {
    const option = slot.options.find((o) => o.productId === slotSelections[slot.id])
    return sum + (option ? option.unitPrice * option.quantity : 0)
  }, 0)
  const gross = fixedTotal + slotsTotal
  const discount = composition.discountPercentage ? (gross * composition.discountPercentage) / 100 : 0
  return Math.round((gross - discount) * 100) / 100
}

export function sameComboSelections(a: SelectedComboSlot[], b: SelectedComboSlot[]): boolean {
  if (a.length !== b.length) return false
  const keyOf = (list: SelectedComboSlot[]) => list.map((s) => `${s.slotId}:${s.productId}`).sort().join(',')
  return keyOf(a) === keyOf(b)
}
