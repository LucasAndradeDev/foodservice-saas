import type { DayOfWeek } from './productAvailability'
import type { DiscountType } from './orders'
import { http } from './http'

export interface HappyHourRule {
  id: string
  categoryId: string
  categoryName: string
  daysOfWeek: DayOfWeek[]
  startTime: string
  endTime: string
  discountType: DiscountType
  discountValue: number
  active: boolean
}

export interface HappyHourRulePayload {
  categoryId: string
  daysOfWeek: DayOfWeek[] | null
  startTime: string
  endTime: string
  discountType: DiscountType
  discountValue: number
  active: boolean
}

export function listHappyHourRules() {
  return http.get<HappyHourRule[]>('/happy-hour-rules').then((res) => res.data)
}

export function createHappyHourRule(payload: HappyHourRulePayload) {
  return http.post<HappyHourRule>('/happy-hour-rules', payload).then((res) => res.data)
}

export function updateHappyHourRule(id: string, payload: HappyHourRulePayload) {
  return http.put<HappyHourRule>(`/happy-hour-rules/${id}`, payload).then((res) => res.data)
}

export function deleteHappyHourRule(id: string) {
  return http.delete<void>(`/happy-hour-rules/${id}`).then((res) => res.data)
}
