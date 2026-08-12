import { http } from './http'

export interface PixIntegrationStatus {
  configured: boolean
}

export function getPixIntegrationStatus() {
  return http.get<PixIntegrationStatus>('/pix-integration').then((res) => res.data)
}

export function savePixIntegration(appId: string) {
  return http.put('/pix-integration', { appId })
}
