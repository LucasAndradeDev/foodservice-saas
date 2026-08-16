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
  Layers,
  PackageCheck,
  PlayCircle,
  Send,
  UtensilsCrossed,
  X,
  type LucideIcon,
} from 'lucide-react'
import { useEffect, useMemo, useState, type KeyboardEvent as ReactKeyboardEvent } from 'react'
import { listKitchenQueue, updateItemStatus, type KitchenItem } from '../api/kitchen'
import type { ItemStatus } from '../api/orders'
import { getMyRestaurant } from '../api/restaurant'
import { useAuth } from '../auth/AuthContext'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { EmptyState } from '../components/EmptyState'
import { PageHeader } from '../components/PageHeader'
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
  PENDING: 'bg-gray-100 text-gray-600 dark:bg-white/10 dark:text-stone-400',
  PREPARING: 'bg-gold-100 text-gold-700 dark:bg-gold-500/10 dark:text-gold-400',
  READY: 'bg-teal-100 text-teal-700 dark:bg-teal-500/10 dark:text-teal-400',
  DELIVERED: 'bg-sage-100 text-sage-700 dark:bg-sage-500/10 dark:text-sage-400',
  CANCELLED: 'bg-wine-100 text-wine-700 dark:bg-wine-500/10 dark:text-wine-400',
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

  const [itemToCancel, setItemToCancel] = useState<KitchenItem | null>(null)

  useEffect(() => {
    if (!itemToCancel) return
    function handleEscape(event: KeyboardEvent) {
      if (event.key === 'Escape') setItemToCancel(null)
    }
    document.addEventListener('keydown', handleEscape)
    return () => document.removeEventListener('keydown', handleEscape)
  }, [itemToCancel])

  function confirmCancel() {
    if (itemToCancel) {
      statusMutation.mutate({ id: itemToCancel.id, status: 'CANCELLED' })
    }
    setItemToCancel(null)
  }

  return (
    <div>
      <div className="mb-5 flex items-center justify-between gap-3 rounded-2xl border border-gray-200 bg-white p-5 shadow-sm dark:border-white/10 dark:bg-stone-900">
        <PageHeader icon={ChefHat} title="Cozinha" />

        <button
          type="button"
          onClick={() => setShowOnlyDelayed((prev) => !prev)}
          aria-label="Mostrar só pedidos atrasados"
          className={`flex shrink-0 items-center gap-1.5 rounded-full border px-2.5 py-1.5 text-sm font-medium transition-colors ${
            showOnlyDelayed
              ? 'border-amber-400 bg-amber-100 text-amber-800 dark:border-amber-500/40 dark:bg-amber-500/15 dark:text-amber-400'
              : 'border-gray-200 bg-white text-gray-600 hover:bg-gray-50 dark:border-white/10 dark:bg-stone-900 dark:text-stone-400 dark:hover:bg-white/5'
          }`}
        >
          <AlertTriangle className="h-4 w-4" />
          <span className="hidden sm:inline">Atrasados</span>
          <span
            className={`rounded-full px-1.5 py-0.5 text-xs font-semibold ${
              showOnlyDelayed
                ? 'bg-amber-200 text-amber-900 dark:bg-amber-500/25 dark:text-amber-300'
                : 'bg-gray-100 text-gray-600 dark:bg-white/10 dark:text-stone-400'
            }`}
          >
            {delayedItemCount}
          </span>
        </button>
      </div>

      {isLoading && <p className="text-sm text-gray-500 dark:text-stone-400">Carregando...</p>}

      {items && items.length === 0 && <EmptyState icon={ChefHat} message="Nenhum item na fila." />}

      {items && items.length > 0 && showOnlyDelayed && visibleGroups.length === 0 && (
        <EmptyState icon={Check} message="Nenhum pedido atrasado no momento." />
      )}

      {items && items.length > 0 && visibleGroups.length > 0 && (
        <div className="space-y-3">
          {visibleGroups.map((group) => {
            const isCollapsed = collapsedGroups[group.key] ?? false
            const tableNumbers = group.items[0].tableNumbers

            return (
              <div
                key={group.key}
                className={`overflow-hidden rounded-xl border-2 bg-white shadow-sm dark:bg-stone-900 ${
                  group.delayLevel === 'critical'
                    ? 'border-red-400 dark:border-red-500/50'
                    : group.delayLevel === 'warning'
                      ? 'border-amber-400 dark:border-amber-500/50'
                      : 'border-gray-200 dark:border-white/10'
                }`}
              >
                <button
                  type="button"
                  onClick={() => toggleGroup(group.key)}
                  className="flex w-full items-center justify-between gap-2 px-3 py-3 text-left hover:bg-gray-50 sm:px-4 dark:hover:bg-white/5"
                >
                  <span className="flex min-w-0 items-center gap-2.5">
                    <span
                      className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-lg text-sm font-bold ${
                        group.delayLevel === 'critical'
                          ? 'bg-red-100 text-red-700 dark:bg-red-500/15 dark:text-red-400'
                          : group.delayLevel === 'warning'
                            ? 'bg-amber-100 text-amber-700 dark:bg-amber-500/15 dark:text-amber-400'
                            : 'bg-brand-50 text-brand-700 dark:bg-brand-500/15 dark:text-brand-400'
                      }`}
                    >
                      {tableNumbers.length > 0 ? tableNumbers.join(',') : <UtensilsCrossed className="h-4 w-4" />}
                    </span>
                    <span className="min-w-0">
                      <span className="flex items-center gap-1.5">
                        <span className="truncate text-sm font-semibold text-gray-800 dark:text-white">
                          {group.label}
                        </span>
                        {group.delayLevel === 'critical' && (
                          <AlertTriangle className="h-3.5 w-3.5 shrink-0 text-wine-600 dark:text-wine-400" />
                        )}
                        {group.delayLevel === 'warning' && (
                          <AlertTriangle className="h-3.5 w-3.5 shrink-0 text-amber-600 dark:text-amber-400" />
                        )}
                      </span>
                      <span className="block text-xs text-gray-500 dark:text-stone-400">
                        {group.items.length} {group.items.length > 1 ? 'itens' : 'item'}
                      </span>
                    </span>
                  </span>
                  <motion.span
                    animate={{ rotate: isCollapsed ? 0 : 180 }}
                    transition={{ duration: 0.35, ease: EASE_OUT }}
                    className="shrink-0"
                  >
                    <ChevronDown className="h-4 w-4 text-gray-400 dark:text-stone-500" />
                  </motion.span>
                </button>

                <AnimatePresence initial={false}>
                  {!isCollapsed && (
                    <motion.div
                      initial="collapsed"
                      animate="expanded"
                      exit="collapsed"
                      variants={groupPanelVariants}
                      className="overflow-hidden border-t border-gray-100 dark:border-white/10"
                    >
                      <div className="divide-y divide-gray-100 dark:divide-white/10">
                        {group.items.map((item) => (
                          <KitchenItemCard
                            key={item.id}
                            item={item}
                            depth={0}
                            warningThresholdMinutes={warningThresholdMinutes}
                            criticalThresholdMinutes={criticalThresholdMinutes}
                            canAdvance={canAdvance}
                            canDeliver={canDeliver}
                            onAdvance={(id, status) => statusMutation.mutate({ id, status })}
                            onCancel={setItemToCancel}
                          />
                        ))}
                      </div>
                    </motion.div>
                  )}
                </AnimatePresence>
              </div>
            )
          })}
        </div>
      )}

      {itemToCancel && (
        <ConfirmDialog
          title="Cancelar item"
          message={`Cancelar "${itemToCancel.productName}"? Essa ação não pode ser desfeita.`}
          confirmLabel="Cancelar item"
          cancelLabel="Voltar"
          danger
          onConfirm={confirmCancel}
          onCancel={() => setItemToCancel(null)}
        />
      )}
    </div>
  )
}

interface KitchenItemCardProps {
  item: KitchenItem
  depth: number
  warningThresholdMinutes: number
  criticalThresholdMinutes: number
  canAdvance: boolean
  canDeliver: boolean
  onAdvance: (id: string, status: ItemStatus) => void
  onCancel: (item: KitchenItem) => void
}

function KitchenItemCard({
  item,
  depth,
  warningThresholdMinutes,
  criticalThresholdMinutes,
  canAdvance,
  canDeliver,
  onAdvance,
  onCancel,
}: KitchenItemCardProps) {
  const minutes = minutesSince(item.createdAt)
  const delayLevel = getDelayLevel(item, warningThresholdMinutes, criticalThresholdMinutes)
  const StatusIcon = STATUS_ICONS[item.status]
  const hasPrimaryAction =
    depth === 0 && ((item.status === 'PENDING' && canAdvance) || (item.status === 'PREPARING' && canAdvance) || (item.status === 'READY' && canDeliver))

  // Enter/Space on a focused card advance it the same way its primary button would --
  // lets kitchen staff tab through the queue without reaching for a mouse.
  function handleCardKeyDown(event: ReactKeyboardEvent<HTMLDivElement>) {
    if (!hasPrimaryAction || event.target !== event.currentTarget) return
    if (event.key !== 'Enter' && event.key !== ' ') return
    event.preventDefault()
    if (item.status === 'PENDING') onAdvance(item.id, 'PREPARING')
    else if (item.status === 'PREPARING') onAdvance(item.id, 'READY')
    else if (item.status === 'READY') onAdvance(item.id, 'DELIVERED')
  }

  return (
    <div
      tabIndex={hasPrimaryAction ? 0 : undefined}
      onKeyDown={hasPrimaryAction ? handleCardKeyDown : undefined}
      className={`p-3 sm:p-4 ${depth > 0 ? 'ml-4 border-l-2 border-gray-200 dark:border-white/10' : 'border-l-4'} ${
        hasPrimaryAction ? 'focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:ring-inset' : ''
      } ${
        depth === 0
          ? delayLevel === 'critical'
            ? 'border-red-400 dark:border-red-500/50'
            : delayLevel === 'warning'
              ? 'border-amber-400 dark:border-amber-500/50'
              : 'border-transparent'
          : ''
      }`}
    >
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-1.5 text-xs text-gray-500 dark:text-stone-400">
            <Clock className="h-3.5 w-3.5" />
            <span
              className={
                delayLevel === 'critical'
                  ? 'font-semibold text-wine-600 dark:text-wine-400'
                  : delayLevel === 'warning'
                    ? 'font-semibold text-amber-600 dark:text-amber-400'
                    : ''
              }
            >
              há {minutes} min
            </span>
            <span className={`flex items-center gap-1 rounded-full px-2 py-0.5 ${STATUS_STYLES[item.status]}`}>
              <StatusIcon className="h-3 w-3" />
              {STATUS_LABELS[item.status]}
            </span>
            {delayLevel === 'critical' && <AlertTriangle className="h-3.5 w-3.5 text-wine-600 dark:text-wine-400" />}
            {delayLevel === 'warning' && <AlertTriangle className="h-3.5 w-3.5 text-amber-600 dark:text-amber-400" />}
          </div>
          <div className="mt-1.5 flex items-center gap-1.5 text-base font-semibold text-gray-800 dark:text-white">
            {item.quantity}x {item.productName}
            {item.isComboHeader && (
              <span className="flex items-center gap-1 rounded-full bg-purple-100 px-2 py-0.5 text-xs font-normal text-purple-700 dark:bg-purple-500/10 dark:text-purple-400">
                <Layers className="h-3 w-3" />
                Combo
              </span>
            )}
          </div>
          {item.modifiers.length > 0 && (
            <div className="mt-1.5 flex flex-wrap gap-1">
              {item.modifiers.map((modifier, index) => (
                <span key={index} className="rounded-full bg-gray-100 px-2 py-0.5 text-xs text-gray-600 dark:bg-white/10 dark:text-stone-400">
                  {modifier.optionName}
                </span>
              ))}
            </div>
          )}
          {item.observation && <div className="mt-1 text-sm text-gray-500 dark:text-stone-400">{item.observation}</div>}
        </div>

        {depth === 0 && (
          <div className="flex items-center gap-2 sm:shrink-0">
            {item.status === 'PENDING' && canAdvance && (
              <button
                type="button"
                onClick={() => onAdvance(item.id, 'PREPARING')}
                className="flex flex-1 items-center justify-center gap-1.5 rounded-lg bg-gold-600 px-3 py-2.5 text-sm font-semibold text-white hover:bg-gold-700 sm:flex-none"
              >
                <PlayCircle className="h-4 w-4" />
                Iniciar preparo
                <kbd className="hidden rounded border border-white/40 px-1 text-[10px] font-normal opacity-70 sm:inline">↵</kbd>
              </button>
            )}
            {item.status === 'PREPARING' && canAdvance && (
              <button
                type="button"
                onClick={() => onAdvance(item.id, 'READY')}
                className="flex flex-1 items-center justify-center gap-1.5 rounded-lg bg-teal-600 px-3 py-2.5 text-sm font-semibold text-white hover:bg-teal-700 sm:flex-none"
              >
                <PackageCheck className="h-4 w-4" />
                Marcar pronto
                <kbd className="hidden rounded border border-white/40 px-1 text-[10px] font-normal opacity-70 sm:inline">↵</kbd>
              </button>
            )}
            {item.status === 'READY' && canDeliver && (
              <button
                type="button"
                onClick={() => onAdvance(item.id, 'DELIVERED')}
                className="flex flex-1 items-center justify-center gap-1.5 rounded-lg bg-sage-600 px-3 py-2.5 text-sm font-semibold text-white hover:bg-sage-700 sm:flex-none"
              >
                <Send className="h-4 w-4" />
                Marcar entregue
                <kbd className="hidden rounded border border-white/40 px-1 text-[10px] font-normal opacity-70 sm:inline">↵</kbd>
              </button>
            )}
            <button
              type="button"
              onClick={() => onCancel(item)}
              aria-label="Cancelar item"
              className={`flex shrink-0 items-center justify-center rounded-lg p-2.5 text-gray-400 hover:bg-red-50 hover:text-red-600 dark:text-stone-500 dark:hover:bg-red-500/10 dark:hover:text-red-400 ${
                hasPrimaryAction ? '' : 'flex-1 sm:flex-none'
              }`}
            >
              <X className="h-4 w-4" />
              <span className="ml-1.5 text-sm sm:hidden">{hasPrimaryAction ? '' : 'Cancelar'}</span>
            </button>
          </div>
        )}
      </div>

      {item.children.length > 0 && (
        <div className="mt-2 space-y-2">
          {item.children.map((child) => (
            <KitchenItemCard
              key={child.id}
              item={child}
              depth={depth + 1}
              warningThresholdMinutes={warningThresholdMinutes}
              criticalThresholdMinutes={criticalThresholdMinutes}
              canAdvance={canAdvance}
              canDeliver={canDeliver}
              onAdvance={onAdvance}
              onCancel={onCancel}
            />
          ))}
        </div>
      )}
    </div>
  )
}
