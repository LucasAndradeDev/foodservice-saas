import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Reorder } from 'framer-motion'
import { GripVertical, LayoutGrid, MapPin, Move, Pencil, Plus, Trash2 } from 'lucide-react'
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
import { ConfirmDialog } from '../components/ConfirmDialog'
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
  const [areaPendingDelete, setAreaPendingDelete] = useState<DiningArea | null>(null)
  const [name, setName] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [pageError, setPageError] = useState<string | null>(null)

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
    onSuccess: () => {
      invalidate()
      setAreaPendingDelete(null)
    },
    onError: () => setPageError('Não foi possível excluir. Mova as mesas dessa área antes de excluí-la.'),
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
    setPageError(null)
    setAreaPendingDelete(area)
  }

  function confirmDelete() {
    if (!areaPendingDelete) return
    deleteMutation.mutate(areaPendingDelete.id)
  }

  function handleDragEnd() {
    reorderMutation.mutate(orderedAreas.map((area) => area.id))
  }

  const isFormOpen = isCreating || editingArea !== null

  return (
    <div>
      <div className="mb-5 flex flex-col gap-4 rounded-2xl border border-gray-200 bg-white p-5 shadow-sm dark:border-white/10 dark:bg-stone-900 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-center gap-3">
          <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-brand-600 text-white shadow-sm">
            <MapPin className="h-5 w-5" />
          </span>
          <h1 className="text-lg font-bold text-gray-900 dark:text-white">Áreas do salão</h1>
        </div>
        {canManage && (
          <button
            type="button"
            onClick={openCreateForm}
            className="flex items-center justify-center gap-2 rounded-xl bg-brand-600 px-4 py-3.5 text-base font-semibold text-white shadow-sm transition-all hover:bg-brand-700 hover:shadow-md active:scale-[0.98] sm:py-2.5 sm:text-sm"
          >
            <Plus className="h-5 w-5 sm:h-4 sm:w-4" />
            Nova área
          </button>
        )}
      </div>

      {pageError && <p className="mb-4 text-sm text-red-600 dark:text-red-400">{pageError}</p>}

      {isLoading && <p className="text-sm text-gray-500 dark:text-stone-400">Carregando...</p>}

      {orderedAreas.length === 0 && !isLoading && (
        <div className="flex flex-col items-center gap-3 rounded-2xl border border-dashed border-gray-300 bg-gray-50 py-16 text-center dark:border-white/10 dark:bg-white/5">
          <span className="flex h-14 w-14 items-center justify-center rounded-full bg-white text-gray-300 shadow-sm dark:bg-stone-800 dark:text-stone-600">
            <LayoutGrid className="h-7 w-7" />
          </span>
          <p className="text-sm text-gray-500 dark:text-stone-400">
            Nenhuma área cadastrada. Sem áreas, as mesas aparecem numa única grade.
          </p>
          {canManage && (
            <button
              type="button"
              onClick={openCreateForm}
              className="mt-1 flex items-center gap-2 rounded-xl bg-brand-600 px-4 py-2 text-sm font-semibold text-white shadow-sm transition hover:bg-brand-700"
            >
              <Plus className="h-4 w-4" />
              Criar primeira área
            </button>
          )}
        </div>
      )}

      {orderedAreas.length > 0 && (
        <>
          <p className="mb-3 flex items-center gap-2 px-1 text-xs font-medium text-gray-500 dark:text-stone-400">
            <Move className="h-3.5 w-3.5" />
            Arraste pra reordenar como as áreas aparecem na tela de Mesas.
          </p>
          <Reorder.Group
            axis="y"
            values={orderedAreas}
            onReorder={setOrderedAreas}
            className="flex flex-col gap-2.5"
          >
            {orderedAreas.map((area, index) => (
              <Reorder.Item
                key={area.id}
                value={area}
                onDragEnd={handleDragEnd}
                className="flex items-center gap-3 rounded-2xl border border-gray-200 bg-white p-3 shadow-sm dark:border-white/10 dark:bg-stone-900"
              >
                <GripVertical className="h-5 w-5 shrink-0 cursor-grab text-gray-300 active:cursor-grabbing dark:text-stone-600" />
                <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-brand-100 text-sm font-bold text-brand-700 dark:bg-brand-500/20 dark:text-brand-400">
                  {index + 1}
                </span>
                <span className="flex-1 text-sm font-semibold text-gray-900 dark:text-white">{area.name}</span>
                {canManage && (
                  <div className="flex items-center gap-1">
                    <button
                      type="button"
                      onClick={() => openEditForm(area)}
                      title="Editar"
                      aria-label="Editar"
                      className="rounded-xl p-2.5 text-gray-500 hover:bg-gray-100 hover:text-brand-700 dark:text-stone-400 dark:hover:bg-white/5 dark:hover:text-brand-400"
                    >
                      <Pencil className="h-4 w-4" />
                    </button>
                    <button
                      type="button"
                      onClick={() => handleDelete(area)}
                      title="Excluir"
                      aria-label="Excluir"
                      className="rounded-xl p-2.5 text-gray-500 hover:bg-red-50 hover:text-red-700 dark:text-stone-400 dark:hover:bg-red-500/10 dark:hover:text-red-400"
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
              className="mb-4 w-full rounded-xl border border-gray-200 px-3 py-2.5 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
            />

            {error && <p className="mb-4 text-sm text-red-600 dark:text-red-400">{error}</p>}

            <button
              type="submit"
              disabled={createMutation.isPending || updateMutation.isPending}
              className="w-full rounded-xl bg-brand-600 px-3 py-2.5 text-sm font-semibold text-white shadow-sm transition hover:bg-brand-700 disabled:opacity-50"
            >
              Salvar
            </button>
          </form>
        </Modal>
      )}

      {areaPendingDelete && (
        <ConfirmDialog
          title="Excluir área"
          message={`Excluir a área "${areaPendingDelete.name}"? Essa ação não pode ser desfeita.`}
          confirmLabel="Excluir"
          cancelLabel="Voltar"
          danger
          isLoading={deleteMutation.isPending}
          onConfirm={confirmDelete}
          onCancel={() => setAreaPendingDelete(null)}
        />
      )}
    </div>
  )
}
