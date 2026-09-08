import { describe, expect, it } from 'vitest'
import type { ComboComposition } from '../api/combos'
import { computeComboUnitPrice, sameComboSelections, type SelectedComboSlot } from './combos'

function composition(overrides: Partial<ComboComposition> = {}): ComboComposition {
  return {
    productId: 'combo-1',
    discountPercentage: null,
    fixedItems: [],
    slots: [],
    minPrice: 0,
    maxPrice: 0,
    ...overrides,
  }
}

describe('computeComboUnitPrice', () => {
  it('sums fixed items when there are no slots', () => {
    const combo = composition({
      fixedItems: [{ id: 'i1', productId: 'p1', productName: 'Burger', unitPrice: 20, quantity: 1 }],
    })
    expect(computeComboUnitPrice(combo, {})).toBe(20)
  })

  it('adds the selected option of each slot', () => {
    const combo = composition({
      fixedItems: [{ id: 'i1', productId: 'p1', productName: 'Burger', unitPrice: 20, quantity: 1 }],
      slots: [
        {
          id: 'slot-drink',
          name: 'Bebida',
          required: true,
          options: [
            { id: 'o1', productId: 'soda', productName: 'Refrigerante', unitPrice: 6, quantity: 1 },
            { id: 'o2', productId: 'juice', productName: 'Suco', unitPrice: 8, quantity: 1 },
          ],
        },
      ],
    })
    expect(computeComboUnitPrice(combo, { 'slot-drink': 'juice' })).toBe(28)
  })

  it('ignores a slot with no selection yet', () => {
    const combo = composition({
      fixedItems: [{ id: 'i1', productId: 'p1', productName: 'Burger', unitPrice: 20, quantity: 1 }],
      slots: [
        {
          id: 'slot-drink',
          name: 'Bebida',
          required: true,
          options: [{ id: 'o1', productId: 'soda', productName: 'Refrigerante', unitPrice: 6, quantity: 1 }],
        },
      ],
    })
    expect(computeComboUnitPrice(combo, {})).toBe(20)
  })

  it('applies the combo discount percentage and rounds to cents', () => {
    const combo = composition({
      fixedItems: [{ id: 'i1', productId: 'p1', productName: 'Burger', unitPrice: 19.9, quantity: 1 }],
      discountPercentage: 10,
    })
    expect(computeComboUnitPrice(combo, {})).toBe(17.91)
  })
})

describe('sameComboSelections', () => {
  function slot(slotId: string, productId: string): SelectedComboSlot {
    return { slotId, slotName: slotId, productId, productName: productId, unitPrice: 0, quantity: 1 }
  }

  it('is true for the same slot selections regardless of order', () => {
    const a = [slot('drink', 'soda'), slot('side', 'fries')]
    const b = [slot('side', 'fries'), slot('drink', 'soda')]
    expect(sameComboSelections(a, b)).toBe(true)
  })

  it('is false when a slot selection changes', () => {
    const a = [slot('drink', 'soda')]
    const b = [slot('drink', 'juice')]
    expect(sameComboSelections(a, b)).toBe(false)
  })
})
