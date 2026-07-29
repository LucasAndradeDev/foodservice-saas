import { useQuery } from '@tanstack/react-query'
import { BarChart3, Calendar, Receipt, TrendingUp, Wallet, type LucideIcon } from 'lucide-react'
import { useState } from 'react'
import { getPeakHours, getReportSummary } from '../api/reports'
import { PeakHoursHeatmap } from './reports/PeakHoursHeatmap'
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
  icon: LucideIcon
  label: string
  value: string
}

function StatTile({ icon: Icon, label, value }: StatTileProps) {
  return (
    <div className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm">
      <div className="flex items-center gap-1.5 text-sm text-gray-500">
        <Icon className="h-4 w-4" />
        {label}
      </div>
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

  const { data: peakHours } = useQuery({
    queryKey: ['reports', 'peak-hours', start, end],
    queryFn: () => getPeakHours(start, end),
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

  const sevenDaysAgo = new Date()
  sevenDaysAgo.setDate(sevenDaysAgo.getDate() - 6)
  const presetRanges = {
    today: { start: today, end: today },
    last7days: { start: toDateInputValue(sevenDaysAgo), end: today },
    thisMonth: { start: toDateInputValue(startOfMonth(new Date())), end: today },
  } as const
  const activePreset = (Object.keys(presetRanges) as Array<keyof typeof presetRanges>).find(
    (preset) => presetRanges[preset].start === start && presetRanges[preset].end === end,
  )

  function presetButtonClasses(preset: keyof typeof presetRanges) {
    return `rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
      activePreset === preset
        ? 'bg-brand-600 text-white shadow-sm'
        : 'border border-gray-200 bg-gray-50 text-gray-700 hover:bg-gray-100'
    }`
  }

  return (
    <div>
      <div className="mb-6 rounded-xl border border-gray-200 bg-white p-4 shadow-xs">
        <h1 className="mb-4 flex items-center gap-2 text-lg font-semibold text-gray-800">
          <BarChart3 className="h-5 w-5 text-brand-600" />
          Relatórios
        </h1>

        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex flex-wrap gap-2">
            <button type="button" onClick={() => applyPreset('today')} className={presetButtonClasses('today')}>
              Hoje
            </button>
            <button type="button" onClick={() => applyPreset('last7days')} className={presetButtonClasses('last7days')}>
              Últimos 7 dias
            </button>
            <button type="button" onClick={() => applyPreset('thisMonth')} className={presetButtonClasses('thisMonth')}>
              Este mês
            </button>
          </div>

          <div className="flex items-center gap-2 rounded-lg border border-gray-200 bg-gray-50 px-3 py-2 transition-colors focus-within:border-brand-500 focus-within:bg-white">
            <Calendar className="h-4 w-4 shrink-0 text-gray-400" />
            <input
              type="date"
              value={start}
              max={end}
              onChange={(e) => setStart(e.target.value)}
              className="bg-transparent text-sm text-gray-700 focus:outline-none"
            />
            <span className="text-sm text-gray-400">até</span>
            <input
              type="date"
              value={end}
              min={start}
              max={today}
              onChange={(e) => setEnd(e.target.value)}
              className="bg-transparent text-sm text-gray-700 focus:outline-none"
            />
          </div>
        </div>
      </div>

      {isLoading || !data ? (
        <p className="text-sm text-gray-500">Carregando...</p>
      ) : (
        <>
          <div className="mb-6 grid grid-cols-1 gap-4 sm:grid-cols-3">
            <StatTile icon={Wallet} label="Faturamento total" value={currencyFormatter.format(data.totalRevenue)} />
            <StatTile icon={Receipt} label="Comandas fechadas" value={String(data.closedTabsCount)} />
            <StatTile icon={TrendingUp} label="Ticket médio" value={currencyFormatter.format(data.averageTicket)} />
          </div>

          <div className="mb-6 grid grid-cols-1 gap-4 lg:grid-cols-2">
            <div className="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm">
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

            <div className="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm">
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

          {peakHours && (
            <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
              <PeakHoursHeatmap
                title="Ocupação de mesas por dia e hora"
                cells={peakHours.cells}
                metric="avgOccupiedTables"
                unitLabel="mesas"
                rangeStart={start}
                rangeEnd={end}
              />
              <PeakHoursHeatmap
                title="Pedidos por dia e hora"
                cells={peakHours.cells}
                metric="avgOrderCount"
                unitLabel="pedidos"
                rangeStart={start}
                rangeEnd={end}
              />
            </div>
          )}
        </>
      )}
    </div>
  )
}
