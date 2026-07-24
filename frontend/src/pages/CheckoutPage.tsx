import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { listOrders, type OrderItem } from '../api/orders'
import { listTabs, payTab, type PaymentMethod, type Tab } from '../api/tabs'
import { useAuth } from '../auth/AuthContext'
import { Modal } from '../components/Modal'

const currencyFormatter = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

const PAYMENT_METHOD_LABELS: Record<PaymentMethod, string> = {
  PIX: 'Pix',
  CASH: 'Dinheiro',
  DEBIT_CARD: 'Cartão de débito',
  CREDIT_CARD: 'Cartão de crédito',
}

interface TabSummary {
  tab: Tab
  items: OrderItem[]
  isLoading: boolean
  isReady: boolean
  total: number
}

export function CheckoutPage() {
  const { user } = useAuth()
  const canPay = user?.role === 'OWNER' || user?.role === 'MANAGER' || user?.role === 'WAITER' || user?.role === 'CASHIER'
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const { data: openTabs, isLoading: isTabsLoading } = useQuery({
    queryKey: ['tabs', 'OPEN'],
    queryFn: () => listTabs('OPEN'),
  })

  const orderQueries = useQueries({
    queries: (openTabs ?? []).map((tab) => ({
      queryKey: ['tabs', tab.id, 'orders'],
      queryFn: () => listOrders(tab.id),
    })),
  })

  const tabSummaries: TabSummary[] = (openTabs ?? []).map((tab, index) => {
    const orders = orderQueries[index]?.data
    const isLoading = orderQueries[index]?.isLoading ?? true
    const items = orders?.flatMap((order) => order.items) ?? []
    const pendingCount = items.filter((item) => item.status !== 'DELIVERED' && item.status !== 'CANCELLED').length
    const total = items
      .filter((item) => item.status !== 'CANCELLED')
      .reduce((sum, item) => sum + item.subtotal, 0)
    return {
      tab,
      items,
      isLoading,
      isReady: !isLoading && items.length > 0 && pendingCount === 0,
      total,
    }
  })

  const [selectedSummary, setSelectedSummary] = useState<TabSummary | null>(null)
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>('PIX')
  const [error, setError] = useState<string | null>(null)

  const payMutation = useMutation({
    mutationFn: ({ id, method, amount }: { id: string; method: PaymentMethod; amount: number }) =>
      payTab(id, method, amount),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tabs'] })
      queryClient.invalidateQueries({ queryKey: ['tables'] })
      setSelectedSummary(null)
    },
    onError: () => setError('Não foi possível fechar a conta. Tente novamente.'),
  })

  function handleCardClick(summary: TabSummary) {
    if (summary.isReady) {
      setSelectedSummary(summary)
      setPaymentMethod('PIX')
      setError(null)
    } else {
      navigate(`/tabs/${summary.tab.id}`)
    }
  }

  function handleConfirmPayment() {
    if (!selectedSummary) return
    setError(null)
    payMutation.mutate({ id: selectedSummary.tab.id, method: paymentMethod, amount: selectedSummary.total })
  }

  return (
    <div>
      <h1 className="mb-4 text-lg font-semibold text-gray-800">Caixa</h1>

      {isTabsLoading && <p className="text-sm text-gray-500">Carregando...</p>}

      {openTabs && openTabs.length === 0 && <p className="text-sm text-gray-500">Nenhuma comanda aberta.</p>}

      {tabSummaries.length > 0 && (
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {tabSummaries.map((summary) => (
            <button
              key={summary.tab.id}
              type="button"
              onClick={() => handleCardClick(summary)}
              className={`rounded-lg border-2 bg-white p-4 text-left shadow-sm transition ${
                summary.isReady ? 'border-green-300 hover:bg-green-50' : 'border-gray-200 hover:bg-gray-50'
              }`}
            >
              <div className="text-base font-semibold text-gray-800">
                Mesa{summary.tab.tables.length > 1 ? 's' : ''}{' '}
                {summary.tab.tables.map((t) => t.number).join(', ')}
              </div>
              {summary.isLoading ? (
                <div className="mt-1 text-sm text-gray-500">Carregando itens...</div>
              ) : summary.isReady ? (
                <>
                  <span className="mt-1 inline-block rounded-full bg-green-100 px-2 py-0.5 text-xs text-green-700">
                    Pronta para fechar
                  </span>
                  <div className="mt-2 text-lg font-semibold text-gray-800">
                    {currencyFormatter.format(summary.total)}
                  </div>
                </>
              ) : (
                <span className="mt-1 inline-block rounded-full bg-amber-100 px-2 py-0.5 text-xs text-amber-700">
                  Itens ainda em preparo
                </span>
              )}
            </button>
          ))}
        </div>
      )}

      {selectedSummary && (
        <Modal title={`Fechar conta — Mesa${selectedSummary.tab.tables.length > 1 ? 's' : ''} ${selectedSummary.tab.tables.map((t) => t.number).join(', ')}`} onClose={() => setSelectedSummary(null)}>
          <ul className="mb-4 divide-y divide-gray-100">
            {selectedSummary.items.map((item) => (
              <li key={item.id} className="flex items-center justify-between py-2 text-sm">
                <span className={item.status === 'CANCELLED' ? 'text-gray-400 line-through' : 'text-gray-800'}>
                  {item.quantity}x {item.productName}
                </span>
                <span className={item.status === 'CANCELLED' ? 'text-gray-400 line-through' : 'text-gray-600'}>
                  {currencyFormatter.format(item.subtotal)}
                </span>
              </li>
            ))}
          </ul>

          <div className="mb-4 flex items-center justify-between border-t border-gray-200 pt-3 text-base font-semibold text-gray-800">
            <span>Total</span>
            <span>{currencyFormatter.format(selectedSummary.total)}</span>
          </div>

          <label className="mb-1 block text-sm font-medium text-gray-700" htmlFor="paymentMethod">
            Forma de pagamento
          </label>
          <select
            id="paymentMethod"
            value={paymentMethod}
            onChange={(e) => setPaymentMethod(e.target.value as PaymentMethod)}
            className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none"
          >
            {(Object.keys(PAYMENT_METHOD_LABELS) as PaymentMethod[]).map((method) => (
              <option key={method} value={method}>
                {PAYMENT_METHOD_LABELS[method]}
              </option>
            ))}
          </select>

          {error && <p className="mb-4 text-sm text-red-600">{error}</p>}

          {canPay && (
            <button
              type="button"
              onClick={handleConfirmPayment}
              disabled={payMutation.isPending}
              className="w-full rounded-md bg-brand-600 px-3 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50"
            >
              Confirmar pagamento
            </button>
          )}
        </Modal>
      )}
    </div>
  )
}
