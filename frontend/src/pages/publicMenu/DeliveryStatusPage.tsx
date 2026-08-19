import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AnimatePresence, motion } from 'framer-motion'
import {
  Check,
  ChefHat,
  Clock,
  CreditCard,
  Loader2,
  MapPin,
  MessageCircle,
  Moon,
  PartyPopper,
  QrCode,
  Receipt,
  ShoppingBag,
  Sun,
} from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  DELIVERY_ACCENT_STYLES,
  DELIVERY_STATUS_LABELS,
  DELIVERY_STATUS_MESSAGES,
  DELIVERY_STATUS_STYLES,
  type DeliveryStatus,
} from '../../api/deliveries'
import { cancelPublicDeliveryCardCharge, cancelPublicDeliveryPixCharge, getPublicDeliveryStatus } from '../../api/publicMenu'
import { DeliveryRiderIcon } from '../../components/DeliveryRiderIcon'
import { buildWhatsAppUrl } from '../../utils/phone'
import { minutesSince } from '../../utils/time'
import { usePublicMenuTheme } from './usePublicMenuTheme'
import { PixPaymentModal } from './PixPaymentModal'
import { CardPaymentModal } from './CardPaymentModal'

const currencyFormatter = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

const STEPS: { status: DeliveryStatus; label: string; icon: typeof ChefHat }[] = [
  { status: 'SEPARATING', label: 'Preparando', icon: ChefHat },
  { status: 'OUT_FOR_DELIVERY', label: 'A caminho', icon: DeliveryRiderIcon },
  { status: 'DELIVERED', label: 'Entregue', icon: PartyPopper },
]

export function DeliveryStatusPage() {
  const { token } = useParams<{ token: string }>()
  const { theme, toggleTheme } = usePublicMenuTheme()
  const queryClient = useQueryClient()
  const [paymentModal, setPaymentModal] = useState<'PIX' | 'CARD' | null>(null)
  // The mutation's own isPending spinner is easy to miss (the call is fast) and, since cancelling
  // doesn't change delivery.paid, the surrounding "falta confirmar o pagamento" box looks identical
  // before and after - without this, clicking the link reads as "nothing happened" even though it
  // worked. Cleared on the next click/unmount so it can't linger past its own confirmation window.
  const [justCancelled, setJustCancelled] = useState(false)

  const { data: delivery, isLoading, isError, isFetching } = useQuery({
    queryKey: ['deliveryStatus', token],
    queryFn: () => getPublicDeliveryStatus(token!),
    enabled: !!token,
    retry: false,
    refetchInterval: 4000,
    refetchIntervalInBackground: true,
  })

  // The payment modals don't poll on their own (see PixPaymentModal) - they rely on this page's
  // polling to notice the tab closed. Without this, a customer who paid would be stuck staring at
  // "Aguardando o pagamento..." forever even though the page behind the modal already updated.
  useEffect(() => {
    if (delivery?.paid) {
      setPaymentModal(null)
    }
  }, [delivery?.paid])

  // Covers the card flow (the customer is redirected away to Mercado Pago and might come back
  // without ever finishing) as well as a stuck Pix QR - cancelling whichever one (if either) is
  // actually PENDING is a safe no-op for the other, so one button handles both without needing to
  // know which method was attempted.
  const cancelPendingMutation = useMutation({
    mutationFn: () => Promise.all([cancelPublicDeliveryPixCharge(token!), cancelPublicDeliveryCardCharge(token!)]),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['deliveryStatus', token] })
      setJustCancelled(true)
    },
  })

  useEffect(() => {
    if (!justCancelled) return
    const timeout = window.setTimeout(() => setJustCancelled(false), 4000)
    return () => window.clearTimeout(timeout)
  }, [justCancelled])

  const themeClass = theme === 'dark' ? 'dark' : ''

  if (isLoading) {
    return (
      <div className={`${themeClass} flex min-h-screen items-center justify-center bg-gray-50 p-4 dark:bg-stone-950`}>
        <p className="text-sm text-gray-500 dark:text-stone-400">Carregando...</p>
      </div>
    )
  }

  if (isError || !delivery) {
    return (
      <div className={`${themeClass} flex min-h-screen items-center justify-center bg-gray-50 p-4 dark:bg-stone-950`}>
        <p className="text-sm text-gray-500 dark:text-stone-400">Pedido não encontrado.</p>
      </div>
    )
  }

  const currentStepIndex = STEPS.findIndex((step) => step.status === delivery.status)
  const itemsTotal = delivery.items.reduce((sum, item) => sum + item.unitPrice * item.quantity, 0)
  const total = delivery.billTotal ?? itemsTotal + delivery.deliveryFee

  return (
    <div className={`${themeClass} min-h-screen bg-gray-50 dark:bg-stone-950`}>
      {/* Full-bleed header - the page IS the tracker, not a card floating over one */}
      <header
        className={`relative overflow-hidden rounded-b-[2rem] px-6 pt-8 pb-6 text-white shadow-sm sm:pt-10 ${DELIVERY_ACCENT_STYLES[delivery.status]}`}
      >
        <div
          className="pointer-events-none absolute -top-10 -right-10 h-40 w-40 rounded-full bg-white/10"
          aria-hidden="true"
        />
        <div className="relative mx-auto max-w-md">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-white/20 shadow-sm">
                <DeliveryRiderIcon className="h-5 w-5" />
              </span>
              <div>
                <h1 className="text-lg font-semibold">Seu pedido</h1>
                <p className="flex items-center gap-1 text-xs text-white/80">
                  <Clock className="h-3 w-3" />
                  Feito há {minutesSince(delivery.createdAt)} min
                </p>
              </div>
            </div>
            <div className="flex shrink-0 items-center gap-2">
              {/* Small pulsing dot = this page is polling live, not a static snapshot */}
              <span className="flex items-center gap-1.5 rounded-full bg-white/15 px-2 py-1 text-[11px] font-medium">
                <span className="relative flex h-1.5 w-1.5">
                  <span
                    className={`absolute inline-flex h-full w-full rounded-full bg-white ${isFetching ? 'animate-ping' : ''} opacity-75`}
                  />
                  <span className="relative inline-flex h-1.5 w-1.5 rounded-full bg-white" />
                </span>
                Ao vivo
              </span>
              <button
                type="button"
                onClick={toggleTheme}
                aria-label="Alternar tema"
                className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-white transition-colors hover:bg-white/15"
              >
                <AnimatePresence mode="wait" initial={false}>
                  <motion.span
                    key={theme}
                    initial={{ opacity: 0, rotate: -90 }}
                    animate={{ opacity: 1, rotate: 0 }}
                    exit={{ opacity: 0, rotate: 90 }}
                    transition={{ duration: 0.2 }}
                    className="flex items-center justify-center"
                  >
                    {theme === 'dark' ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
                  </motion.span>
                </AnimatePresence>
              </button>
            </div>
          </div>

          <AnimatePresence mode="wait">
            <motion.p
              key={delivery.status}
              initial={{ opacity: 0, y: 4 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.25 }}
              className="relative mt-4 text-sm font-medium text-white/95"
            >
              {delivery.paid ? DELIVERY_STATUS_MESSAGES[delivery.status] : 'Aguardando a confirmação do pagamento.'}
            </motion.p>
          </AnimatePresence>
        </div>
      </header>

      <main className="mx-auto max-w-md space-y-4 px-4 pt-6 pb-12">
        {/* Step tracker */}
        {delivery.paid && (
          <div className="rounded-2xl border border-gray-100 bg-white p-5 shadow-sm dark:border-white/10 dark:bg-stone-900">
            <div className="flex items-start">
              {STEPS.map((step, index) => {
                const Icon = step.icon
                const isDone = index < currentStepIndex
                const isCurrent = index === currentStepIndex
                const isActive = index <= currentStepIndex
                return (
                  <div key={step.status} className="flex flex-1 items-start last:flex-none">
                    <div className="flex flex-col items-center gap-1.5">
                      <motion.span
                        animate={isCurrent ? { scale: [1, 1.08, 1] } : { scale: 1 }}
                        transition={isCurrent ? { duration: 1.6, repeat: Infinity, ease: 'easeInOut' } : {}}
                        className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-white shadow-sm transition-colors duration-500 ${
                          isActive ? DELIVERY_ACCENT_STYLES[delivery.status] : 'bg-gray-200 dark:bg-white/10'
                        }`}
                      >
                        {isDone ? <Check className="h-4 w-4" /> : <Icon className="h-4 w-4" />}
                      </motion.span>
                      <span
                        className={`text-[11px] font-medium whitespace-nowrap ${
                          isActive ? 'text-gray-700 dark:text-stone-200' : 'text-gray-400 dark:text-stone-600'
                        }`}
                      >
                        {step.label}
                      </span>
                    </div>
                    {index < STEPS.length - 1 && (
                      <div className="mt-4.5 h-1 flex-1 overflow-hidden rounded-full bg-gray-200 dark:bg-white/10">
                        <motion.div
                          initial={false}
                          animate={{ width: index < currentStepIndex ? '100%' : '0%' }}
                          transition={{ duration: 0.5, ease: 'easeOut' }}
                          className={`h-full rounded-full ${DELIVERY_ACCENT_STYLES[delivery.status]}`}
                        />
                      </div>
                    )}
                  </div>
                )
              })}
            </div>
          </div>
        )}

        {!delivery.paid && (
          <span
            className={`inline-flex rounded-full px-2.5 py-1 text-xs font-medium ${DELIVERY_STATUS_STYLES[delivery.status]}`}
          >
            {DELIVERY_STATUS_LABELS[delivery.status]}
          </span>
        )}

        {/* Order items */}
        {delivery.items.length > 0 && (
          <div className="overflow-hidden rounded-2xl border border-gray-100 bg-white shadow-sm dark:border-white/10 dark:bg-stone-900">
            <div className="flex items-center gap-2 border-b border-gray-100 px-4 py-3 dark:border-white/10">
              <Receipt className="h-4 w-4 text-gray-400 dark:text-stone-500" />
              <span className="text-sm font-semibold text-gray-800 dark:text-white">Seu pedido</span>
            </div>
            <div className="divide-y divide-gray-100 px-4 dark:divide-white/10">
              {delivery.items.map((item, index) => (
                <div key={index} className="flex items-center justify-between gap-3 py-2.5 text-sm">
                  <span className="text-gray-600 dark:text-stone-300">
                    <span className="font-medium text-gray-800 dark:text-white">{item.quantity}x</span> {item.productName}
                  </span>
                  <span className="shrink-0 text-gray-500 dark:text-stone-400">
                    {currencyFormatter.format(item.unitPrice * item.quantity)}
                  </span>
                </div>
              ))}
            </div>
            <div className="space-y-1 border-t border-gray-100 px-4 py-3 text-sm dark:border-white/10">
              <div className="flex items-center justify-between text-gray-500 dark:text-stone-400">
                <span>Taxa de entrega</span>
                <span>{currencyFormatter.format(delivery.deliveryFee)}</span>
              </div>
              <div className="flex items-center justify-between pt-1 font-semibold text-gray-900 dark:text-white">
                <span>Total</span>
                <span>{currencyFormatter.format(total)}</span>
              </div>
            </div>
          </div>
        )}

        {/* Address */}
        <div className="flex items-start gap-2 rounded-2xl border border-gray-100 bg-white p-4 text-sm text-gray-600 shadow-sm dark:border-white/10 dark:bg-stone-900 dark:text-stone-300">
          <MapPin className="mt-0.5 h-4 w-4 shrink-0" />
          <span>
            {delivery.street}, {delivery.number}
            {delivery.complement && ` - ${delivery.complement}`} - {delivery.neighborhood}
          </span>
        </div>

        {!delivery.paid && (
          <div className="rounded-2xl border border-gold-200 bg-gold-50 p-4 shadow-sm dark:border-gold-500/30 dark:bg-gold-500/10">
            <p className="mb-3 text-sm font-medium text-gold-800 dark:text-gold-300">
              Falta confirmar o pagamento pra sua comanda seguir pra cozinha.
            </p>
            <div className="flex flex-col gap-2 sm:flex-row">
              <button
                type="button"
                onClick={() => setPaymentModal('PIX')}
                className="flex flex-1 items-center justify-center gap-2 rounded-xl bg-brand-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-brand-700"
              >
                <QrCode className="h-4 w-4" />
                Pagar com Pix
              </button>
              <button
                type="button"
                onClick={() => setPaymentModal('CARD')}
                className="flex flex-1 items-center justify-center gap-2 rounded-xl border border-brand-600 px-4 py-2.5 text-sm font-semibold text-brand-600 hover:bg-brand-50 dark:border-brand-400 dark:text-brand-400 dark:hover:bg-white/5"
              >
                <CreditCard className="h-4 w-4" />
                Pagar com cartão
              </button>
            </div>
            <div className="mt-3 border-t border-gold-200/70 pt-3 text-center dark:border-gold-500/20">
              {justCancelled ? (
                <p className="flex items-center justify-center gap-1 text-xs font-semibold text-sage-700 dark:text-sage-400">
                  <Check className="h-3.5 w-3.5" />
                  Cobrança cancelada. Escolha uma forma de pagamento pra tentar de novo.
                </p>
              ) : (
                <>
                  <p className="text-xs text-gold-700/80 dark:text-gold-400/70">Pagamento travado ou já tentou pagar?</p>
                  <button
                    type="button"
                    onClick={() => cancelPendingMutation.mutate()}
                    disabled={cancelPendingMutation.isPending}
                    className="inline-flex items-center gap-1 text-xs font-semibold text-gold-800 underline decoration-gold-400 underline-offset-2 hover:text-gold-900 disabled:opacity-50 dark:text-gold-300 dark:hover:text-gold-200"
                  >
                    {cancelPendingMutation.isPending ? (
                      <>
                        <Loader2 className="h-3 w-3 animate-spin" />
                        Cancelando...
                      </>
                    ) : (
                      'Cancelar cobrança pendente'
                    )}
                  </button>
                </>
              )}
            </div>
          </div>
        )}

        {/* What next - keeps the customer from being stranded once they're done looking at status */}
        <div className="grid grid-cols-1 gap-2 pt-2 sm:grid-cols-2">
          <Link
            to={`/menu/${delivery.restaurantSlug}`}
            className="flex items-center justify-center gap-2 rounded-xl border border-gray-200 bg-white px-4 py-2.5 text-sm font-semibold text-gray-700 shadow-sm transition hover:bg-gray-50 dark:border-white/10 dark:bg-stone-900 dark:text-stone-200 dark:hover:bg-white/5"
          >
            <ShoppingBag className="h-4 w-4" />
            Fazer novo pedido
          </Link>
          {delivery.restaurantPhone && (
            <a
              href={buildWhatsAppUrl(
                delivery.restaurantPhone,
                `Olá! Tenho uma dúvida sobre meu pedido em ${delivery.street}, ${delivery.number}.`,
              )}
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-center justify-center gap-2 rounded-xl border border-gray-200 bg-white px-4 py-2.5 text-sm font-semibold text-gray-700 shadow-sm transition hover:bg-gray-50 dark:border-white/10 dark:bg-stone-900 dark:text-stone-200 dark:hover:bg-white/5"
            >
              <MessageCircle className="h-4 w-4" />
              Falar com {delivery.restaurantName}
            </a>
          )}
        </div>
      </main>

      {paymentModal === 'PIX' && token && (
        <PixPaymentModal deliveryToken={token} onClose={() => setPaymentModal(null)} />
      )}
      {paymentModal === 'CARD' && token && (
        <CardPaymentModal deliveryToken={token} onClose={() => setPaymentModal(null)} />
      )}
    </div>
  )
}
