import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AnimatePresence, motion } from 'framer-motion'
import { CheckCircle2, MapPin, MapPinOff, MessageCircle, Navigation, Phone } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { DeliveryRiderIcon } from '../../components/DeliveryRiderIcon'
import { EmptyState } from '../../components/EmptyState'
import { listMyDeliveries, updateDeliveryStatus, updateMyLocation } from '../../api/deliveries'
import { buildMapsUrl, formatAddressLines } from '../../utils/delivery'
import { buildWhatsAppUrl } from '../../utils/phone'

const currencyFormatter = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

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
        const now = Date.now()
        if (now - lastSentAtRef.current < LOCATION_SEND_MIN_INTERVAL_MS) return
        lastSentAtRef.current = now
        updateMyLocation(position.coords.latitude, position.coords.longitude).catch(() => {})
      },
      () => setLocationDenied(true),
      { enableHighAccuracy: false, maximumAge: 15000 },
    )
    return () => navigator.geolocation.clearWatch(watchId)
  }, [])

  const { data: deliveries, isLoading } = useQuery({
    queryKey: ['my-deliveries'],
    queryFn: listMyDeliveries,
    refetchInterval: 15000,
  })

  const deliverMutation = useMutation({
    mutationFn: (tabId: string) => updateDeliveryStatus(tabId, 'DELIVERED'),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['my-deliveries'] }),
  })

  return (
    <div className="mx-auto max-w-lg px-4 py-6">
      <h1 className="mb-4 text-lg font-bold text-gray-900 dark:text-white">Minhas entregas</h1>

      {locationDenied && (
        <p className="mb-4 flex items-center gap-1.5 text-xs text-amber-600 dark:text-amber-400">
          <MapPinOff className="h-3.5 w-3.5 shrink-0" />
          Não conseguimos acessar sua localização — a loja não vai te ver no mapa.
        </p>
      )}

      {isLoading && <p className="text-sm text-gray-500 dark:text-stone-400">Carregando...</p>}

      {deliveries?.length === 0 && !isLoading && (
        <EmptyState icon={DeliveryRiderIcon} message="Nenhuma entrega com você no momento." />
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
                  <span className="font-semibold text-gray-900 dark:text-white">{delivery.customerName}</span>
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
