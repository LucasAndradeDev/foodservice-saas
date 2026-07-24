import { useQuery } from '@tanstack/react-query'
import { getDashboard } from '../api/dashboard'

const currencyFormatter = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

interface StatTileProps {
  label: string
  value: number
  accentClassName: string
}

function StatTile({ label, value, accentClassName }: StatTileProps) {
  return (
    <div className="rounded-lg border border-gray-200 bg-white p-4">
      <div className="text-sm text-gray-500">{label}</div>
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
    return <p className="text-sm text-gray-500">Carregando...</p>
  }

  return (
    <div>
      <h1 className="mb-4 text-lg font-semibold text-gray-800">Dashboard</h1>

      <div className="mb-6 rounded-lg border border-gray-200 bg-white p-6">
        <div className="text-sm text-gray-500">Faturamento hoje</div>
        <div className="mt-1 text-5xl font-semibold text-gray-900">
          {currencyFormatter.format(data.revenueToday)}
        </div>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <StatTile label="Mesas livres" value={data.freeTables} accentClassName="text-green-700" />
        <StatTile label="Mesas ocupadas" value={data.occupiedTables} accentClassName="text-red-700" />
        <StatTile label="Pedidos em preparo" value={data.ordersInPreparation} accentClassName="text-amber-700" />
      </div>
    </div>
  )
}
