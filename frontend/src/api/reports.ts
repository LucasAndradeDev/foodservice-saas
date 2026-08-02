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

export interface FeedbackEntry {
  rating: number
  comment: string | null
  createdAt: string
  tableNumbers: number[]
}

export interface FeedbackReport {
  averageRating: number | null
  totalCount: number
  recent: FeedbackEntry[]
}

export function getFeedbackReport(start: string, end: string) {
  return http.get<FeedbackReport>('/reports/feedback', { params: { start, end } }).then((res) => res.data)
}

export interface FeedbackPage {
  entries: FeedbackEntry[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export function getFeedbackEntries(start: string, end: string, page: number, size: number) {
  return http
    .get<FeedbackPage>('/reports/feedback/entries', { params: { start, end, page, size } })
    .then((res) => res.data)
}

export interface WaiterPerformanceRow {
  waiterId: string | null
  waiterName: string | null
  active: boolean | null
  totalSales: number
  orderCount: number
  averageServiceTimeMinutes: number | null
}

export interface WaiterPerformanceReport {
  rows: WaiterPerformanceRow[]
}

export function getWaiterPerformance(start: string, end: string) {
  return http.get<WaiterPerformanceReport>('/reports/waiters', { params: { start, end } }).then((res) => res.data)
}
