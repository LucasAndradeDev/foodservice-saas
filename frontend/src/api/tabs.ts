import { http } from './http'
import type { ApplyDiscountPayload, DiscountType } from './orders'

export type TabStatus = 'OPEN' | 'CLOSED' | 'MERGED'
export type PaymentMethod = 'PIX' | 'CASH' | 'DEBIT_CARD' | 'CREDIT_CARD'

export const PAYMENT_METHOD_LABELS: Record<PaymentMethod, string> = {
  PIX: 'Pix',
  CASH: 'Dinheiro',
  DEBIT_CARD: 'Cartão de débito',
  CREDIT_CARD: 'Cartão de crédito',
}

export interface TabTableSummary {
  id: string
  number: number
}

export interface Tab {
  id: string
  restaurantId: string
  status: TabStatus
  openedAt: string
  lastOrderAt: string | null
  closedAt: string | null
  paymentMethod: PaymentMethod | null
  paidAmount: number | null
  paidAt: string | null
  tables: TabTableSummary[]
  receiptPrintedAt: string | null
  discountType: DiscountType | null
  discountValue: number | null
  discountReason: string | null
  discountAppliedBy: string | null
  discountAppliedAt: string | null
  serviceChargePercentage: number | null
  serviceChargeAmount: number | null
  paymentCancelledBy: string | null
  paymentCancelledAt: string | null
  paymentCancelReason: string | null
}

/** Rounds to cents, avoiding binary floating-point artifacts (e.g. 51.3 + 38.9 === 90.19999999999999 in JS). */
export function roundCurrency(value: number) {
  return Math.round(value * 100) / 100
}

/** Mirrors Tab.getDiscountAmount on the backend: capped fixed/percentage discount over a base amount. */
export function computeDiscountAmount(discountType: DiscountType | null, discountValue: number | null, baseAmount: number) {
  if (!discountType || !discountValue) return 0
  const amount = discountType === 'PERCENTAGE' ? roundCurrency((baseAmount * discountValue) / 100) : discountValue
  return Math.min(amount, baseAmount)
}

export function listTabs(status?: TabStatus) {
  return http.get<Tab[]>('/tabs', { params: { status } }).then((res) => res.data)
}

export function getTab(id: string) {
  return http.get<Tab>(`/tabs/${id}`).then((res) => res.data)
}

export function openTab(tableIds: string[]) {
  return http.post<Tab>('/tabs', { tableIds }).then((res) => res.data)
}

export function cancelTab(id: string) {
  return http.patch<Tab>(`/tabs/${id}/cancel`).then((res) => res.data)
}

export function addTableToTab(tabId: string, tableId: string) {
  return http.patch<Tab>(`/tabs/${tabId}/tables`, { tableId }).then((res) => res.data)
}

export function mergeTabs(targetTabId: string, sourceTabId: string) {
  return http.patch<Tab>(`/tabs/${targetTabId}/merge`, { sourceTabId }).then((res) => res.data)
}

export function unmergeTabs(targetTabId: string, sourceTabId: string) {
  return http.patch<Tab>(`/tabs/${targetTabId}/unmerge`, { sourceTabId }).then((res) => res.data)
}

export function payTab(id: string, paymentMethod: PaymentMethod, paidAmount: number, serviceChargePercentage?: number) {
  return http.patch<Tab>(`/tabs/${id}/pay`, { paymentMethod, paidAmount, serviceChargePercentage }).then((res) => res.data)
}

export function cancelTabPayment(
  id: string,
  reason: string,
  paymentMethod: PaymentMethod,
  paidAmount: number,
  serviceChargePercentage?: number,
) {
  return http
    .patch<Tab>(`/tabs/${id}/cancel-payment`, { reason, paymentMethod, paidAmount, serviceChargePercentage })
    .then((res) => res.data)
}

export function markTabReceiptPrinted(id: string) {
  return http.patch<Tab>(`/tabs/${id}/print`).then((res) => res.data)
}

export function applyTabDiscount(id: string, payload: ApplyDiscountPayload) {
  return http.patch<Tab>(`/tabs/${id}/discount`, payload).then((res) => res.data)
}
