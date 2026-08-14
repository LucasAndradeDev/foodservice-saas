import { http } from './http'

export interface SsoExchangeResponse {
  accessToken: string
  restaurantName: string
}

export function exchangeSsoToken(token: string) {
  return http.post<SsoExchangeResponse>('/auth/sso', { token }).then((res) => res.data)
}
