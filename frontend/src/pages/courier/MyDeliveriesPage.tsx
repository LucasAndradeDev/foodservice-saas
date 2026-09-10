import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AnimatePresence, motion } from 'framer-motion'
import {
  CheckCircle2,
  ChevronDown,
  Clock,
  MapPin,
  MapPinOff,
  MessageCircle,
  Navigation,
  Phone,
  Route,
} from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { DeliveryRiderIcon } from '../../components/DeliveryRiderIcon'
import { listMyDeliveredToday, listMyDeliveries, updateDeliveryStatus, updateMyLocation } from '../../api/deliveries'
import { buildMapsUrl, formatAddressLines } from '../../utils/delivery'
import { buildWhatsAppUrl } from '../../utils/phone'
import { minutesSince } from '../../utils/time'
import { translateApiError } from '../../utils/apiErrorMessage'

const currencyFormatter = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })
const timeFormatter = new Intl.DateTimeFormat('pt-BR', { hour: '2-digit', minute: '2-digit' })

const cardVariants = {
  hidden: { opacity: 0, y: 12 },
  visible: { opacity: 1, y: 0 },
  exit: { opacity: 0, y: -12 },
}

// watchPosition can fire far more often than needed (every few seconds on a moving phone) -
// throttle actual network sends to once per this interval instead of hitting the backend on
// every callback.
const LOCATION_SEND_MIN_INTERVAL_MS = 20000

export function MyDeliveriesPage() {
  const queryClient = useQueryClient()
  const [locationDenied, setLocationDenied] = useState(false)
  const [locationActive, setLocationActive] = useState(false)
  const [deliverError, setDeliverError] = useState<{ tabId: string; message: string } | null>(null)
  const [historyOpen, setHistoryOpen] = useState(false)
  const lastSentAtRef = useRef(0)

  // Reports position whenever this screen is open, not gated on having an active delivery - the
  // staff map (DeliveryPage) is only useful for "who's free/nearby" if it shows every online
  // courier, not just ones currently carrying an order. Runs once for the life of the page;
  // permission denial fails silently (a small note below, nothing blocks marking deliveries done).
  useEffect(() => {
    if (!('geolocation' in navigator)) return

    const watchId = navigator.geolocation.watchPosition(
      (position) => {
        // A prior error (e.g. GPS briefly unavailable indoors, or a timeout) doesn't mean
        // permission was denied for good - watchPosition keeps calling back on its own once the
        // signal comes back, so clear the warning here instead of leaving it stuck until reload.
        setLocationDenied(false)
        setLocationActive(true)
        const now = Date.now()
        if (now - lastSentAtRef.current < LOCATION_SEND_MIN_INTERVAL_MS) return
        lastSentAtRef.current = now
        updateMyLocation(position.coords.latitude, position.coords.longitude).catch(() => {})
      },
      () => {
        setLocationDenied(true)
        setLocationActive(false)
      },
      { enableHighAccuracy: false, maximumAge: 15000 },
    )
    return () => navigator.geolocation.clearWatch(watchId)
  }, [])

  const { data: deliveries, isLoading } = useQuery({
    queryKey: ['my-deliveries'],
    queryFn: listMyDeliveries,
    refetchInterval: 15000,
  })

  // Backs the day summary card and the "history" disclosure below - refetched less often than the
  // active list since it only changes when this courier finishes a delivery (also invalidated
  // directly in deliverMutation's onSuccess, so it updates right away instead of waiting a full
  // interval).
  const { data: deliveredToday } = useQuery({
    queryKey: ['my-deliveries-history'],
    queryFn: listMyDeliveredToday,
    refetchInterval: 60000,
  })

  const deliverMutation = useMutation({
    mutationFn: (tabId: string) => updateDeliveryStatus(tabId, 'DELIVERED'),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['my-deliveries'] })
      queryClient.invalidateQueries({ queryKey: ['my-deliveries-history'] })
      setDeliverError(null)
    },
    onError: (err, tabId) => setDeliverError({ tabId, message: translateApiError(err, 'Não foi possível marcar como entregue.') }),
  })

  const isEmpty = deliveries?.length === 0 && !isLoading
  const deliveredCount = deliveredToday?.length ?? 0
  const feesToday = (deliveredToday ?? []).reduce((sum, delivery) => sum + delivery.deliveryFee, 0)

  return (
    <div className="mx-auto flex w-full max-w-lg flex-1 flex-col px-4 py-6">
      <h1 className="text-center text-xl font-semibold text-gray-900 dark:text-white">Minhas entregas</h1>

      {locationDenied ? (
        <p className="mt-2 flex items-center justify-center gap-1.5 text-center text-xs text-amber-600 dark:text-amber-400">
          <MapPinOff className="h-3.5 w-3.5 shrink-0" />
          Sem acesso à localização: a loja não vai te ver no mapa.
        </p>
      ) : (
        locationActive && (
          <p className="mt-2 flex items-center justify-center gap-1.5 text-center text-xs text-gray-400 dark:text-stone-500">
            <span className="h-1.5 w-1.5 shrink-0 rounded-full bg-sage-500" />
            Compartilhando localização com a loja
          </p>
        )
      )}

      {deliveredToday && (
        <div className="mt-4 grid grid-cols-2 gap-3">
          <div className="rounded-2xl border border-gray-200 bg-white p-3.5 text-center shadow-sm dark:border-white/10 dark:bg-stone-900">
            <p className="text-2xl font-bold text-gray-900 dark:text-white">{deliveredCount}</p>
            <p className="text-xs text-gray-500 dark:text-stone-400">
              {deliveredCount === 1 ? 'entrega hoje' : 'entregas hoje'}
            </p>
          </div>
          <div className="rounded-2xl border border-gray-200 bg-white p-3.5 text-center shadow-sm dark:border-white/10 dark:bg-stone-900">
            <p className="text-2xl font-bold text-gray-900 dark:text-white">{currencyFormatter.format(feesToday)}</p>
            <p className="text-xs text-gray-500 dark:text-stone-400">em taxas de entrega</p>
          </div>
        </div>
      )}

      {deliveredCount > 0 && (
        <div className="mt-3 overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-sm dark:border-white/10 dark:bg-stone-900">
          <button
            type="button"
            onClick={() => setHistoryOpen((open) => !open)}
            className="flex w-full items-center justify-between gap-2 px-4 py-3 text-left"
          >
            <span className="text-sm font-semibold text-gray-700 dark:text-stone-200">
              Entregas concluídas hoje
            </span>
            <motion.span animate={{ rotate: historyOpen ? 180 : 0 }} transition={{ duration: 0.2 }}>
              <ChevronDown className="h-4 w-4 text-gray-400 dark:text-stone-500" />
            </motion.span>
          </button>
          <AnimatePresence initial={false}>
            {historyOpen && (
              <motion.div
                initial={{ height: 0, opacity: 0 }}
                animate={{ height: 'auto', opacity: 1 }}
                exit={{ height: 0, opacity: 0 }}
                transition={{ duration: 0.2 }}
                className="overflow-hidden"
              >
                <div className="divide-y divide-gray-100 border-t border-gray-100 dark:divide-white/10 dark:border-white/10">
                  {deliveredToday?.map((delivery) => (
                    <div key={delivery.id} className="flex items-center justify-between gap-3 px-4 py-2.5 text-sm">
                      <span className="min-w-0 truncate text-gray-700 dark:text-stone-200">{delivery.customerName}</span>
                      <span className="flex shrink-0 items-center gap-2 text-xs text-gray-400 dark:text-stone-500">
                        {delivery.deliveredAt && timeFormatter.format(new Date(delivery.deliveredAt))}
                        <span className="font-semibold text-gray-600 dark:text-stone-300">
                          {currencyFormatter.format(delivery.deliveryFee)}
                        </span>
                      </span>
                    </div>
                  ))}
                </div>
              </motion.div>
            )}
          </AnimatePresence>
        </div>
      )}

      {isLoading && (
        <div className="mt-5 space-y-3">
          {[0, 1].map((i) => (
            <div
              key={i}
              className="h-40 animate-pulse rounded-2xl border border-gray-200 bg-white dark:border-white/10 dark:bg-stone-900"
            />
          ))}
        </div>
      )}

      {isEmpty && (
        <div className="flex flex-1 flex-col items-center justify-center pb-16">
          <motion.div
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.2 }}
            className="flex w-full flex-col items-center gap-4 rounded-2xl border border-gray-200 bg-white px-6 py-14 text-center shadow-sm dark:border-white/10 dark:bg-stone-900"
          >
            <div className="flex h-16 w-16 items-center justify-center rounded-full bg-gray-50 text-gray-300 dark:bg-white/5 dark:text-stone-600">
              <DeliveryRiderIcon className="h-8 w-8" />
            </div>
            <div className="space-y-1.5">
              <p className="text-sm font-semibold text-gray-700 dark:text-stone-200">
                Nenhuma entrega com você no momento
              </p>
              <p className="max-w-[16rem] text-xs text-gray-400 dark:text-stone-500">
                Novos pedidos aparecem aqui automaticamente assim que forem despachados.
              </p>
            </div>
          </motion.div>
        </div>
      )}

      <div className="space-y-3">
        <AnimatePresence initial={false}>
          {deliveries?.map((delivery) => {
            const { line1, line2 } = formatAddressLines(delivery)
            const itemsTotal = delivery.items.reduce((sum, item) => sum + item.unitPrice * item.quantity, 0)
            const total = delivery.billTotal ?? itemsTotal + delivery.deliveryFee

            return (
              <motion.div
                key={delivery.id}
                variants={cardVariants}
                initial="hidden"
                animate="visible"
                exit="exit"
                transition={{ duration: 0.2 }}
                className="overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-sm dark:border-white/10 dark:bg-stone-900"
              >
                <div className="flex items-start justify-between gap-3 p-4 pb-3">
                  <span className="min-w-0">
                    <span className="block font-semibold text-gray-900 dark:text-white">{delivery.customerName}</span>
                    <span className="mt-0.5 flex flex-wrap items-center gap-x-3 gap-y-0.5 text-xs text-gray-400 dark:text-stone-500">
                      {delivery.outForDeliveryAt && (
                        <span className="flex items-center gap-1">
                          <Clock className="h-3 w-3" />
                          Saiu há {minutesSince(delivery.outForDeliveryAt)} min
                        </span>
                      )}
                      {delivery.deliveryDistanceKm != null && (
                        <span className="flex items-center gap-1">
                          <Route className="h-3 w-3" />
                          {delivery.deliveryDistanceKm.toFixed(1).replace('.', ',')} km
                        </span>
                      )}
                    </span>
                  </span>
                  <span className="shrink-0 text-sm font-bold text-gray-900 dark:text-white">
                    {currencyFormatter.format(total)}
                  </span>
                </div>

                <div className="px-4 pb-3">
                  <div className="flex items-start gap-2 text-sm text-gray-600 dark:text-stone-300">
                    <MapPin className="mt-0.5 h-4 w-4 shrink-0 text-gray-400 dark:text-stone-500" />
                    <span className="min-w-0">
                      {line1}
                      {line2 && <span className="text-gray-400 dark:text-stone-500"> · {line2}</span>}
                      {delivery.referencePoint && (
                        <span className="block text-xs text-gray-400 dark:text-stone-500">
                          Referência: {delivery.referencePoint}
                        </span>
                      )}
                    </span>
                  </div>

                  <div className="mt-3 grid grid-cols-2 gap-2">
                    <a
                      href={buildMapsUrl(delivery)}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="flex items-center justify-center gap-1.5 rounded-xl border border-gray-200 bg-white py-2.5 text-sm font-semibold text-gray-700 shadow-sm transition hover:border-brand-300 hover:text-brand-600 active:scale-95 dark:border-white/10 dark:bg-stone-800 dark:text-stone-200 dark:hover:text-brand-400"
                    >
                      <Navigation className="h-4 w-4" />
                      Mapa
                    </a>
                    <a
                      href={buildWhatsAppUrl(delivery.customerPhone)}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="flex items-center justify-center gap-1.5 rounded-xl border border-sage-200 bg-sage-50 py-2.5 text-sm font-semibold text-sage-700 shadow-sm transition hover:bg-sage-100 active:scale-95 dark:border-sage-500/30 dark:bg-sage-500/10 dark:text-sage-400 dark:hover:bg-sage-500/20"
                    >
                      <MessageCircle className="h-4 w-4" />
                      WhatsApp
                    </a>
                  </div>

                  <a
                    href={`tel:${delivery.customerPhone.replace(/\D/g, '')}`}
                    className="mt-2 flex items-center justify-center gap-1.5 rounded-xl border border-gray-200 bg-white py-2.5 text-sm font-semibold text-gray-700 shadow-sm transition hover:border-brand-300 hover:text-brand-600 active:scale-95 dark:border-white/10 dark:bg-stone-800 dark:text-stone-200 dark:hover:text-brand-400"
                  >
                    <Phone className="h-4 w-4" />
                    {delivery.customerPhone}
                  </a>
                </div>

                <div className="space-y-1 border-t border-gray-100 px-4 py-2.5 text-xs text-gray-500 dark:border-white/10 dark:text-stone-400">
                  {delivery.items.map((item, index) => (
                    <div key={index}>
                      {item.quantity}x {item.productName}
                    </div>
                  ))}
                </div>

                <div className="border-t border-gray-100 p-3 dark:border-white/10">
                  {deliverError?.tabId === delivery.tabId && (
                    <p className="mb-2 text-center text-xs text-wine-600 dark:text-wine-400">{deliverError.message}</p>
                  )}
                  <button
                    type="button"
                    onClick={() => deliverMutation.mutate(delivery.tabId)}
                    // Scoped to this card's own tabId (finding #14, 2026-09-07 review) - isPending
                    // alone is shared across the whole mutation, so with 2+ deliveries in progress
                    // it used to disable every card's button while only one submission was in flight.
                    disabled={deliverMutation.isPending && deliverMutation.variables === delivery.tabId}
                    className="flex w-full items-center justify-center gap-2 rounded-xl bg-brand-600 px-3 py-2.5 text-sm font-semibold text-white shadow-sm transition hover:bg-brand-700 disabled:opacity-50"
                  >
                    <CheckCircle2 className="h-4 w-4" />
                    Marcar como entregue
                  </button>
                </div>
              </motion.div>
            )
          })}
        </AnimatePresence>
      </div>
    </div>
  )
}
