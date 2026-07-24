import { http } from './http'
import type { PaymentMethod } from './tabs'

export interface PaymentMethodTotal {
  paymentMethod: PaymentMethod
  total: number
  tabsCount: number
}

export interface TopProduct {
  productId: string
  productName: string
  quantitySold: number
  revenue: number
}

export interface ReportSummary {
  totalRevenue: number
  closedTabsCount: number
  averageTicket: number
  byPaymentMethod: PaymentMethodTotal[]
  topProducts: TopProduct[]
}

export function getReportSummary(start: string, end: string) {
  return http.get<ReportSummary>('/reports/summary', { params: { start, end } }).then((res) => res.data)
}
