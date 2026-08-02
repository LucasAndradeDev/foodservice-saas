import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import { Banknote, History, Lock, MinusCircle, Unlock } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import {
  addCashWithdrawal,
  closeCashRegister,
  getCurrentSession,
  listCashRegisterSessions,
  openCashRegister,
} from '../api/cashRegister'
import { Modal } from '../components/Modal'

const currencyFormatter = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })
const dateTimeFormatter = new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })

function extractErrorMessage(err: unknown, fallback: string) {
  if (isAxiosError(err) && err.response?.data?.message) {
    return err.response.data.message as string
  }
  return fallback
}

export function CashRegisterPage() {
  const queryClient = useQueryClient()

  const { data: currentSession, isLoading } = useQuery({
    queryKey: ['cash-register', 'current'],
    queryFn: getCurrentSession,
    refetchInterval: 15000,
  })

  const { data: sessions } = useQuery({
    queryKey: ['cash-register', 'sessions'],
    queryFn: listCashRegisterSessions,
  })

  const [isOpeningForm, setIsOpeningForm] = useState(false)
  const [openingAmount, setOpeningAmount] = useState('')
  const [isWithdrawalForm, setIsWithdrawalForm] = useState(false)
  const [withdrawalAmount, setWithdrawalAmount] = useState('')
  const [withdrawalReason, setWithdrawalReason] = useState('')
  const [isClosingForm, setIsClosingForm] = useState(false)
  const [countedAmount, setCountedAmount] = useState('')
  const [closingNotes, setClosingNotes] = useState('')
  const [error, setError] = useState<string | null>(null)

  function invalidate() {
    queryClient.invalidateQueries({ queryKey: ['cash-register'] })
  }

  const openMutation = useMutation({
    mutationFn: (amount: number) => openCashRegister(amount),
    onSuccess: () => {
      invalidate()
      setIsOpeningForm(false)
    },
    onError: (err) => setError(extractErrorMessage(err, 'Não foi possível abrir o caixa.')),
  })

  const withdrawalMutation = useMutation({
    mutationFn: ({ amount, reason }: { amount: number; reason: string }) => addCashWithdrawal(amount, reason),
    onSuccess: () => {
      invalidate()
      setIsWithdrawalForm(false)
    },
    onError: (err) => setError(extractErrorMessage(err, 'Não foi possível registrar a sangria.')),
  })

  const closeMutation = useMutation({
    mutationFn: ({ counted, notes }: { counted: number; notes?: string }) => closeCashRegister(counted, notes),
    onSuccess: () => {
      invalidate()
      setIsClosingForm(false)
    },
    onError: (err) => setError(extractErrorMessage(err, 'Não foi possível fechar o caixa.')),
  })

  function openOpeningForm() {
    setOpeningAmount('')
    setError(null)
    setIsOpeningForm(true)
  }

  function openWithdrawalForm() {
    setWithdrawalAmount('')
    setWithdrawalReason('')
    setError(null)
    setIsWithdrawalForm(true)
  }

  function openClosingForm() {
    setCountedAmount('')
    setClosingNotes('')
    setError(null)
    setIsClosingForm(true)
  }

  function handleOpenSubmit(event: FormEvent) {
    event.preventDefault()
    const amount = Number(openingAmount)
    if (Number.isNaN(amount) || amount < 0) return
    setError(null)
    openMutation.mutate(amount)
  }

  function handleWithdrawalSubmit(event: FormEvent) {
    event.preventDefault()
    const amount = Number(withdrawalAmount)
    if (!amount || amount <= 0 || !withdrawalReason.trim()) return
    setError(null)
    withdrawalMutation.mutate({ amount, reason: withdrawalReason.trim() })
  }

  function handleCloseSubmit(event: FormEvent) {
    event.preventDefault()
    const amount = Number(countedAmount)
    if (Number.isNaN(amount) || amount < 0) return
    setError(null)
    closeMutation.mutate({ counted: amount, notes: closingNotes.trim() || undefined })
  }

  return (
    <div>
      <h1 className="mb-4 flex items-center gap-2 rounded-xl border border-gray-200 bg-white p-4 text-lg font-semibold text-gray-800 shadow-xs dark:border-white/10 dark:bg-stone-900 dark:text-white">
        <Banknote className="h-5 w-5 text-brand-600 dark:text-brand-400" />
        Caixa
      </h1>

      {isLoading && <p className="text-sm text-gray-500 dark:text-stone-400">Carregando...</p>}

      {!isLoading && !currentSession && (
        <div className="rounded-xl border border-gray-200 bg-white p-4 shadow-xs dark:border-white/10 dark:bg-stone-900">
          <div className="mb-3 flex items-center gap-2 text-sm text-gray-500 dark:text-stone-400">
            <Lock className="h-4 w-4" />
            Caixa fechado.
          </div>
          <button
            type="button"
            onClick={openOpeningForm}
            className="flex items-center gap-1.5 rounded-lg bg-brand-600 px-3.5 py-2 text-sm font-medium text-white hover:bg-brand-700"
          >
            <Unlock className="h-4 w-4" />
            Abrir caixa
          </button>
        </div>
      )}

      {currentSession && (
        <div className="rounded-xl border border-green-300 bg-white p-4 shadow-sm dark:border-green-500/30 dark:bg-stone-900">
          <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
            <span className="flex items-center gap-1.5 rounded-full bg-green-100 px-2 py-0.5 text-xs font-medium text-green-700 dark:bg-green-500/10 dark:text-green-400">
              <Unlock className="h-3 w-3" />
              Caixa aberto
            </span>
            <span className="text-xs text-gray-400 dark:text-stone-500">
              Aberto por {currentSession.openedByName ?? '—'} às {dateTimeFormatter.format(new Date(currentSession.openedAt))}
            </span>
          </div>

          <div className="mb-4 grid grid-cols-2 gap-3">
            <div>
              <div className="text-xs text-gray-500 dark:text-stone-400">Valor inicial</div>
              <div className="text-base font-semibold text-gray-800 dark:text-white">
                {currencyFormatter.format(currentSession.openingAmount)}
              </div>
            </div>
            <div>
              <div className="text-xs text-gray-500 dark:text-stone-400">Esperado em dinheiro</div>
              <div className="text-base font-semibold text-gray-800 dark:text-white">
                {currencyFormatter.format(currentSession.expectedAmount)}
              </div>
            </div>
          </div>

          <div className="mb-4 flex gap-2">
            <button
              type="button"
              onClick={openWithdrawalForm}
              className="flex flex-1 items-center justify-center gap-1.5 rounded-md border border-gray-300 px-3 py-2 text-sm text-gray-700 hover:bg-gray-100 dark:border-white/10 dark:text-stone-300 dark:hover:bg-white/5"
            >
              <MinusCircle className="h-4 w-4" />
              Fazer sangria
            </button>
            <button
              type="button"
              onClick={openClosingForm}
              className="flex flex-1 items-center justify-center gap-1.5 rounded-md bg-brand-600 px-3 py-2 text-sm font-medium text-white hover:bg-brand-700"
            >
              <Lock className="h-4 w-4" />
              Fechar caixa
            </button>
          </div>

          {currentSession.withdrawals.length > 0 && (
            <div>
              <div className="mb-1 text-xs font-medium text-gray-500 dark:text-stone-400">Sangrias deste turno</div>
              <ul className="divide-y divide-gray-100 dark:divide-white/10">
                {currentSession.withdrawals.map((withdrawal) => (
                  <li key={withdrawal.id} className="flex items-center justify-between py-1.5 text-sm">
                    <div>
                      <span className="text-gray-800 dark:text-white">{withdrawal.reason}</span>
                      <div className="text-xs text-gray-400 dark:text-stone-500">
                        {withdrawal.withdrawnByName ?? '—'} às {dateTimeFormatter.format(new Date(withdrawal.withdrawnAt))}
                      </div>
                    </div>
                    <span className="shrink-0 text-gray-600 dark:text-stone-300">
                      -{currencyFormatter.format(withdrawal.amount)}
                    </span>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}

      {sessions && sessions.length > 0 && (
        <div className="mt-8">
          <div className="mb-2 flex items-center gap-2 text-sm font-semibold text-gray-700 dark:text-stone-300">
            <History className="h-4 w-4 text-gray-400 dark:text-stone-500" />
            Histórico de turnos
          </div>
          <div className="overflow-x-auto rounded-xl border border-gray-200 bg-white shadow-xs dark:border-white/10 dark:bg-stone-900">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="border-b border-gray-100 text-xs text-gray-500 dark:border-white/10 dark:text-stone-400">
                  <th className="px-4 py-2 font-medium">Aberto</th>
                  <th className="px-4 py-2 font-medium">Fechado</th>
                  <th className="px-4 py-2 font-medium">Inicial</th>
                  <th className="px-4 py-2 font-medium">Contado</th>
                  <th className="px-4 py-2 font-medium">Diferença</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100 dark:divide-white/10">
                {sessions.map((session) => (
                  <tr key={session.id}>
                    <td className="px-4 py-2 text-gray-700 dark:text-stone-300">
                      {dateTimeFormatter.format(new Date(session.openedAt))}
                      <div className="text-xs text-gray-400 dark:text-stone-500">{session.openedByName ?? '—'}</div>
                    </td>
                    <td className="px-4 py-2 text-gray-700 dark:text-stone-300">
                      {session.closedAt ? (
                        <>
                          {dateTimeFormatter.format(new Date(session.closedAt))}
                          <div className="text-xs text-gray-400 dark:text-stone-500">{session.closedByName ?? '—'}</div>
                        </>
                      ) : (
                        <span className="text-green-600 dark:text-green-400">Em aberto</span>
                      )}
                    </td>
                    <td className="px-4 py-2 text-gray-700 dark:text-stone-300">
                      {currencyFormatter.format(session.openingAmount)}
                    </td>
                    <td className="px-4 py-2 text-gray-700 dark:text-stone-300">
                      {session.countedAmount != null ? currencyFormatter.format(session.countedAmount) : '—'}
                    </td>
                    <td
                      className={`px-4 py-2 ${
                        session.differenceAmount ? 'text-amber-600 dark:text-amber-400' : 'text-gray-700 dark:text-stone-300'
                      }`}
                    >
                      {session.differenceAmount != null ? currencyFormatter.format(session.differenceAmount) : '—'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {isOpeningForm && (
        <Modal title="Abrir caixa" onClose={() => setIsOpeningForm(false)}>
          <form onSubmit={handleOpenSubmit}>
            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="openingAmount">
              Valor inicial em dinheiro
            </label>
            <input
              id="openingAmount"
              type="number"
              required
              min="0"
              step="0.01"
              value={openingAmount}
              onChange={(e) => setOpeningAmount(e.target.value)}
              placeholder="0,00"
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
            />

            {error && <p className="mb-4 text-sm text-red-600 dark:text-red-400">{error}</p>}

            <button
              type="submit"
              disabled={openMutation.isPending}
              className="w-full rounded-md bg-brand-600 px-3 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50"
            >
              Abrir
            </button>
          </form>
        </Modal>
      )}

      {isWithdrawalForm && (
        <Modal title="Fazer sangria" onClose={() => setIsWithdrawalForm(false)}>
          <form onSubmit={handleWithdrawalSubmit}>
            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="withdrawalAmount">
              Valor retirado
            </label>
            <input
              id="withdrawalAmount"
              type="number"
              required
              min="0.01"
              step="0.01"
              value={withdrawalAmount}
              onChange={(e) => setWithdrawalAmount(e.target.value)}
              placeholder="0,00"
              className="mb-3 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
            />

            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="withdrawalReason">
              Motivo
            </label>
            <input
              id="withdrawalReason"
              type="text"
              required
              maxLength={255}
              value={withdrawalReason}
              onChange={(e) => setWithdrawalReason(e.target.value)}
              placeholder="Ex: Levado ao cofre"
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
            />

            {error && <p className="mb-4 text-sm text-red-600 dark:text-red-400">{error}</p>}

            <button
              type="submit"
              disabled={withdrawalMutation.isPending}
              className="w-full rounded-md bg-brand-600 px-3 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50"
            >
              Registrar sangria
            </button>
          </form>
        </Modal>
      )}

      {isClosingForm && currentSession && (
        <Modal title="Fechar caixa" onClose={() => setIsClosingForm(false)}>
          <form onSubmit={handleCloseSubmit}>
            <p className="mb-3 text-sm text-gray-500 dark:text-stone-400">
              Esperado em dinheiro:{' '}
              <span className="font-medium text-gray-800 dark:text-white">
                {currencyFormatter.format(currentSession.expectedAmount)}
              </span>
            </p>

            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="countedAmount">
              Valor contado na gaveta
            </label>
            <input
              id="countedAmount"
              type="number"
              required
              min="0"
              step="0.01"
              value={countedAmount}
              onChange={(e) => setCountedAmount(e.target.value)}
              placeholder="0,00"
              className="mb-3 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
            />

            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="closingNotes">
              Observação (obrigatória se o valor contado for diferente do esperado)
            </label>
            <textarea
              id="closingNotes"
              rows={2}
              maxLength={500}
              value={closingNotes}
              onChange={(e) => setClosingNotes(e.target.value)}
              placeholder="Ex: Faltou troco, conferido com a equipe"
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
            />

            {error && <p className="mb-4 text-sm text-red-600 dark:text-red-400">{error}</p>}

            <button
              type="submit"
              disabled={closeMutation.isPending}
              className="w-full rounded-md bg-brand-600 px-3 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50"
            >
              Fechar caixa
            </button>
          </form>
        </Modal>
      )}
    </div>
  )
}
