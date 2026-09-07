import { useMutation } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import { Check, CheckCircle2, Copy } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { createPublicReservation, type PublicCreateReservationPayload } from '../../api/reservations'
import { DateTimePicker } from '../../components/DateTimePicker'
import { Modal } from '../../components/Modal'

interface ReservationFormModalProps {
  slug: string
  onClose: () => void
}

// Branches by HTTP status rather than passing the backend's own message through - that message is
// in English (project convention), fine for staff screens but wrong to show a customer here. 403
// is genuinely "no table available" (ReservationService); 429 is the per-phone/IP rate limit; a
// 400 (bad party size, a past date slipping past DateTimePicker's own guard, etc.) used to get the
// same "no table available" text, hiding what was actually wrong (finding #8, 2026-09-07 review).
function reservationErrorMessage(error: unknown): string {
  const status = isAxiosError(error) ? error.response?.status : undefined

  if (status === 429) {
    return 'Muitas tentativas. Aguarde alguns minutos e tente de novo.'
  }
  if (status === 400) {
    return 'Não foi possível fazer a reserva. Confira o número de pessoas e o horário escolhido.'
  }
  return 'Não há mesa disponível para esse horário e número de pessoas. Tente outro horário.'
}

export function ReservationFormModal({ slug, onClose }: ReservationFormModalProps) {
  const [error, setError] = useState<string | null>(null)
  const [reservationTime, setReservationTime] = useState('')
  const [copied, setCopied] = useState(false)

  const createMutation = useMutation({
    mutationFn: (payload: PublicCreateReservationPayload) => createPublicReservation(slug, payload),
    onError: (err) => setError(reservationErrorMessage(err)),
  })

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!reservationTime) {
      setError('Escolha a data e o horário da reserva.')
      return
    }
    // DateTimePicker's time input carries a `min` for "today", but that input is portaled to
    // document.body - outside this <form>'s DOM subtree - so the browser's native constraint
    // validation on submit never actually sees it and doesn't block the request (confirmed live:
    // picking today + an already-passed hour still reached the API). Catching it here, before the
    // round-trip, is what actually prevents it (finding #8, 2026-09-07 review).
    if (new Date(reservationTime).getTime() <= Date.now()) {
      setError('Escolha um horário no futuro.')
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

  function handleCopy(url: string) {
    navigator.clipboard.writeText(url)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
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
          <button
            type="button"
            onClick={() => handleCopy(statusUrl)}
            className="flex w-full items-center justify-center gap-2 rounded-xl bg-brand-600 px-3 py-2.5 text-sm font-semibold text-white shadow-sm transition hover:bg-brand-700"
          >
            {copied ? <Check className="h-4 w-4" /> : <Copy className="h-4 w-4" />}
            {copied ? 'Copiado!' : 'Copiar link'}
          </button>
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
