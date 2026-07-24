export function publicMenuUrl(slug: string, tableId?: string) {
  return tableId
    ? `${window.location.origin}/menu/${slug}/${tableId}`
    : `${window.location.origin}/menu/${slug}`
}
