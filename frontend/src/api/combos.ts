import { http } from './http'

export interface ComboItem {
  id: string
  productId: string
  productName: string
  unitPrice: number
  quantity: number
}

export interface ComboItemInput {
  productId: string
  quantity: number
}

export interface ComboSlot {
  id: string
  name: string
  required: boolean
  options: ComboItem[]
}

export interface ComboSlotInput {
  name: string
  options: ComboItemInput[]
}

export interface ComboComposition {
  productId: string
  discountPercentage: number | null
  fixedItems: ComboItem[]
  slots: ComboSlot[]
  minPrice: number
  maxPrice: number
}

export interface UpdateComboCompositionPayload {
  discountPercentage: number
  fixedItems: ComboItemInput[]
  slots: ComboSlotInput[]
}

export function getComboComposition(productId: string) {
  return http.get<ComboComposition>(`/products/${productId}/combo`).then((res) => res.data)
}

export function updateComboComposition(productId: string, payload: UpdateComboCompositionPayload) {
  return http.put<ComboComposition>(`/products/${productId}/combo`, payload).then((res) => res.data)
}
