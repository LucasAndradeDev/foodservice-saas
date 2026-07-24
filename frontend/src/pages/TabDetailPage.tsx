import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { createOrder, listOrders, type ItemStatus, type Order } from '../api/orders'
import { listProducts } from '../api/products'
import { getMyRestaurant } from '../api/restaurant'
import { listTables } from '../api/tables'
import { addTableToTab, cancelTab, getTab, listTabs, mergeTabs, type Tab } from '../api/tabs'
import { useAuth } from '../auth/AuthContext'
import { Modal } from '../components/Modal'
import { formatTableLabel } from '../utils/tableLabel'

const currencyFormatter = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

const ITEM_STATUS_LABELS: Record<ItemStatus, string> = {
  PENDING: 'Pendente',
  PREPARING: 'Em preparo',
  READY: 'Pronto',
  DELIVERED: 'Entregue',
  CANCELLED: 'Cancelado',
}

const ITEM_STATUS_STYLES: Record<ItemStatus, string> = {
  PENDING: 'bg-gray-100 text-gray-600',
  PREPARING: 'bg-blue-100 text-blue-700',
  READY: 'bg-amber-100 text-amber-700',
  DELIVERED: 'bg-green-100 text-green-700',
  CANCELLED: 'bg-red-100 text-red-700 line-through',
}

interface DraftItem {
  productId: string
  productName: string
  unitPrice: number
  quantity: number
  observation: string
}

export function TabDetailPage() {
  const { tabId } = useParams<{ tabId: string }>()
  const navigate = useNavigate()
  const { user } = useAuth()
  const canOrder = user?.role === 'OWNER' || user?.role === 'MANAGER' || user?.role === 'WAITER' || user?.role === 'CASHIER'
  const queryClient = useQueryClient()

  const { data: tab, isLoading: isTabLoading } = useQuery({
    queryKey: ['tabs', tabId],
    queryFn: () => getTab(tabId!),
    enabled: !!tabId,
  })

  const { data: orders, isLoading: isOrdersLoading } = useQuery({
    queryKey: ['tabs', tabId, 'orders'],
    queryFn: () => listOrders(tabId!),
    enabled: !!tabId,
  })

  const { data: products } = useQuery({
    queryKey: ['products', 'active'],
    queryFn: () => listProducts({ active: true }),
  })

  const { data: restaurant } = useQuery({
    queryKey: ['restaurant'],
    queryFn: getMyRestaurant,
  })

  const [isAddingItem, setIsAddingItem] = useState(false)
  const [draftItems, setDraftItems] = useState<DraftItem[]>([])
  const [productId, setProductId] = useState('')
  const [quantity, setQuantity] = useState('1')
  const [observation, setObservation] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [isMerging, setIsMerging] = useState(false)

  const { data: freeTables } = useQuery({
    queryKey: ['tables', 'FREE'],
    queryFn: () => listTables({ status: 'FREE', active: true }),
    enabled: isMerging,
  })

  const { data: otherOpenTabs } = useQuery({
    queryKey: ['tabs', 'OPEN'],
    queryFn: () => listTabs('OPEN'),
    enabled: isMerging,
  })

  const createOrderMutation = useMutation({
    mutationFn: (items: DraftItem[]) =>
      createOrder(
        tabId!,
        items.map((item) => ({
          productId: item.productId,
          quantity: item.quantity,
          observation: item.observation || undefined,
        })),
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tabs', tabId, 'orders'] })
      setDraftItems([])
    },
    onError: () => setError('Não foi possível enviar o pedido para a cozinha.'),
  })

  const cancelMutation = useMutation({
    mutationFn: () => cancelTab(tabId!),
    onSuccess: () => navigate('/tables'),
    onError: () => setError('Não foi possível cancelar a comanda.'),
  })

  function invalidateAfterMerge() {
    queryClient.invalidateQueries({ queryKey: ['tabs', tabId] })
    queryClient.invalidateQueries({ queryKey: ['tabs', tabId, 'orders'] })
    queryClient.invalidateQueries({ queryKey: ['tables'] })
    queryClient.invalidateQueries({ queryKey: ['tabs', 'OPEN'] })
    setIsMerging(false)
  }

  const addTableMutation = useMutation({
    mutationFn: (tableId: string) => addTableToTab(tabId!, tableId),
    onSuccess: invalidateAfterMerge,
    onError: () => setError('Não foi possível adicionar essa mesa à comanda.'),
  })

  const mergeMutation = useMutation({
    mutationFn: (sourceTabId: string) => mergeTabs(tabId!, sourceTabId),
    onSuccess: invalidateAfterMerge,
    onError: () => setError('Não foi possível mesclar essa comanda.'),
  })

  function openAddItemForm() {
    setProductId(products?.[0]?.id ?? '')
    setQuantity('1')
    setObservation('')
    setError(null)
    setIsAddingItem(true)
  }

  function handleAddDraftItem(event: FormEvent) {
    event.preventDefault()
    const product = products?.find((p) => p.id === productId)
    if (!product) return
    const trimmedObservation = observation.trim()
    const addedQuantity = Number(quantity)

    setDraftItems((prev) => {
      const existingIndex = prev.findIndex(
        (item) => item.productId === product.id && item.observation === trimmedObservation,
      )
      if (existingIndex !== -1) {
        return prev.map((item, index) =>
          index === existingIndex ? { ...item, quantity: item.quantity + addedQuantity } : item,
        )
      }
      return [
        ...prev,
        {
          productId: product.id,
          productName: product.name,
          unitPrice: product.price,
          quantity: addedQuantity,
          observation: trimmedObservation,
        },
      ]
    })
    setQuantity('1')
    setObservation('')
  }

  function removeDraftItem(index: number) {
    setDraftItems((prev) => prev.filter((_, i) => i !== index))
  }

  function handleSendToKitchen() {
    setError(null)
    const printWindow = restaurant?.autoPrintKitchenTickets ? window.open('about:blank', '_blank') : null
    createOrderMutation.mutate(draftItems, {
      onSuccess: (createdOrder) => {
        if (printWindow) {
          printWindow.location.href = `/orders/${createdOrder.id}/print?auto=1`
        }
      },
    })
  }

  function handleCancelTab() {
    const confirmed = window.confirm('Cancelar essa comanda? As mesas voltam a ficar livres.')
    if (confirmed) {
      setError(null)
      cancelMutation.mutate()
    }
  }

  function openMergeModal() {
    setError(null)
    setIsMerging(true)
  }

  function handleMergeTab(sourceTab: Tab) {
    const label = formatTableLabel(sourceTab.tables.map((t) => t.number))
    const confirmed = window.confirm(
      `Mesclar com ${label}? Os pedidos dessa comanda serão movidos pra cá, e ela será encerrada.`,
    )
    if (confirmed) {
      setError(null)
      mergeMutation.mutate(sourceTab.id)
    }
  }

  if (isTabLoading || isOrdersLoading || !tab) {
    return <p className="text-sm text-gray-500">Carregando...</p>
  }

  const allItems = orders?.flatMap((order) => order.items) ?? []
  const grandTotal = allItems
    .filter((item) => item.status !== 'CANCELLED')
    .reduce((sum, item) => sum + item.subtotal, 0)

  return (
    <div>
      <button
        type="button"
        onClick={() => navigate('/tables')}
        className="mb-4 text-sm text-gray-500 hover:underline"
      >
        ← Voltar para Mesas
      </button>

      <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-lg font-semibold text-gray-800">
            {formatTableLabel(tab.tables.map((t) => t.number))}
          </h1>
          <p className="text-sm text-gray-500">
            {tab.status === 'OPEN' ? 'Comanda aberta' : 'Comanda fechada'}
          </p>
        </div>
        {tab.status === 'OPEN' && canOrder && (
          <div className="flex gap-2">
            {orders?.length === 0 && (
              <button
                type="button"
                onClick={handleCancelTab}
                disabled={cancelMutation.isPending}
                className="rounded-md border border-red-300 px-3 py-1.5 text-sm text-red-700 hover:bg-red-50 disabled:opacity-50"
              >
                Cancelar comanda
              </button>
            )}
            <button
              type="button"
              onClick={openMergeModal}
              className="rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-100"
            >
              Mesclar comanda
            </button>
            <button
              type="button"
              onClick={openAddItemForm}
              className="rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-100"
            >
              Adicionar item
            </button>
          </div>
        )}
      </div>

      {error && draftItems.length === 0 && <p className="mb-4 text-sm text-red-600">{error}</p>}

      {draftItems.length > 0 && (
        <div className="mb-6 rounded-lg border border-gray-200 bg-white p-4">
          <h2 className="mb-2 text-sm font-semibold text-gray-800">Novo pedido (ainda não enviado)</h2>
          <ul className="mb-3 divide-y divide-gray-100">
            {draftItems.map((item, index) => (
              <li key={index} className="flex flex-wrap items-center justify-between gap-2 py-2 text-sm">
                <div>
                  <span className="font-medium text-gray-800">
                    {item.quantity}x {item.productName}
                  </span>
                  {item.observation && <span className="ml-2 text-gray-500">({item.observation})</span>}
                </div>
                <div className="flex items-center gap-3">
                  <span className="text-gray-600">
                    {currencyFormatter.format(item.unitPrice * item.quantity)}
                  </span>
                  <button
                    type="button"
                    onClick={() => removeDraftItem(index)}
                    className="text-red-600 hover:underline"
                  >
                    Remover
                  </button>
                </div>
              </li>
            ))}
          </ul>

          {error && <p className="mb-3 text-sm text-red-600">{error}</p>}

          <button
            type="button"
            onClick={handleSendToKitchen}
            disabled={createOrderMutation.isPending}
            className="w-full rounded-md bg-brand-600 px-3 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50"
          >
            Enviar para Cozinha
          </button>
        </div>
      )}

      <div className="space-y-4">
        {orders && orders.length === 0 && draftItems.length === 0 && (
          <p className="text-sm text-gray-500">Nenhum pedido enviado ainda.</p>
        )}

        {orders?.map((order: Order) => (
          <div key={order.id} className="rounded-lg border border-gray-200 bg-white p-4">
            <div className="mb-2 flex items-center justify-between text-sm text-gray-500">
              <span>Pedido às {new Date(order.createdAt).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}</span>
              <div className="flex items-center gap-3">
                {order.printedAt && <span className="text-xs text-gray-400">Impresso</span>}
                <Link to={`/orders/${order.id}/print`} target="_blank" className="text-brand-600 hover:underline">
                  Imprimir
                </Link>
                <span>{currencyFormatter.format(order.total)}</span>
              </div>
            </div>
            <ul className="divide-y divide-gray-100">
              {order.items.map((item) => (
                <li key={item.id} className="flex flex-wrap items-center justify-between gap-2 py-2 text-sm">
                  <div>
                    <span className="font-medium text-gray-800">
                      {item.quantity}x {item.productName}
                    </span>
                    {item.observation && <span className="ml-2 text-gray-500">({item.observation})</span>}
                  </div>
                  <div className="flex items-center gap-3">
                    <span
                      className={`rounded-full px-2 py-0.5 text-xs ${ITEM_STATUS_STYLES[item.status]}`}
                    >
                      {ITEM_STATUS_LABELS[item.status]}
                    </span>
                    <span className="text-gray-600">{currencyFormatter.format(item.subtotal)}</span>
                  </div>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>

      {allItems.length > 0 && (
        <div className="mt-4 flex justify-end text-sm font-semibold text-gray-800">
          Total da comanda: {currencyFormatter.format(grandTotal)}
        </div>
      )}

      {isAddingItem && (
        <Modal title="Adicionar item" onClose={() => setIsAddingItem(false)}>
          <form onSubmit={handleAddDraftItem}>
            <label className="mb-1 block text-sm font-medium text-gray-700" htmlFor="product">
              Produto
            </label>
            <select
              id="product"
              required
              value={productId}
              onChange={(e) => setProductId(e.target.value)}
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none"
            >
              <option value="" disabled>
                Selecione um produto
              </option>
              {products?.map((product) => (
                <option key={product.id} value={product.id}>
                  {product.name} — {currencyFormatter.format(product.price)}
                </option>
              ))}
            </select>

            <label className="mb-1 block text-sm font-medium text-gray-700" htmlFor="quantity">
              Quantidade
            </label>
            <input
              id="quantity"
              type="number"
              required
              min="1"
              value={quantity}
              onChange={(e) => setQuantity(e.target.value)}
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none"
            />

            <label className="mb-1 block text-sm font-medium text-gray-700" htmlFor="observation">
              Observação <span className="font-normal text-gray-400">(opcional)</span>
            </label>
            <input
              id="observation"
              type="text"
              maxLength={255}
              value={observation}
              onChange={(e) => setObservation(e.target.value)}
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none"
            />

            <button
              type="submit"
              disabled={!productId}
              className="w-full rounded-md bg-brand-600 px-3 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50"
            >
              Adicionar à comanda
            </button>
          </form>
        </Modal>
      )}

      {isMerging && (
        <Modal title="Mesclar comanda" onClose={() => setIsMerging(false)}>
          {error && <p className="mb-3 text-sm text-red-600">{error}</p>}

          <p className="mb-1 text-xs font-semibold tracking-wide text-gray-400 uppercase">Mesas livres</p>
          {freeTables?.length === 0 && (
            <p className="mb-4 text-sm text-gray-500">Nenhuma mesa livre no momento.</p>
          )}
          <ul className="mb-4 divide-y divide-gray-100">
            {freeTables?.map((table) => (
              <li key={table.id} className="flex items-center justify-between py-2 text-sm">
                <span>Mesa {table.number}</span>
                <button
                  type="button"
                  onClick={() => addTableMutation.mutate(table.id)}
                  disabled={addTableMutation.isPending}
                  className="rounded-md border border-gray-300 px-3 py-1 text-xs text-gray-700 hover:bg-gray-100 disabled:opacity-50"
                >
                  Adicionar a esta comanda
                </button>
              </li>
            ))}
          </ul>

          <p className="mb-1 text-xs font-semibold tracking-wide text-gray-400 uppercase">Outras comandas abertas</p>
          {otherOpenTabs?.filter((t) => t.id !== tabId).length === 0 && (
            <p className="text-sm text-gray-500">Nenhuma outra comanda aberta.</p>
          )}
          <ul className="divide-y divide-gray-100">
            {otherOpenTabs
              ?.filter((t) => t.id !== tabId)
              .map((otherTab) => (
                <li key={otherTab.id} className="flex items-center justify-between py-2 text-sm">
                  <span>{formatTableLabel(otherTab.tables.map((t) => t.number))}</span>
                  <button
                    type="button"
                    onClick={() => handleMergeTab(otherTab)}
                    disabled={mergeMutation.isPending}
                    className="rounded-md border border-gray-300 px-3 py-1 text-xs text-gray-700 hover:bg-gray-100 disabled:opacity-50"
                  >
                    Mesclar aqui
                  </button>
                </li>
              ))}
          </ul>
        </Modal>
      )}
    </div>
  )
}
