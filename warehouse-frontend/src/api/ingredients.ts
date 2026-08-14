import { http } from './http'

export interface Ingredient {
  id: string
  name: string
  unit: string
  currentQuantity: number
  lowStockThreshold: number | null
  active: boolean
  lowStock: boolean
}

export interface IngredientPayload {
  name: string
  unit: string
  currentQuantity?: number
  lowStockThreshold?: number | null
}

export function listIngredients(active?: boolean) {
  return http.get<Ingredient[]>('/ingredients', { params: { active } }).then((res) => res.data)
}

export function createIngredient(payload: IngredientPayload) {
  return http.post<Ingredient>('/ingredients', payload).then((res) => res.data)
}

export function updateIngredient(id: string, payload: Partial<IngredientPayload & { active: boolean }>) {
  return http.put<Ingredient>(`/ingredients/${id}`, payload).then((res) => res.data)
}
