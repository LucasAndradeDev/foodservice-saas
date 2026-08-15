import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CalendarClock, Pencil, Trash2 } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { useParams } from 'react-router-dom'
import {
  createAvailabilityWindow,
  deleteAvailabilityWindow,
  listAvailabilityWindows,
  updateAvailabilityWindow,
  type AvailabilityWindow,
  type AvailabilityWindowPayload,
  type DayOfWeek,
} from '../api/productAvailability'
import { getProduct } from '../api/products'
import { BackLink } from '../components/BackLink'
import { Button } from '../components/Button'
import { Modal } from '../components/Modal'

const DAY_OPTIONS: { value: DayOfWeek | ''; label: string }[] = [
  { value: '', label: 'Todo dia' },
  { value: 'MONDAY', label: 'Segunda-feira' },
  { value: 'TUESDAY', label: 'Terça-feira' },
  { value: 'WEDNESDAY', label: 'Quarta-feira' },
  { value: 'THURSDAY', label: 'Quinta-feira' },
  { value: 'FRIDAY', label: 'Sexta-feira' },
  { value: 'SATURDAY', label: 'Sábado' },
  { value: 'SUNDAY', label: 'Domingo' },
]

function dayLabel(dayOfWeek: DayOfWeek | null): string {
  return DAY_OPTIONS.find((option) => option.value === (dayOfWeek ?? ''))?.label ?? 'Todo dia'
}

function formatTime(time: string): string {
  return time.slice(0, 5)
}

export function ProductAvailabilityPage() {
  const { productId } = useParams<{ productId: string }>()
  const queryClient = useQueryClient()

  const { data: product } = useQuery({
    queryKey: ['products', productId],
    queryFn: () => getProduct(productId!),
    enabled: !!productId,
  })

  const { data: windows, isLoading } = useQuery({
    queryKey: ['products', productId, 'availabilityWindows'],
    queryFn: () => listAvailabilityWindows(productId!),
    enabled: !!productId,
  })

  const [editingWindow, setEditingWindow] = useState<AvailabilityWindow | null>(null)
  const [isCreating, setIsCreating] = useState(false)
  const [dayOfWeek, setDayOfWeek] = useState<DayOfWeek | ''>('')
  const [startTime, setStartTime] = useState('11:00')
  const [endTime, setEndTime] = useState('15:00')
  const [error, setError] = useState<string | null>(null)

  function invalidate() {
    queryClient.invalidateQueries({ queryKey: ['products', productId, 'availabilityWindows'] })
  }

  function handleError() {
    setError('Não foi possível salvar. Verifique se o horário de início é antes do horário de fim.')
  }

  const createMutation = useMutation({
    mutationFn: (payload: AvailabilityWindowPayload) => createAvailabilityWindow(productId!, payload),
    onSuccess: () => {
      invalidate()
      closeForm()
    },
    onError: handleError,
  })

  const updateMutation = useMutation({
    mutationFn: ({ windowId, payload }: { windowId: string; payload: AvailabilityWindowPayload }) =>
      updateAvailabilityWindow(productId!, windowId, payload),
    onSuccess: () => {
      invalidate()
      closeForm()
    },
    onError: handleError,
  })

  const deleteMutation = useMutation({
    mutationFn: (windowId: string) => deleteAvailabilityWindow(productId!, windowId),
    onSuccess: invalidate,
  })

  function openCreateForm() {
    setDayOfWeek('')
    setStartTime('11:00')
    setEndTime('15:00')
    setError(null)
    setIsCreating(true)
  }

  function openEditForm(window: AvailabilityWindow) {
    setEditingWindow(window)
    setDayOfWeek(window.dayOfWeek ?? '')
    setStartTime(formatTime(window.startTime))
    setEndTime(formatTime(window.endTime))
    setError(null)
  }

  function closeForm() {
    setIsCreating(false)
    setEditingWindow(null)
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)

    if (startTime >= endTime) {
      setError('O horário de início precisa ser antes do horário de fim.')
      return
    }

    const payload: AvailabilityWindowPayload = {
      dayOfWeek: dayOfWeek === '' ? null : dayOfWeek,
      startTime: `${startTime}:00`,
      endTime: `${endTime}:00`,
    }

    if (editingWindow) {
      updateMutation.mutate({ windowId: editingWindow.id, payload })
    } else {
      createMutation.mutate(payload)
    }
  }

  function confirmAndDelete(availabilityWindow: AvailabilityWindow) {
    const confirmed = globalThis.confirm(
      `Excluir a janela "${dayLabel(availabilityWindow.dayOfWeek)}, ${formatTime(availabilityWindow.startTime)}–${formatTime(availabilityWindow.endTime)}"?`,
    )
    if (confirmed) {
      deleteMutation.mutate(availabilityWindow.id)
    }
  }

  const isFormOpen = isCreating || editingWindow !== null

  return (
    <div>
      <BackLink to="/products" className="mb-2">
        Produtos
      </BackLink>

      <div className="mb-1 flex items-center justify-between">
        <h1 className="flex items-center gap-2 text-lg font-semibold text-gray-800 dark:text-white">
          <CalendarClock className="h-5 w-5 text-brand-600 dark:text-brand-400" />
          Horários de disponibilidade {product && `· ${product.name}`}
        </h1>
        <button
          type="button"
          onClick={openCreateForm}
          className="rounded-md bg-brand-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-brand-700"
        >
          Adicionar horário
        </button>
      </div>
      <p className="mb-4 text-sm text-gray-500 dark:text-stone-400">
        Restrinja em quais dias e horários esse produto pode ser vendido — ex: cardápio de almoço só ao meio-dia, ou
        bebida alcoólica só a partir de certo horário. Sem nenhum horário cadastrado, o produto fica disponível
        sempre.
      </p>

      {isLoading && <p className="text-sm text-gray-500 dark:text-stone-400">Carregando...</p>}

      {windows && windows.length === 0 && (
        <div className="rounded-xl border border-dashed border-gray-300 bg-gray-50 p-6 text-center dark:border-white/10 dark:bg-white/5">
          <CalendarClock className="mx-auto mb-2 h-8 w-8 text-gray-300 dark:text-stone-600" />
          <p className="mb-1 text-sm font-medium text-gray-700 dark:text-stone-300">Nenhum horário cadastrado ainda</p>
          <p className="mx-auto max-w-sm text-sm text-gray-500 dark:text-stone-400">
            Exemplo: "Sábado, 11:00–16:00" pra um prato que só sai aos sábados. Clique em "Adicionar horário" pra
            criar o primeiro.
          </p>
        </div>
      )}

      {windows && windows.length > 0 && (
        <div className="space-y-2">
          {windows.map((availabilityWindow) => (
            <div
              key={availabilityWindow.id}
              className="flex items-center justify-between gap-2 rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-white/10 dark:bg-stone-900"
            >
              <div>
                <span className="font-medium text-gray-800 dark:text-white">{dayLabel(availabilityWindow.dayOfWeek)}</span>
                <span className="ml-2 text-sm text-gray-500 dark:text-stone-400">
                  {formatTime(availabilityWindow.startTime)}–{formatTime(availabilityWindow.endTime)}
                </span>
              </div>
              <div className="flex shrink-0 items-center gap-1">
                <button
                  type="button"
                  onClick={() => openEditForm(availabilityWindow)}
                  title="Editar"
                  aria-label="Editar"
                  className="rounded-md p-1.5 text-gray-500 hover:bg-gray-100 hover:text-brand-700 dark:text-stone-400 dark:hover:bg-white/5 dark:hover:text-brand-400"
                >
                  <Pencil className="h-4 w-4" />
                </button>
                <button
                  type="button"
                  onClick={() => confirmAndDelete(availabilityWindow)}
                  title="Excluir"
                  aria-label="Excluir"
                  className="rounded-md p-1.5 text-gray-500 hover:bg-red-50 hover:text-red-700 dark:text-stone-400 dark:hover:bg-red-500/10 dark:hover:text-red-400"
                >
                  <Trash2 className="h-4 w-4" />
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {isFormOpen && (
        <Modal title={editingWindow ? 'Editar horário' : 'Novo horário de disponibilidade'} onClose={closeForm}>
          <form onSubmit={handleSubmit}>
            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="dayOfWeek">
              Dia da semana
            </label>
            <select
              id="dayOfWeek"
              value={dayOfWeek}
              onChange={(e) => setDayOfWeek(e.target.value as DayOfWeek | '')}
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
            >
              {DAY_OPTIONS.map((option) => (
                <option key={option.label} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>

            <div className="mb-4 flex gap-3">
              <div className="flex-1">
                <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="startTime">
                  Início
                </label>
                <input
                  id="startTime"
                  type="time"
                  required
                  value={startTime}
                  onChange={(e) => setStartTime(e.target.value)}
                  className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
                />
              </div>
              <div className="flex-1">
                <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="endTime">
                  Fim
                </label>
                <input
                  id="endTime"
                  type="time"
                  required
                  value={endTime}
                  onChange={(e) => setEndTime(e.target.value)}
                  className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
                />
              </div>
            </div>

            {error && <p className="mb-4 text-sm text-wine-600 dark:text-wine-400">{error}</p>}

            <Button type="submit" disabled={createMutation.isPending || updateMutation.isPending} className="w-full">
              Salvar
            </Button>
          </form>
        </Modal>
      )}
    </div>
  )
}
