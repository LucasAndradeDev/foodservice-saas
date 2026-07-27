import { useMutation, useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { listOrders } from '../api/orders'
import { computeDiscountAmount, getTab, markTabReceiptPrinted, type PaymentMethod } from '../api/tabs'
import { getMyRestaurant } from '../api/restaurant'
import { formatTableLabel } from '../utils/tableLabel'

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
  const itemsTotal = items
    .filter((item) => item.status !== 'CANCELLED')
    .reduce((sum, item) => sum + item.netSubtotal, 0)
  const tabDiscountAmount = computeDiscountAmount(tab.discountType, tab.discountValue, itemsTotal)
  const total = tab.paidAmount ?? itemsTotal - tabDiscountAmount

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
          {formatTableLabel(tab.tables.map((t) => t.number))}
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
              {item.modifiers.length > 0 && ` (${item.modifiers.map((modifier) => modifier.optionName).join(', ')})`}
              {item.discountType && item.status !== 'CANCELLED' && (
                <span className="block text-xs text-gray-500">
                  Desconto: -{currencyFormatter.format(item.discountAmount)}
                  {item.discountReason && ` (${item.discountReason})`}
                </span>
              )}
            </span>
            <span>{currencyFormatter.format(item.status === 'CANCELLED' ? item.subtotal : item.netSubtotal)}</span>
          </li>
        ))}
      </ul>

      {tab.discountType && (
        <div className="mt-2 flex items-center justify-between text-sm text-gray-600">
          <span>
            Desconto na comanda
            {tab.discountReason && ` (${tab.discountReason})`}
          </span>
          <span>-{currencyFormatter.format(tabDiscountAmount)}</span>
        </div>
      )}

      {tab.serviceChargePercentage != null && (
        <div className="mt-2 flex items-center justify-between text-sm text-gray-600">
          <span>Taxa de serviço ({tab.serviceChargePercentage}%)</span>
          <span>{currencyFormatter.format(tab.serviceChargeAmount ?? 0)}</span>
        </div>
      )}

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
