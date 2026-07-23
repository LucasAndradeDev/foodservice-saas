import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { listKitchenQueue, updateItemStatus, type KitchenItem } from '../api/kitchen'
import type { ItemStatus } from '../api/orders'
import { useAuth } from '../auth/AuthContext'

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

const STALE_MINUTES = 10
const POLL_INTERVAL_MS = 4000

function minutesSince(dateString: string) {
  return Math.floor((Date.now() - new Date(dateString).getTime()) / 60000)
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
      <h1 className="mb-4 text-lg font-semibold text-gray-800">Cozinha</h1>

      {isLoading && <p className="text-sm text-gray-500">Carregando...</p>}

      {items && items.length === 0 && <p className="text-sm text-gray-500">Nenhum item na fila.</p>}

      {items && items.length > 0 && (
        <div className="space-y-2">
          {items.map((item) => {
            const minutes = minutesSince(item.createdAt)
            const isStale = minutes >= STALE_MINUTES

            return (
              <div
                key={item.id}
                className={`flex items-center justify-between rounded-lg border-2 bg-white p-4 shadow-sm ${
                  isStale ? 'border-red-400' : 'border-gray-200'
                }`}
              >
                <div>
                  <div className="text-sm text-gray-500">
                    Mesa{item.tableNumbers.length > 1 ? 's' : ''} {item.tableNumbers.join(', ')}
                    {' · '}
                    <span className={isStale ? 'font-semibold text-red-600' : ''}>há {minutes} min</span>
                  </div>
                  <div className="text-base font-medium text-gray-800">
                    {item.quantity}x {item.productName}
                  </div>
                  {item.observation && <div className="text-sm text-gray-500">{item.observation}</div>}
                </div>

                <div className="flex items-center gap-3">
                  <span className={`rounded-full px-2 py-0.5 text-xs ${STATUS_STYLES[item.status]}`}>
                    {STATUS_LABELS[item.status]}
                  </span>

                  {item.status === 'PENDING' && canAdvance && (
                    <button
                      type="button"
                      onClick={() => statusMutation.mutate({ id: item.id, status: 'PREPARING' })}
                      className="rounded-md bg-gray-900 px-3 py-1.5 text-sm font-medium text-white hover:bg-gray-800"
                    >
                      Iniciar preparo
                    </button>
                  )}
                  {item.status === 'PREPARING' && canAdvance && (
                    <button
                      type="button"
                      onClick={() => statusMutation.mutate({ id: item.id, status: 'READY' })}
                      className="rounded-md bg-gray-900 px-3 py-1.5 text-sm font-medium text-white hover:bg-gray-800"
                    >
                      Marcar pronto
                    </button>
                  )}
                  {item.status === 'READY' && canDeliver && (
                    <button
                      type="button"
                      onClick={() => statusMutation.mutate({ id: item.id, status: 'DELIVERED' })}
                      className="rounded-md bg-gray-900 px-3 py-1.5 text-sm font-medium text-white hover:bg-gray-800"
                    >
                      Marcar entregue
                    </button>
                  )}
                  <button type="button" onClick={() => handleCancel(item)} className="text-sm text-red-600 hover:underline">
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
