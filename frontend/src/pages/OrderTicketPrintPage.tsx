import { useMutation, useQuery } from '@tanstack/react-query'
import { useEffect, useRef } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { getOrder, markOrderPrinted } from '../api/orders'
import { getTab } from '../api/tabs'
import { getMyRestaurant } from '../api/restaurant'

export function OrderTicketPrintPage() {
  const { orderId } = useParams<{ orderId: string }>()
  const [searchParams] = useSearchParams()
  const autoPrint = searchParams.get('auto') === '1'
  const hasAutoPrinted = useRef(false)

  const { data: order, isLoading: isOrderLoading } = useQuery({
    queryKey: ['orders', orderId],
    queryFn: () => getOrder(orderId!),
    enabled: !!orderId,
  })

  const { data: tab } = useQuery({
    queryKey: ['tabs', order?.tabId],
    queryFn: () => getTab(order!.tabId),
    enabled: !!order,
  })

  const { data: restaurant } = useQuery({
    queryKey: ['restaurant'],
    queryFn: getMyRestaurant,
  })

  const markPrintedMutation = useMutation({
    mutationFn: () => markOrderPrinted(orderId!),
  })

  function handlePrint() {
    markPrintedMutation.mutate()
    window.print()
  }

  useEffect(() => {
    if (autoPrint && order && tab && !hasAutoPrinted.current) {
      hasAutoPrinted.current = true
      handlePrint()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [autoPrint, order, tab])

  if (isOrderLoading || !order) {
    return <p className="p-6 text-sm text-gray-500">Carregando...</p>
  }

  return (
    <div className="mx-auto max-w-sm p-6">
      <Link to={`/tabs/${order.tabId}`} className="mb-4 block text-sm text-gray-500 hover:underline print:hidden">
        ← Voltar
      </Link>

      <div className="text-center">
        <h1 className="text-base font-semibold text-gray-800">
          {restaurant?.tradeName || restaurant?.name}
        </h1>
        <p className="text-sm text-gray-600">
          Mesa{tab && tab.tables.length > 1 ? 's' : ''} {tab?.tables.map((t) => t.number).join(', ')}
        </p>
        <p className="text-xs text-gray-500">
          {new Date(order.createdAt).toLocaleString('pt-BR')}
        </p>
      </div>

      <ul className="mt-4 divide-y divide-gray-200 border-t border-gray-300">
        {order.items.map((item) => (
          <li key={item.id} className="py-2 text-sm">
            <div className="font-medium text-gray-800">
              {item.quantity}x {item.productName}
            </div>
            {item.observation && <div className="text-gray-500">Obs: {item.observation}</div>}
          </li>
        ))}
      </ul>

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
