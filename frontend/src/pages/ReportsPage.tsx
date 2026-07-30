import { useQuery } from '@tanstack/react-query'
import { BarChart3, Calendar, Receipt, TrendingDown, TrendingUp, Wallet, type LucideIcon } from 'lucide-react'
import { useState } from 'react'
import { getPeakHours, getReportSummary } from '../api/reports'
import { FeedbackCard } from './reports/FeedbackCard'
import { MonthlyGoalCard } from './reports/MonthlyGoalCard'
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

function parseDateInput(value: string) {
  const [year, month, day] = value.split('-').map(Number)
  return new Date(year, month - 1, day)
}

function addDays(value: string, delta: number) {
  const date = parseDateInput(value)
  date.setDate(date.getDate() + delta)
  return toDateInputValue(date)
}

function daysBetweenInclusive(start: string, end: string) {
  const startDate = parseDateInput(start)
  const endDate = parseDateInput(end)
  return Math.round((endDate.getTime() - startDate.getTime()) / 86400000) + 1
}

// Rule A: the same number of days immediately before the selected range — always an
// equal-length, apples-to-apples window, even if it has to borrow days from an earlier
// month (see MonthlyGoalCard/backend ReportService for the full rationale).
function getPreviousPeriodRange(start: string, end: string) {
  const periodDays = daysBetweenInclusive(start, end)
  const previousEnd = addDays(start, -1)
  const previousStart = addDays(previousEnd, -(periodDays - 1))
  return { start: previousStart, end: previousEnd }
}

function formatDateShort(value: string, referenceYear: number) {
  const [year, month, day] = value.split('-')
  return Number(year) === referenceYear ? `${day}/${month}` : `${day}/${month}/${year}`
}

interface StatTileProps {
  icon: LucideIcon
  label: string
  value: string
  changePercentage?: number | null
  previousValueLabel?: string
  previousRangeLabel?: string
}

function ChangeLine({
  changePercentage,
  previousValueLabel,
  previousRangeLabel,
}: {
  changePercentage: number
  previousValueLabel: string
  previousRangeLabel: string
}) {
  const isPositive = changePercentage >= 0
  const Icon = isPositive ? TrendingUp : TrendingDown
  return (
    <div className={`mt-1 flex items-start gap-1 text-xs ${isPositive ? 'text-green-600' : 'text-red-600'}`}>
      <Icon className="mt-0.5 h-3 w-3 shrink-0" />
      <span>
        {Math.abs(changePercentage)}% vs {previousValueLabel}
        <span className="text-gray-400"> ({previousRangeLabel})</span>
      </span>
    </div>
  )
}

function StatTile({ icon: Icon, label, value, changePercentage, previousValueLabel, previousRangeLabel }: StatTileProps) {
  return (
    <div className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm">
      <div className="flex items-center gap-1.5 text-sm text-gray-500">
        <Icon className="h-4 w-4" />
        {label}
      </div>
      <div className="mt-1 text-3xl font-semibold text-brand-700">{value}</div>
      {changePercentage !== null && changePercentage !== undefined && previousValueLabel && previousRangeLabel && (
        <ChangeLine
          changePercentage={changePercentage}
          previousValueLabel={previousValueLabel}
          previousRangeLabel={previousRangeLabel}
        />
      )}
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

  const previousPeriod = getPreviousPeriodRange(start, end)
  const referenceYear = parseDateInput(start).getFullYear()
  const previousRangeLabel = `${formatDateShort(previousPeriod.start, referenceYear)} a ${formatDateShort(previousPeriod.end, referenceYear)}`

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
            <StatTile
              icon={Wallet}
              label="Faturamento total"
              value={currencyFormatter.format(data.totalRevenue)}
              changePercentage={data.comparison.revenueChangePercentage}
              previousValueLabel={currencyFormatter.format(data.comparison.previousTotalRevenue)}
              previousRangeLabel={previousRangeLabel}
            />
            <StatTile
              icon={Receipt}
              label="Comandas fechadas"
              value={String(data.closedTabsCount)}
              changePercentage={data.comparison.closedTabsChangePercentage}
              previousValueLabel={String(data.comparison.previousClosedTabsCount)}
              previousRangeLabel={previousRangeLabel}
            />
            <StatTile
              icon={TrendingUp}
              label="Ticket médio"
              value={currencyFormatter.format(data.averageTicket)}
              changePercentage={data.comparison.averageTicketChangePercentage}
              previousValueLabel={currencyFormatter.format(data.comparison.previousAverageTicket)}
              previousRangeLabel={previousRangeLabel}
            />
          </div>

          <div className="mb-6">
            <MonthlyGoalCard />
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
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead className="bg-gray-50 text-left text-gray-500">
                      <tr>
                        <th className="px-4 py-2 font-medium">Produto</th>
                        <th className="px-4 py-2 font-medium">Qtd.</th>
                        <th className="px-4 py-2 font-medium">Faturamento</th>
                        <th className="px-4 py-2 font-medium">Margem</th>
                        <th className="px-4 py-2 font-medium">Margem %</th>
                      </tr>
                    </thead>
                    <tbody>
                      {data.topProducts.map((product) => {
                        const hasCostData = product.marginPercentage !== null
                        const partialCostData = hasCostData && product.costQuantityCovered < product.quantitySold
                        return (
                          <tr key={product.productId} className="border-t border-gray-100">
                            <td className="px-4 py-2 text-gray-800">{product.productName}</td>
                            <td className="px-4 py-2 text-gray-600">{product.quantitySold}</td>
                            <td className="px-4 py-2 text-gray-600">{currencyFormatter.format(product.revenue)}</td>
                            <td className="px-4 py-2 text-gray-600">
                              {hasCostData ? (
                                <>
                                  {currencyFormatter.format(product.marginTotal)}
                                  {partialCostData && (
                                    <span
                                      title="Preço de custo não cadastrado para todas as vendas do período"
                                      className="ml-1 text-amber-500"
                                    >
                                      *
                                    </span>
                                  )}
                                </>
                              ) : (
                                <span className="text-gray-400">—</span>
                              )}
                            </td>
                            <td className="px-4 py-2 text-gray-600">
                              {hasCostData ? `${product.marginPercentage}%` : <span className="text-gray-400">—</span>}
                            </td>
                          </tr>
                        )
                      })}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </div>

          <div className="mb-6">
            <FeedbackCard start={start} end={end} />
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
