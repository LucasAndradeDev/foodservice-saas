import { useQuery } from '@tanstack/react-query'
import { motion } from 'framer-motion'
import {
  ArrowRight,
  CheckCircle2,
  Flame,
  LayoutDashboard,
  Receipt,
  Sparkles,
  Table2,
  TrendingUp,
  UtensilsCrossed,
  Wallet,
  type LucideIcon,
} from 'lucide-react'
import { Link } from 'react-router-dom'
import { getDashboard } from '../api/dashboard'
import { getReportSummary } from '../api/reports'
import { useAuth } from '../auth/AuthContext'
import { Card } from '../components/Card'
import { PageHeader } from '../components/PageHeader'
import { ChangeLine, StatTile } from '../components/StatTile'
import { TableHead, TableRow } from '../components/Table'
import { toDateInputValue } from '../utils/calendarGrid'

const currencyFormatter = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

const containerVariants = {
  hidden: {},
  visible: { transition: { staggerChildren: 0.06 } },
}

const itemVariants = {
  hidden: { opacity: 0, y: 10 },
  visible: { opacity: 1, y: 0 },
}

interface SimpleStatTileProps {
  icon: LucideIcon
  label: string
  value: number
  accentClassName: string
}

function SimpleStatTile({ icon: Icon, label, value, accentClassName }: SimpleStatTileProps) {
  return (
    <div className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm transition-all duration-200 hover:-translate-y-0.5 hover:shadow-md dark:border-white/10 dark:bg-stone-900">
      <div className="flex items-center gap-1.5 text-sm text-gray-500 dark:text-stone-400">
        <Icon className="h-4 w-4" />
        {label}
      </div>
      <div className={`mt-1 text-3xl font-semibold ${accentClassName}`}>{value}</div>
    </div>
  )
}

interface FirstStepProps {
  icon: LucideIcon
  title: string
  description: string
  to: string
  ctaLabel: string
}

function FirstStep({ icon: Icon, title, description, to, ctaLabel }: FirstStepProps) {
  return (
    <div className="flex flex-col gap-3 rounded-xl border border-gray-200 bg-white p-4 sm:flex-row sm:items-center sm:justify-between dark:border-white/10 dark:bg-stone-900/60">
      <div className="flex items-start gap-3">
        <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-brand-50 text-brand-700 dark:bg-brand-500/10 dark:text-brand-400">
          <Icon className="h-4.5 w-4.5" />
        </span>
        <div>
          <p className="text-sm font-medium text-gray-800 dark:text-white">{title}</p>
          <p className="text-sm text-gray-500 dark:text-stone-400">{description}</p>
        </div>
      </div>
      <Link
        to={to}
        className="flex shrink-0 items-center justify-center gap-1.5 rounded-lg bg-brand-600 px-3.5 py-2 text-sm font-medium text-white shadow-sm transition hover:bg-brand-700 sm:justify-start"
      >
        {ctaLabel}
        <ArrowRight className="h-4 w-4" />
      </Link>
    </div>
  )
}

function GettingStartedCard() {
  return (
    <motion.div
      variants={itemVariants}
      className="mb-6 rounded-2xl border border-dashed border-brand-300 bg-brand-50/40 p-5 dark:border-brand-500/30 dark:bg-brand-500/5"
    >
      <div className="mb-4 flex items-center gap-2">
        <Sparkles className="h-5 w-5 text-brand-700 dark:text-brand-400" />
        <h2 className="text-base font-semibold text-gray-800 dark:text-white">Vamos configurar seu restaurante</h2>
      </div>
      <p className="mb-4 text-sm text-gray-500 dark:text-stone-400">
        Ainda não encontramos mesas cadastradas — comece por aqui pra deixar tudo pronto pra receber os primeiros pedidos.
      </p>
      <div className="space-y-3">
        <FirstStep
          icon={UtensilsCrossed}
          title="Cadastre seu cardápio"
          description="Importe automaticamente de um PDF ou foto do cardápio, ou cadastre os produtos manualmente."
          to="/products/import"
          ctaLabel="Importar cardápio"
        />
        <FirstStep
          icon={Table2}
          title="Cadastre as mesas do salão"
          description="Organize as mesas por área para começar a abrir comandas."
          to="/tables"
          ctaLabel="Cadastrar mesas"
        />
      </div>
    </motion.div>
  )
}

export function DashboardPage() {
  const { user } = useAuth()
  const canSeeTrends = user?.role === 'OWNER' || user?.role === 'MANAGER'
  const today = toDateInputValue(new Date())

  const { data, isLoading } = useQuery({
    queryKey: ['dashboard'],
    queryFn: getDashboard,
    refetchInterval: 15000,
  })

  const { data: summary } = useQuery({
    queryKey: ['reports', 'summary', today, today],
    queryFn: () => getReportSummary(today, today),
    enabled: canSeeTrends,
    refetchInterval: 15000,
  })

  if (isLoading || !data) {
    return <p className="text-sm text-gray-500 dark:text-stone-400">Carregando...</p>
  }

  return (
    <motion.div variants={containerVariants} initial="hidden" animate="visible">
      <motion.div
        variants={itemVariants}
        className="mb-5 rounded-2xl border border-gray-200 bg-white p-5 shadow-sm dark:border-white/10 dark:bg-stone-900"
      >
        <PageHeader icon={LayoutDashboard} title="Dashboard" />
      </motion.div>

      {canSeeTrends && data.freeTables === 0 && data.occupiedTables === 0 && <GettingStartedCard />}

      <motion.div
        variants={itemVariants}
        className="mb-6 rounded-xl border border-gray-200 bg-white p-6 shadow-sm dark:border-white/10 dark:bg-stone-900"
      >
        <div className="flex items-center gap-1.5 text-sm text-gray-500 dark:text-stone-400">
          <Wallet className="h-4 w-4" />
          Faturamento hoje
        </div>
        <div className="mt-1 text-4xl font-semibold text-brand-700 sm:text-5xl dark:text-brand-400">
          {currencyFormatter.format(data.revenueToday)}
        </div>
        {summary && summary.comparison.revenueChangePercentage !== null && (
          <ChangeLine
            changePercentage={summary.comparison.revenueChangePercentage}
            previousValueLabel={currencyFormatter.format(summary.comparison.previousTotalRevenue)}
            previousRangeLabel="ontem"
          />
        )}
      </motion.div>

      <motion.div variants={itemVariants} className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <SimpleStatTile
          icon={CheckCircle2}
          label="Mesas livres"
          value={data.freeTables}
          accentClassName="text-sage-700 dark:text-sage-400"
        />
        <SimpleStatTile
          icon={Flame}
          label="Mesas ocupadas"
          value={data.occupiedTables}
          accentClassName="text-brand-700 dark:text-brand-400"
        />
        <SimpleStatTile
          icon={UtensilsCrossed}
          label="Pedidos em preparo"
          value={data.ordersInPreparation}
          accentClassName="text-gold-700 dark:text-gold-400"
        />
      </motion.div>

      {canSeeTrends && summary && (
        <>
          <motion.div variants={itemVariants} className="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-2">
            <StatTile
              icon={Receipt}
              label="Comandas fechadas hoje"
              value={String(summary.closedTabsCount)}
              changePercentage={summary.comparison.closedTabsChangePercentage}
              previousValueLabel={String(summary.comparison.previousClosedTabsCount)}
              previousRangeLabel="ontem"
            />
            <StatTile
              icon={TrendingUp}
              label="Ticket médio hoje"
              value={currencyFormatter.format(summary.averageTicket)}
              changePercentage={summary.comparison.averageTicketChangePercentage}
              previousValueLabel={currencyFormatter.format(summary.comparison.previousAverageTicket)}
              previousRangeLabel="ontem"
            />
          </motion.div>

          <motion.div variants={itemVariants}>
            <Card className="mt-6 overflow-hidden">
              <h2 className="border-b border-gray-100 px-4 py-3 text-sm font-medium text-gray-700 dark:border-white/10 dark:text-stone-300">
                Produtos mais vendidos hoje
              </h2>
              {summary.topProducts.length === 0 ? (
                <p className="px-4 py-3 text-sm text-gray-500 dark:text-stone-400">Nenhuma venda hoje ainda.</p>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <TableHead>
                      <tr>
                        <th className="px-4 py-2 font-medium">Produto</th>
                        <th className="px-4 py-2 font-medium">Qtd.</th>
                        <th className="px-4 py-2 font-medium">Faturamento</th>
                      </tr>
                    </TableHead>
                    <tbody>
                      {summary.topProducts.slice(0, 5).map((product) => (
                        <TableRow key={product.productId}>
                          <td className="px-4 py-2 text-gray-800 dark:text-white">{product.productName}</td>
                          <td className="px-4 py-2 text-gray-600 dark:text-stone-400">{product.quantitySold}</td>
                          <td className="px-4 py-2 text-gray-600 dark:text-stone-400">{currencyFormatter.format(product.revenue)}</td>
                        </TableRow>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </Card>
          </motion.div>
        </>
      )}
    </motion.div>
  )
}
