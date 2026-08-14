import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, Boxes, CheckCircle2, Circle, Pencil, Plus, Power } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { createIngredient, listIngredients, updateIngredient, type Ingredient } from '../api/ingredients'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { EmptyState } from '../components/EmptyState'
import { Modal } from '../components/Modal'
import { Table, TableHead, TableRow } from '../components/Table'

export function IngredientsPage() {
  const queryClient = useQueryClient()

  const { data: ingredients, isLoading } = useQuery({
    queryKey: ['ingredients'],
    queryFn: () => listIngredients(),
  })

  const [editingIngredient, setEditingIngredient] = useState<Ingredient | null>(null)
  const [isCreating, setIsCreating] = useState(false)
  const [name, setName] = useState('')
  const [unit, setUnit] = useState('')
  const [currentQuantity, setCurrentQuantity] = useState('')
  const [lowStockThreshold, setLowStockThreshold] = useState('')
  const [error, setError] = useState<string | null>(null)

  const createMutation = useMutation({
    mutationFn: createIngredient,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['ingredients'] })
      closeForm()
    },
    onError: () => setError('Não foi possível salvar. Verifique se o nome já está em uso.'),
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: Parameters<typeof updateIngredient>[1] }) =>
      updateIngredient(id, payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['ingredients'] }),
  })

  function openCreateForm() {
    setName('')
    setUnit('')
    setCurrentQuantity('')
    setLowStockThreshold('')
    setError(null)
    setIsCreating(true)
  }

  function openEditForm(ingredient: Ingredient) {
    setEditingIngredient(ingredient)
    setName(ingredient.name)
    setUnit(ingredient.unit)
    setCurrentQuantity(String(ingredient.currentQuantity))
    setLowStockThreshold(ingredient.lowStockThreshold != null ? String(ingredient.lowStockThreshold) : '')
    setError(null)
  }

  function closeForm() {
    setIsCreating(false)
    setEditingIngredient(null)
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    const payload = {
      name,
      unit,
      currentQuantity: currentQuantity === '' ? undefined : Number(currentQuantity),
      lowStockThreshold: lowStockThreshold === '' ? null : Number(lowStockThreshold),
    }
    if (editingIngredient) {
      updateMutation.mutate(
        { id: editingIngredient.id, payload },
        { onSuccess: closeForm, onError: () => setError('Não foi possível salvar. Verifique se o nome já está em uso.') },
      )
    } else {
      createMutation.mutate(payload)
    }
  }

  function toggleActive(ingredient: Ingredient) {
    updateMutation.mutate({ id: ingredient.id, payload: { active: !ingredient.active } })
  }

  const isFormOpen = isCreating || editingIngredient !== null

  return (
    <div>
      <div className="mb-4 flex items-center justify-between gap-3 rounded-xl border border-gray-200 bg-white p-4 shadow-xs dark:border-white/10 dark:bg-stone-900">
        <div className="flex min-w-0 items-center gap-3">
          <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-teal-600 text-white shadow-sm">
            <Boxes className="h-5 w-5" />
          </span>
          <h1 className="truncate text-lg font-bold text-gray-900 dark:text-white">Insumos</h1>
        </div>
        <Button type="button" onClick={openCreateForm} className="shrink-0 whitespace-nowrap">
          <Plus className="h-4 w-4" />
          <span className="hidden sm:inline">Novo insumo</span>
        </Button>
      </div>

      {isLoading && <p className="text-sm text-gray-500 dark:text-stone-400">Carregando...</p>}

      {ingredients && ingredients.length === 0 && (
        <EmptyState icon={Boxes} message="Nenhum insumo cadastrado ainda." />
      )}

      {ingredients && ingredients.length > 0 && (
        <>
          {/* Mobile: stacked cards */}
          <div className="space-y-2 sm:hidden">
            {ingredients.map((ingredient) => (
              <Card
                key={ingredient.id}
                className={`p-4 ${ingredient.lowStock ? 'border-wine-400 dark:border-wine-500/50' : ''}`}
              >
                <div className="mb-2 flex items-start justify-between gap-2">
                  <div>
                    <p className="font-medium text-gray-800 dark:text-white">{ingredient.name}</p>
                    <p className="text-xs text-gray-500 dark:text-stone-400">
                      {ingredient.currentQuantity} {ingredient.unit}
                    </p>
                  </div>
                  <IngredientStatusBadges ingredient={ingredient} />
                </div>
                <IngredientActionButtons
                  ingredient={ingredient}
                  onEdit={() => openEditForm(ingredient)}
                  onToggleActive={() => toggleActive(ingredient)}
                  align="end"
                />
              </Card>
            ))}
          </div>

          {/* Desktop: table */}
          <div className="hidden sm:block">
            <Table>
              <TableHead>
                <tr>
                  <th className="px-4 py-2 font-medium">Nome</th>
                  <th className="px-4 py-2 font-medium">Saldo</th>
                  <th className="px-4 py-2 font-medium">Status</th>
                  <th className="px-4 py-2" />
                </tr>
              </TableHead>
              <tbody>
                {ingredients.map((ingredient) => (
                  <TableRow
                    key={ingredient.id}
                    className={ingredient.lowStock ? 'bg-wine-100/40 dark:bg-wine-500/5' : ''}
                  >
                    <td className="px-4 py-2 text-gray-800 dark:text-white">{ingredient.name}</td>
                    <td className="px-4 py-2 text-gray-800 dark:text-white">
                      {ingredient.currentQuantity} {ingredient.unit}
                    </td>
                    <td className="px-4 py-2">
                      <IngredientStatusBadges ingredient={ingredient} />
                    </td>
                    <td className="px-4 py-2 text-right">
                      <IngredientActionButtons
                        ingredient={ingredient}
                        onEdit={() => openEditForm(ingredient)}
                        onToggleActive={() => toggleActive(ingredient)}
                        align="end"
                      />
                    </td>
                  </TableRow>
                ))}
              </tbody>
            </Table>
          </div>
        </>
      )}

      {isFormOpen && (
        <Modal title={editingIngredient ? 'Editar insumo' : 'Novo insumo'} onClose={closeForm}>
          <form onSubmit={handleSubmit}>
            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="ingredientName">
              Nome
            </label>
            <input
              id="ingredientName"
              type="text"
              required
              maxLength={100}
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-teal-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-teal-400"
            />

            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="ingredientUnit">
              Unidade <span className="font-normal text-gray-400 dark:text-stone-500">(kg, l, un...)</span>
            </label>
            <input
              id="ingredientUnit"
              type="text"
              required
              maxLength={20}
              value={unit}
              onChange={(e) => setUnit(e.target.value)}
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-teal-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-teal-400"
            />

            <div className="mb-4 grid grid-cols-2 gap-3">
              <div>
                <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="ingredientQuantity">
                  Saldo atual
                </label>
                <input
                  id="ingredientQuantity"
                  type="number"
                  step="0.001"
                  min="0"
                  value={currentQuantity}
                  onChange={(e) => setCurrentQuantity(e.target.value)}
                  className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-teal-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-teal-400"
                />
              </div>
              <div>
                <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="ingredientThreshold">
                  Limiar de alerta
                </label>
                <input
                  id="ingredientThreshold"
                  type="number"
                  step="0.001"
                  min="0"
                  placeholder="Opcional"
                  value={lowStockThreshold}
                  onChange={(e) => setLowStockThreshold(e.target.value)}
                  className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-teal-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-teal-400"
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

function IngredientStatusBadges({ ingredient }: { ingredient: Ingredient }) {
  return (
    <div className="flex flex-wrap items-center gap-1.5">
      {ingredient.lowStock && (
        <span className="flex w-fit items-center gap-1 rounded-full bg-wine-100 px-2 py-0.5 text-xs text-wine-700 dark:bg-wine-500/10 dark:text-wine-400">
          <AlertTriangle className="h-3 w-3" />
          Estoque baixo
        </span>
      )}
      <span
        className={`flex w-fit items-center gap-1 rounded-full px-2 py-0.5 text-xs ${
          ingredient.active
            ? 'bg-sage-100 text-sage-700 dark:bg-sage-500/10 dark:text-sage-400'
            : 'bg-gray-100 text-gray-500 dark:bg-white/10 dark:text-stone-400'
        }`}
      >
        {ingredient.active ? <CheckCircle2 className="h-3 w-3" /> : <Circle className="h-3 w-3" />}
        {ingredient.active ? 'Ativo' : 'Inativo'}
      </span>
    </div>
  )
}

interface IngredientActionButtonsProps {
  ingredient: Ingredient
  onEdit: () => void
  onToggleActive: () => void
  align?: 'start' | 'end'
}

function IngredientActionButtons({ ingredient, onEdit, onToggleActive, align = 'start' }: IngredientActionButtonsProps) {
  return (
    <div className={`flex items-center gap-1 ${align === 'end' ? 'justify-end' : ''}`}>
      <button
        type="button"
        onClick={onEdit}
        title="Editar"
        aria-label="Editar"
        className="rounded-md p-2 text-gray-500 hover:bg-gray-100 hover:text-teal-700 dark:text-stone-400 dark:hover:bg-white/5 dark:hover:text-teal-400"
      >
        <Pencil className="h-[18px] w-[18px]" />
      </button>
      <button
        type="button"
        onClick={onToggleActive}
        title={ingredient.active ? 'Desativar' : 'Ativar'}
        aria-label={ingredient.active ? 'Desativar' : 'Ativar'}
        className={`rounded-md p-2 hover:bg-gray-100 dark:hover:bg-white/5 ${ingredient.active ? 'text-gray-500 hover:text-amber-700 dark:text-stone-400 dark:hover:text-amber-400' : 'text-gray-400 hover:text-green-700 dark:text-stone-500 dark:hover:text-green-400'}`}
      >
        <Power className="h-[18px] w-[18px]" />
      </button>
    </div>
  )
}
