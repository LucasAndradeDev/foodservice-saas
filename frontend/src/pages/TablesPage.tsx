import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import {
  createTable,
  createTablesBulk,
  listTables,
  updateTable,
  updateTableStatus,
  type RestaurantTable,
  type TableStatus,
} from '../api/tables'
import { useAuth } from '../auth/AuthContext'
import { Modal } from '../components/Modal'

const STATUS_LABELS: Record<TableStatus, string> = {
  FREE: 'Livre',
  OCCUPIED: 'Ocupada',
  CLOSING: 'Fechando',
}

const STATUS_STYLES: Record<TableStatus, string> = {
  FREE: 'border-green-300 bg-green-50 text-green-800',
  OCCUPIED: 'border-red-300 bg-red-50 text-red-800',
  CLOSING: 'border-amber-300 bg-amber-50 text-amber-800',
}

export function TablesPage() {
  const { user } = useAuth()
  const canManage = user?.role === 'OWNER' || user?.role === 'MANAGER'
  const canChangeStatus = canManage || user?.role === 'WAITER'
  const queryClient = useQueryClient()

  const { data: tables, isLoading } = useQuery({
    queryKey: ['tables'],
    queryFn: () => listTables(),
  })

  const [isCreating, setIsCreating] = useState(false)
  const [isBulkCreating, setIsBulkCreating] = useState(false)
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

  return (
    <div>
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-lg font-semibold text-gray-800">Mesas</h1>
        {canManage && (
          <div className="flex gap-2">
            <button
              type="button"
              onClick={openBulkForm}
              className="rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-100"
            >
              Criar em lote
            </button>
            <button
              type="button"
              onClick={openCreateForm}
              className="rounded-md bg-gray-900 px-3 py-1.5 text-sm font-medium text-white hover:bg-gray-800"
            >
              Nova mesa
            </button>
          </div>
        )}
      </div>

      {isLoading && <p className="text-sm text-gray-500">Carregando...</p>}

      {tables && tables.length === 0 && (
        <p className="text-sm text-gray-500">Nenhuma mesa cadastrada.</p>
      )}

      {tables && tables.length > 0 && (
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6">
          {tables.map((table) => (
            <button
              key={table.id}
              type="button"
              disabled={!canChangeStatus && !canManage}
              onClick={() => openTableModal(table)}
              className={`rounded-lg border-2 p-4 text-center shadow-sm transition disabled:cursor-default ${STATUS_STYLES[table.status]} ${!table.active ? 'opacity-40' : ''}`}
            >
              <div className="text-lg font-semibold">Mesa {table.number}</div>
              <div className="text-xs">{STATUS_LABELS[table.status]}</div>
              {!table.active && <div className="text-xs">(inativa)</div>}
            </button>
          ))}
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
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-gray-500 focus:outline-none"
            />

            {error && <p className="mb-4 text-sm text-red-600">{error}</p>}

            <button
              type="submit"
              disabled={createMutation.isPending}
              className="w-full rounded-md bg-gray-900 px-3 py-2 text-sm font-medium text-white hover:bg-gray-800 disabled:opacity-50"
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
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-gray-500 focus:outline-none"
            />
            <p className="mb-4 text-xs text-gray-500">
              As mesas serão numeradas em sequência, a partir do próximo número disponível.
            </p>

            {error && <p className="mb-4 text-sm text-red-600">{error}</p>}

            <button
              type="submit"
              disabled={bulkMutation.isPending}
              className="w-full rounded-md bg-gray-900 px-3 py-2 text-sm font-medium text-white hover:bg-gray-800 disabled:opacity-50"
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
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-gray-500 focus:outline-none disabled:bg-gray-50 disabled:text-gray-500"
            />

            <label className="mb-1 block text-sm font-medium text-gray-700" htmlFor="editStatus">
              Status
            </label>
            <select
              id="editStatus"
              disabled={!canChangeStatus}
              value={editStatus}
              onChange={(e) => setEditStatus(e.target.value as TableStatus)}
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-gray-500 focus:outline-none disabled:bg-gray-50 disabled:text-gray-500"
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
                className="w-full rounded-md bg-gray-900 px-3 py-2 text-sm font-medium text-white hover:bg-gray-800 disabled:opacity-50"
              >
                Salvar
              </button>
            )}
          </form>
        </Modal>
      )}
    </div>
  )
}
