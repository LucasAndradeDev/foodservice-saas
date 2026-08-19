import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AnimatePresence, motion } from 'framer-motion'
import { Clock, MapPin, Phone } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { DeliveryRiderIcon } from '../components/DeliveryRiderIcon'
import {
  DELIVERY_ACCENT_STYLES,
  DELIVERY_NEXT_STATUS,
  DELIVERY_NEXT_STATUS_LABELS,
  DELIVERY_STATUS_LABELS,
  DELIVERY_STATUS_STYLES,
  listOpenDeliveries,
  updateDeliveryStatus,
  type DeliveryDetails,
} from '../api/deliveries'
import { PageHeader } from '../components/PageHeader'
import { minutesSince } from '../utils/time'

const currencyFormatter = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

const rowVariants = {
  hidden: { opacity: 0, y: 6 },
  visible: { opacity: 1, y: 0 },
  exit: { opacity: 0 },
}

export function DeliveryPage() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()

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

  return (
    <div>
      <div className="mb-5 rounded-2xl border border-gray-200 bg-white p-5 shadow-sm dark:border-white/10 dark:bg-stone-900">
        <PageHeader icon={DeliveryRiderIcon} title="Delivery" />
      </div>

      {isLoading && <p className="text-sm text-gray-500 dark:text-stone-400">Carregando...</p>}

      {deliveries?.length === 0 && !isLoading && (
        <div className="flex flex-col items-center gap-3 rounded-2xl border border-dashed border-gray-300 bg-gray-50 py-16 text-center dark:border-white/10 dark:bg-white/5">
          <span className="flex h-14 w-14 items-center justify-center rounded-full bg-white text-gray-300 shadow-sm dark:bg-stone-800 dark:text-stone-600">
            <DeliveryRiderIcon className="h-7 w-7" />
          </span>
          <p className="text-sm text-gray-500 dark:text-stone-400">Nenhuma entrega em andamento.</p>
        </div>
      )}

      {deliveries && deliveries.length > 0 && (
        <div className="overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-sm dark:border-white/10 dark:bg-stone-900">
          <div className="divide-y divide-gray-100 dark:divide-white/10">
            <AnimatePresence initial={false}>
              {deliveries.map((delivery) => {
                const nextStatus = DELIVERY_NEXT_STATUS[delivery.status]
                const nextStatusLabel = DELIVERY_NEXT_STATUS_LABELS[delivery.status]
                const blockedByKitchen = delivery.status === 'SEPARATING' && !delivery.kitchenReady
                const blockedByPayment = delivery.status === 'SEPARATING' && !delivery.paid
                const isBlocked = blockedByKitchen || blockedByPayment
                const blockedLabel = blockedByPayment ? 'Aguardando pagamento' : 'Aguardando cozinha'
                const blockedTitle = blockedByPayment
                  ? 'O cliente ainda não confirmou o pagamento desse pedido'
                  : 'Aguardando a cozinha terminar de preparar o pedido'
                return (
                  <motion.div
                    key={delivery.id}
                    layout
                    variants={rowVariants}
                    initial="hidden"
                    animate="visible"
                    exit="exit"
                    transition={{ duration: 0.18 }}
                    className="relative flex flex-col gap-3 py-4 pr-4 pl-5 transition-colors hover:bg-gray-50 sm:flex-row sm:items-center sm:justify-between sm:gap-6 dark:hover:bg-white/5"
                  >
                    <span
                      className={`absolute inset-y-0 left-0 w-1 ${DELIVERY_ACCENT_STYLES[delivery.status]}`}
                      aria-hidden="true"
                    />
                    <button
                      type="button"
                      onClick={() => navigate(`/tabs/${delivery.tabId}`)}
                      className="min-w-0 flex-1 text-left"
                    >
                      <p className="truncate text-sm font-semibold text-gray-900 dark:text-white">
                        {delivery.customerName}
                      </p>
                      <p className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-gray-500 dark:text-stone-400">
                        <span className="flex items-center gap-1">
                          <Clock className="h-3.5 w-3.5" />
                          há {minutesSince(delivery.createdAt)} min
                        </span>
                        <span className="flex items-center gap-1">
                          <Phone className="h-3.5 w-3.5" />
                          {delivery.customerPhone}
                        </span>
                        <span className="flex items-center gap-1">
                          <MapPin className="h-3.5 w-3.5" />
                          {delivery.street}, {delivery.number} - {delivery.neighborhood}
                        </span>
                        <span>{currencyFormatter.format(delivery.deliveryFee)}</span>
                      </p>
                    </button>

                    <div className="flex shrink-0 items-center gap-2 sm:justify-end">
                      <span className={`rounded-full px-2.5 py-1 text-xs font-medium ${DELIVERY_STATUS_STYLES[delivery.status]}`}>
                        {DELIVERY_STATUS_LABELS[delivery.status]}
                      </span>
                      {nextStatus && nextStatusLabel && (
                        <button
                          type="button"
                          onClick={() => advanceMutation.mutate({ tabId: delivery.tabId, status: nextStatus })}
                          disabled={advanceMutation.isPending || isBlocked}
                          title={isBlocked ? blockedTitle : undefined}
                          className="rounded-xl bg-brand-600 px-3 py-2 text-xs font-semibold text-white shadow-sm transition hover:bg-brand-700 disabled:opacity-50"
                        >
                          {isBlocked ? blockedLabel : nextStatusLabel}
                        </button>
                      )}
                    </div>
                  </motion.div>
                )
              })}
            </AnimatePresence>
          </div>
        </div>
      )}
    </div>
  )
}
