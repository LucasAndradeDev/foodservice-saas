import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, ShoppingCart, Trash2 } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { listIngredients } from '../api/ingredients'
import { createPurchase, listPurchases, type CreatePurchaseItemPayload, type Purchase } from '../api/purchases'
import { listSuppliers } from '../api/suppliers'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { EmptyState } from '../components/EmptyState'
import { Modal } from '../components/Modal'
import { Table, TableHead, TableRow } from '../components/Table'

interface ItemRow {
  key: number
  ingredientId: string
  quantity: string
  unitCost: string
}

let nextRowKey = 0

function emptyRow(): ItemRow {
  return { key: nextRowKey++, ingredientId: '', quantity: '', unitCost: '' }
}

export function PurchasesPage() {
  const queryClient = useQueryClient()

  const { data: purchases, isLoading } = useQuery({
    queryKey: ['purchases'],
    queryFn: () => listPurchases(),
  })
  const { data: suppliers } = useQuery({
    queryKey: ['suppliers', { active: true }],
    queryFn: () => listSuppliers(true),
  })
  const { data: ingredients } = useQuery({
    queryKey: ['ingredients', { active: true }],
    queryFn: () => listIngredients(true),
  })

  const [isCreating, setIsCreating] = useState(false)
  const [supplierId, setSupplierId] = useState('')
  const [rows, setRows] = useState<ItemRow[]>([emptyRow()])
  const [error, setError] = useState<string | null>(null)

  const createMutation = useMutation({
    mutationFn: createPurchase,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['purchases'] })
      queryClient.invalidateQueries({ queryKey: ['ingredients'] })
      closeForm()
    },
    onError: () => setError('Não foi possível registrar a compra. Confira os campos.'),
  })

  function openCreateForm() {
    setSupplierId('')
    setRows([emptyRow()])
    setError(null)
    setIsCreating(true)
  }

  function closeForm() {
    setIsCreating(false)
  }

  function updateRow(key: number, patch: Partial<ItemRow>) {
    setRows((current) => current.map((row) => (row.key === key ? { ...row, ...patch } : row)))
  }

  function addRow() {
    setRows((current) => [...current, emptyRow()])
  }

  function removeRow(key: number) {
    setRows((current) => (current.length > 1 ? current.filter((row) => row.key !== key) : current))
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)

    const items: CreatePurchaseItemPayload[] = rows
      .filter((row) => row.ingredientId && row.quantity !== '')
      .map((row) => ({
        ingredientId: row.ingredientId,
        quantity: Number(row.quantity),
        unitCost: row.unitCost === '' ? undefined : Number(row.unitCost),
      }))

    if (items.length === 0) {
      setError('Adicione ao menos um item.')
      return
    }

    createMutation.mutate({ supplierId, items })
  }

  return (
    <div>
      <div className="mb-4 flex items-center justify-between gap-3 rounded-xl border border-gray-200 bg-white p-4 shadow-xs dark:border-white/10 dark:bg-stone-900">
        <div className="flex min-w-0 items-center gap-3">
          <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-teal-600 text-white shadow-sm">
            <ShoppingCart className="h-5 w-5" />
          </span>
          <h1 className="truncate text-lg font-bold text-gray-900 dark:text-white">Compras</h1>
        </div>
        <Button type="button" onClick={openCreateForm} className="shrink-0 whitespace-nowrap">
          <Plus className="h-4 w-4" />
          <span className="hidden sm:inline">Nova compra</span>
        </Button>
      </div>

      {isLoading && <p className="text-sm text-gray-500 dark:text-stone-400">Carregando...</p>}

      {purchases && purchases.length === 0 && (
        <EmptyState icon={ShoppingCart} message="Nenhuma compra registrada ainda." />
      )}

      {purchases && purchases.length > 0 && (
        <>
          {/* Mobile: stacked cards */}
          <div className="space-y-2 sm:hidden">
            {purchases.map((purchase) => (
              <Card key={purchase.id} className="p-4">
                <div className="mb-1 flex items-start justify-between gap-2">
                  <p className="font-medium text-gray-800 dark:text-white">{purchase.supplierName}</p>
                  <p className="whitespace-nowrap text-xs text-gray-500 dark:text-stone-400">{formatDate(purchase.purchasedAt)}</p>
                </div>
                <p className="text-xs text-gray-500 dark:text-stone-400">{summarizeItems(purchase)}</p>
              </Card>
            ))}
          </div>

          {/* Desktop: table */}
          <div className="hidden sm:block">
            <Table>
              <TableHead>
                <tr>
                  <th className="px-4 py-2 font-medium">Data</th>
                  <th className="px-4 py-2 font-medium">Fornecedor</th>
                  <th className="px-4 py-2 font-medium">Itens</th>
                </tr>
              </TableHead>
              <tbody>
                {purchases.map((purchase) => (
                  <TableRow key={purchase.id}>
                    <td className="px-4 py-2 whitespace-nowrap text-gray-800 dark:text-white">{formatDate(purchase.purchasedAt)}</td>
                    <td className="px-4 py-2 text-gray-800 dark:text-white">{purchase.supplierName}</td>
                    <td className="px-4 py-2 text-gray-600 dark:text-stone-300">{summarizeItems(purchase)}</td>
                  </TableRow>
                ))}
              </tbody>
            </Table>
          </div>
        </>
      )}

      {isCreating && (
        <Modal title="Nova compra" onClose={closeForm}>
          <form onSubmit={handleSubmit}>
            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="purchaseSupplier">
              Fornecedor
            </label>
            <select
              id="purchaseSupplier"
              required
              value={supplierId}
              onChange={(e) => setSupplierId(e.target.value)}
              className="mb-4 w-full rounded-md border border-gray-300 bg-white px-3 py-2 text-sm focus:border-teal-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-teal-400"
            >
              <option value="" disabled>
                Selecione...
              </option>
              {suppliers?.map((supplier) => (
                <option key={supplier.id} value={supplier.id}>
                  {supplier.name}
                </option>
              ))}
            </select>

            <p className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300">Itens</p>
            <div className="mb-3 space-y-2">
              {rows.map((row) => (
                <div key={row.key} className="flex items-start gap-2">
                  <select
                    required
                    value={row.ingredientId}
                    onChange={(e) => updateRow(row.key, { ingredientId: e.target.value })}
                    className="min-w-0 flex-1 rounded-md border border-gray-300 bg-white px-2 py-2 text-sm focus:border-teal-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-teal-400"
                  >
                    <option value="" disabled>
                      Insumo...
                    </option>
                    {ingredients?.map((ingredient) => (
                      <option key={ingredient.id} value={ingredient.id}>
                        {ingredient.name}
                      </option>
                    ))}
                  </select>
                  <input
                    type="number"
                    step="0.001"
                    min="0"
                    required
                    placeholder="Qtd"
                    value={row.quantity}
                    onChange={(e) => updateRow(row.key, { quantity: e.target.value })}
                    className="w-20 rounded-md border border-gray-300 px-2 py-2 text-sm focus:border-teal-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-teal-400"
                  />
                  <input
                    type="number"
                    step="0.01"
                    min="0"
                    placeholder="Custo un."
                    value={row.unitCost}
                    onChange={(e) => updateRow(row.key, { unitCost: e.target.value })}
                    className="w-24 rounded-md border border-gray-300 px-2 py-2 text-sm focus:border-teal-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-teal-400"
                  />
                  <button
                    type="button"
                    onClick={() => removeRow(row.key)}
                    disabled={rows.length === 1}
                    aria-label="Remover item"
                    className="shrink-0 rounded-md p-2 text-gray-400 hover:bg-gray-100 hover:text-wine-600 disabled:cursor-default disabled:opacity-40 dark:text-stone-500 dark:hover:bg-white/5 dark:hover:text-wine-400"
                  >
                    <Trash2 className="h-4 w-4" />
                  </button>
                </div>
              ))}
            </div>

            <Button type="button" variant="ghost" size="sm" onClick={addRow} className="mb-4">
              <Plus className="h-4 w-4" />
              Adicionar item
            </Button>

            {error && <p className="mb-4 text-sm text-wine-600 dark:text-wine-400">{error}</p>}

            <Button type="submit" disabled={createMutation.isPending} className="w-full">
              Registrar compra
            </Button>
          </form>
        </Modal>
      )}
    </div>
  )
}

function summarizeItems(purchase: Purchase) {
  return purchase.items.map((item) => `${item.ingredientName} ${item.quantity}`).join(', ')
}

function formatDate(isoDate: string) {
  return new Date(isoDate).toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit', year: 'numeric' })
}
