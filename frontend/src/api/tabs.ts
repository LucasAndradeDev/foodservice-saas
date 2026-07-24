import { http } from './http'

export type TabStatus = 'OPEN' | 'CLOSED' | 'MERGED'
export type PaymentMethod = 'PIX' | 'CASH' | 'DEBIT_CARD' | 'CREDIT_CARD'

export interface TabTableSummary {
  id: string
  number: number
}

export interface Tab {
  id: string
  restaurantId: string
  status: TabStatus
  openedAt: string
  closedAt: string | null
  paymentMethod: PaymentMethod | null
  paidAmount: number | null
  paidAt: string | null
  tables: TabTableSummary[]
  receiptPrintedAt: string | null
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

export function payTab(id: string, paymentMethod: PaymentMethod, paidAmount: number) {
  return http.patch<Tab>(`/tabs/${id}/pay`, { paymentMethod, paidAmount }).then((res) => res.data)
}

export function markTabReceiptPrinted(id: string) {
  return http.patch<Tab>(`/tabs/${id}/print`).then((res) => res.data)
}
