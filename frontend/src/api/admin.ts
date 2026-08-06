import { adminHttp } from '../admin/adminHttp'

export interface AdminLoginResponse {
  accessToken: string
  tokenType: string
}

export function adminLogin(username: string, password: string) {
  return adminHttp.post<AdminLoginResponse>('/login', { username, password }).then((res) => res.data)
}

export interface AdminRestaurant {
  id: string
  name: string
  tradeName: string | null
  cnpj: string | null
  phone: string | null
  active: boolean
  paymentDueDate: string | null
}

export function listRestaurants() {
  return adminHttp.get<AdminRestaurant[]>('/restaurants').then((res) => res.data)
}

export function updateRestaurantStatus(id: string, active: boolean, paymentDueDate: string | null) {
  return adminHttp
    .patch<AdminRestaurant>(`/restaurants/${id}`, { active, paymentDueDate })
    .then((res) => res.data)
}
