import { useMutation, useQuery } from '@tanstack/react-query'
import { useEffect, useRef } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { listOrders } from '../api/orders'
import { computeDiscountAmount, getTab, markTabReceiptPrinted, type PaymentMethod } from '../api/tabs'
import { getMyRestaurant } from '../api/restaurant'
import { formatTableLabel } from '../utils/tableLabel'
import { applyReceiptPrintPageSize } from '../utils/printPageSize'

const currencyFormatter = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

const PAYMENT_METHOD_LABELS: Record<PaymentMethod, string> = {
  PIX: 'Pix',
  CASH: 'Dinheiro',
  DEBIT_CARD: 'Cartão de débito',
  CREDIT_CARD: 'Cartão de crédito',
}

export function TabReceiptPrintPage() {
  const { tabId } = useParams<{ tabId: string }>()
  const [searchParams] = useSearchParams()
  const autoPrint = searchParams.get('auto') === '1'
  const hasAutoPrinted = useRef(false)
  const receiptRef = useRef<HTMLDivElement>(null)

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
    applyReceiptPrintPageSize(receiptRef.current)
    window.print()
  }

  useEffect(() => {
    if (autoPrint && tab && orders && !hasAutoPrinted.current) {
      hasAutoPrinted.current = true
      handlePrint()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [autoPrint, tab, orders])

  if (isTabLoading || isOrdersLoading || !tab) {
    return (
      <div className="min-h-screen bg-white p-6 text-sm text-gray-500" style={{ colorScheme: 'light' }}>
        Carregando...
      </div>
    )
  }

  const items = (orders ?? []).flatMap((order) => order.items)
  const itemsTotal = items
    .filter((item) => item.status !== 'CANCELLED')
    .reduce((sum, item) => sum + item.netSubtotal, 0)
  const tabDiscountAmount = computeDiscountAmount(tab.discountType, tab.discountValue, itemsTotal)
  const total = tab.billTotal ?? itemsTotal - tabDiscountAmount
  const activePayments = tab.payments.filter((payment) => payment.status === 'ACTIVE')

  return (
    <div className="flex min-h-screen justify-center bg-gray-100 py-6 print:block print:min-h-0 print:bg-white print:py-0" style={{ colorScheme: 'light' }}>
      <div className="h-fit w-[80mm] bg-white p-3 font-mono text-gray-800 shadow print:shadow-none" style={{ colorScheme: 'light' }}>
        <Link to="/checkout" className="mb-4 block text-sm font-sans text-gray-500 hover:underline print:hidden">
          ← Voltar
        </Link>

        <div ref={receiptRef}>
        <div className="text-center">
          <h1 className="text-base font-bold uppercase tracking-wide">
            {restaurant?.tradeName || restaurant?.name}
          </h1>
          <p className="mt-0.5 text-xs text-gray-600">
            {formatTableLabel(tab.tables.map((t) => t.number))}
          </p>
        </div>

        <div className="my-3 border-t border-dashed border-gray-400" />

        <ul className="divide-y divide-dashed divide-gray-300">
          {items.map((item) => (
            <li key={item.id} className={item.status === 'CANCELLED' ? 'text-gray-400' : ''}>
              <div className={`flex items-start justify-between gap-2 py-1.5 text-xs ${item.status === 'CANCELLED' ? 'line-through' : ''}`}>
                <span>
                  {item.quantity}x {item.productName}
                  {item.isComboHeader && <span className="ml-1 font-normal text-gray-500">(combo)</span>}
                  {item.modifiers.length > 0 && ` (${item.modifiers.map((modifier) => modifier.optionName).join(', ')})`}
                  {item.discountType && item.status !== 'CANCELLED' && (
                    <span className="block text-[11px] text-gray-500">
                      Desconto: -{currencyFormatter.format(item.discountAmount)}
                      {item.discountReason && ` (${item.discountReason})`}
                    </span>
                  )}
                </span>
                <span className="shrink-0 tabular-nums">
                  {currencyFormatter.format(item.status === 'CANCELLED' ? item.subtotal : item.netSubtotal)}
                </span>
              </div>
              {item.children.length > 0 && (
                <ul className="pb-1.5 pl-4">
                  {item.children.map((child) => (
                    <li key={child.id} className="text-[11px] text-gray-500">
                      {child.quantity}x {child.productName}
                      {child.modifiers.length > 0 && ` (${child.modifiers.map((modifier) => modifier.optionName).join(', ')})`}
                    </li>
                  ))}
                </ul>
              )}
            </li>
          ))}
        </ul>

        <div className="border-t border-dashed border-gray-400 pt-2" />

        {tab.discountType && (
          <div className="flex items-center justify-between py-0.5 text-xs text-gray-600">
            <span>
              Desconto na comanda
              {tab.discountReason && ` (${tab.discountReason})`}
            </span>
            <span className="tabular-nums">-{currencyFormatter.format(tabDiscountAmount)}</span>
          </div>
        )}

        {tab.serviceChargePercentage != null && (
          <div className="flex items-center justify-between py-0.5 text-xs text-gray-600">
            <span>Taxa de serviço ({tab.serviceChargePercentage}%)</span>
            <span className="tabular-nums">{currencyFormatter.format(tab.serviceChargeAmount ?? 0)}</span>
          </div>
        )}

        <div className="mt-2 flex items-center justify-between border-t-2 border-gray-800 pt-2 text-sm font-bold">
          <span>Total</span>
          <span className="tabular-nums">{currencyFormatter.format(total)}</span>
        </div>

        {activePayments.length > 0 && (
          <div className="mt-1.5 space-y-0.5 text-xs text-gray-600">
            {activePayments.map((payment) => (
              <div key={payment.id} className="flex items-center justify-between gap-2">
                <span>Pago via {PAYMENT_METHOD_LABELS[payment.paymentMethod]}</span>
                <span className="tabular-nums">{currencyFormatter.format(payment.amount)}</span>
              </div>
            ))}
          </div>
        )}

        <p className="mt-4 text-center text-[11px] text-gray-400">Obrigado pela preferência!</p>
        </div>

        <button
          type="button"
          onClick={handlePrint}
          className="mt-6 w-full rounded-md bg-brand-600 px-3 py-2 font-sans text-sm font-medium text-white hover:bg-brand-700 print:hidden"
        >
          Imprimir
        </button>
      </div>
    </div>
  )
}
