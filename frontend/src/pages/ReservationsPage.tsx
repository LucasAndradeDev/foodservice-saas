import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AnimatePresence, motion } from 'framer-motion'
import { CalendarClock, ChevronLeft, ChevronRight, MessageCircle, Phone, Plus, Users } from 'lucide-react'
import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  RESERVATION_STATUS_LABELS,
  cancelReservation,
  checkInReservation,
  createReservation,
  listBlockedTables,
  listReservations,
  reservationErrorMessage,
  type Reservation,
  type ReservationStatus,
} from '../api/reservations'
import { listTables } from '../api/tables'
import { useAuth } from '../auth/AuthContext'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { DatePicker } from '../components/DatePicker'
import { DateTimePicker } from '../components/DateTimePicker'
import { Modal } from '../components/Modal'
import { PageHeader } from '../components/PageHeader'
import { Toggle } from '../components/Toggle'
import { buildWhatsAppUrl, formatBrazilianPhone } from '../utils/phone'

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
  const [customerPhone, setCustomerPhone] = useState('')
  const [reservationPendingCancel, setReservationPendingCancel] = useState<Reservation | null>(null)
  const [formError, setFormError] = useState<string | null>(null)
  const [pageError, setPageError] = useState<string | null>(null)
  // Auto-assign never combines more than two tables (see backend ReservationService) - this lets
  // staff pick tables themselves for a party that needs three or more, the escape hatch the
  // backend already supported but no UI ever exposed (2026-09-09 reservation audit, finding #1).
  const [manualTableSelection, setManualTableSelection] = useState(false)
  const [selectedTableIds, setSelectedTableIds] = useState<Set<string>>(new Set())

  const { data: reservations, isLoading } = useQuery({
    queryKey: ['reservations', date],
    queryFn: () => listReservations(date),
  })

  // Only fetched while the modal that needs it is open - this list rarely changes and every other
  // screen that needs it (Mesas) already fetches it independently.
  const { data: allTables } = useQuery({
    queryKey: ['tables-for-reservation-picker'],
    queryFn: () => listTables({ active: true }),
    enabled: isCreating,
  })

  const { data: blockedTableIds } = useQuery({
    queryKey: ['reservationBlockedTables', reservationTime],
    queryFn: () => listBlockedTables(new Date(reservationTime).toISOString()),
    enabled: isCreating && manualTableSelection && !!reservationTime,
  })

  const blockedTableIdSet = useMemo(() => new Set(blockedTableIds ?? []), [blockedTableIds])
  const sortedTables = useMemo(() => (allTables ?? []).slice().sort((a, b) => a.number - b.number), [allTables])
  const selectedTablesSummary = useMemo(() => {
    const selected = sortedTables.filter((table) => selectedTableIds.has(table.id))
    return { count: selected.length, capacity: selected.reduce((sum, table) => sum + table.capacity, 0) }
  }, [sortedTables, selectedTableIds])

  // A table selected under one time can stop being valid the moment the time changes (it might now
  // be blocked, or a table blocked before might now be free) - clearing the selection instead of
  // silently carrying it over forces staff to re-confirm against the new availability.
  useEffect(() => {
    setSelectedTableIds(new Set())
  }, [reservationTime])

  function toggleTableSelection(tableId: string) {
    setSelectedTableIds((prev) => {
      const next = new Set(prev)
      if (next.has(tableId)) next.delete(tableId)
      else next.add(tableId)
      return next
    })
  }

  function invalidate() {
    queryClient.invalidateQueries({ queryKey: ['reservations', date] })
  }

  const createMutation = useMutation({
    mutationFn: createReservation,
    onSuccess: () => {
      invalidate()
      setIsCreating(false)
    },
    onError: (err) => setFormError(reservationErrorMessage(err)),
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
    // DateTimePicker's time input carries a `min` for "today", but that input is portaled to
    // document.body - outside this <form>'s DOM subtree - so the browser's native constraint
    // validation on submit never actually sees it and doesn't block the request (same gap fixed
    // in the customer-facing ReservationFormModal.tsx, finding #8 of the 2026-09-07 review, never
    // applied here - 2026-09-09 reservation audit, finding #2).
    if (new Date(reservationTime).getTime() <= Date.now()) {
      setFormError('Escolha um horário no futuro.')
      return
    }
    if (manualTableSelection && selectedTableIds.size === 0) {
      setFormError('Escolha pelo menos uma mesa.')
      return
    }
    setFormError(null)
    const form = new FormData(event.currentTarget)
    createMutation.mutate({
      customerName: String(form.get('customerName')),
      customerPhone,
      note: String(form.get('note') || '') || undefined,
      partySize: Number(form.get('partySize')),
      reservationTime: new Date(reservationTime).toISOString(),
      ...(manualTableSelection ? { tableIds: Array.from(selectedTableIds) } : {}),
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
              setCustomerPhone('')
              setManualTableSelection(false)
              setSelectedTableIds(new Set())
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
                    <a
                      href={`tel:${reservation.customerPhone.replace(/\D/g, '')}`}
                      className="flex items-center gap-1 hover:text-brand-600 dark:hover:text-brand-400"
                    >
                      <Phone className="h-3.5 w-3.5" />
                      {reservation.customerPhone}
                    </a>
                    <a
                      href={buildWhatsAppUrl(reservation.customerPhone)}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="flex items-center gap-1 text-sage-600 hover:text-sage-700 dark:text-sage-400 dark:hover:text-sage-300"
                    >
                      <MessageCircle className="h-3.5 w-3.5" />
                      WhatsApp
                    </a>
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
              maxLength={16}
              value={customerPhone}
              onChange={(e) => setCustomerPhone(formatBrazilianPhone(e.target.value))}
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
                <DateTimePicker
                  id="reservationTime"
                  value={reservationTime}
                  onChange={setReservationTime}
                  initialViewDate={date}
                />
              </div>
            </div>

            <div className="mb-3 rounded-xl border border-gray-200 p-3 dark:border-white/10">
              <div className="flex items-center justify-between gap-3">
                <div>
                  <p className="text-sm font-medium text-gray-700 dark:text-stone-300">Escolher mesas manualmente</p>
                  <p className="text-xs text-gray-400 dark:text-stone-500">
                    Necessário pra grupos grandes — o sistema só combina até 2 mesas sozinho.
                  </p>
                </div>
                <Toggle
                  checked={manualTableSelection}
                  onChange={(checked) => {
                    setManualTableSelection(checked)
                    setSelectedTableIds(new Set())
                  }}
                />
              </div>

              {manualTableSelection && (
                <div className="mt-3">
                  {!reservationTime ? (
                    <p className="text-xs text-gray-400 dark:text-stone-500">Escolha a data e hora primeiro.</p>
                  ) : (
                    <>
                      <div className="grid grid-cols-4 gap-2 sm:grid-cols-5">
                        {sortedTables.map((table) => {
                          const isBlocked = blockedTableIdSet.has(table.id)
                          const isSelected = selectedTableIds.has(table.id)
                          return (
                            <button
                              key={table.id}
                              type="button"
                              disabled={isBlocked}
                              onClick={() => toggleTableSelection(table.id)}
                              title={
                                isBlocked
                                  ? 'Já reservada perto desse horário'
                                  : `Mesa ${table.number} · ${table.capacity} lugares`
                              }
                              className={`flex flex-col items-center justify-center gap-0.5 rounded-xl border-2 px-2 py-2 text-xs font-semibold transition ${
                                isBlocked
                                  ? 'cursor-not-allowed border-gray-100 bg-gray-50 text-gray-300 line-through dark:border-white/5 dark:bg-white/5 dark:text-stone-600'
                                  : isSelected
                                    ? 'border-brand-500 bg-brand-50 text-brand-700 dark:border-brand-400 dark:bg-brand-500/10 dark:text-brand-400'
                                    : 'border-gray-200 text-gray-600 hover:border-brand-300 dark:border-white/10 dark:text-stone-300'
                              }`}
                            >
                              <span>Mesa {table.number}</span>
                              <span className="text-[10px] font-normal opacity-70">{table.capacity} lug.</span>
                            </button>
                          )
                        })}
                      </div>
                      <p className="mt-2 text-xs text-gray-500 dark:text-stone-400">
                        {selectedTablesSummary.count} mesa{selectedTablesSummary.count === 1 ? '' : 's'} selecionada
                        {selectedTablesSummary.count === 1 ? '' : 's'} · {selectedTablesSummary.capacity} lugares
                      </p>
                    </>
                  )}
                </div>
              )}
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
