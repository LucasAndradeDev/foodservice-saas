const LOWERCASE_WORDS = new Set([
  'de', 'da', 'do', 'das', 'dos', 'e', 'ou', 'com', 'sem', 'para', 'por',
  'a', 'o', 'as', 'os', 'em', 'no', 'na', 'nos', 'nas', 'um', 'uma', 'ao', 'aos', 'à', 'às',
])

// Normalizes text extracted fully in caps (common in scanned PDFs/photos, e.g. "HAMBURGUER") into
// a readable title case ("Hamburguer"). Only touches text that's entirely uppercase, so a name
// someone deliberately typed with mixed case (before or after import) is left alone.
export function toTitleCase(text: string): string {
  if (!text || text !== text.toUpperCase()) return text

  return text
    .toLowerCase()
    .split(' ')
    .map((word, index) => {
      if (word.length === 0) return word
      if (index !== 0 && LOWERCASE_WORDS.has(word)) return word
      return word.charAt(0).toUpperCase() + word.slice(1)
    })
    .join(' ')
}
