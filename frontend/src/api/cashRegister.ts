import { http } from './http'

export type CashRegisterSessionStatus = 'OPEN' | 'CLOSED'

export interface CashWithdrawal {
  id: string
  amount: number
  reason: string
  withdrawnByName: string | null
  withdrawnAt: string
}

export interface CashRegisterSession {
  id: string
  status: CashRegisterSessionStatus
  openedAt: string
  openedByName: string | null
  openingAmount: number
  expectedAmount: number
  countedAmount: number | null
  differenceAmount: number | null
  closedAt: string | null
  closedByName: string | null
  closingNotes: string | null
  withdrawals: CashWithdrawal[]
}

export function getCurrentSession() {
  return http.get<CashRegisterSession>('/cash-register/current').then((res) => res.data || null)
}

export function listCashRegisterSessions(range?: { start: string; end: string }) {
  return http
    .get<CashRegisterSession[]>('/cash-register/sessions', { params: range })
    .then((res) => res.data)
}

export function openCashRegister(openingAmount: number) {
  return http.post<CashRegisterSession>('/cash-register/open', { openingAmount }).then((res) => res.data)
}

export function closeCashRegister(countedAmount: number, closingNotes?: string) {
  return http.post<CashRegisterSession>('/cash-register/close', { countedAmount, closingNotes }).then((res) => res.data)
}

export function addCashWithdrawal(amount: number, reason: string) {
  return http.post<CashWithdrawal>('/cash-register/withdrawals', { amount, reason }).then((res) => res.data)
}
