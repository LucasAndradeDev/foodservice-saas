export interface SelectedModifier {
  groupId: string
  groupName: string
  optionId: string
  optionName: string
  priceDelta: number
}

export function modifiersTotal(modifiers: SelectedModifier[]): number {
  return modifiers.reduce((sum, modifier) => sum + modifier.priceDelta, 0)
}

export function sameModifiers(a: SelectedModifier[], b: SelectedModifier[]): boolean {
  if (a.length !== b.length) return false
  const idsA = a.map((m) => m.optionId).sort().join(',')
  const idsB = b.map((m) => m.optionId).sort().join(',')
  return idsA === idsB
}
