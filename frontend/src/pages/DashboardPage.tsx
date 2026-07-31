import { useQuery } from '@tanstack/react-query'
import { CheckCircle2, Flame, LayoutDashboard, UtensilsCrossed, Wallet, type LucideIcon } from 'lucide-react'
import { getDashboard } from '../api/dashboard'

const currencyFormatter = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

interface StatTileProps {
  icon: LucideIcon
  label: string
  value: number
  accentClassName: string
}

function StatTile({ icon: Icon, label, value, accentClassName }: StatTileProps) {
  return (
    <div className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-white/10 dark:bg-stone-900">
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
    <div>
      <h1 className="mb-4 flex items-center gap-2 rounded-xl border border-gray-200 bg-white p-4 text-lg font-semibold text-gray-800 shadow-xs dark:border-white/10 dark:bg-stone-900 dark:text-white">
        <LayoutDashboard className="h-5 w-5 text-brand-600 dark:text-brand-400" />
        Dashboard
      </h1>

      <div className="mb-6 rounded-xl border border-gray-200 bg-white p-6 shadow-sm dark:border-white/10 dark:bg-stone-900">
        <div className="flex items-center gap-1.5 text-sm text-gray-500 dark:text-stone-400">
          <Wallet className="h-4 w-4" />
          Faturamento hoje
        </div>
        <div className="mt-1 text-4xl font-semibold text-brand-700 sm:text-5xl dark:text-brand-400">
          {currencyFormatter.format(data.revenueToday)}
        </div>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <StatTile
          icon={CheckCircle2}
          label="Mesas livres"
          value={data.freeTables}
          accentClassName="text-green-700 dark:text-green-400"
        />
        <StatTile
          icon={Flame}
          label="Mesas ocupadas"
          value={data.occupiedTables}
          accentClassName="text-red-700 dark:text-red-400"
        />
        <StatTile
          icon={UtensilsCrossed}
          label="Pedidos em preparo"
          value={data.ordersInPreparation}
          accentClassName="text-amber-700 dark:text-amber-400"
        />
      </div>
    </div>
  )
}
