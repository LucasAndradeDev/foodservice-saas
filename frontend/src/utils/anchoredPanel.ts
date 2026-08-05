export interface AnchoredPanelPosition {
  left: number
  width: number
  top?: number
  bottom?: number
  maxHeight: number
}

const MARGIN = 8
const MIN_HEIGHT = 120

/**
 * Positions a fixed-position panel relative to a trigger's measured rect, flipping it above the
 * trigger (and clamping its height) when there isn't enough room below to fit `desiredMaxHeight`.
 */
export function computeAnchoredPanelPosition(rect: DOMRect, desiredMaxHeight: number): AnchoredPanelPosition {
  const spaceBelow = window.innerHeight - rect.bottom - MARGIN
  const spaceAbove = rect.top - MARGIN

  if (spaceBelow >= desiredMaxHeight || spaceBelow >= spaceAbove) {
    return {
      left: rect.left,
      width: rect.width,
      top: rect.bottom + 4,
      maxHeight: Math.max(MIN_HEIGHT, Math.min(desiredMaxHeight, spaceBelow)),
    }
  }

  return {
    left: rect.left,
    width: rect.width,
    bottom: window.innerHeight - rect.top + 4,
    maxHeight: Math.max(MIN_HEIGHT, Math.min(desiredMaxHeight, spaceAbove)),
  }
}
