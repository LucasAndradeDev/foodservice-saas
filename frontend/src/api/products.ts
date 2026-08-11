import { http } from './http'

export type ProductType = 'SIMPLE' | 'COMBO'

export interface Product {
  id: string
  restaurantId: string
  categoryId: string
  name: string
  description: string | null
  imageUrl: string | null
  galleryImageUrls: string[]
  price: number
  costPrice: number | null
  active: boolean
  soldOutToday: boolean
  featured: boolean
  availableNow: boolean
  hasModifierGroups: boolean
  type: ProductType
  discountPercentage: number | null
}

export interface ProductFilters {
  categoryId?: string
  search?: string
  active?: boolean
  type?: ProductType
}

export interface ProductPayload {
  name: string
  description?: string
  imageUrl?: string
  galleryImageUrls?: string[]
  price: number
  costPrice?: number
  categoryId: string
  featured?: boolean
  type?: ProductType
}

export function listProducts(filters: ProductFilters = {}) {
  return http.get<Product[]>('/products', { params: filters }).then((res) => res.data)
}

export function getProduct(id: string) {
  return http.get<Product>(`/products/${id}`).then((res) => res.data)
}

export function createProduct(payload: ProductPayload) {
  return http.post<Product>('/products', payload).then((res) => res.data)
}

export function updateProduct(id: string, payload: Partial<Omit<ProductPayload, 'type'> & { active: boolean; soldOut: boolean }>) {
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
