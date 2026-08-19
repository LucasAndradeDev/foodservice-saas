import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AnimatePresence, motion } from 'framer-motion'
import {
  AlertTriangle,
  ChevronDown,
  Clock,
  MapPin,
  MessageCircle,
  Navigation,
  PackageCheck,
  Phone,
  Receipt,
} from 'lucide-react'
import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Badge } from '../components/Badge'
import { DeliveryRiderIcon } from '../components/DeliveryRiderIcon'
import { EmptyState } from '../components/EmptyState'
import { PageHeader } from '../components/PageHeader'
import {
  DELIVERY_ACCENT_STYLES,
  DELIVERY_NEXT_STATUS,
  DELIVERY_NEXT_STATUS_LABELS,
  DELIVERY_STATUS_LABELS,
  listOpenDeliveries,
  updateDeliveryStatus,
  type DeliveryDetails,
  type DeliveryStatus,
} from '../api/deliveries'
import { buildWhatsAppUrl } from '../utils/phone'
import { minutesSince } from '../utils/time'

const currencyFormatter = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

// Kept intentionally simple (no per-restaurant setting like the kitchen's thresholds) - this is
// a visual nudge, not a configurable SLA, and delivery has no equivalent settings field yet.
const WARNING_THRESHOLD_MINUTES = 25
const CRITICAL_THRESHOLD_MINUTES = 45

type DelayLevel = 'none' | 'warning' | 'critical'

function getDelayLevel(delivery: DeliveryDetails): DelayLevel {
  const minutes = minutesSince(delivery.createdAt)
  if (minutes >= CRITICAL_THRESHOLD_MINUTES) return 'critical'
  if (minutes >= WARNING_THRESHOLD_MINUTES) return 'warning'
  return 'none'
}

const STATUS_BADGE_TONE: Record<DeliveryStatus, 'reserved' | 'occupied' | 'free'> = {
  SEPARATING: 'reserved',
  OUT_FOR_DELIVERY: 'occupied',
  DELIVERED: 'free',
}

// Flat fills for the avatar and progress bar, same status colors as DELIVERY_ACCENT_STYLES
// but as solid Tailwind classes (that map already returns a plain "bg-X-500" string, reused
// directly where a flat background is all that's needed).
const NEXT_STATUS_ICON: Partial<Record<DeliveryStatus, typeof DeliveryRiderIcon>> = {
  SEPARATING: DeliveryRiderIcon,
  OUT_FOR_DELIVERY: PackageCheck,
}

// listOpenDeliveries never returns DELIVERED (see backend), so in practice only these two
// sections ever render - the order here is the order they appear top to bottom.
const SECTION_ORDER: DeliveryStatus[] = ['SEPARATING', 'OUT_FOR_DELIVERY']

function formatAddressLines(delivery: DeliveryDetails) {
  const line1 = `${delivery.street}, ${delivery.number}${delivery.complement ? ` - ${delivery.complement}` : ''}`
  const line2 = [delivery.neighborhood, delivery.city].filter(Boolean).join(' - ')
  return { line1, line2 }
}

function buildMapsUrl(delivery: DeliveryDetails) {
  const query = [`${delivery.street}, ${delivery.number}`, delivery.neighborhood, delivery.city]
    .filter(Boolean)
    .join(', ')
  return `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(query)}`
}

function getInitials(name: string) {
  const parts = name.trim().split(/\s+/).filter(Boolean)
  if (parts.length === 0) return '?'
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase()
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase()
}

function itemsPreview(items: DeliveryDetails['items']) {
  if (items.length === 0) return ''
  return items.length > 1 ? `${items[0].productName} +${items.length - 1}` : items[0].productName
}

const cardVariants = {
  hidden: { opacity: 0, y: 10, scale: 0.98 },
  visible: { opacity: 1, y: 0, scale: 1 },
  exit: { opacity: 0, scale: 0.98 },
}

export function DeliveryPage() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const [expandedItems, setExpandedItems] = useState<Record<string, boolean>>({})

  const { data: deliveries, isLoading } = useQuery({
    queryKey: ['deliveries'],
    queryFn: listOpenDeliveries,
    refetchInterval: 15000,
  })

  const advanceMutation = useMutation({
    mutationFn: ({ tabId, status }: { tabId: string; status: DeliveryDetails['status'] }) =>
      updateDeliveryStatus(tabId, status),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['deliveries'] }),
  })

  const sections = useMemo(() => {
    const byStatus = new Map<DeliveryStatus, DeliveryDetails[]>()
    for (const delivery of deliveries ?? []) {
      const bucket = byStatus.get(delivery.status)
      if (bucket) bucket.push(delivery)
      else byStatus.set(delivery.status, [delivery])
    }
    return SECTION_ORDER.map((status) => ({ status, deliveries: byStatus.get(status) ?? [] })).filter(
      (section) => section.deliveries.length > 0,
    )
  }, [deliveries])

  const delayedCount = useMemo(
    () => (deliveries ?? []).filter((delivery) => getDelayLevel(delivery) !== 'none').length,
    [deliveries],
  )

  function toggleItems(id: string) {
    setExpandedItems((prev) => ({ ...prev, [id]: !prev[id] }))
  }

  return (
    <div>
      <div className="mb-5 flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-gray-200 bg-white p-5 shadow-sm dark:border-white/10 dark:bg-stone-900">
        <PageHeader icon={DeliveryRiderIcon} title="Delivery" />
        {deliveries && deliveries.length > 0 && (
          <div className="flex items-center gap-2">
            {sections.map((section) => (
              <span
                key={section.status}
                className="flex items-center gap-1.5 rounded-full bg-gray-50 px-2.5 py-1.5 text-xs font-medium text-gray-600 dark:bg-white/5 dark:text-stone-300"
              >
                <span className={`h-1.5 w-1.5 rounded-full ${DELIVERY_ACCENT_STYLES[section.status]}`} />
                {DELIVERY_STATUS_LABELS[section.status]}
                <span className="font-semibold text-gray-800 dark:text-white">{section.deliveries.length}</span>
              </span>
            ))}
            {delayedCount > 0 && (
              <span className="flex items-center gap-1.5 rounded-full border border-amber-400 bg-amber-100 px-2.5 py-1.5 text-xs font-semibold text-amber-800 dark:border-amber-500/40 dark:bg-amber-500/15 dark:text-amber-400">
                <AlertTriangle className="h-3.5 w-3.5" />
                {delayedCount} atrasado{delayedCount > 1 ? 's' : ''}
              </span>
            )}
          </div>
        )}
      </div>

      {isLoading && <p className="text-sm text-gray-500 dark:text-stone-400">Carregando...</p>}

      {deliveries?.length === 0 && !isLoading && (
        <EmptyState icon={DeliveryRiderIcon} message="Nenhuma entrega em andamento." />
      )}

      {sections.length > 0 && (
        <div className="space-y-6">
          {sections.map((section) => (
            <div key={section.status}>
              <div className="mb-2 flex items-center gap-2 px-1">
                <span className={`h-2 w-2 shrink-0 rounded-full ${DELIVERY_ACCENT_STYLES[section.status]}`} />
                <h2 className="text-sm font-semibold text-gray-700 dark:text-stone-300">
                  {DELIVERY_STATUS_LABELS[section.status]}
                </h2>
                <span className="rounded-full bg-gray-100 px-2 py-0.5 text-xs font-medium text-gray-500 dark:bg-white/10 dark:text-stone-400">
                  {section.deliveries.length}
                </span>
              </div>

              <div className="space-y-3">
                <AnimatePresence initial={false}>
                  {section.deliveries.map((delivery, index) => {
                    const nextStatus = DELIVERY_NEXT_STATUS[delivery.status]
                    const nextStatusLabel = DELIVERY_NEXT_STATUS_LABELS[delivery.status]
                    const NextIcon = NEXT_STATUS_ICON[delivery.status]
                    const blockedByKitchen = delivery.status === 'SEPARATING' && !delivery.kitchenReady
                    const blockedByPayment = delivery.status === 'SEPARATING' && !delivery.paid
                    const isBlocked = blockedByKitchen || blockedByPayment
                    const blockedLabel = blockedByPayment ? 'Aguardando pagamento' : 'Aguardando cozinha'
                    const blockedTitle = blockedByPayment
                      ? 'O cliente ainda não confirmou o pagamento desse pedido'
                      : 'Aguardando a cozinha terminar de preparar o pedido'
                    const delayLevel = getDelayLevel(delivery)
                    const { line1, line2 } = formatAddressLines(delivery)
                    const itemsTotal = delivery.items.reduce((sum, item) => sum + item.unitPrice * item.quantity, 0)
                    const total = delivery.billTotal ?? itemsTotal + delivery.deliveryFee
                    const itemsOpen = expandedItems[delivery.id] ?? false
                    const elapsedMinutes = minutesSince(delivery.createdAt)
                    const progressPct = Math.min(100, (elapsedMinutes / CRITICAL_THRESHOLD_MINUTES) * 100)
                    const progressColor =
                      delayLevel === 'critical'
                        ? 'bg-wine-500'
                        : delayLevel === 'warning'
                          ? 'bg-amber-400'
                          : DELIVERY_ACCENT_STYLES[delivery.status]

                    return (
                      <motion.div
                        key={delivery.id}
                        variants={cardVariants}
                        initial="hidden"
                        animate="visible"
                        exit="exit"
                        transition={{ duration: 0.22, delay: index * 0.03, ease: [0.16, 1, 0.3, 1] }}
                        className={`group relative overflow-hidden rounded-2xl border bg-white shadow-sm transition-shadow duration-200 hover:shadow-lg dark:bg-stone-900 ${
                          delayLevel === 'critical'
                            ? 'border-wine-300 dark:border-wine-500/40'
                            : delayLevel === 'warning'
                              ? 'border-amber-300 dark:border-amber-500/40'
                              : 'border-gray-200 dark:border-white/10'
                        }`}
                      >
                        <div className="h-1 w-full bg-gray-100 dark:bg-white/5">
                          <motion.div
                            initial={false}
                            animate={{ width: `${progressPct}%` }}
                            transition={{ duration: 0.6, ease: 'easeOut' }}
                            className={`h-full ${progressColor}`}
                          />
                        </div>

                        <div className="flex items-start justify-between gap-3 p-4 pb-3">
                          <button
                            type="button"
                            onClick={() => navigate(`/tabs/${delivery.tabId}`)}
                            className="flex min-w-0 flex-1 items-center gap-3 text-left"
                          >
                            <span
                              className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-full text-sm font-bold text-white shadow-sm ${DELIVERY_ACCENT_STYLES[delivery.status]}`}
                            >
                              {getInitials(delivery.customerName)}
                            </span>
                            <span className="min-w-0">
                              <span className="flex items-center gap-1.5 truncate text-sm font-semibold text-gray-900 group-hover:underline dark:text-white">
                                {delivery.customerName}
                                {delayLevel === 'critical' && (
                                  <AlertTriangle className="h-3.5 w-3.5 shrink-0 text-wine-600 dark:text-wine-400" />
                                )}
                                {delayLevel === 'warning' && (
                                  <AlertTriangle className="h-3.5 w-3.5 shrink-0 text-amber-600 dark:text-amber-400" />
                                )}
                              </span>
                              <span
                                className={`flex items-center gap-1 text-xs ${
                                  delayLevel === 'critical'
                                    ? 'font-semibold text-wine-600 dark:text-wine-400'
                                    : delayLevel === 'warning'
                                      ? 'font-semibold text-amber-600 dark:text-amber-400'
                                      : 'text-gray-500 dark:text-stone-400'
                                }`}
                              >
                                <Clock className="h-3.5 w-3.5" />
                                há {elapsedMinutes} min
                              </span>
                            </span>
                          </button>
                          <div className="flex shrink-0 flex-col items-end gap-1.5">
                            <span className="text-sm font-bold text-gray-900 dark:text-white">
                              {currencyFormatter.format(total)}
                            </span>
                            <Badge tone={STATUS_BADGE_TONE[delivery.status]} dot>
                              {DELIVERY_STATUS_LABELS[delivery.status]}
                            </Badge>
                          </div>
                        </div>

                        <div className="px-4 py-2.5">
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

                          <div className="mt-2 grid grid-cols-3 gap-2">
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
                              href={`tel:${delivery.customerPhone.replace(/\D/g, '')}`}
                              className="flex items-center justify-center gap-1.5 rounded-xl border border-gray-200 bg-white py-2.5 text-sm font-semibold text-gray-700 shadow-sm transition hover:border-brand-300 hover:text-brand-600 active:scale-95 dark:border-white/10 dark:bg-stone-800 dark:text-stone-200 dark:hover:text-brand-400"
                            >
                              <Phone className="h-4 w-4" />
                              <span className="truncate">{delivery.customerPhone}</span>
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
                        </div>

                        <button
                          type="button"
                          onClick={() => toggleItems(delivery.id)}
                          className="mt-1.5 flex w-full items-center justify-between gap-2 border-t border-gray-100 px-4 py-2.5 text-left hover:bg-gray-50 dark:border-white/10 dark:hover:bg-white/5"
                        >
                          <span className="flex min-w-0 items-center gap-1.5 text-xs font-medium text-gray-600 dark:text-stone-300">
                            <Receipt className="h-3.5 w-3.5 shrink-0 text-gray-400 dark:text-stone-500" />
                            <span className="truncate">
                              {delivery.items.length} {delivery.items.length === 1 ? 'item' : 'itens'} ·{' '}
                              {itemsPreview(delivery.items)}
                            </span>
                          </span>
                          <motion.span
                            animate={{ rotate: itemsOpen ? 180 : 0 }}
                            transition={{ duration: 0.2 }}
                            className="shrink-0"
                          >
                            <ChevronDown className="h-3.5 w-3.5 text-gray-400 dark:text-stone-500" />
                          </motion.span>
                        </button>

                        <AnimatePresence initial={false}>
                          {itemsOpen && (
                            <motion.div
                              initial={{ height: 0, opacity: 0 }}
                              animate={{ height: 'auto', opacity: 1 }}
                              exit={{ height: 0, opacity: 0 }}
                              transition={{ duration: 0.2 }}
                              className="overflow-hidden"
                            >
                              <div className="space-y-1.5 border-t border-gray-100 px-4 py-3 text-xs dark:border-white/10">
                                {delivery.items.map((item, itemIndex) => (
                                  <div key={itemIndex} className="flex items-center justify-between gap-2 text-gray-600 dark:text-stone-300">
                                    <span>
                                      <span className="font-medium text-gray-800 dark:text-white">{item.quantity}x</span>{' '}
                                      {item.productName}
                                    </span>
                                    <span className="shrink-0 text-gray-500 dark:text-stone-400">
                                      {currencyFormatter.format(item.unitPrice * item.quantity)}
                                    </span>
                                  </div>
                                ))}
                                <div className="flex items-center justify-between gap-2 border-t border-gray-100 pt-1.5 text-gray-500 dark:border-white/10 dark:text-stone-400">
                                  <span>Taxa de entrega</span>
                                  <span>{currencyFormatter.format(delivery.deliveryFee)}</span>
                                </div>
                                <div className="flex items-center justify-between gap-2 font-semibold text-gray-900 dark:text-white">
                                  <span>Total</span>
                                  <span>{currencyFormatter.format(total)}</span>
                                </div>
                              </div>
                            </motion.div>
                          )}
                        </AnimatePresence>

                        {nextStatus && nextStatusLabel && (
                          <div className="border-t border-gray-100 p-3 dark:border-white/10">
                            <button
                              type="button"
                              onClick={() => advanceMutation.mutate({ tabId: delivery.tabId, status: nextStatus })}
                              disabled={advanceMutation.isPending || isBlocked}
                              title={isBlocked ? blockedTitle : undefined}
                              className="flex w-full items-center justify-center gap-2 rounded-xl bg-brand-600 px-3 py-2.5 text-sm font-semibold text-white shadow-sm transition hover:bg-brand-700 disabled:opacity-50"
                            >
                              {!isBlocked && NextIcon && <NextIcon className="h-4 w-4" />}
                              {isBlocked ? blockedLabel : nextStatusLabel}
                            </button>
                          </div>
                        )}
                      </motion.div>
                    )
                  })}
                </AnimatePresence>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
