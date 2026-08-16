import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AnimatePresence, motion } from 'framer-motion'
import { CalendarClock, ChevronLeft, ChevronRight, Phone, Plus, Users } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  RESERVATION_STATUS_LABELS,
  cancelReservation,
  checkInReservation,
  createReservation,
  listReservations,
  type Reservation,
  type ReservationStatus,
} from '../api/reservations'
import { useAuth } from '../auth/AuthContext'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { DatePicker } from '../components/DatePicker'
import { DateTimePicker } from '../components/DateTimePicker'
import { Modal } from '../components/Modal'
import { PageHeader } from '../components/PageHeader'

const RESERVATION_STATUS_STYLES: Record<ReservationStatus, string> = {
  SCHEDULED: 'bg-teal-100 text-teal-700 dark:bg-teal-500/10 dark:text-teal-400',
  SEATED: 'bg-sage-100 text-sage-700 dark:bg-sage-500/10 dark:text-sage-400',
  CANCELLED: 'bg-gray-100 text-gray-600 dark:bg-white/10 dark:text-stone-400',
  NO_SHOW: 'bg-wine-100 text-wine-700 dark:bg-wine-500/10 dark:text-wine-400',
}

const RESERVATION_ACCENT_STYLES: Record<ReservationStatus, string> = {
  SCHEDULED: 'bg-teal-500',
  SEATED: 'bg-sage-500',
  CANCELLED: 'bg-gray-300 dark:bg-white/10',
  NO_SHOW: 'bg-wine-500',
}

const rowVariants = {
  hidden: { opacity: 0, y: 6 },
  visible: { opacity: 1, y: 0 },
  exit: { opacity: 0 },
}

function toLocalDateString(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function formatTime(iso: string) {
  return new Date(iso).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })
}

export function ReservationsPage() {
  const { user } = useAuth()
  const canManage =
    user?.role === 'OWNER' || user?.role === 'MANAGER' || user?.role === 'WAITER' || user?.role === 'CASHIER'
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  const [date, setDate] = useState(() => toLocalDateString(new Date()))
  const [isCreating, setIsCreating] = useState(false)
  const [reservationTime, setReservationTime] = useState('')
  const [reservationPendingCancel, setReservationPendingCancel] = useState<Reservation | null>(null)
  const [formError, setFormError] = useState<string | null>(null)
  const [pageError, setPageError] = useState<string | null>(null)

  const { data: reservations, isLoading } = useQuery({
    queryKey: ['reservations', date],
    queryFn: () => listReservations(date),
  })

  function invalidate() {
    queryClient.invalidateQueries({ queryKey: ['reservations', date] })
  }

  const createMutation = useMutation({
    mutationFn: createReservation,
    onSuccess: () => {
      invalidate()
      setIsCreating(false)
    },
    onError: () => setFormError('Não foi possível criar a reserva. Verifique se há mesa disponível nesse horário.'),
  })

  const checkInMutation = useMutation({
    mutationFn: checkInReservation,
    onSuccess: (reservation) => {
      invalidate()
      setPageError(null)
      navigate(`/tabs/${reservation.tabId}`)
    },
    onError: () => setPageError('Não foi possível fazer o check-in. A mesa pode não estar mais disponível.'),
  })

  const cancelMutation = useMutation({
    mutationFn: cancelReservation,
    onSuccess: () => {
      invalidate()
      setReservationPendingCancel(null)
    },
    onError: () => setPageError('Não foi possível cancelar a reserva.'),
  })

  function changeDay(offset: number) {
    const next = new Date(date + 'T00:00:00')
    next.setDate(next.getDate() + offset)
    setDate(toLocalDateString(next))
  }

  function handleCreateSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!reservationTime) {
      setFormError('Escolha a data e o horário da reserva.')
      return
    }
    setFormError(null)
    const form = new FormData(event.currentTarget)
    createMutation.mutate({
      customerName: String(form.get('customerName')),
      customerPhone: String(form.get('customerPhone')),
      note: String(form.get('note') || '') || undefined,
      partySize: Number(form.get('partySize')),
      reservationTime: new Date(reservationTime).toISOString(),
    })
  }

  return (
    <div>
      <div className="mb-5 flex flex-col gap-4 rounded-2xl border border-gray-200 bg-white p-5 shadow-sm dark:border-white/10 dark:bg-stone-900 sm:flex-row sm:items-center sm:justify-between">
        <PageHeader icon={CalendarClock} title="Reservas" />
        {canManage && (
          <button
            type="button"
            onClick={() => {
              setFormError(null)
              setReservationTime('')
              setIsCreating(true)
            }}
            className="flex items-center justify-center gap-2 rounded-xl bg-brand-600 px-4 py-3.5 text-base font-semibold text-white shadow-sm transition-all hover:bg-brand-700 hover:shadow-md active:scale-[0.98] sm:py-2.5 sm:text-sm"
          >
            <Plus className="h-5 w-5 sm:h-4 sm:w-4" />
            Nova reserva
          </button>
        )}
      </div>

      <div className="mb-4 flex items-center justify-center gap-3">
        <button
          type="button"
          onClick={() => changeDay(-1)}
          className="rounded-xl p-2 text-gray-500 hover:bg-gray-100 dark:text-stone-400 dark:hover:bg-white/5"
          aria-label="Dia anterior"
        >
          <ChevronLeft className="h-5 w-5" />
        </button>
        <DatePicker value={date} onChange={setDate} />
        <button
          type="button"
          onClick={() => changeDay(1)}
          className="rounded-xl p-2 text-gray-500 hover:bg-gray-100 dark:text-stone-400 dark:hover:bg-white/5"
          aria-label="Próximo dia"
        >
          <ChevronRight className="h-5 w-5" />
        </button>
      </div>

      {pageError && <p className="mb-4 text-sm text-wine-600 dark:text-wine-400">{pageError}</p>}

      {isLoading && <p className="text-sm text-gray-500 dark:text-stone-400">Carregando...</p>}

      {reservations?.length === 0 && !isLoading && (
        <div className="flex flex-col items-center gap-3 rounded-2xl border border-dashed border-gray-300 bg-gray-50 py-16 text-center dark:border-white/10 dark:bg-white/5">
          <span className="flex h-14 w-14 items-center justify-center rounded-full bg-white text-gray-300 shadow-sm dark:bg-stone-800 dark:text-stone-600">
            <CalendarClock className="h-7 w-7" />
          </span>
          <p className="text-sm text-gray-500 dark:text-stone-400">Nenhuma reserva pra esse dia.</p>
        </div>
      )}

      {reservations && reservations.length > 0 && (
        <div className="overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-sm dark:border-white/10 dark:bg-stone-900">
          <div className="divide-y divide-gray-100 dark:divide-white/10">
            <AnimatePresence initial={false}>
              {reservations.map((reservation) => {
          const isInactive = reservation.status === 'CANCELLED' || reservation.status === 'NO_SHOW'
          return (
            <motion.div
              key={reservation.id}
              layout
              variants={rowVariants}
              initial="hidden"
              animate="visible"
              exit="exit"
              transition={{ duration: 0.18 }}
              className={`relative flex flex-col gap-3 py-4 pr-4 pl-5 transition-colors hover:bg-gray-50 sm:flex-row sm:items-center sm:justify-between sm:gap-6 dark:hover:bg-white/5 ${
                isInactive ? 'opacity-60' : ''
              }`}
            >
              <span
                className={`absolute inset-y-0 left-0 w-1 ${RESERVATION_ACCENT_STYLES[reservation.status]}`}
                aria-hidden="true"
              />
              <div className="flex min-w-0 items-center gap-3">
                <span
                  className={`flex h-12 w-12 shrink-0 flex-col items-center justify-center rounded-xl text-sm font-bold ${
                    isInactive
                      ? 'bg-gray-100 text-gray-500 dark:bg-white/5 dark:text-stone-400'
                      : 'bg-brand-100 text-brand-700 dark:bg-brand-500/20 dark:text-brand-400'
                  }`}
                >
                  {formatTime(reservation.reservationTime)}
                </span>
                <div className="min-w-0">
                  <p className="truncate text-sm font-semibold text-gray-900 dark:text-white">{reservation.customerName}</p>
                  <p className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-gray-500 dark:text-stone-400">
                    <span className="flex items-center gap-1">
                      <Users className="h-3.5 w-3.5" />
                      {reservation.partySize}
                    </span>
                    <span className="flex items-center gap-1">
                      <Phone className="h-3.5 w-3.5" />
                      {reservation.customerPhone}
                    </span>
                    {reservation.tables.length > 0 && (
                      <span>Mesa {reservation.tables.map((t) => t.number).join(', ')}</span>
                    )}
                  </p>
                  {reservation.note && (
                    <p className="mt-0.5 text-xs italic text-gray-400 dark:text-stone-500">{reservation.note}</p>
                  )}
                </div>
              </div>

              <div className="flex shrink-0 items-center gap-2 sm:justify-end">
                <span className={`rounded-full px-2.5 py-1 text-xs font-medium ${RESERVATION_STATUS_STYLES[reservation.status]}`}>
                  {RESERVATION_STATUS_LABELS[reservation.status]}
                </span>
                {canManage && (reservation.status === 'SCHEDULED' || reservation.status === 'NO_SHOW') && (
                  <>
                    <button
                      type="button"
                      onClick={() => checkInMutation.mutate(reservation.id)}
                      disabled={checkInMutation.isPending}
                      className="rounded-xl bg-brand-600 px-3 py-2 text-xs font-semibold text-white shadow-sm transition hover:bg-brand-700 disabled:opacity-50"
                    >
                      Cliente chegou
                    </button>
                    <button
                      type="button"
                      onClick={() => setReservationPendingCancel(reservation)}
                      className="rounded-xl border border-gray-200 px-3 py-2 text-xs font-medium text-gray-600 hover:bg-gray-50 dark:border-white/10 dark:text-stone-400 dark:hover:bg-white/5"
                    >
                      Cancelar
                    </button>
                  </>
                )}
              </div>
            </motion.div>
          )
              })}
            </AnimatePresence>
          </div>
        </div>
      )}

      {isCreating && (
        <Modal title="Nova reserva" onClose={() => setIsCreating(false)}>
          <form onSubmit={handleCreateSubmit}>
            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="customerName">
              Nome
            </label>
            <input
              id="customerName"
              name="customerName"
              type="text"
              required
              maxLength={255}
              className="mb-3 w-full rounded-xl border border-gray-200 px-3 py-2.5 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white"
            />

            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="customerPhone">
              Telefone
            </label>
            <input
              id="customerPhone"
              name="customerPhone"
              type="tel"
              required
              maxLength={20}
              className="mb-3 w-full rounded-xl border border-gray-200 px-3 py-2.5 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white"
            />

            <div className="mb-3 grid grid-cols-2 gap-3">
              <div>
                <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="partySize">
                  Pessoas
                </label>
                <input
                  id="partySize"
                  name="partySize"
                  type="number"
                  min={1}
                  max={50}
                  required
                  defaultValue={2}
                  className="w-full rounded-xl border border-gray-200 px-3 py-2.5 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white"
                />
              </div>
              <div>
                <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="reservationTime">
                  Data e hora
                </label>
                <DateTimePicker id="reservationTime" value={reservationTime} onChange={setReservationTime} />
              </div>
            </div>

            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="note">
              Observação (opcional)
            </label>
            <input
              id="note"
              name="note"
              type="text"
              maxLength={500}
              placeholder="Ex: aniversário, cadeira de bebê"
              className="mb-4 w-full rounded-xl border border-gray-200 px-3 py-2.5 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white"
            />

            {formError && <p className="mb-4 text-sm text-wine-600 dark:text-wine-400">{formError}</p>}

            <button
              type="submit"
              disabled={createMutation.isPending}
              className="w-full rounded-xl bg-brand-600 px-3 py-2.5 text-sm font-semibold text-white shadow-sm transition hover:bg-brand-700 disabled:opacity-50"
            >
              Salvar
            </button>
          </form>
        </Modal>
      )}

      {reservationPendingCancel && (
        <ConfirmDialog
          title="Cancelar reserva"
          message={`Cancelar a reserva de "${reservationPendingCancel.customerName}"? A mesa fica livre imediatamente.`}
          confirmLabel="Cancelar reserva"
          cancelLabel="Voltar"
          danger
          isLoading={cancelMutation.isPending}
          onConfirm={() => cancelMutation.mutate(reservationPendingCancel.id)}
          onCancel={() => setReservationPendingCancel(null)}
        />
      )}
    </div>
  )
}
