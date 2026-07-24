import { useMutation, useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { listOrders } from '../api/orders'
import { getTab, markTabReceiptPrinted, type PaymentMethod } from '../api/tabs'
import { getMyRestaurant } from '../api/restaurant'

const currencyFormatter = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

const PAYMENT_METHOD_LABELS: Record<PaymentMethod, string> = {
  PIX: 'Pix',
  CASH: 'Dinheiro',
  DEBIT_CARD: 'Cartão de débito',
  CREDIT_CARD: 'Cartão de crédito',
}

export function TabReceiptPrintPage() {
  const { tabId } = useParams<{ tabId: string }>()

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

  const { data: restaurant } = useQuery({
    queryKey: ['restaurant'],
    queryFn: getMyRestaurant,
  })

  const markPrintedMutation = useMutation({
    mutationFn: () => markTabReceiptPrinted(tabId!),
  })

  function handlePrint() {
    markPrintedMutation.mutate()
    window.print()
  }

  if (isTabLoading || isOrdersLoading || !tab) {
    return <p className="p-6 text-sm text-gray-500">Carregando...</p>
  }

  const items = (orders ?? []).flatMap((order) => order.items)
  const total = items
    .filter((item) => item.status !== 'CANCELLED')
    .reduce((sum, item) => sum + item.subtotal, 0)

  return (
    <div className="mx-auto max-w-sm p-6">
      <Link to="/checkout" className="mb-4 block text-sm text-gray-500 hover:underline print:hidden">
        ← Voltar
      </Link>

      <div className="text-center">
        <h1 className="text-base font-semibold text-gray-800">
          {restaurant?.tradeName || restaurant?.name}
        </h1>
        <p className="text-sm text-gray-600">
          Mesa{tab.tables.length > 1 ? 's' : ''} {tab.tables.map((t) => t.number).join(', ')}
        </p>
      </div>

      <ul className="mt-4 divide-y divide-gray-200 border-t border-gray-300">
        {items.map((item) => (
          <li
            key={item.id}
            className={`flex items-center justify-between py-2 text-sm ${item.status === 'CANCELLED' ? 'text-gray-400 line-through' : 'text-gray-800'}`}
          >
            <span>
              {item.quantity}x {item.productName}
            </span>
            <span>{currencyFormatter.format(item.subtotal)}</span>
          </li>
        ))}
      </ul>

      <div className="mt-3 flex items-center justify-between border-t border-gray-300 pt-3 text-base font-semibold text-gray-800">
        <span>Total</span>
        <span>{currencyFormatter.format(total)}</span>
      </div>

      {tab.paidAt && tab.paymentMethod && (
        <div className="mt-2 text-sm text-gray-600">
          Pago via {PAYMENT_METHOD_LABELS[tab.paymentMethod]}
          {tab.paidAmount !== null && ` — ${currencyFormatter.format(tab.paidAmount)}`}
        </div>
      )}

      <button
        type="button"
        onClick={handlePrint}
        className="mt-6 w-full rounded-md bg-brand-600 px-3 py-2 text-sm font-medium text-white hover:bg-brand-700 print:hidden"
      >
        Imprimir
      </button>
    </div>
  )
}
