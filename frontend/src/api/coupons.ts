import type { DiscountType } from './orders'
import { http } from './http'

export interface Coupon {
  id: string
  restaurantId: string
  code: string
  discountType: DiscountType
  discountValue: number
  active: boolean
  expiresAt: string | null
  maxUses: number | null
  usedCount: number
}

export interface CreateCouponPayload {
  code: string
  discountType: DiscountType
  discountValue: number
  expiresAt?: string
  maxUses?: number
}

export interface UpdateCouponPayload {
  discountType?: DiscountType
  discountValue?: number
  active?: boolean
  expiresAt?: string
  clearExpiresAt?: boolean
  maxUses?: number
  clearMaxUses?: boolean
}

export function listCoupons() {
  return http.get<Coupon[]>('/coupons').then((res) => res.data)
}

export function createCoupon(payload: CreateCouponPayload) {
  return http.post<Coupon>('/coupons', payload).then((res) => res.data)
}

export function updateCoupon(id: string, payload: UpdateCouponPayload) {
  return http.put<Coupon>(`/coupons/${id}`, payload).then((res) => res.data)
}

export function deleteCoupon(id: string) {
  return http.delete<void>(`/coupons/${id}`).then((res) => res.data)
}
