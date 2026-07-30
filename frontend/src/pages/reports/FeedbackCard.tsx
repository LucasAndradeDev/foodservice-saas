import { useQuery } from '@tanstack/react-query'
import { ChevronRight, MessageSquare } from 'lucide-react'
import { Link } from 'react-router-dom'
import { getFeedbackReport } from '../../api/reports'
import { StarRating } from '../../components/StarRating'
import { formatTableLabel } from '../../utils/tableLabel'

const dateFormatter = new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })

interface FeedbackCardProps {
  start: string
  end: string
}

export function FeedbackCard({ start, end }: FeedbackCardProps) {
  const { data } = useQuery({
    queryKey: ['reports', 'feedback', start, end],
    queryFn: () => getFeedbackReport(start, end),
  })

  return (
    <div className="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm">
      <h2 className="flex items-center gap-2 border-b border-gray-100 px-4 py-3 text-sm font-medium text-gray-700">
        <MessageSquare className="h-4 w-4 text-brand-600" />
        Avaliação pós-refeição
      </h2>

      {!data || data.totalCount === 0 ? (
        <p className="px-4 py-3 text-sm text-gray-500">Nenhuma avaliação recebida no período.</p>
      ) : (
        <>
          <div className="flex items-center gap-3 border-b border-gray-100 px-4 py-3">
            <StarRating value={data.averageRating ?? 0} readOnly />
            <span className="text-sm text-gray-600">
              {data.averageRating?.toFixed(1)} de 5 · {data.totalCount} avaliaç{data.totalCount === 1 ? 'ão' : 'ões'}
            </span>
          </div>
          <ul className="divide-y divide-gray-100">
            {data.recent.map((entry, index) => (
              <li key={index} className="px-4 py-3">
                <div className="mb-1 flex items-center justify-between gap-2">
                  <StarRating value={entry.rating} readOnly />
                  <span className="shrink-0 text-xs text-gray-400">
                    {formatTableLabel(entry.tableNumbers)} · {dateFormatter.format(new Date(entry.createdAt))}
                  </span>
                </div>
                {entry.comment && <p className="text-sm text-gray-700">{entry.comment}</p>}
              </li>
            ))}
          </ul>
          {data.totalCount > data.recent.length && (
            <Link
              to={`/reports/feedback?start=${start}&end=${end}`}
              className="flex items-center justify-center gap-1 border-t border-gray-100 px-4 py-2.5 text-sm font-medium text-brand-600 hover:bg-gray-50 hover:text-brand-700"
            >
              Ver todas as avaliações
              <ChevronRight className="h-4 w-4" />
            </Link>
          )}
        </>
      )}
    </div>
  )
}
