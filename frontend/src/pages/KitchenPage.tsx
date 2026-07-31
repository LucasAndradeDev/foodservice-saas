import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AnimatePresence, motion, type Variants } from 'framer-motion'
import {
  AlertTriangle,
  Ban,
  ChefHat,
  Check,
  ChevronDown,
  Clock,
  Flame,
  PackageCheck,
  PlayCircle,
  Send,
  UtensilsCrossed,
  X,
  type LucideIcon,
} from 'lucide-react'
import { useMemo, useState } from 'react'
import { listKitchenQueue, updateItemStatus, type KitchenItem } from '../api/kitchen'
import type { ItemStatus } from '../api/orders'
import { getMyRestaurant } from '../api/restaurant'
import { useAuth } from '../auth/AuthContext'
import { EmptyState } from '../components/EmptyState'
import { formatTableLabel } from '../utils/tableLabel'
import { minutesSince } from '../utils/time'

const STATUS_LABELS: Record<ItemStatus, string> = {
  PENDING: 'Pendente',
  PREPARING: 'Em preparo',
  READY: 'Pronto',
  DELIVERED: 'Entregue',
  CANCELLED: 'Cancelado',
}

const STATUS_STYLES: Record<ItemStatus, string> = {
  PENDING: 'bg-gray-100 text-gray-600',
  PREPARING: 'bg-blue-100 text-blue-700',
  READY: 'bg-amber-100 text-amber-700',
  DELIVERED: 'bg-green-100 text-green-700',
  CANCELLED: 'bg-red-100 text-red-700',
}

const STATUS_ICONS: Record<ItemStatus, LucideIcon> = {
  PENDING: Clock,
  PREPARING: Flame,
  READY: PackageCheck,
  DELIVERED: Check,
  CANCELLED: Ban,
}

const POLL_INTERVAL_MS = 4000

const EASE_OUT: [number, number, number, number] = [0.16, 1, 0.3, 1]

const groupPanelVariants: Variants = {
  collapsed: {
    height: 0,
    opacity: 0,
    transition: { height: { duration: 0.25, ease: [0.4, 0, 1, 1] }, opacity: { duration: 0.15, ease: 'easeIn' } },
  },
  expanded: {
    height: 'auto',
    opacity: 1,
    transition: { height: { duration: 0.4, ease: EASE_OUT }, opacity: { duration: 0.3, delay: 0.08, ease: EASE_OUT } },
  },
}

type DelayLevel = 'none' | 'warning' | 'critical'

function getDelayLevel(
  item: KitchenItem,
  warningThresholdMinutes: number,
  criticalThresholdMinutes: number,
): DelayLevel {
  const isWaiting = item.status === 'PENDING' || item.status === 'PREPARING'
  if (!isWaiting) return 'none'
  const minutes = minutesSince(item.createdAt)
  if (minutes >= criticalThresholdMinutes) return 'critical'
  if (minutes >= warningThresholdMinutes) return 'warning'
  return 'none'
}

interface KitchenGroup {
  key: string
  label: string
  items: KitchenItem[]
  delayLevel: DelayLevel
}

function groupByTable(
  items: KitchenItem[],
  warningThresholdMinutes: number,
  criticalThresholdMinutes: number,
): KitchenGroup[] {
  const itemsByKey = new Map<string, KitchenItem[]>()
  for (const item of items) {
    const key = [...item.tableNumbers].sort((a, b) => a - b).join(',')
    const group = itemsByKey.get(key)
    if (group) {
      group.push(item)
    } else {
      itemsByKey.set(key, [item])
    }
  }

  const groups = Array.from(itemsByKey.values()).map((groupItems) => {
    const levels = groupItems.map((item) => getDelayLevel(item, warningThresholdMinutes, criticalThresholdMinutes))
    const delayLevel: DelayLevel = levels.includes('critical') ? 'critical' : levels.includes('warning') ? 'warning' : 'none'

    return {
      key: groupItems[0].tableNumbers.slice().sort((a, b) => a - b).join(','),
      label: formatTableLabel(groupItems[0].tableNumbers),
      items: groupItems,
      delayLevel,
    }
  })

  // Groups start in item order (oldest item first), so items[0] of each group is its oldest item.
  groups.sort((a, b) => new Date(a.items[0].createdAt).getTime() - new Date(b.items[0].createdAt).getTime())

  return groups
}

export function KitchenPage() {
  const { user } = useAuth()
  const canAdvance = user?.role === 'OWNER' || user?.role === 'MANAGER' || user?.role === 'KITCHEN'
  const canDeliver =
    user?.role === 'OWNER' || user?.role === 'MANAGER' || user?.role === 'WAITER' || user?.role === 'CASHIER'
  const queryClient = useQueryClient()

  const { data: items, isLoading } = useQuery({
    queryKey: ['kitchen-queue'],
    queryFn: () => listKitchenQueue(),
    refetchInterval: POLL_INTERVAL_MS,
    refetchIntervalInBackground: true,
  })

  const { data: restaurant } = useQuery({
    queryKey: ['restaurant'],
    queryFn: getMyRestaurant,
  })

  const warningThresholdMinutes = restaurant?.kitchenWarningThresholdMinutes ?? 10
  const criticalThresholdMinutes = restaurant?.kitchenCriticalThresholdMinutes ?? 20

  const groups = useMemo(
    () => groupByTable(items ?? [], warningThresholdMinutes, criticalThresholdMinutes),
    [items, warningThresholdMinutes, criticalThresholdMinutes],
  )

  const delayedItemCount = useMemo(
    () =>
      (items ?? []).filter((item) => getDelayLevel(item, warningThresholdMinutes, criticalThresholdMinutes) !== 'none')
        .length,
    [items, warningThresholdMinutes, criticalThresholdMinutes],
  )

  const [showOnlyDelayed, setShowOnlyDelayed] = useState(false)
  const visibleGroups = showOnlyDelayed ? groups.filter((group) => group.delayLevel !== 'none') : groups

  const [collapsedGroups, setCollapsedGroups] = useState<Record<string, boolean>>({})

  function toggleGroup(key: string) {
    setCollapsedGroups((prev) => ({ ...prev, [key]: !prev[key] }))
  }

  const statusMutation = useMutation({
    mutationFn: ({ id, status }: { id: string; status: ItemStatus }) => updateItemStatus(id, status),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['kitchen-queue'] }),
  })

  function handleCancel(item: KitchenItem) {
    const confirmed = window.confirm(`Cancelar "${item.productName}"? Essa ação não pode ser desfeita.`)
    if (confirmed) {
      statusMutation.mutate({ id: item.id, status: 'CANCELLED' })
    }
  }

  return (
    <div>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-gray-200 bg-white p-4 shadow-xs">
        <h1 className="flex items-center gap-2 text-lg font-semibold text-gray-800">
          <ChefHat className="h-5 w-5 text-brand-600" />
          Cozinha
        </h1>

        <button
          type="button"
          onClick={() => setShowOnlyDelayed((prev) => !prev)}
          className={`flex items-center gap-1.5 rounded-full border px-3 py-1.5 text-sm font-medium ${
            showOnlyDelayed
              ? 'border-amber-400 bg-amber-100 text-amber-800'
              : 'border-gray-200 bg-white text-gray-600 hover:bg-gray-50'
          }`}
        >
          <AlertTriangle className="h-4 w-4" />
          Só atrasados
          <span
            className={`rounded-full px-1.5 py-0.5 text-xs ${
              showOnlyDelayed ? 'bg-amber-200 text-amber-900' : 'bg-gray-100 text-gray-600'
            }`}
          >
            {delayedItemCount}
          </span>
        </button>
      </div>

      {isLoading && <p className="text-sm text-gray-500">Carregando...</p>}

      {items && items.length === 0 && <EmptyState icon={ChefHat} message="Nenhum item na fila." />}

      {items && items.length > 0 && showOnlyDelayed && visibleGroups.length === 0 && (
        <EmptyState icon={Check} message="Nenhum pedido atrasado no momento." />
      )}

      {items && items.length > 0 && visibleGroups.length > 0 && (
        <div className="space-y-3">
          {visibleGroups.map((group) => {
            const isCollapsed = collapsedGroups[group.key] ?? false

            return (
              <div
                key={group.key}
                className={`overflow-hidden rounded-xl border-2 bg-white shadow-sm ${
                  group.delayLevel === 'critical'
                    ? 'border-red-400'
                    : group.delayLevel === 'warning'
                      ? 'border-amber-400'
                      : 'border-gray-200'
                }`}
              >
                <button
                  type="button"
                  onClick={() => toggleGroup(group.key)}
                  className="flex w-full items-center justify-between gap-2 px-4 py-3 text-left hover:bg-gray-50"
                >
                  <span className="flex items-center gap-2">
                    <UtensilsCrossed className="h-4 w-4 text-brand-600" />
                    <span className="text-sm font-semibold text-gray-800">{group.label}</span>
                    <span className="rounded-full bg-gray-100 px-2 py-0.5 text-xs text-gray-600">
                      {group.items.length} {group.items.length > 1 ? 'itens' : 'item'}
                    </span>
                    {group.delayLevel === 'critical' && <AlertTriangle className="h-3.5 w-3.5 text-red-600" />}
                    {group.delayLevel === 'warning' && <AlertTriangle className="h-3.5 w-3.5 text-amber-600" />}
                  </span>
                  <motion.span
                    animate={{ rotate: isCollapsed ? 0 : 180 }}
                    transition={{ duration: 0.35, ease: EASE_OUT }}
                  >
                    <ChevronDown className="h-4 w-4 text-gray-400" />
                  </motion.span>
                </button>

                <AnimatePresence initial={false}>
                  {!isCollapsed && (
                    <motion.div
                      initial="collapsed"
                      animate="expanded"
                      exit="collapsed"
                      variants={groupPanelVariants}
                      className="overflow-hidden border-t border-gray-100"
                    >
                      <div className="space-y-2 p-3">
                        {group.items.map((item) => {
                          const minutes = minutesSince(item.createdAt)
                          const delayLevel = getDelayLevel(item, warningThresholdMinutes, criticalThresholdMinutes)
                          const StatusIcon = STATUS_ICONS[item.status]

                          return (
                            <div
                              key={item.id}
                              className={`flex flex-col gap-3 rounded-lg border-2 bg-white p-4 shadow-sm sm:flex-row sm:items-center sm:justify-between ${
                                delayLevel === 'critical'
                                  ? 'border-red-400'
                                  : delayLevel === 'warning'
                                    ? 'border-amber-400'
                                    : 'border-gray-200'
                              }`}
                            >
                              <div>
                                <div className="flex items-center gap-1.5 text-sm text-gray-500">
                                  <Clock className="h-3.5 w-3.5" />
                                  <span
                                    className={
                                      delayLevel === 'critical'
                                        ? 'font-semibold text-red-600'
                                        : delayLevel === 'warning'
                                          ? 'font-semibold text-amber-600'
                                          : ''
                                    }
                                  >
                                    há {minutes} min
                                  </span>
                                  {delayLevel === 'critical' && <AlertTriangle className="h-3.5 w-3.5 text-red-600" />}
                                  {delayLevel === 'warning' && <AlertTriangle className="h-3.5 w-3.5 text-amber-600" />}
                                </div>
                                <div className="text-base font-medium text-gray-800">
                                  {item.quantity}x {item.productName}
                                </div>
                                {item.modifiers.length > 0 && (
                                  <div className="mt-1 flex flex-wrap gap-1">
                                    {item.modifiers.map((modifier, index) => (
                                      <span
                                        key={index}
                                        className="rounded-full bg-gray-100 px-2 py-0.5 text-xs text-gray-600"
                                      >
                                        {modifier.optionName}
                                      </span>
                                    ))}
                                  </div>
                                )}
                                {item.observation && <div className="text-sm text-gray-500">{item.observation}</div>}
                              </div>

                              <div className="flex flex-wrap items-center gap-3">
                                <span
                                  className={`flex items-center gap-1 rounded-full px-2 py-0.5 text-xs ${STATUS_STYLES[item.status]}`}
                                >
                                  <StatusIcon className="h-3 w-3" />
                                  {STATUS_LABELS[item.status]}
                                </span>

                                {item.status === 'PENDING' && canAdvance && (
                                  <button
                                    type="button"
                                    onClick={() => statusMutation.mutate({ id: item.id, status: 'PREPARING' })}
                                    className="flex items-center gap-1.5 rounded-md bg-brand-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-brand-700"
                                  >
                                    <PlayCircle className="h-4 w-4" />
                                    Iniciar preparo
                                  </button>
                                )}
                                {item.status === 'PREPARING' && canAdvance && (
                                  <button
                                    type="button"
                                    onClick={() => statusMutation.mutate({ id: item.id, status: 'READY' })}
                                    className="flex items-center gap-1.5 rounded-md bg-brand-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-brand-700"
                                  >
                                    <PackageCheck className="h-4 w-4" />
                                    Marcar pronto
                                  </button>
                                )}
                                {item.status === 'READY' && canDeliver && (
                                  <button
                                    type="button"
                                    onClick={() => statusMutation.mutate({ id: item.id, status: 'DELIVERED' })}
                                    className="flex items-center gap-1.5 rounded-md bg-brand-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-brand-700"
                                  >
                                    <Send className="h-4 w-4" />
                                    Marcar entregue
                                  </button>
                                )}
                                <button
                                  type="button"
                                  onClick={() => handleCancel(item)}
                                  className="flex items-center gap-1 text-sm text-red-600 hover:underline"
                                >
                                  <X className="h-3.5 w-3.5" />
                                  Cancelar
                                </button>
                              </div>
                            </div>
                          )
                        })}
                      </div>
                    </motion.div>
                  )}
                </AnimatePresence>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
