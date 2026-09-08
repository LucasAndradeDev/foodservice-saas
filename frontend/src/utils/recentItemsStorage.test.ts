import { beforeEach, describe, expect, it } from 'vitest'
import { addRecentProductId, getRecentProductIds } from './recentItemsStorage'

beforeEach(() => {
  localStorage.clear()
})

describe('getRecentProductIds', () => {
  it('returns an empty list when nothing was stored', () => {
    expect(getRecentProductIds('restaurant-1')).toEqual([])
  })

  it('ignores malformed JSON in storage', () => {
    localStorage.setItem('restaurant_saas_recent_products_restaurant-1', 'not-json')
    expect(getRecentProductIds('restaurant-1')).toEqual([])
  })

  it('filters out non-string entries', () => {
    localStorage.setItem('restaurant_saas_recent_products_restaurant-1', JSON.stringify(['p1', 2, null, 'p2']))
    expect(getRecentProductIds('restaurant-1')).toEqual(['p1', 'p2'])
  })
})

describe('addRecentProductId', () => {
  it('adds a product to the front of the list', () => {
    addRecentProductId('restaurant-1', 'p1')
    addRecentProductId('restaurant-1', 'p2')
    expect(getRecentProductIds('restaurant-1')).toEqual(['p2', 'p1'])
  })

  it('moves an already-recent product back to the front instead of duplicating it', () => {
    addRecentProductId('restaurant-1', 'p1')
    addRecentProductId('restaurant-1', 'p2')
    addRecentProductId('restaurant-1', 'p1')
    expect(getRecentProductIds('restaurant-1')).toEqual(['p1', 'p2'])
  })

  it('caps the list at 8 items', () => {
    for (let i = 0; i < 10; i++) {
      addRecentProductId('restaurant-1', `p${i}`)
    }
    const ids = getRecentProductIds('restaurant-1')
    expect(ids).toHaveLength(8)
    expect(ids[0]).toBe('p9')
  })

  it('keeps lists for different restaurants separate', () => {
    addRecentProductId('restaurant-1', 'p1')
    addRecentProductId('restaurant-2', 'p2')
    expect(getRecentProductIds('restaurant-1')).toEqual(['p1'])
    expect(getRecentProductIds('restaurant-2')).toEqual(['p2'])
  })
})
