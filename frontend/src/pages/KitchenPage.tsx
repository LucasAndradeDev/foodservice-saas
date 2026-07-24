import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  AlertTriangle,
  Ban,
  ChefHat,
  Check,
  Clock,
  Flame,
  PackageCheck,
  PlayCircle,
  Send,
  UtensilsCrossed,
  X,
  type LucideIcon,
} from 'lucide-react'
import { listKitchenQueue, updateItemStatus, type KitchenItem } from '../api/kitchen'
import type { ItemStatus } from '../api/orders'
import { useAuth } from '../auth/AuthContext'
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

const STALE_MINUTES = 10
const POLL_INTERVAL_MS = 4000

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
  })

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
      <h1 className="mb-4 flex items-center gap-2 text-lg font-semibold text-gray-800">
        <ChefHat className="h-5 w-5 text-brand-600" />
        Cozinha
      </h1>

      {isLoading && <p className="text-sm text-gray-500">Carregando...</p>}

      {items && items.length === 0 && <p className="text-sm text-gray-500">Nenhum item na fila.</p>}

      {items && items.length > 0 && (
        <div className="space-y-2">
          {items.map((item) => {
            const minutes = minutesSince(item.createdAt)
            const isStale = minutes >= STALE_MINUTES
            const StatusIcon = STATUS_ICONS[item.status]

            return (
              <div
                key={item.id}
                className={`flex flex-col gap-3 rounded-lg border-2 bg-white p-4 shadow-sm sm:flex-row sm:items-center sm:justify-between ${
                  isStale ? 'border-red-400' : 'border-gray-200'
                }`}
              >
                <div>
                  <div className="flex items-center gap-1.5 text-sm text-gray-500">
                    <UtensilsCrossed className="h-3.5 w-3.5" />
                    {formatTableLabel(item.tableNumbers)}
                    {' · '}
                    <Clock className="h-3.5 w-3.5" />
                    <span className={isStale ? 'font-semibold text-red-600' : ''}>há {minutes} min</span>
                    {isStale && <AlertTriangle className="h-3.5 w-3.5 text-red-600" />}
                  </div>
                  <div className="text-base font-medium text-gray-800">
                    {item.quantity}x {item.productName}
                  </div>
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
      )}
    </div>
  )
}
