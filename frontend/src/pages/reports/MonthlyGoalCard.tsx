import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ChevronLeft, ChevronRight, Pencil, Target } from 'lucide-react'
import { useState } from 'react'
import { getMonthlyGoal, setMonthlyGoal } from '../../api/reports'

const currencyFormatter = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

const MONTH_LABEL_FORMATTER = new Intl.DateTimeFormat('pt-BR', { month: 'long', year: 'numeric' })

function toMonthValue(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-01`
}

function shiftMonth(month: string, delta: number) {
  const [year, monthIndex] = month.split('-').map(Number)
  const date = new Date(year, monthIndex - 1 + delta, 1)
  return toMonthValue(date)
}

function monthLabel(month: string) {
  const [year, monthIndex, day] = month.split('-').map(Number)
  const label = MONTH_LABEL_FORMATTER.format(new Date(year, monthIndex - 1, day))
  return label.charAt(0).toUpperCase() + label.slice(1)
}

export function MonthlyGoalCard() {
  const [month, setMonth] = useState(() => toMonthValue(new Date()))
  const [isEditing, setIsEditing] = useState(false)
  const [goalInput, setGoalInput] = useState('')
  const queryClient = useQueryClient()

  const { data } = useQuery({
    queryKey: ['reports', 'goal', month],
    queryFn: () => getMonthlyGoal(month),
  })

  const mutation = useMutation({
    mutationFn: (revenueGoal: number) => setMonthlyGoal(month, revenueGoal),
    onSuccess: (updated) => {
      queryClient.setQueryData(['reports', 'goal', month], updated)
      setIsEditing(false)
    },
  })

  function startEditing() {
    setGoalInput(data?.revenueGoal ? String(data.revenueGoal) : '')
    setIsEditing(true)
  }

  function handleSave() {
    const value = Number(goalInput)
    if (value > 0) {
      mutation.mutate(value)
    }
  }

  const progress = data?.progressPercentage ?? null
  const progressWidth = progress === null ? 0 : Math.min(progress, 100)
  const progressColor = progress !== null && progress >= 100 ? 'bg-green-500' : 'bg-brand-600'

  return (
    <div className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-1.5 text-sm font-medium text-gray-700">
          <Target className="h-4 w-4 text-brand-600" />
          Meta do mês
        </div>
        <div className="flex items-center gap-1">
          <button
            type="button"
            aria-label="Mês anterior"
            onClick={() => setMonth((current) => shiftMonth(current, -1))}
            className="rounded p-1 text-gray-400 hover:bg-gray-100 hover:text-gray-600"
          >
            <ChevronLeft className="h-4 w-4" />
          </button>
          <span className="min-w-[9rem] text-center text-sm text-gray-600">{monthLabel(month)}</span>
          <button
            type="button"
            aria-label="Próximo mês"
            onClick={() => setMonth((current) => shiftMonth(current, 1))}
            className="rounded p-1 text-gray-400 hover:bg-gray-100 hover:text-gray-600"
          >
            <ChevronRight className="h-4 w-4" />
          </button>
        </div>
      </div>

      {!data ? (
        <p className="mt-3 text-sm text-gray-400">Carregando...</p>
      ) : isEditing ? (
        <div className="mt-3 flex items-center gap-2">
          <input
            type="number"
            min="0.01"
            step="0.01"
            autoFocus
            value={goalInput}
            onChange={(e) => setGoalInput(e.target.value)}
            placeholder="Ex: 50000"
            className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none"
          />
          <button
            type="button"
            onClick={handleSave}
            disabled={mutation.isPending}
            className="shrink-0 rounded-md bg-brand-600 px-3 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-60"
          >
            Salvar
          </button>
          <button
            type="button"
            onClick={() => setIsEditing(false)}
            className="shrink-0 rounded-md border border-gray-200 px-3 py-2 text-sm text-gray-600 hover:bg-gray-50"
          >
            Cancelar
          </button>
        </div>
      ) : data.revenueGoal === null ? (
        <button
          type="button"
          onClick={startEditing}
          className="mt-3 text-sm font-medium text-brand-600 hover:text-brand-700"
        >
          + Definir meta pra esse mês
        </button>
      ) : (
        <div className="mt-3">
          <div className="flex items-baseline justify-between">
            <span className="text-2xl font-semibold text-brand-700">{currencyFormatter.format(data.currentRevenue)}</span>
            <span className="flex items-center gap-1 text-sm text-gray-500">
              de {currencyFormatter.format(data.revenueGoal)}
              <button type="button" onClick={startEditing} aria-label="Editar meta" className="text-gray-400 hover:text-gray-600">
                <Pencil className="h-3.5 w-3.5" />
              </button>
            </span>
          </div>
          <div className="mt-2 h-2.5 w-full overflow-hidden rounded-full bg-gray-100">
            <div className={`h-full rounded-full ${progressColor}`} style={{ width: `${progressWidth}%` }} />
          </div>
          <p className="mt-1 text-xs text-gray-400">{progress}% da meta</p>
        </div>
      )}
    </div>
  )
}
