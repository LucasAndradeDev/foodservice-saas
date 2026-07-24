import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { getReportSummary } from '../api/reports'
import type { PaymentMethod } from '../api/tabs'

const currencyFormatter = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

const PAYMENT_METHOD_LABELS: Record<PaymentMethod, string> = {
  PIX: 'Pix',
  CASH: 'Dinheiro',
  DEBIT_CARD: 'Cartão de débito',
  CREDIT_CARD: 'Cartão de crédito',
}

function toDateInputValue(date: Date) {
  return date.toISOString().slice(0, 10)
}

function startOfMonth(date: Date) {
  return new Date(date.getFullYear(), date.getMonth(), 1)
}

interface StatTileProps {
  label: string
  value: string
}

function StatTile({ label, value }: StatTileProps) {
  return (
    <div className="rounded-lg border border-gray-200 bg-white p-4">
      <div className="text-sm text-gray-500">{label}</div>
      <div className="mt-1 text-3xl font-semibold text-brand-700">{value}</div>
    </div>
  )
}

export function ReportsPage() {
  const today = toDateInputValue(new Date())
  const [start, setStart] = useState(today)
  const [end, setEnd] = useState(today)

  const { data, isLoading } = useQuery({
    queryKey: ['reports', 'summary', start, end],
    queryFn: () => getReportSummary(start, end),
  })

  function applyPreset(preset: 'today' | 'last7days' | 'thisMonth') {
    const now = new Date()
    if (preset === 'today') {
      setStart(today)
      setEnd(today)
    } else if (preset === 'last7days') {
      const sevenDaysAgo = new Date(now)
      sevenDaysAgo.setDate(now.getDate() - 6)
      setStart(toDateInputValue(sevenDaysAgo))
      setEnd(today)
    } else {
      setStart(toDateInputValue(startOfMonth(now)))
      setEnd(today)
    }
  }

  return (
    <div>
      <h1 className="mb-4 text-lg font-semibold text-gray-800">Relatórios</h1>

      <div className="mb-6 flex flex-col gap-3 rounded-lg border border-gray-200 bg-white p-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            onClick={() => applyPreset('today')}
            className="rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-100"
          >
            Hoje
          </button>
          <button
            type="button"
            onClick={() => applyPreset('last7days')}
            className="rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-100"
          >
            Últimos 7 dias
          </button>
          <button
            type="button"
            onClick={() => applyPreset('thisMonth')}
            className="rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-100"
          >
            Este mês
          </button>
        </div>

        <div className="flex items-center gap-2">
          <input
            type="date"
            value={start}
            max={end}
            onChange={(e) => setStart(e.target.value)}
            className="rounded-md border border-gray-300 px-3 py-1.5 text-sm focus:border-brand-500 focus:outline-none"
          />
          <span className="text-gray-400">até</span>
          <input
            type="date"
            value={end}
            min={start}
            max={today}
            onChange={(e) => setEnd(e.target.value)}
            className="rounded-md border border-gray-300 px-3 py-1.5 text-sm focus:border-brand-500 focus:outline-none"
          />
        </div>
      </div>

      {isLoading || !data ? (
        <p className="text-sm text-gray-500">Carregando...</p>
      ) : (
        <>
          <div className="mb-6 grid grid-cols-1 gap-4 sm:grid-cols-3">
            <StatTile label="Faturamento total" value={currencyFormatter.format(data.totalRevenue)} />
            <StatTile label="Comandas fechadas" value={String(data.closedTabsCount)} />
            <StatTile label="Ticket médio" value={currencyFormatter.format(data.averageTicket)} />
          </div>

          <div className="mb-6 grid grid-cols-1 gap-4 lg:grid-cols-2">
            <div className="overflow-hidden rounded-lg border border-gray-200 bg-white">
              <h2 className="border-b border-gray-100 px-4 py-3 text-sm font-medium text-gray-700">
                Faturamento por forma de pagamento
              </h2>
              {data.byPaymentMethod.length === 0 ? (
                <p className="px-4 py-3 text-sm text-gray-500">Nenhuma comanda fechada no período.</p>
              ) : (
                <table className="w-full text-sm">
                  <thead className="bg-gray-50 text-left text-gray-500">
                    <tr>
                      <th className="px-4 py-2 font-medium">Forma de pagamento</th>
                      <th className="px-4 py-2 font-medium">Comandas</th>
                      <th className="px-4 py-2 font-medium">Total</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.byPaymentMethod.map((row) => (
                      <tr key={row.paymentMethod} className="border-t border-gray-100">
                        <td className="px-4 py-2 text-gray-800">{PAYMENT_METHOD_LABELS[row.paymentMethod]}</td>
                        <td className="px-4 py-2 text-gray-600">{row.tabsCount}</td>
                        <td className="px-4 py-2 text-gray-600">{currencyFormatter.format(row.total)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>

            <div className="overflow-hidden rounded-lg border border-gray-200 bg-white">
              <h2 className="border-b border-gray-100 px-4 py-3 text-sm font-medium text-gray-700">Produtos mais vendidos</h2>
              {data.topProducts.length === 0 ? (
                <p className="px-4 py-3 text-sm text-gray-500">Nenhuma venda no período.</p>
              ) : (
                <table className="w-full text-sm">
                  <thead className="bg-gray-50 text-left text-gray-500">
                    <tr>
                      <th className="px-4 py-2 font-medium">Produto</th>
                      <th className="px-4 py-2 font-medium">Qtd.</th>
                      <th className="px-4 py-2 font-medium">Faturamento</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.topProducts.map((product) => (
                      <tr key={product.productId} className="border-t border-gray-100">
                        <td className="px-4 py-2 text-gray-800">{product.productName}</td>
                        <td className="px-4 py-2 text-gray-600">{product.quantitySold}</td>
                        <td className="px-4 py-2 text-gray-600">{currencyFormatter.format(product.revenue)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          </div>
        </>
      )}
    </div>
  )
}
