import { http } from './http'
import type { ApplyDiscountPayload, DiscountType } from './orders'

export type TabStatus = 'OPEN' | 'CLOSED' | 'MERGED'
export type PaymentMethod = 'PIX' | 'CASH' | 'DEBIT_CARD' | 'CREDIT_CARD'
export type PaymentStatus = 'ACTIVE' | 'VOIDED'

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

export interface Payment {
  id: string
  paymentMethod: PaymentMethod
  amount: number
  status: PaymentStatus
  paidAt: string
  createdByName: string | null
  voidedByName: string | null
  voidedAt: string | null
  voidReason: string | null
}

export interface Tab {
  id: string
  restaurantId: string
  status: TabStatus
  openedAt: string
  lastOrderAt: string | null
  closedAt: string | null
  billTotal: number | null
  amountPaid: number
  remainingBalance: number | null
  payments: Payment[]
  tables: TabTableSummary[]
  receiptPrintedAt: string | null
  discountType: DiscountType | null
  discountValue: number | null
  discountReason: string | null
  discountAppliedBy: string | null
  discountAppliedAt: string | null
  serviceChargePercentage: number | null
  serviceChargeAmount: number | null
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

export interface PaymentEntry {
  paymentMethod: PaymentMethod
  amount: number
}

export function registerPayments(id: string, payments: PaymentEntry[], serviceChargePercentage?: number) {
  return http.post<Tab>(`/tabs/${id}/payments`, { payments, serviceChargePercentage }).then((res) => res.data)
}

export function voidPayment(id: string, paymentId: string, reason: string) {
  return http.patch<Tab>(`/tabs/${id}/payments/${paymentId}/void`, { reason }).then((res) => res.data)
}

export function markTabReceiptPrinted(id: string) {
  return http.patch<Tab>(`/tabs/${id}/print`).then((res) => res.data)
}

export function applyTabDiscount(id: string, payload: ApplyDiscountPayload) {
  return http.patch<Tab>(`/tabs/${id}/discount`, payload).then((res) => res.data)
}

export interface PixCharge {
  id: string
  amount: number
  brCode: string
  qrCodeImage: string | null
  paymentLinkUrl: string | null
}

/** Freezes the tab's total (same freeze as the first manual payment) and asks Woovi for a Pix QR
 * code. Confirmation happens asynchronously via webhook - the caller must poll getTab and watch
 * for the tab closing to know it was paid. serviceChargePercentage is only honored for an
 * OWNER/MANAGER caller (silently ignored otherwise, same as registerPayments) - omit it to use the
 * restaurant's configured default. */
export function createPixCharge(id: string, serviceChargePercentage?: number | null) {
  return http
    .post<PixCharge>(`/tabs/${id}/pix-charges`, serviceChargePercentage !== undefined ? { serviceChargePercentage } : undefined)
    .then((res) => res.data)
}

/** Cancels a still-unpaid Pix charge and unfreezes the tab's total, so items/discount/service
 * charge become editable again. No-op if there's no pending charge. */
export function cancelPixCharge(id: string) {
  return http.delete<void>(`/tabs/${id}/pix-charges`).then((res) => res.data)
}
