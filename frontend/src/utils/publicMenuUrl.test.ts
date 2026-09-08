import { describe, expect, it } from 'vitest'
import { feedbackUrl, publicMenuUrl } from './publicMenuUrl'

const origin = window.location.origin

describe('publicMenuUrl', () => {
  it('builds a slug-only URL when there is no table', () => {
    expect(publicMenuUrl('burger-house')).toBe(`${origin}/menu/burger-house`)
  })

  it('includes the table id when provided', () => {
    expect(publicMenuUrl('burger-house', 'table-42')).toBe(`${origin}/menu/burger-house/table-42`)
  })
})

describe('feedbackUrl', () => {
  it('builds a feedback URL from slug and tab id', () => {
    expect(feedbackUrl('burger-house', 'tab-1')).toBe(`${origin}/feedback/burger-house/tab-1`)
  })
})
