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

export type DayOfWeek = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY'

export interface PeakHourCell {
  dayOfWeek: DayOfWeek
  hour: number
  avgOccupiedTables: number
  avgOrderCount: number
  sampleCount: number
}

export interface PeakHours {
  cells: PeakHourCell[]
}

export function getPeakHours(start: string, end: string) {
  return http.get<PeakHours>('/reports/peak-hours', { params: { start, end } }).then((res) => res.data)
}
