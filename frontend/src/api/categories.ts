import { http } from './http'

export interface Category {
  id: string
  restaurantId: string
  name: string
  active: boolean
}

export interface CategoryPayload {
  name: string
}

export function listCategories(active?: boolean) {
  return http.get<Category[]>('/categories', { params: { active } }).then((res) => res.data)
}

export function createCategory(payload: CategoryPayload) {
  return http.post<Category>('/categories', payload).then((res) => res.data)
}

export function updateCategory(id: string, payload: Partial<CategoryPayload & { active: boolean }>) {
  return http.put<Category>(`/categories/${id}`, payload).then((res) => res.data)
}
