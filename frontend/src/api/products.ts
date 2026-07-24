import { http } from './http'

export interface Product {
  id: string
  restaurantId: string
  categoryId: string
  name: string
  description: string | null
  imageUrl: string | null
  price: number
  active: boolean
}

export interface ProductFilters {
  categoryId?: string
  search?: string
  active?: boolean
}

export interface ProductPayload {
  name: string
  description?: string
  imageUrl?: string
  price: number
  categoryId: string
}

export function listProducts(filters: ProductFilters = {}) {
  return http.get<Product[]>('/products', { params: filters }).then((res) => res.data)
}

export function createProduct(payload: ProductPayload) {
  return http.post<Product>('/products', payload).then((res) => res.data)
}

export function updateProduct(id: string, payload: Partial<ProductPayload & { active: boolean }>) {
  return http.put<Product>(`/products/${id}`, payload).then((res) => res.data)
}

export function deleteProduct(id: string) {
  return http.delete<void>(`/products/${id}`).then((res) => res.data)
}

export function uploadProductImage(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return http.post<{ url: string }>('/products/upload-image', formData).then((res) => res.data.url)
}
