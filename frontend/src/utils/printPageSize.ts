const PAGE_STYLE_ID = 'dynamic-print-page-size'
const PX_PER_MM = 96 / 25.4
const RECEIPT_WIDTH_MM = 80
const HEIGHT_BUFFER_MM = 4

/**
 * Chrome only honors a custom @page size (e.g. for "Save as PDF" or a receipt
 * printer) when it matches the rendered content height. There's no reliable
 * cross-browser "auto height" page size, so we measure the receipt content
 * and inject the exact height right before printing.
 */
export function applyReceiptPrintPageSize(element: HTMLElement | null) {
  if (!element) return

  const heightMm = element.getBoundingClientRect().height / PX_PER_MM + HEIGHT_BUFFER_MM

  let styleTag = document.getElementById(PAGE_STYLE_ID) as HTMLStyleElement | null
  if (!styleTag) {
    styleTag = document.createElement('style')
    styleTag.id = PAGE_STYLE_ID
    document.head.appendChild(styleTag)
  }
  styleTag.textContent = `@media print { @page { size: ${RECEIPT_WIDTH_MM}mm ${heightMm.toFixed(2)}mm; margin: 0; } }`
}
