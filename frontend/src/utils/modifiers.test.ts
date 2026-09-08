import { describe, expect, it } from 'vitest'
import { modifiersTotal, sameModifiers, type SelectedModifier } from './modifiers'

function modifier(optionId: string, priceDelta: number): SelectedModifier {
  return { groupId: 'g1', groupName: 'Tamanho', optionId, optionName: optionId, priceDelta }
}

describe('modifiersTotal', () => {
  it('is zero for no modifiers', () => {
    expect(modifiersTotal([])).toBe(0)
  })

  it('sums the price delta of every modifier', () => {
    expect(modifiersTotal([modifier('big', 5), modifier('extra-cheese', 3.5)])).toBe(8.5)
  })
})

describe('sameModifiers', () => {
  it('is true for the same options regardless of order', () => {
    const a = [modifier('big', 5), modifier('extra-cheese', 3.5)]
    const b = [modifier('extra-cheese', 3.5), modifier('big', 5)]
    expect(sameModifiers(a, b)).toBe(true)
  })

  it('is false when the option sets differ', () => {
    const a = [modifier('big', 5)]
    const b = [modifier('small', 0)]
    expect(sameModifiers(a, b)).toBe(false)
  })

  it('is false when the counts differ', () => {
    const a = [modifier('big', 5)]
    const b = [modifier('big', 5), modifier('extra-cheese', 3.5)]
    expect(sameModifiers(a, b)).toBe(false)
  })
})
