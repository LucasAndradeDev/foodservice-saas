import { afterEach, describe, expect, it } from 'vitest'
import { computeAnchoredPanelPosition } from './anchoredPanel'

const originalInnerHeight = window.innerHeight

afterEach(() => {
  Object.defineProperty(window, 'innerHeight', { value: originalInnerHeight, configurable: true })
})

function setInnerHeight(height: number) {
  Object.defineProperty(window, 'innerHeight', { value: height, configurable: true })
}

describe('computeAnchoredPanelPosition', () => {
  it('anchors below the trigger when there is enough room', () => {
    setInnerHeight(768)
    const rect = new DOMRect(10, 100, 200, 50) // top 100, bottom 150
    const position = computeAnchoredPanelPosition(rect, 300)
    expect(position).toEqual({ left: 10, width: 200, top: 154, maxHeight: 300 })
  })

  it('flips above the trigger when there is more room there than below', () => {
    setInnerHeight(200)
    const rect = new DOMRect(0, 150, 100, 30) // top 150, bottom 180
    const position = computeAnchoredPanelPosition(rect, 300)
    expect(position).toEqual({ left: 0, width: 100, bottom: 54, maxHeight: 142 })
  })

  it('clamps maxHeight to the 120px minimum even when both sides are cramped', () => {
    setInnerHeight(100)
    const rect = new DOMRect(0, 50, 100, 10) // top 50, bottom 60
    const position = computeAnchoredPanelPosition(rect, 300)
    expect(position.maxHeight).toBe(120)
    expect(position.bottom).toBe(54)
  })
})
