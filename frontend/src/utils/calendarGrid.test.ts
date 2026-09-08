import { describe, expect, it } from 'vitest'
import { buildCalendarGrid, formatDateDisplay, isSameDay, parseDateInput, toDateInputValue } from './calendarGrid'

describe('parseDateInput / toDateInputValue', () => {
  it('round-trips a yyyy-mm-dd string through local Date parsing', () => {
    const date = parseDateInput('2026-08-31')
    expect(date.getFullYear()).toBe(2026)
    expect(date.getMonth()).toBe(7)
    expect(date.getDate()).toBe(31)
    expect(toDateInputValue(date)).toBe('2026-08-31')
  })
})

describe('formatDateDisplay', () => {
  it('converts yyyy-mm-dd to dd/mm/yyyy', () => {
    expect(formatDateDisplay('2026-08-31')).toBe('31/08/2026')
  })
})

describe('isSameDay', () => {
  it('is true for the same calendar day at different times', () => {
    expect(isSameDay(new Date(2026, 7, 31, 8, 0), new Date(2026, 7, 31, 23, 59))).toBe(true)
  })

  it('is false for different days', () => {
    expect(isSameDay(new Date(2026, 7, 31), new Date(2026, 8, 1))).toBe(false)
  })
})

describe('buildCalendarGrid', () => {
  it('always returns a 6-week (42 cell) grid', () => {
    expect(buildCalendarGrid(2026, 4)).toHaveLength(42)
  })

  it('pads the start of the month with trailing days from the previous month', () => {
    // May 2026 starts on a Friday, so the grid leads with Apr 26-30.
    const cells = buildCalendarGrid(2026, 4)
    const leading = cells.slice(0, 5)
    expect(leading.every((cell) => !cell.inMonth)).toBe(true)
    expect(leading.map((cell) => cell.date.getDate())).toEqual([26, 27, 28, 29, 30])
    expect(cells[5].date.getDate()).toBe(1)
    expect(cells[5].inMonth).toBe(true)
  })

  it('pads the end of the month with leading days from the next month', () => {
    const cells = buildCalendarGrid(2026, 4)
    const trailing = cells.slice(36)
    expect(trailing.every((cell) => !cell.inMonth)).toBe(true)
    expect(trailing.map((cell) => cell.date.getDate())).toEqual([1, 2, 3, 4, 5, 6])
  })
})
