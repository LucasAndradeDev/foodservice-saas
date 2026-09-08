import { describe, expect, it } from 'vitest'
import { formatTableLabel } from './tableLabel'

describe('formatTableLabel', () => {
  it('returns Balcão for an empty table list', () => {
    expect(formatTableLabel([])).toBe('Balcão')
  })

  it('formats a single table in the singular', () => {
    expect(formatTableLabel([7])).toBe('Mesa 7')
  })

  it('formats merged tables in the plural, joined by comma', () => {
    expect(formatTableLabel([5, 6])).toBe('Mesas 5, 6')
  })
})
