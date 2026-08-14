import { http } from './http'

export interface MoraProduct {
  productId: string
  productName: string
}

export function listMoraProducts() {
  return http.get<MoraProduct[]>('/mora-products').then((res) => res.data)
}
