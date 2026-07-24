import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  CheckCircle2,
  Clock,
  Coffee,
  Flame,
  LayoutGrid,
  Layers,
  Plus,
  Users,
  UtensilsCrossed,
  type LucideIcon,
} from 'lucide-react'
import { useMemo, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  createTable,
  createTablesBulk,
  deleteTable,
  listTables,
  updateTable,
  updateTableStatus,
  type RestaurantTable,
  type TableStatus,
} from '../api/tables'
import { listTabs, openTab, type Tab } from '../api/tabs'
import { getMyRestaurant } from '../api/restaurant'
import { useAuth } from '../auth/AuthContext'
import { Modal } from '../components/Modal'
import { QrCodeCard } from '../components/QrCodeCard'
import { minutesSince } from '../utils/time'
import { publicMenuUrl } from '../utils/publicMenuUrl'

const STATUS_LABELS: Record<TableStatus, string> = {
  FREE: 'Livre',
  OCCUPIED: 'Ocupada',
  CLOSING: 'Fechando',
}

const STATUS_STYLES: Record<TableStatus, string> = {
  FREE: 'border-green-300 bg-gradient-to-br from-green-50 to-white text-green-800',
  OCCUPIED: 'border-red-300 bg-gradient-to-br from-red-50 to-white text-red-800',
  CLOSING: 'border-amber-300 bg-gradient-to-br from-amber-50 to-white text-amber-800',
}

const STATUS_ICONS: Record<TableStatus, LucideIcon> = {
  FREE: CheckCircle2,
  OCCUPIED: Flame,
  CLOSING: Clock,
}

const STATUS_PILL_STYLES: Record<TableStatus, string> = {
  FREE: 'border-green-200 bg-green-50 text-green-800',
  OCCUPIED: 'border-red-200 bg-red-50 text-red-800',
  CLOSING: 'border-amber-200 bg-amber-50 text-amber-800',
}

const POLL_INTERVAL_MS = 4000

interface StatPillProps {
  icon: LucideIcon
  label: string
  value: number
  className: string
}

function StatPill({ icon: Icon, label, value, className }: StatPillProps) {
  return (
    <div className={`flex items-center gap-2 rounded-lg border px-3 py-2 ${className}`}>
      <Icon className="h-4 w-4" />
      <span className="text-lg font-semibold leading-none">{value}</span>
      <span className="text-xs font-medium">{label}</span>
    </div>
  )
}

export function TablesPage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const canManage = user?.role === 'OWNER' || user?.role === 'MANAGER'
  const canChangeStatus = canManage || user?.role === 'WAITER'
  const canOpenTab = canManage || user?.role === 'WAITER' || user?.role === 'CASHIER'
  const queryClient = useQueryClient()

  const { data: tables, isLoading } = useQuery({
    queryKey: ['tables'],
    queryFn: () => listTables(),
    refetchInterval: POLL_INTERVAL_MS,
    refetchIntervalInBackground: true,
  })

  const { data: openTabs } = useQuery({
    queryKey: ['tabs', 'OPEN'],
    queryFn: () => listTabs('OPEN'),
    enabled: canOpenTab,
    refetchInterval: POLL_INTERVAL_MS,
    refetchIntervalInBackground: true,
  })

  const { data: restaurant } = useQuery({
    queryKey: ['restaurant'],
    queryFn: getMyRestaurant,
  })

  const tableTabMap = useMemo(() => {
    const map = new Map<string, Tab>()
    openTabs?.forEach((tab) => {
      tab.tables.forEach((table) => map.set(table.id, tab))
    })
    return map
  }, [openTabs])

  const counterTabs = useMemo(() => openTabs?.filter((tab) => tab.tables.length === 0) ?? [], [openTabs])

  const statusCounts = useMemo(() => {
    const counts: Record<TableStatus, number> = { FREE: 0, OCCUPIED: 0, CLOSING: 0 }
    tables?.forEach((table) => {
      counts[table.status] += 1
    })
    return counts
  }, [tables])

  const [isCreating, setIsCreating] = useState(false)
  const [isBulkCreating, setIsBulkCreating] = useState(false)
  const [isSelectingTables, setIsSelectingTables] = useState(false)
  const [selectedTableIds, setSelectedTableIds] = useState<Set<string>>(new Set())
  const [selectedTable, setSelectedTable] = useState<RestaurantTable | null>(null)
  const [numberInput, setNumberInput] = useState('')
  const [quantityInput, setQuantityInput] = useState('')
  const [editNumber, setEditNumber] = useState('')
  const [editStatus, setEditStatus] = useState<TableStatus>('FREE')
  const [editActive, setEditActive] = useState(true)
  const [error, setError] = useState<string | null>(null)

  function invalidate() {
    queryClient.invalidateQueries({ queryKey: ['tables'] })
  }

  const createMutation = useMutation({
    mutationFn: (number?: number) => createTable(number),
    onSuccess: () => {
      invalidate()
      setIsCreating(false)
    },
    onError: () => setError('Não foi possível criar a mesa. Verifique se o número já está em uso.'),
  })

  const bulkMutation = useMutation({
    mutationFn: createTablesBulk,
    onSuccess: () => {
      invalidate()
      setIsBulkCreating(false)
    },
    onError: () => setError('Não foi possível criar as mesas.'),
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: Parameters<typeof updateTable>[1] }) =>
      updateTable(id, payload),
    onSuccess: invalidate,
  })

  const statusMutation = useMutation({
    mutationFn: ({ id, status }: { id: string; status: TableStatus }) => updateTableStatus(id, status),
    onSuccess: invalidate,
  })

  const deleteMutation = useMutation({
    mutationFn: deleteTable,
    onSuccess: () => {
      invalidate()
      closeTableModal()
    },
    onError: () => setError('Não foi possível excluir a mesa.'),
  })

  const openTabMutation = useMutation({
    mutationFn: openTab,
    onSuccess: (tab) => {
      invalidate()
      queryClient.invalidateQueries({ queryKey: ['tabs'] })
      setIsSelectingTables(false)
      setSelectedTableIds(new Set())
      navigate(`/tabs/${tab.id}`)
    },
    onError: () => setError('Não foi possível abrir a comanda. Verifique se as mesas selecionadas estão livres.'),
  })

  function openCreateForm() {
    setNumberInput('')
    setError(null)
    setIsCreating(true)
  }

  function openBulkForm() {
    setQuantityInput('')
    setError(null)
    setIsBulkCreating(true)
  }

  function openTableModal(table: RestaurantTable) {
    setSelectedTable(table)
    setEditNumber(String(table.number))
    setEditStatus(table.status)
    setEditActive(table.active)
    setError(null)
  }

  function closeTableModal() {
    setSelectedTable(null)
  }

  function handleDelete() {
    if (!selectedTable) return
    const confirmed = window.confirm(
      `Excluir a Mesa ${selectedTable.number}? Essa ação não pode ser desfeita.`,
    )
    if (confirmed) {
      setError(null)
      deleteMutation.mutate(selectedTable.id)
    }
  }

  function handleCreateSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    createMutation.mutate(numberInput ? Number(numberInput) : undefined)
  }

  function handleBulkSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    bulkMutation.mutate(Number(quantityInput))
  }

  function handleTableSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    if (!selectedTable) return

    if (canManage) {
      updateMutation.mutate(
        { id: selectedTable.id, payload: { number: Number(editNumber), active: editActive } },
        { onError: () => setError('Não foi possível salvar. Verifique se o número já está em uso.') },
      )
    }
    if (editStatus !== selectedTable.status) {
      statusMutation.mutate({ id: selectedTable.id, status: editStatus })
    }
    closeTableModal()
  }

  function startSelectingTables() {
    setError(null)
    setSelectedTableIds(new Set())
    setIsSelectingTables(true)
  }

  function cancelSelectingTables() {
    setIsSelectingTables(false)
    setSelectedTableIds(new Set())
  }

  function toggleTableSelection(table: RestaurantTable) {
    if (table.status !== 'FREE') return
    setSelectedTableIds((prev) => {
      const next = new Set(prev)
      if (next.has(table.id)) {
        next.delete(table.id)
      } else {
        next.add(table.id)
      }
      return next
    })
  }

  function confirmOpenTab() {
    setError(null)
    openTabMutation.mutate(Array.from(selectedTableIds))
  }

  function openCounterTab() {
    setError(null)
    openTabMutation.mutate([])
  }

  function handleTableClick(table: RestaurantTable) {
    if (isSelectingTables) {
      toggleTableSelection(table)
      return
    }
    const tab = tableTabMap.get(table.id)
    if (tab) {
      navigate(`/tabs/${tab.id}`)
      return
    }
    openTableModal(table)
  }

  return (
    <div>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h1 className="flex items-center gap-2 text-lg font-semibold text-gray-800">
          <LayoutGrid className="h-5 w-5 text-brand-600" />
          Mesas
        </h1>
        <div className="flex flex-wrap gap-2">
          {canOpenTab && !isSelectingTables && (
            <>
              <button
                type="button"
                onClick={startSelectingTables}
                className="flex items-center gap-1.5 rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-100"
              >
                <Users className="h-4 w-4" />
                Abrir comanda
              </button>
              <button
                type="button"
                onClick={openCounterTab}
                disabled={openTabMutation.isPending}
                className="flex items-center gap-1.5 rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-100 disabled:opacity-50"
              >
                <Coffee className="h-4 w-4" />
                Balcão
              </button>
            </>
          )}
          {canManage && !isSelectingTables && (
            <>
              <button
                type="button"
                onClick={openBulkForm}
                className="flex items-center gap-1.5 rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-100"
              >
                <Layers className="h-4 w-4" />
                Criar em lote
              </button>
              <button
                type="button"
                onClick={openCreateForm}
                className="flex items-center gap-1.5 rounded-md bg-brand-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-brand-700"
              >
                <Plus className="h-4 w-4" />
                Nova mesa
              </button>
            </>
          )}
        </div>
      </div>

      {tables && tables.length > 0 && (
        <div className="mb-6 flex flex-wrap gap-3">
          <StatPill icon={CheckCircle2} label="livres" value={statusCounts.FREE} className={STATUS_PILL_STYLES.FREE} />
          <StatPill icon={Flame} label="ocupadas" value={statusCounts.OCCUPIED} className={STATUS_PILL_STYLES.OCCUPIED} />
          {statusCounts.CLOSING > 0 && (
            <StatPill icon={Clock} label="fechando" value={statusCounts.CLOSING} className={STATUS_PILL_STYLES.CLOSING} />
          )}
        </div>
      )}

      {isSelectingTables && (
        <div className="mb-4 flex flex-col gap-2 rounded-lg border border-brand-300 bg-brand-50 px-4 py-3 text-sm sm:flex-row sm:items-center sm:justify-between">
          <span className="flex items-center gap-2 text-brand-800">
            <Users className="h-4 w-4" />
            Selecione uma ou mais mesas livres ({selectedTableIds.size} selecionada
            {selectedTableIds.size === 1 ? '' : 's'})
          </span>
          <div className="flex gap-2">
            <button
              type="button"
              onClick={cancelSelectingTables}
              className="rounded-md border border-gray-300 bg-white px-3 py-1.5 text-gray-700 hover:bg-gray-100"
            >
              Cancelar
            </button>
            <button
              type="button"
              onClick={confirmOpenTab}
              disabled={selectedTableIds.size === 0 || openTabMutation.isPending}
              className="rounded-md bg-brand-600 px-3 py-1.5 font-medium text-white hover:bg-brand-700 disabled:opacity-50"
            >
              Abrir comanda
            </button>
          </div>
        </div>
      )}

      {error && !selectedTable && <p className="mb-4 text-sm text-red-600">{error}</p>}

      {canOpenTab && counterTabs.length > 0 && (
        <div className="mb-6">
          <p className="mb-2 text-xs font-semibold tracking-wide text-gray-400 uppercase">
            Comandas de balcão abertas
          </p>
          <div className="flex flex-wrap gap-2">
            {counterTabs.map((tab) => (
              <button
                key={tab.id}
                type="button"
                onClick={() => navigate(`/tabs/${tab.id}`)}
                className="flex items-center gap-2 rounded-lg border border-gray-200 bg-white px-4 py-2.5 text-sm text-gray-700 shadow-sm transition-all hover:-translate-y-0.5 hover:shadow-md"
              >
                <Coffee className="h-4 w-4 text-brand-600" />
                Balcão · aberta às{' '}
                {new Date(tab.openedAt).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}
                <span className="text-gray-400">· há {minutesSince(tab.openedAt)} min</span>
              </button>
            ))}
          </div>
        </div>
      )}

      {isLoading && <p className="text-sm text-gray-500">Carregando...</p>}

      {tables && tables.length === 0 && (
        <div className="flex flex-col items-center gap-3 rounded-xl border border-dashed border-gray-300 bg-gray-50 py-16 text-center">
          <UtensilsCrossed className="h-10 w-10 text-gray-300" />
          <p className="text-sm text-gray-500">Nenhuma mesa cadastrada ainda.</p>
          {canManage && (
            <button
              type="button"
              onClick={openCreateForm}
              className="mt-1 flex items-center gap-1.5 rounded-md bg-brand-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-brand-700"
            >
              <Plus className="h-4 w-4" />
              Criar primeira mesa
            </button>
          )}
        </div>
      )}

      {tables && tables.length > 0 && (
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6">
          {tables.map((table) => {
            const isSelected = selectedTableIds.has(table.id)
            const isSelectableNow = !isSelectingTables || table.status === 'FREE'
            const tab = tableTabMap.get(table.id)
            const StatusIcon = STATUS_ICONS[table.status]
            return (
              <button
                key={table.id}
                type="button"
                disabled={(!canChangeStatus && !canManage && !canOpenTab) || !isSelectableNow}
                onClick={() => handleTableClick(table)}
                className={`flex flex-col items-center gap-1 rounded-xl border-2 p-4 text-center shadow-sm transition-all hover:-translate-y-0.5 hover:shadow-md disabled:cursor-default disabled:opacity-40 disabled:hover:translate-y-0 disabled:hover:shadow-sm ${STATUS_STYLES[table.status]} ${!table.active ? 'opacity-50 grayscale' : ''} ${isSelected ? 'ring-2 ring-brand-500 ring-offset-2' : ''}`}
              >
                <StatusIcon className="h-5 w-5" />
                <div className="text-xl font-bold leading-none">{table.number}</div>
                <div className="text-[11px] font-semibold tracking-wide uppercase">{STATUS_LABELS[table.status]}</div>
                {tab && table.status === 'OCCUPIED' && (
                  <div className="text-[11px] opacity-75">há {minutesSince(tab.openedAt)} min</div>
                )}
                {!table.active && <div className="text-[11px]">Inativa</div>}
              </button>
            )
          })}
        </div>
      )}

      {isCreating && (
        <Modal title="Nova mesa" onClose={() => setIsCreating(false)}>
          <form onSubmit={handleCreateSubmit}>
            <label className="mb-1 block text-sm font-medium text-gray-700" htmlFor="tableNumber">
              Número <span className="font-normal text-gray-400">(opcional, automático se vazio)</span>
            </label>
            <input
              id="tableNumber"
              type="number"
              min="1"
              value={numberInput}
              onChange={(e) => setNumberInput(e.target.value)}
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none"
            />

            {error && <p className="mb-4 text-sm text-red-600">{error}</p>}

            <button
              type="submit"
              disabled={createMutation.isPending}
              className="w-full rounded-md bg-brand-600 px-3 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50"
            >
              Salvar
            </button>
          </form>
        </Modal>
      )}

      {isBulkCreating && (
        <Modal title="Criar mesas em lote" onClose={() => setIsBulkCreating(false)}>
          <form onSubmit={handleBulkSubmit}>
            <label className="mb-1 block text-sm font-medium text-gray-700" htmlFor="quantity">
              Quantidade
            </label>
            <input
              id="quantity"
              type="number"
              required
              min="1"
              max="200"
              value={quantityInput}
              onChange={(e) => setQuantityInput(e.target.value)}
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none"
            />
            <p className="mb-4 text-xs text-gray-500">
              As mesas serão numeradas em sequência, a partir do próximo número disponível.
            </p>

            {error && <p className="mb-4 text-sm text-red-600">{error}</p>}

            <button
              type="submit"
              disabled={bulkMutation.isPending}
              className="w-full rounded-md bg-brand-600 px-3 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50"
            >
              Criar
            </button>
          </form>
        </Modal>
      )}

      {selectedTable && (
        <Modal title={`Mesa ${selectedTable.number}`} onClose={closeTableModal}>
          <form onSubmit={handleTableSubmit}>
            <label className="mb-1 block text-sm font-medium text-gray-700" htmlFor="editNumber">
              Número
            </label>
            <input
              id="editNumber"
              type="number"
              min="1"
              disabled={!canManage}
              value={editNumber}
              onChange={(e) => setEditNumber(e.target.value)}
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none disabled:bg-gray-50 disabled:text-gray-500"
            />

            <label className="mb-1 block text-sm font-medium text-gray-700" htmlFor="editStatus">
              Status
            </label>
            <select
              id="editStatus"
              disabled={!canChangeStatus}
              value={editStatus}
              onChange={(e) => setEditStatus(e.target.value as TableStatus)}
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none disabled:bg-gray-50 disabled:text-gray-500"
            >
              {(Object.keys(STATUS_LABELS) as TableStatus[]).map((status) => (
                <option key={status} value={status}>
                  {STATUS_LABELS[status]}
                </option>
              ))}
            </select>

            {canManage && (
              <label className="mb-4 flex items-center gap-2 text-sm text-gray-700">
                <input
                  type="checkbox"
                  checked={editActive}
                  onChange={(e) => setEditActive(e.target.checked)}
                />
                Mesa ativa
              </label>
            )}

            {error && <p className="mb-4 text-sm text-red-600">{error}</p>}

            {(canManage || canChangeStatus) && (
              <button
                type="submit"
                disabled={updateMutation.isPending || statusMutation.isPending}
                className="w-full rounded-md bg-brand-600 px-3 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50"
              >
                Salvar
              </button>
            )}

            {canManage && selectedTable.status === 'FREE' && (
              <button
                type="button"
                onClick={handleDelete}
                disabled={deleteMutation.isPending}
                className="mt-2 w-full rounded-md border border-red-300 px-3 py-2 text-sm font-medium text-red-700 hover:bg-red-50 disabled:opacity-50"
              >
                Excluir mesa
              </button>
            )}
          </form>

          {restaurant?.slug && (
            <div className="mt-6 border-t border-gray-100 pt-6">
              <QrCodeCard
                title={`Autoatendimento · Mesa ${selectedTable.number}`}
                url={publicMenuUrl(restaurant.slug, selectedTable.id)}
                helperText="Clique com o botão direito pra salvar e imprimir nessa mesa"
              />
            </div>
          )}
        </Modal>
      )}
    </div>
  )
}
