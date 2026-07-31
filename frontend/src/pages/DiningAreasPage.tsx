import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Reorder } from 'framer-motion'
import { GripVertical, MapPin, Pencil, Plus, Trash2 } from 'lucide-react'
import { useEffect, useState, type FormEvent } from 'react'
import {
  createDiningArea,
  deleteDiningArea,
  listDiningAreas,
  reorderDiningAreas,
  updateDiningArea,
  type DiningArea,
} from '../api/diningAreas'
import { useAuth } from '../auth/AuthContext'
import { Modal } from '../components/Modal'

export function DiningAreasPage() {
  const { user } = useAuth()
  const canManage = user?.role === 'OWNER' || user?.role === 'MANAGER'
  const queryClient = useQueryClient()

  const { data: areas, isLoading } = useQuery({
    queryKey: ['dining-areas'],
    queryFn: listDiningAreas,
  })

  const [orderedAreas, setOrderedAreas] = useState<DiningArea[]>([])
  useEffect(() => {
    if (areas) setOrderedAreas(areas)
  }, [areas])

  const [editingArea, setEditingArea] = useState<DiningArea | null>(null)
  const [isCreating, setIsCreating] = useState(false)
  const [name, setName] = useState('')
  const [error, setError] = useState<string | null>(null)

  function invalidate() {
    queryClient.invalidateQueries({ queryKey: ['dining-areas'] })
  }

  const createMutation = useMutation({
    mutationFn: createDiningArea,
    onSuccess: () => {
      invalidate()
      closeForm()
    },
    onError: () => setError('Não foi possível salvar. Verifique se o nome já está em uso.'),
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: Parameters<typeof updateDiningArea>[1] }) =>
      updateDiningArea(id, payload),
    onSuccess: () => {
      invalidate()
      closeForm()
    },
    onError: () => setError('Não foi possível salvar. Verifique se o nome já está em uso.'),
  })

  const deleteMutation = useMutation({
    mutationFn: deleteDiningArea,
    onSuccess: invalidate,
    onError: () => window.alert('Não foi possível excluir. Mova as mesas dessa área antes de excluí-la.'),
  })

  const reorderMutation = useMutation({
    mutationFn: reorderDiningAreas,
    onSuccess: invalidate,
  })

  function openCreateForm() {
    setName('')
    setError(null)
    setIsCreating(true)
  }

  function openEditForm(area: DiningArea) {
    setEditingArea(area)
    setName(area.name)
    setError(null)
  }

  function closeForm() {
    setIsCreating(false)
    setEditingArea(null)
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    if (editingArea) {
      updateMutation.mutate({ id: editingArea.id, payload: { name } })
    } else {
      createMutation.mutate({ name })
    }
  }

  function handleDelete(area: DiningArea) {
    const confirmed = window.confirm(`Excluir a área "${area.name}"? Essa ação não pode ser desfeita.`)
    if (confirmed) {
      deleteMutation.mutate(area.id)
    }
  }

  function handleDragEnd() {
    reorderMutation.mutate(orderedAreas.map((area) => area.id))
  }

  const isFormOpen = isCreating || editingArea !== null

  return (
    <div>
      <div className="mb-4 flex items-center justify-between gap-3 rounded-xl border border-gray-200 bg-white p-4 shadow-xs dark:border-white/10 dark:bg-stone-900">
        <h1 className="flex items-center gap-2 text-lg font-semibold text-gray-800 dark:text-white">
          <MapPin className="h-5 w-5 text-brand-600 dark:text-brand-400" />
          Áreas do salão
        </h1>
        {canManage && (
          <button
            type="button"
            onClick={openCreateForm}
            className="flex items-center gap-1.5 rounded-lg bg-brand-600 px-3.5 py-2 text-sm font-medium text-white hover:bg-brand-700"
          >
            <Plus className="h-4 w-4" />
            Nova área
          </button>
        )}
      </div>

      {isLoading && <p className="text-sm text-gray-500 dark:text-stone-400">Carregando...</p>}

      {orderedAreas.length === 0 && !isLoading && (
        <p className="text-sm text-gray-500 dark:text-stone-400">
          Nenhuma área cadastrada. Sem áreas, as mesas aparecem numa única grade.
        </p>
      )}

      {orderedAreas.length > 0 && (
        <>
          <p className="mb-2 text-xs text-gray-500 dark:text-stone-400">
            Arraste para reordenar como as áreas aparecem na tela de Mesas.
          </p>
          <Reorder.Group
            axis="y"
            values={orderedAreas}
            onReorder={setOrderedAreas}
            className="flex flex-col gap-2"
          >
            {orderedAreas.map((area) => (
              <Reorder.Item
                key={area.id}
                value={area}
                onDragEnd={handleDragEnd}
                className="flex items-center gap-3 rounded-xl border border-gray-200 bg-white p-3 shadow-sm dark:border-white/10 dark:bg-stone-900"
              >
                <GripVertical className="h-4 w-4 shrink-0 cursor-grab text-gray-300 active:cursor-grabbing dark:text-stone-600" />
                <span className="flex-1 text-sm font-medium text-gray-800 dark:text-white">{area.name}</span>
                {canManage && (
                  <div className="flex items-center gap-1">
                    <button
                      type="button"
                      onClick={() => openEditForm(area)}
                      title="Editar"
                      aria-label="Editar"
                      className="rounded-md p-1.5 text-gray-500 hover:bg-gray-100 hover:text-brand-700 dark:text-stone-400 dark:hover:bg-white/5 dark:hover:text-brand-400"
                    >
                      <Pencil className="h-4 w-4" />
                    </button>
                    <button
                      type="button"
                      onClick={() => handleDelete(area)}
                      title="Excluir"
                      aria-label="Excluir"
                      className="rounded-md p-1.5 text-gray-500 hover:bg-red-50 hover:text-red-700 dark:text-stone-400 dark:hover:bg-red-500/10 dark:hover:text-red-400"
                    >
                      <Trash2 className="h-4 w-4" />
                    </button>
                  </div>
                )}
              </Reorder.Item>
            ))}
          </Reorder.Group>
        </>
      )}

      {isFormOpen && (
        <Modal title={editingArea ? 'Editar área' : 'Nova área'} onClose={closeForm}>
          <form onSubmit={handleSubmit}>
            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="areaName">
              Nome
            </label>
            <input
              id="areaName"
              type="text"
              required
              maxLength={100}
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Ex: Salão interno, Varanda"
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
