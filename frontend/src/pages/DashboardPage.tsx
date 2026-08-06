import { useQuery } from '@tanstack/react-query'
import { motion } from 'framer-motion'
import { CheckCircle2, Flame, LayoutDashboard, UtensilsCrossed, Wallet, type LucideIcon } from 'lucide-react'
import { getDashboard } from '../api/dashboard'

const currencyFormatter = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

const containerVariants = {
  hidden: {},
  visible: { transition: { staggerChildren: 0.06 } },
}

const itemVariants = {
  hidden: { opacity: 0, y: 10 },
  visible: { opacity: 1, y: 0 },
}

interface StatTileProps {
  icon: LucideIcon
  label: string
  value: number
  accentClassName: string
}

function StatTile({ icon: Icon, label, value, accentClassName }: StatTileProps) {
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

export function DashboardPage() {
  const { data, isLoading } = useQuery({
    queryKey: ['dashboard'],
    queryFn: getDashboard,
    refetchInterval: 15000,
  })

  if (isLoading || !data) {
    return <p className="text-sm text-gray-500 dark:text-stone-400">Carregando...</p>
  }

  return (
    <motion.div variants={containerVariants} initial="hidden" animate="visible">
      <motion.div
        variants={itemVariants}
        className="mb-5 flex items-center gap-3 rounded-2xl border border-gray-200 bg-white p-5 shadow-sm dark:border-white/10 dark:bg-stone-900"
      >
        <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-brand-600 text-white shadow-sm">
          <LayoutDashboard className="h-5 w-5" />
        </span>
        <h1 className="text-lg font-bold text-gray-900 dark:text-white">Dashboard</h1>
      </motion.div>

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
      </motion.div>

      <motion.div variants={itemVariants} className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <StatTile
          icon={CheckCircle2}
          label="Mesas livres"
          value={data.freeTables}
          accentClassName="text-sage-700 dark:text-sage-400"
        />
        <StatTile
          icon={Flame}
          label="Mesas ocupadas"
          value={data.occupiedTables}
          accentClassName="text-brand-700 dark:text-brand-400"
        />
        <StatTile
          icon={UtensilsCrossed}
          label="Pedidos em preparo"
          value={data.ordersInPreparation}
          accentClassName="text-gold-700 dark:text-gold-400"
        />
      </motion.div>
    </motion.div>
  )
}
