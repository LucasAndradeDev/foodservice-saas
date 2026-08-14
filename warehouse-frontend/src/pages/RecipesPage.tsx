import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ClipboardList, Plus, Trash2 } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { listIngredients } from '../api/ingredients'
import { listMoraProducts } from '../api/moraProducts'
import { createRecipe, listRecipes, updateRecipe, type Recipe, type RecipeItemPayload } from '../api/recipes'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { EmptyState } from '../components/EmptyState'
import { Modal } from '../components/Modal'
import { Table, TableHead, TableRow } from '../components/Table'

interface ItemRow {
  key: number
  ingredientId: string
  quantityPerUnit: string
}

let nextRowKey = 0

function emptyRow(): ItemRow {
  return { key: nextRowKey++, ingredientId: '', quantityPerUnit: '' }
}

export function RecipesPage() {
  const queryClient = useQueryClient()

  const { data: recipes, isLoading } = useQuery({
    queryKey: ['recipes'],
    queryFn: () => listRecipes(),
  })
  const { data: moraProducts } = useQuery({
    queryKey: ['moraProducts'],
    queryFn: () => listMoraProducts(),
  })
  const { data: ingredients } = useQuery({
    queryKey: ['ingredients', { active: true }],
    queryFn: () => listIngredients(true),
  })

  const [editingRecipe, setEditingRecipe] = useState<Recipe | null>(null)
  const [isCreating, setIsCreating] = useState(false)
  const [moraProductId, setMoraProductId] = useState('')
  const [moraProductName, setMoraProductName] = useState('')
  const [rows, setRows] = useState<ItemRow[]>([emptyRow()])
  const [error, setError] = useState<string | null>(null)

  const createMutation = useMutation({
    mutationFn: createRecipe,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['recipes'] })
      closeForm()
    },
    onError: () => setError('Não foi possível salvar. Esse produto já pode ter uma ficha técnica.'),
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: Parameters<typeof updateRecipe>[1] }) => updateRecipe(id, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['recipes'] })
      closeForm()
    },
    onError: () => setError('Não foi possível salvar.'),
  })

  const recipedProductIds = new Set(recipes?.map((recipe) => recipe.moraProductId))
  const availableProducts = moraProducts?.filter((product) => !recipedProductIds.has(product.productId))

  function openCreateForm() {
    setMoraProductId('')
    setMoraProductName('')
    setRows([emptyRow()])
    setError(null)
    setIsCreating(true)
  }

  function openEditForm(recipe: Recipe) {
    setEditingRecipe(recipe)
    setMoraProductName(recipe.moraProductName)
    setRows(
      recipe.items.length > 0
        ? recipe.items.map((item) => ({ key: nextRowKey++, ingredientId: item.ingredientId, quantityPerUnit: String(item.quantityPerUnit) }))
        : [emptyRow()],
    )
    setError(null)
  }

  function closeForm() {
    setIsCreating(false)
    setEditingRecipe(null)
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

  function handleProductChange(productId: string) {
    setMoraProductId(productId)
    setMoraProductName(moraProducts?.find((product) => product.productId === productId)?.productName ?? '')
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)

    const items: RecipeItemPayload[] = rows
      .filter((row) => row.ingredientId && row.quantityPerUnit !== '')
      .map((row) => ({ ingredientId: row.ingredientId, quantityPerUnit: Number(row.quantityPerUnit) }))

    if (items.length === 0) {
      setError('Adicione ao menos um insumo.')
      return
    }

    if (editingRecipe) {
      updateMutation.mutate({ id: editingRecipe.id, payload: { moraProductName, items } })
    } else {
      createMutation.mutate({ moraProductId, moraProductName, items })
    }
  }

  const isFormOpen = isCreating || editingRecipe !== null

  return (
    <div>
      <div className="mb-4 flex items-center justify-between gap-3 rounded-xl border border-gray-200 bg-white p-4 shadow-xs dark:border-white/10 dark:bg-stone-900">
        <div className="flex min-w-0 items-center gap-3">
          <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-teal-600 text-white shadow-sm">
            <ClipboardList className="h-5 w-5" />
          </span>
          <h1 className="truncate text-lg font-bold text-gray-900 dark:text-white">Receitas</h1>
        </div>
        <Button type="button" onClick={openCreateForm} className="shrink-0 whitespace-nowrap">
          <Plus className="h-4 w-4" />
          <span className="hidden sm:inline">Nova receita</span>
        </Button>
      </div>

      {isLoading && <p className="text-sm text-gray-500 dark:text-stone-400">Carregando...</p>}

      {recipes && recipes.length === 0 && (
        <EmptyState icon={ClipboardList} message="Nenhuma ficha técnica cadastrada ainda." />
      )}

      {recipes && recipes.length > 0 && (
        <>
          {/* Mobile: stacked cards */}
          <div className="space-y-2 sm:hidden">
            {recipes.map((recipe) => (
              <Card key={recipe.id} className="cursor-pointer p-4" onClick={() => openEditForm(recipe)}>
                <p className="font-medium text-gray-800 dark:text-white">{recipe.moraProductName}</p>
                <p className="text-xs text-gray-500 dark:text-stone-400">{summarizeItems(recipe)}</p>
              </Card>
            ))}
          </div>

          {/* Desktop: table */}
          <div className="hidden sm:block">
            <Table>
              <TableHead>
                <tr>
                  <th className="px-4 py-2 font-medium">Produto (Morá)</th>
                  <th className="px-4 py-2 font-medium">Insumos</th>
                  <th className="px-4 py-2" />
                </tr>
              </TableHead>
              <tbody>
                {recipes.map((recipe) => (
                  <TableRow key={recipe.id} className="cursor-pointer" onClick={() => openEditForm(recipe)}>
                    <td className="px-4 py-2 text-gray-800 dark:text-white">{recipe.moraProductName}</td>
                    <td className="px-4 py-2 text-gray-600 dark:text-stone-300">{summarizeItems(recipe)}</td>
                    <td className="px-4 py-2 text-right">
                      <Button type="button" variant="ghost" size="sm" onClick={() => openEditForm(recipe)}>
                        Editar
                      </Button>
                    </td>
                  </TableRow>
                ))}
              </tbody>
            </Table>
          </div>
        </>
      )}

      {isFormOpen && (
        <Modal title={editingRecipe ? 'Editar receita' : 'Nova receita'} onClose={closeForm}>
          <form onSubmit={handleSubmit}>
            {editingRecipe ? (
              <>
                <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="recipeProductName">
                  Produto (Morá)
                </label>
                <input
                  id="recipeProductName"
                  type="text"
                  required
                  maxLength={150}
                  value={moraProductName}
                  onChange={(e) => setMoraProductName(e.target.value)}
                  className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-teal-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-teal-400"
                />
              </>
            ) : (
              <>
                <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="recipeProduct">
                  Produto (Morá)
                </label>
                <select
                  id="recipeProduct"
                  required
                  value={moraProductId}
                  onChange={(e) => handleProductChange(e.target.value)}
                  className="mb-4 w-full rounded-md border border-gray-300 bg-white px-3 py-2 text-sm focus:border-teal-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-teal-400"
                >
                  <option value="" disabled>
                    Selecione...
                  </option>
                  {availableProducts?.map((product) => (
                    <option key={product.productId} value={product.productId}>
                      {product.productName}
                    </option>
                  ))}
                </select>
                {availableProducts && availableProducts.length === 0 && (
                  <p className="-mt-3 mb-4 text-xs text-gray-500 dark:text-stone-400">
                    Todos os produtos ativos do Morá já têm ficha técnica.
                  </p>
                )}
              </>
            )}

            <p className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300">Insumos por unidade vendida</p>
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
                    placeholder="Qtd/un."
                    value={row.quantityPerUnit}
                    onChange={(e) => updateRow(row.key, { quantityPerUnit: e.target.value })}
                    className="w-24 rounded-md border border-gray-300 px-2 py-2 text-sm focus:border-teal-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-teal-400"
                  />
                  <button
                    type="button"
                    onClick={() => removeRow(row.key)}
                    disabled={rows.length === 1}
                    aria-label="Remover insumo"
                    className="shrink-0 rounded-md p-2 text-gray-400 hover:bg-gray-100 hover:text-wine-600 disabled:cursor-default disabled:opacity-40 dark:text-stone-500 dark:hover:bg-white/5 dark:hover:text-wine-400"
                  >
                    <Trash2 className="h-4 w-4" />
                  </button>
                </div>
              ))}
            </div>

            <Button type="button" variant="ghost" size="sm" onClick={addRow} className="mb-4">
              <Plus className="h-4 w-4" />
              Adicionar insumo
            </Button>

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

function summarizeItems(recipe: Recipe) {
  return recipe.items.map((item) => `${item.ingredientName} ${item.quantityPerUnit}`).join(', ')
}
