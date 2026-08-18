const STORAGE_KEY = 'restaurant_saas_reports_range'

export type ReportsRangePreset = 'today' | 'last7days' | 'thisMonth' | 'custom'

export interface StoredReportsRange {
  preset: ReportsRangePreset
  start?: string
  end?: string
}

// Storing the preset name (not raw dates) keeps "Hoje"/"Este mês" resolving to the actual
// current day on the next visit instead of freezing on whatever date they were picked on -
// only a manually picked custom range needs its literal start/end remembered.
export function loadLastReportsRange(): StoredReportsRange | null {
  const raw = localStorage.getItem(STORAGE_KEY)
  if (!raw) return null
  try {
    const parsed = JSON.parse(raw)
    return parsed && typeof parsed === 'object' ? parsed : null
  } catch {
    return null
  }
}

export function saveLastReportsRange(range: StoredReportsRange): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(range))
}
