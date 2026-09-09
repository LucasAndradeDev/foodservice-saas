import { http } from './http'

export interface Dashboard {
  freeTables: number
  occupiedTables: number
  ordersInPreparation: number
  revenueToday: number
  hasProducts: boolean
}

export function getDashboard() {
  return http.get<Dashboard>('/dashboard').then((res) => res.data)
}
