export function formatTableLabel(tableNumbers: number[]): string {
  if (tableNumbers.length === 0) return 'Balcão'
  return `Mesa${tableNumbers.length > 1 ? 's' : ''} ${tableNumbers.join(', ')}`
}
