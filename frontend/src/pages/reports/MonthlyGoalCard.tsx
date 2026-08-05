import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ChevronLeft, ChevronRight, Pencil, Target } from 'lucide-react'
import { useState } from 'react'
import { getMonthlyGoal, setMonthlyGoal } from '../../api/reports'
import { Button } from '../../components/Button'
import { Card } from '../../components/Card'

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
  const progressColor = progress !== null && progress >= 100 ? 'bg-sage-500' : 'bg-brand-600'

  return (
    <Card className="p-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-1.5 text-sm font-medium text-gray-700 dark:text-stone-300">
          <Target className="h-4 w-4 text-brand-600 dark:text-brand-400" />
          Meta do mês
        </div>
        <div className="flex items-center gap-1">
          <button
            type="button"
            aria-label="Mês anterior"
            onClick={() => setMonth((current) => shiftMonth(current, -1))}
            className="rounded p-1 text-gray-400 hover:bg-gray-100 hover:text-gray-600 dark:text-stone-500 dark:hover:bg-white/5 dark:hover:text-stone-300"
          >
            <ChevronLeft className="h-4 w-4" />
          </button>
          <span className="min-w-[9rem] text-center text-sm text-gray-600 dark:text-stone-400">{monthLabel(month)}</span>
          <button
            type="button"
            aria-label="Próximo mês"
            onClick={() => setMonth((current) => shiftMonth(current, 1))}
            className="rounded p-1 text-gray-400 hover:bg-gray-100 hover:text-gray-600 dark:text-stone-500 dark:hover:bg-white/5 dark:hover:text-stone-300"
          >
            <ChevronRight className="h-4 w-4" />
          </button>
        </div>
      </div>

      {!data ? (
        <p className="mt-3 text-sm text-gray-400 dark:text-stone-500">Carregando...</p>
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
            className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
          />
          <Button type="button" onClick={handleSave} disabled={mutation.isPending} className="shrink-0">
            Salvar
          </Button>
          <button
            type="button"
            onClick={() => setIsEditing(false)}
            className="shrink-0 rounded-md border border-gray-200 px-3 py-2 text-sm text-gray-600 hover:bg-gray-50 dark:border-white/10 dark:text-stone-400 dark:hover:bg-white/5"
          >
            Cancelar
          </button>
        </div>
      ) : data.revenueGoal === null ? (
        <button
          type="button"
          onClick={startEditing}
          className="mt-3 text-sm font-medium text-brand-600 hover:text-brand-700 dark:text-brand-400 dark:hover:text-brand-300"
        >
          + Definir meta pra esse mês
        </button>
      ) : (
        <div className="mt-3">
          <div className="flex items-baseline justify-between">
            <span className="text-2xl font-semibold text-brand-700 dark:text-brand-400">
              {currencyFormatter.format(data.currentRevenue)}
            </span>
            <span className="flex items-center gap-1 text-sm text-gray-500 dark:text-stone-400">
              de {currencyFormatter.format(data.revenueGoal)}
              <button
                type="button"
                onClick={startEditing}
                aria-label="Editar meta"
                className="text-gray-400 hover:text-gray-600 dark:text-stone-500 dark:hover:text-stone-300"
              >
                <Pencil className="h-3.5 w-3.5" />
              </button>
            </span>
          </div>
          <div className="mt-2 h-2.5 w-full overflow-hidden rounded-full bg-gray-100 dark:bg-white/10">
            <div className={`h-full rounded-full ${progressColor}`} style={{ width: `${progressWidth}%` }} />
          </div>
          <p className="mt-1 text-xs text-gray-400 dark:text-stone-500">{progress}% da meta</p>
        </div>
      )}
    </Card>
  )
}
