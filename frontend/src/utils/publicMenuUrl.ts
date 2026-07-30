export function publicMenuUrl(slug: string, tableId?: string) {
  return tableId
    ? `${window.location.origin}/menu/${slug}/${tableId}`
    : `${window.location.origin}/menu/${slug}`
}

export function feedbackUrl(slug: string, tabId: string) {
  return `${window.location.origin}/feedback/${slug}/${tabId}`
}
