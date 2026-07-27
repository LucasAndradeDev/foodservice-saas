import { http } from './http'

export type SelectionType = 'SINGLE' | 'MULTIPLE'

export interface ModifierOption {
  id: string
  name: string
  priceDelta: number
}

export interface ModifierOptionInput {
  name: string
  priceDelta: number
}

export interface ModifierGroup {
  id: string
  name: string
  selectionType: SelectionType
  required: boolean
  options: ModifierOption[]
}

export interface ModifierGroupPayload {
  name: string
  selectionType: SelectionType
  required: boolean
  options: ModifierOptionInput[]
}

export function listModifierGroups(productId: string) {
  return http.get<ModifierGroup[]>(`/products/${productId}/modifier-groups`).then((res) => res.data)
}

export function createModifierGroup(productId: string, payload: ModifierGroupPayload) {
  return http.post<ModifierGroup>(`/products/${productId}/modifier-groups`, payload).then((res) => res.data)
}

export function updateModifierGroup(productId: string, groupId: string, payload: Partial<ModifierGroupPayload>) {
  return http.put<ModifierGroup>(`/products/${productId}/modifier-groups/${groupId}`, payload).then((res) => res.data)
}

export function deleteModifierGroup(productId: string, groupId: string) {
  return http.delete<void>(`/products/${productId}/modifier-groups/${groupId}`).then((res) => res.data)
}
