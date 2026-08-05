import { useMutation } from '@tanstack/react-query'
import { CheckCircle2 } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { createPublicReservation, type PublicCreateReservationPayload } from '../../api/reservations'
import { DateTimePicker } from '../../components/DateTimePicker'
import { Modal } from '../../components/Modal'

interface ReservationFormModalProps {
  slug: string
  onClose: () => void
}

export function ReservationFormModal({ slug, onClose }: ReservationFormModalProps) {
  const [error, setError] = useState<string | null>(null)
  const [reservationTime, setReservationTime] = useState('')

  const createMutation = useMutation({
    mutationFn: (payload: PublicCreateReservationPayload) => createPublicReservation(slug, payload),
    onError: () => setError('Não há mesa disponível para esse horário e número de pessoas. Tente outro horário.'),
  })

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!reservationTime) {
      setError('Escolha a data e o horário da reserva.')
      return
    }
    setError(null)
    const form = new FormData(event.currentTarget)
    createMutation.mutate({
      customerName: String(form.get('customerName')),
      customerPhone: String(form.get('customerPhone')),
      note: String(form.get('note') || '') || undefined,
      partySize: Number(form.get('partySize')),
      reservationTime: new Date(reservationTime).toISOString(),
    })
  }

  if (createMutation.isSuccess) {
    const reservation = createMutation.data
    const statusUrl = `${window.location.origin}/reservations/status/${reservation.accessToken}`
    return (
      <Modal title="Reserva confirmada!" onClose={onClose}>
        <div className="flex flex-col items-center gap-3 text-center">
          <CheckCircle2 className="h-10 w-10 text-sage-500" />
          <p className="text-sm text-gray-600 dark:text-stone-300">
            Sua mesa está reservada. Guarde este link para ver ou cancelar sua reserva depois:
          </p>
          <a
            href={statusUrl}
            className="w-full break-all rounded-xl border border-gray-200 bg-gray-50 px-3 py-2.5 text-sm font-medium text-brand-600 dark:border-white/10 dark:bg-white/5 dark:text-brand-400"
          >
            {statusUrl}
          </a>
        </div>
      </Modal>
    )
  }

  return (
    <Modal title="Reservar mesa" onClose={onClose}>
      <form onSubmit={handleSubmit}>
        <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="reservationCustomerName">
          Nome
        </label>
        <input
          id="reservationCustomerName"
          name="customerName"
          type="text"
          required
          maxLength={255}
          className="mb-3 w-full rounded-xl border border-gray-200 px-3 py-2.5 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white"
        />

        <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="reservationCustomerPhone">
          Telefone
        </label>
        <input
          id="reservationCustomerPhone"
          name="customerPhone"
          type="tel"
          required
          maxLength={20}
          className="mb-3 w-full rounded-xl border border-gray-200 px-3 py-2.5 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white"
        />

        <div className="mb-3 grid grid-cols-2 gap-3">
          <div>
            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="reservationPartySize">
              Pessoas
            </label>
            <input
              id="reservationPartySize"
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

        <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="reservationNote">
          Observação <span className="font-normal text-gray-400 dark:text-stone-500">(opcional)</span>
        </label>
        <input
          id="reservationNote"
          name="note"
          type="text"
          maxLength={500}
          placeholder="Ex: aniversário, cadeira de bebê"
          className="mb-4 w-full rounded-xl border border-gray-200 px-3 py-2.5 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white"
        />

        {error && <p className="mb-4 text-sm text-wine-600 dark:text-wine-400">{error}</p>}

        <button
          type="submit"
          disabled={createMutation.isPending}
          className="w-full rounded-xl bg-brand-600 px-3 py-2.5 text-sm font-semibold text-white shadow-sm transition hover:bg-brand-700 disabled:opacity-50"
        >
          Reservar
        </button>
      </form>
    </Modal>
  )
}
