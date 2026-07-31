import { http } from './http'

export type NavSection = 'KITCHEN' | 'TABLES' | 'CHECKOUT'

export interface NavNotificationStatus {
  kitchen: boolean
  tables: boolean
  checkout: boolean
}

export function getNavNotificationStatus() {
  return http.get<NavNotificationStatus>('/nav-notifications/status').then((res) => res.data)
}

export function markNavSectionSeen(section: NavSection) {
  return http.post<void>(`/nav-notifications/${section}/seen`).then(() => undefined)
}
