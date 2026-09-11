import { useMutation, useQuery } from '@tanstack/react-query'
import { CalendarClock, Check, CheckCircle2, MessageSquare, Phone, Share2, User, Users } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { useParams } from 'react-router-dom'
import { getPublicMenu } from '../api/publicMenu'
import { createPublicReservation, reservationErrorMessage, type PublicCreateReservationPayload } from '../api/reservations'
import { AmbientBackground } from './publicMenu/AmbientBackground'
import { BackLink } from '../components/BackLink'
import { DateTimePicker } from '../components/DateTimePicker'
import { formatBrazilianPhone } from '../utils/phone'
import { MenuHero } from './publicMenu/MenuHero'
import { usePublicMenuTheme } from './publicMenu/usePublicMenuTheme'

const fieldClassName =
  'w-full rounded-2xl border border-gray-200 bg-white py-3 pl-10 pr-3 text-sm shadow-sm transition focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/15 dark:border-white/10 dark:bg-stone-800 dark:text-white'

export function PublicReservationPage() {
  const { slug } = useParams<{ slug: string }>()
  const { theme, toggleTheme } = usePublicMenuTheme()
  const [error, setError] = useState<string | null>(null)
  const [reservationTime, setReservationTime] = useState('')
  const [customerPhone, setCustomerPhone] = useState('')
  const [copied, setCopied] = useState(false)

  const { data: menu, isLoading } = useQuery({
    queryKey: ['publicMenuBrief', slug],
    queryFn: () => getPublicMenu(slug!),
    enabled: !!slug,
    retry: false,
  })

  const createMutation = useMutation({
    mutationFn: (payload: PublicCreateReservationPayload) => createPublicReservation(slug!, payload),
    onError: (err) => setError(reservationErrorMessage(err)),
  })

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!reservationTime) {
      setError('Escolha a data e o horário da reserva.')
      return
    }
    // Same guard as the old ReservationFormModal: DateTimePicker's time input carries a `min` for
    // "today", but that input is portaled to document.body - outside this <form>'s DOM subtree -
    // so native constraint validation on submit never sees it. Catching it here is what actually
    // prevents booking a past hour.
    if (new Date(reservationTime).getTime() <= Date.now()) {
      setError('Escolha um horário no futuro.')
      return
    }
    setError(null)
    const form = new FormData(event.currentTarget)
    createMutation.mutate({
      customerName: String(form.get('customerName')),
      customerPhone,
      note: String(form.get('note') || '') || undefined,
      partySize: Number(form.get('partySize')),
      reservationTime: new Date(reservationTime).toISOString(),
    })
  }

  async function handleShare(url: string) {
    if (navigator.share) {
      try {
        await navigator.share({ title: 'Minha reserva', text: 'Guarde o link da sua reserva:', url })
      } catch (error) {
        if (error instanceof Error && error.name === 'AbortError') return
      }
      return
    }
    navigator.clipboard.writeText(url)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  const themeClass = theme === 'dark' ? 'dark' : ''

  if (isLoading || !menu) {
    return (
      <div className={`${themeClass} flex min-h-screen items-center justify-center bg-gray-50 p-4 dark:bg-stone-950`}>
        <p className="text-sm text-gray-500 dark:text-stone-400">Carregando...</p>
      </div>
    )
  }

  return (
    <div className={`${themeClass} isolate flex min-h-screen flex-col bg-gray-50 dark:bg-stone-950`}>
      <AmbientBackground theme={theme} />

      <div className="relative z-10">
        <MenuHero restaurantName={menu.restaurantName} logo={menu.logo} theme={theme} onToggleTheme={toggleTheme} />
      </div>

      {/* No card here on purpose - this page reads as its own full screen, not a dialog sitting
          on top of the menu, so the form content shares the page's own background all the way down. */}
      <main className="relative z-10 mx-auto w-full max-w-md flex-1 px-4 py-4">
        <BackLink to={`/menu/${slug}`} className="mb-6">
          Voltar ao cardápio
        </BackLink>

        {createMutation.isSuccess ? (
          <div className="flex flex-col items-center gap-4 py-8 text-center">
            <span className="flex h-16 w-16 items-center justify-center rounded-full bg-sage-100 text-sage-600 dark:bg-sage-500/10 dark:text-sage-400">
              <CheckCircle2 className="h-8 w-8" />
            </span>
            <div>
              <h1 className="text-2xl font-black tracking-tight text-gray-900 dark:text-white">Reserva confirmada!</h1>
              <p className="mt-1 text-sm text-gray-500 dark:text-stone-400">
                Sua mesa em {menu.restaurantName} está reservada.
              </p>
            </div>
            <p className="text-sm text-gray-600 dark:text-stone-300">
              Guarde este link para ver ou cancelar sua reserva depois:
            </p>
            {(() => {
              const statusUrl = `${window.location.origin}/reservations/status/${createMutation.data.accessToken}`
              return (
                <>
                  <a
                    href={statusUrl}
                    className="w-full break-all rounded-2xl border border-gray-200 bg-white px-4 py-3 text-sm font-medium text-brand-600 shadow-sm dark:border-white/10 dark:bg-stone-900 dark:text-brand-400"
                  >
                    {statusUrl}
                  </a>
                  <button
                    type="button"
                    onClick={() => handleShare(statusUrl)}
                    className="flex w-full items-center justify-center gap-2 rounded-2xl bg-brand-600 px-4 py-3.5 text-sm font-bold text-white shadow-lg shadow-brand-600/20 transition hover:bg-brand-700 active:scale-[0.99]"
                  >
                    {copied ? <Check className="h-4 w-4" /> : <Share2 className="h-4 w-4" />}
                    {copied ? 'Copiado!' : 'Compartilhar link'}
                  </button>
                </>
              )
            })()}
          </div>
        ) : (
          <>
            <div className="mb-8 flex flex-col items-center text-center">
              <span className="mb-3 flex h-16 w-16 items-center justify-center rounded-3xl bg-brand-600 text-white shadow-lg shadow-brand-600/25">
                <CalendarClock className="h-7 w-7" />
              </span>
              <h1 className="text-2xl font-black tracking-tight text-gray-900 dark:text-white">Reservar mesa</h1>
              <p className="text-sm text-gray-500 dark:text-stone-400">em {menu.restaurantName}</p>
            </div>

            <form onSubmit={handleSubmit} className="space-y-5">
              <div>
                <label className="mb-1.5 block text-sm font-semibold text-gray-700 dark:text-stone-300" htmlFor="reservationCustomerName">
                  Nome
                </label>
                <div className="relative">
                  <User className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400 dark:text-stone-500" />
                  <input
                    id="reservationCustomerName"
                    name="customerName"
                    type="text"
                    required
                    maxLength={255}
                    className={fieldClassName}
                  />
                </div>
              </div>

              <div>
                <label className="mb-1.5 block text-sm font-semibold text-gray-700 dark:text-stone-300" htmlFor="reservationCustomerPhone">
                  Telefone
                </label>
                <div className="relative">
                  <Phone className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400 dark:text-stone-500" />
                  <input
                    id="reservationCustomerPhone"
                    name="customerPhone"
                    type="tel"
                    required
                    maxLength={16}
                    value={customerPhone}
                    onChange={(e) => setCustomerPhone(formatBrazilianPhone(e.target.value))}
                    className={fieldClassName}
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="mb-1.5 block text-sm font-semibold text-gray-700 dark:text-stone-300" htmlFor="reservationPartySize">
                    Pessoas
                  </label>
                  <div className="relative">
                    <Users className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400 dark:text-stone-500" />
                    <input
                      id="reservationPartySize"
                      name="partySize"
                      type="number"
                      min={1}
                      max={50}
                      required
                      defaultValue={2}
                      className={fieldClassName}
                    />
                  </div>
                </div>
                <div>
                  <label className="mb-1.5 block text-sm font-semibold text-gray-700 dark:text-stone-300" htmlFor="reservationTime">
                    Data e hora
                  </label>
                  <DateTimePicker id="reservationTime" value={reservationTime} onChange={setReservationTime} />
                </div>
              </div>

              <div>
                <label className="mb-1.5 block text-sm font-semibold text-gray-700 dark:text-stone-300" htmlFor="reservationNote">
                  Observação <span className="font-normal text-gray-400 dark:text-stone-500">(opcional)</span>
                </label>
                <div className="relative">
                  <MessageSquare className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400 dark:text-stone-500" />
                  <input
                    id="reservationNote"
                    name="note"
                    type="text"
                    maxLength={500}
                    placeholder="Ex: aniversário, cadeira de bebê"
                    className={fieldClassName}
                  />
                </div>
              </div>

              {error && <p className="text-sm text-wine-600 dark:text-wine-400">{error}</p>}

              <button
                type="submit"
                disabled={createMutation.isPending}
                className="flex w-full items-center justify-center gap-2 rounded-2xl bg-brand-600 px-4 py-3.5 text-sm font-bold text-white shadow-lg shadow-brand-600/20 transition hover:bg-brand-700 active:scale-[0.99] disabled:opacity-50"
              >
                <CalendarClock className="h-4 w-4" />
                Reservar
              </button>
            </form>
          </>
        )}
      </main>
    </div>
  )
}
