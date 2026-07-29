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
  costQuantityCovered: number
  costTotal: number
  marginTotal: number
  marginPercentage: number | null
}

export interface ReportComparison {
  previousTotalRevenue: number
  previousClosedTabsCount: number
  previousAverageTicket: number
  revenueChangePercentage: number | null
  closedTabsChangePercentage: number | null
  averageTicketChangePercentage: number | null
}

export interface ReportSummary {
  totalRevenue: number
  closedTabsCount: number
  averageTicket: number
  byPaymentMethod: PaymentMethodTotal[]
  topProducts: TopProduct[]
  comparison: ReportComparison
}

export function getReportSummary(start: string, end: string) {
  return http.get<ReportSummary>('/reports/summary', { params: { start, end } }).then((res) => res.data)
}

export interface MonthlyGoal {
  month: string
  revenueGoal: number | null
  currentRevenue: number
  progressPercentage: number | null
}

export function getMonthlyGoal(month: string) {
  return http.get<MonthlyGoal>('/reports/goals', { params: { month } }).then((res) => res.data)
}

export function setMonthlyGoal(month: string, revenueGoal: number) {
  return http.put<MonthlyGoal>('/reports/goals', { month, revenueGoal }).then((res) => res.data)
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
