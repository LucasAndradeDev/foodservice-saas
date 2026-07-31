import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CheckCircle2, Circle, Pencil, Plus, Power, Tag } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { createCategory, listCategories, updateCategory, type Category } from '../api/categories'
import { useAuth } from '../auth/AuthContext'
import { Modal } from '../components/Modal'

export function CategoriesPage() {
  const { user } = useAuth()
  const canManage = user?.role === 'OWNER' || user?.role === 'MANAGER'
  const queryClient = useQueryClient()

  const { data: categories, isLoading } = useQuery({
    queryKey: ['categories'],
    queryFn: () => listCategories(),
  })

  const [editingCategory, setEditingCategory] = useState<Category | null>(null)
  const [isCreating, setIsCreating] = useState(false)
  const [name, setName] = useState('')
  const [error, setError] = useState<string | null>(null)

  const createMutation = useMutation({
    mutationFn: createCategory,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['categories'] })
      closeForm()
    },
    onError: () => setError('Não foi possível salvar. Verifique se o nome já está em uso.'),
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: Parameters<typeof updateCategory>[1] }) =>
      updateCategory(id, payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['categories'] }),
  })

  function openCreateForm() {
    setName('')
    setError(null)
    setIsCreating(true)
  }

  function openEditForm(category: Category) {
    setEditingCategory(category)
    setName(category.name)
    setError(null)
  }

  function closeForm() {
    setIsCreating(false)
    setEditingCategory(null)
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    if (editingCategory) {
      updateMutation.mutate(
        { id: editingCategory.id, payload: { name } },
        { onSuccess: closeForm, onError: () => setError('Não foi possível salvar. Verifique se o nome já está em uso.') },
      )
    } else {
      createMutation.mutate({ name })
    }
  }

  function toggleActive(category: Category) {
    updateMutation.mutate({ id: category.id, payload: { active: !category.active } })
  }

  const isFormOpen = isCreating || editingCategory !== null

  return (
    <div>
      <div className="mb-4 flex items-center justify-between gap-3 rounded-xl border border-gray-200 bg-white p-4 shadow-xs dark:border-white/10 dark:bg-stone-900">
        <h1 className="flex items-center gap-2 text-lg font-semibold text-gray-800 dark:text-white">
          <Tag className="h-5 w-5 text-brand-600 dark:text-brand-400" />
          Categorias
        </h1>
        {canManage && (
          <button
            type="button"
            onClick={openCreateForm}
            className="flex items-center gap-1.5 rounded-lg bg-brand-600 px-3.5 py-2 text-sm font-medium text-white hover:bg-brand-700"
          >
            <Plus className="h-4 w-4" />
            Nova categoria
          </button>
        )}
      </div>

      {isLoading && <p className="text-sm text-gray-500 dark:text-stone-400">Carregando...</p>}

      {categories && categories.length === 0 && (
        <p className="text-sm text-gray-500 dark:text-stone-400">Nenhuma categoria cadastrada.</p>
      )}

      {categories && categories.length > 0 && (
        <>
          {/* Mobile: stacked cards */}
          <div className="space-y-2 sm:hidden">
            {categories.map((category) => (
              <div
                key={category.id}
                className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-white/10 dark:bg-stone-900"
              >
                <div className="mb-2 flex items-center justify-between">
                  <span className="font-medium text-gray-800 dark:text-white">{category.name}</span>
                  <span
                    className={`flex items-center gap-1 rounded-full px-2 py-0.5 text-xs ${
                      category.active
                        ? 'bg-green-100 text-green-700 dark:bg-green-500/10 dark:text-green-400'
                        : 'bg-gray-100 text-gray-500 dark:bg-white/10 dark:text-stone-400'
                    }`}
                  >
                    {category.active ? <CheckCircle2 className="h-3 w-3" /> : <Circle className="h-3 w-3" />}
                    {category.active ? 'Ativa' : 'Inativa'}
                  </span>
                </div>
                {canManage && (
                  <CategoryActionButtons
                    category={category}
                    onEdit={() => openEditForm(category)}
                    onToggleActive={() => toggleActive(category)}
                  />
                )}
              </div>
            ))}
          </div>

          {/* Desktop: table */}
          <div className="hidden overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm sm:block dark:border-white/10 dark:bg-stone-900">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 text-left text-gray-500 dark:bg-white/5 dark:text-stone-400">
                <tr>
                  <th className="px-4 py-2 font-medium">Nome</th>
                  <th className="px-4 py-2 font-medium">Status</th>
                  {canManage && <th className="px-4 py-2" />}
                </tr>
              </thead>
              <tbody>
                {categories.map((category) => (
                  <tr key={category.id} className="border-t border-gray-100 dark:border-white/10">
                    <td className="px-4 py-2 text-gray-800 dark:text-white">{category.name}</td>
                    <td className="px-4 py-2">
                      <span
                        className={`flex w-fit items-center gap-1 rounded-full px-2 py-0.5 text-xs ${
                          category.active
                            ? 'bg-green-100 text-green-700 dark:bg-green-500/10 dark:text-green-400'
                            : 'bg-gray-100 text-gray-500 dark:bg-white/10 dark:text-stone-400'
                        }`}
                      >
                        {category.active ? <CheckCircle2 className="h-3 w-3" /> : <Circle className="h-3 w-3" />}
                        {category.active ? 'Ativa' : 'Inativa'}
                      </span>
                    </td>
                    {canManage && (
                      <td className="px-4 py-2 text-right">
                        <CategoryActionButtons
                          category={category}
                          onEdit={() => openEditForm(category)}
                          onToggleActive={() => toggleActive(category)}
                          align="end"
                        />
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}

      {isFormOpen && (
        <Modal title={editingCategory ? 'Editar categoria' : 'Nova categoria'} onClose={closeForm}>
          <form onSubmit={handleSubmit}>
            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="categoryName">
              Nome
            </label>
            <input
              id="categoryName"
              type="text"
              required
              maxLength={100}
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
            />

            {error && <p className="mb-4 text-sm text-red-600 dark:text-red-400">{error}</p>}

            <button
              type="submit"
              disabled={createMutation.isPending || updateMutation.isPending}
              className="w-full rounded-md bg-brand-600 px-3 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50"
            >
              Salvar
            </button>
          </form>
        </Modal>
      )}
    </div>
  )
}

interface CategoryActionButtonsProps {
  category: Category
  onEdit: () => void
  onToggleActive: () => void
  align?: 'start' | 'end'
}

function CategoryActionButtons({ category, onEdit, onToggleActive, align = 'start' }: CategoryActionButtonsProps) {
  return (
    <div className={`flex items-center gap-1 ${align === 'end' ? 'justify-end' : ''}`}>
      <button
        type="button"
        onClick={onEdit}
        title="Editar"
        aria-label="Editar"
        className="rounded-md p-1.5 text-gray-500 hover:bg-gray-100 hover:text-brand-700 dark:text-stone-400 dark:hover:bg-white/5 dark:hover:text-brand-400"
      >
        <Pencil className="h-4 w-4" />
      </button>
      <button
        type="button"
        onClick={onToggleActive}
        title={category.active ? 'Desativar' : 'Ativar'}
        aria-label={category.active ? 'Desativar' : 'Ativar'}
        className={`rounded-md p-1.5 hover:bg-gray-100 dark:hover:bg-white/5 ${category.active ? 'text-gray-500 hover:text-amber-700 dark:text-stone-400 dark:hover:text-amber-400' : 'text-gray-400 hover:text-green-700 dark:text-stone-500 dark:hover:text-green-400'}`}
      >
        <Power className="h-4 w-4" />
      </button>
    </div>
  )
}
