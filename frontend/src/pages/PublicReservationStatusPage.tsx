import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CalendarClock, Phone, Users } from 'lucide-react'
import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { RESERVATION_STATUS_LABELS, cancelReservationByToken, getReservationByToken } from '../api/reservations'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { usePublicMenuTheme } from './publicMenu/usePublicMenuTheme'

function formatDateTime(iso: string) {
  return new Date(iso).toLocaleString('pt-BR', { dateStyle: 'long', timeStyle: 'short' })
}

export function PublicReservationStatusPage() {
  const { token } = useParams<{ token: string }>()
  const { theme } = usePublicMenuTheme()
  const queryClient = useQueryClient()
  const [isCancelling, setIsCancelling] = useState(false)

  const { data: reservation, isLoading, isError } = useQuery({
    queryKey: ['reservationStatus', token],
    queryFn: () => getReservationByToken(token!),
    enabled: !!token,
    retry: false,
    refetchInterval: 3000,
    refetchIntervalInBackground: true,
  })

  const cancelMutation = useMutation({
    mutationFn: () => cancelReservationByToken(token!),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['reservationStatus', token] })
      setIsCancelling(false)
    },
  })

  const themeClass = theme === 'dark' ? 'dark' : ''

  if (isLoading) {
    return (
      <div className={`${themeClass} flex min-h-screen items-center justify-center bg-gray-50 p-4 dark:bg-stone-950`}>
        <p className="text-sm text-gray-500 dark:text-stone-400">Carregando...</p>
      </div>
    )
  }

  if (isError || !reservation) {
    return (
      <div className={`${themeClass} flex min-h-screen items-center justify-center bg-gray-50 p-4 dark:bg-stone-950`}>
        <p className="text-sm text-gray-500 dark:text-stone-400">Reserva não encontrada.</p>
      </div>
    )
  }

  const canCancel = reservation.status === 'SCHEDULED' || reservation.status === 'NO_SHOW'

  return (
    <div className={`${themeClass} min-h-screen bg-gray-50 dark:bg-stone-950`}>
      <main className="mx-auto max-w-md px-4 py-10">
        <div className="rounded-2xl border border-gray-200 bg-white p-6 shadow-sm dark:border-stone-800 dark:bg-stone-900">
          <div className="mb-4 flex items-center gap-3">
            <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-brand-600 text-white shadow-sm">
              <CalendarClock className="h-5 w-5" />
            </span>
            <div>
              <h1 className="text-lg font-semibold text-gray-900 dark:text-white">Sua reserva</h1>
              <p className="text-sm text-gray-500 dark:text-stone-400">{RESERVATION_STATUS_LABELS[reservation.status]}</p>
            </div>
          </div>

          <div className="space-y-2 rounded-xl bg-gray-50 p-4 text-sm dark:bg-white/5">
            <p className="font-medium text-gray-900 dark:text-white">{formatDateTime(reservation.reservationTime)}</p>
            <p className="flex items-center gap-2 text-gray-600 dark:text-stone-300">
              <Users className="h-4 w-4" />
              {reservation.partySize} pessoas
            </p>
            <p className="flex items-center gap-2 text-gray-600 dark:text-stone-300">
              <Phone className="h-4 w-4" />
              {reservation.customerPhone}
            </p>
            {reservation.note && <p className="italic text-gray-500 dark:text-stone-400">{reservation.note}</p>}
          </div>

          {cancelMutation.isSuccess && (
            <p className="mt-4 text-sm text-gray-500 dark:text-stone-400">Reserva cancelada.</p>
          )}

          {canCancel && !cancelMutation.isSuccess && (
            <button
              type="button"
              onClick={() => setIsCancelling(true)}
              className="mt-4 w-full rounded-xl border border-red-200 px-3 py-2.5 text-sm font-medium text-red-600 hover:bg-red-50 dark:border-red-500/30 dark:text-red-400 dark:hover:bg-red-500/10"
            >
              Cancelar reserva
            </button>
          )}
        </div>
      </main>

      {isCancelling && (
        <ConfirmDialog
          title="Cancelar reserva"
          message="Tem certeza que quer cancelar sua reserva?"
          confirmLabel="Cancelar reserva"
          cancelLabel="Voltar"
          danger
          isLoading={cancelMutation.isPending}
          onConfirm={() => cancelMutation.mutate()}
          onCancel={() => setIsCancelling(false)}
        />
      )}
    </div>
  )
}
