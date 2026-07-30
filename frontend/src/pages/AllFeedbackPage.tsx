import { useQuery } from '@tanstack/react-query'
import { ChevronLeft, ChevronRight, MessageSquare } from 'lucide-react'
import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { getFeedbackEntries, getFeedbackReport } from '../api/reports'
import { StarRating } from '../components/StarRating'
import { formatTableLabel } from '../utils/tableLabel'

const PAGE_SIZE = 20
const dateFormatter = new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })

function toDateInputValue(date: Date) {
  return date.toISOString().slice(0, 10)
}

export function AllFeedbackPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const today = toDateInputValue(new Date())
  const thirtyDaysAgo = (() => {
    const date = new Date()
    date.setDate(date.getDate() - 29)
    return toDateInputValue(date)
  })()

  const [start, setStart] = useState(searchParams.get('start') ?? thirtyDaysAgo)
  const [end, setEnd] = useState(searchParams.get('end') ?? today)
  const [page, setPage] = useState(0)

  function updateRange(nextStart: string, nextEnd: string) {
    setStart(nextStart)
    setEnd(nextEnd)
    setPage(0)
    setSearchParams({ start: nextStart, end: nextEnd })
  }

  const { data: summary } = useQuery({
    queryKey: ['reports', 'feedback', start, end],
    queryFn: () => getFeedbackReport(start, end),
  })

  const { data: entriesPage, isLoading } = useQuery({
    queryKey: ['reports', 'feedback', 'entries', start, end, page],
    queryFn: () => getFeedbackEntries(start, end, page, PAGE_SIZE),
  })

  return (
    <div>
      <div className="mb-6 rounded-xl border border-gray-200 bg-white p-4 shadow-xs">
        <h1 className="mb-4 flex items-center gap-2 text-lg font-semibold text-gray-800">
          <MessageSquare className="h-5 w-5 text-brand-600" />
          Avaliações
        </h1>
        <div className="flex w-fit items-center gap-2 rounded-lg border border-gray-200 bg-gray-50 px-3 py-2 transition-colors focus-within:border-brand-500 focus-within:bg-white">
          <input
            type="date"
            value={start}
            max={end}
            onChange={(e) => updateRange(e.target.value, end)}
            className="bg-transparent text-sm text-gray-700 focus:outline-none"
          />
          <span className="text-sm text-gray-400">até</span>
          <input
            type="date"
            value={end}
            min={start}
            max={today}
            onChange={(e) => updateRange(start, e.target.value)}
            className="bg-transparent text-sm text-gray-700 focus:outline-none"
          />
        </div>
      </div>

      {summary && summary.totalCount > 0 && (
        <div className="mb-6 flex items-center gap-3 rounded-xl border border-gray-200 bg-white p-4 shadow-sm">
          <StarRating value={summary.averageRating ?? 0} readOnly />
          <span className="text-sm text-gray-600">
            {summary.averageRating?.toFixed(1)} de 5 · {summary.totalCount} avaliaç{summary.totalCount === 1 ? 'ão' : 'ões'} no
            período
          </span>
        </div>
      )}

      <div className="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm">
        {isLoading || !entriesPage ? (
          <p className="px-4 py-3 text-sm text-gray-500">Carregando...</p>
        ) : entriesPage.entries.length === 0 ? (
          <p className="px-4 py-3 text-sm text-gray-500">Nenhuma avaliação recebida no período.</p>
        ) : (
          <>
            <ul className="divide-y divide-gray-100">
              {entriesPage.entries.map((entry, index) => (
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

            <div className="flex items-center justify-between border-t border-gray-100 px-4 py-3">
              <button
                type="button"
                disabled={page === 0}
                onClick={() => setPage((current) => current - 1)}
                className="flex items-center gap-1 rounded-md border border-gray-200 px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-50 disabled:opacity-40"
              >
                <ChevronLeft className="h-4 w-4" />
                Anterior
              </button>
              <span className="text-sm text-gray-500">
                Página {entriesPage.page + 1} de {entriesPage.totalPages}
              </span>
              <button
                type="button"
                disabled={page + 1 >= entriesPage.totalPages}
                onClick={() => setPage((current) => current + 1)}
                className="flex items-center gap-1 rounded-md border border-gray-200 px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-50 disabled:opacity-40"
              >
                Próxima
                <ChevronRight className="h-4 w-4" />
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  )
}
