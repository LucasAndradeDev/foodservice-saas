import { useQuery } from '@tanstack/react-query'
import { Users } from 'lucide-react'
import { getWaiterPerformance } from '../../api/reports'
import { Card } from '../../components/Card'
import { TableHead, TableRow } from '../../components/Table'

const currencyFormatter = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

interface WaiterPerformanceCardProps {
  start: string
  end: string
}

export function WaiterPerformanceCard({ start, end }: WaiterPerformanceCardProps) {
  const { data } = useQuery({
    queryKey: ['reports', 'waiter-performance', start, end],
    queryFn: () => getWaiterPerformance(start, end),
  })

  return (
    <Card className="overflow-hidden">
      <h2 className="flex items-center gap-2 border-b border-gray-100 px-4 py-3 text-sm font-medium text-gray-700 dark:border-white/10 dark:text-stone-300">
        <Users className="h-4 w-4 text-brand-600 dark:text-brand-400" />
        Desempenho por garçom
      </h2>

      {!data || data.rows.length === 0 ? (
        <p className="px-4 py-3 text-sm text-gray-500 dark:text-stone-400">Nenhum pedido entregue no período.</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <TableHead>
              <tr>
                <th className="px-4 py-2 font-medium">Garçom</th>
                <th className="px-4 py-2 font-medium">Pedidos</th>
                <th className="px-4 py-2 font-medium">Vendas</th>
                <th className="px-4 py-2 font-medium">Tempo médio</th>
              </tr>
            </TableHead>
            <tbody>
              {data.rows.map((row) => {
                const isSelfService = row.waiterId === null
                return (
                  <TableRow
                    key={row.waiterId ?? 'self-service'}
                    className={isSelfService ? 'text-gray-500 italic dark:text-stone-400' : ''}
                  >
                    <td className="px-4 py-2 text-gray-800 dark:text-white">
                      {isSelfService ? 'Autoatendimento' : row.waiterName}
                    </td>
                    <td className="px-4 py-2 text-gray-600 dark:text-stone-400">{row.orderCount}</td>
                    <td className="px-4 py-2 text-gray-600 dark:text-stone-400">{currencyFormatter.format(row.totalSales)}</td>
                    <td className="px-4 py-2 text-gray-600 dark:text-stone-400">
                      {row.averageServiceTimeMinutes === null ? (
                        <span className="text-gray-400 dark:text-stone-500">—</span>
                      ) : (
                        `${row.averageServiceTimeMinutes} min`
                      )}
                    </td>
                  </TableRow>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </Card>
  )
}
