import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Clock, Pencil, Plus, Store, Ticket, Trash2, Users } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import {
  createDeliveryZone,
  deleteDeliveryZone,
  listDeliveryZones,
  updateDeliveryZone,
  type DeliveryZone,
} from '../api/deliveryZones'
import { Button } from '../components/Button'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { TableHead, TableRow } from '../components/Table'
import { DeliveryRiderIcon } from '../components/DeliveryRiderIcon'
import { Modal } from '../components/Modal'
import { PageHeader } from '../components/PageHeader'
import { SectionTabs } from '../components/SectionTabs'
import { Toggle } from '../components/Toggle'

const MANAGEMENT_TABS = [
  { to: '/settings', label: 'Geral', icon: Store },
  { to: '/coupons', label: 'Cupons', icon: Ticket },
  { to: '/happy-hour', label: 'Happy Hour', icon: Clock },
  { to: '/delivery-zones', label: 'Entrega', icon: DeliveryRiderIcon },
  { to: '/staff', label: 'Funcionários', icon: Users },
]

const currencyFormatter = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

export function DeliveryZonesPage() {
  const queryClient = useQueryClient()

  const { data: zones, isLoading } = useQuery({
    queryKey: ['deliveryZones'],
    queryFn: listDeliveryZones,
  })

  const [editingZone, setEditingZone] = useState<DeliveryZone | null>(null)
  const [isCreating, setIsCreating] = useState(false)
  const [neighborhood, setNeighborhood] = useState('')
  const [fee, setFee] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [listError, setListError] = useState<string | null>(null)
  const [zoneToDelete, setZoneToDelete] = useState<DeliveryZone | null>(null)

  const createMutation = useMutation({
    mutationFn: createDeliveryZone,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['deliveryZones'] })
      closeForm()
    },
    onError: () => setError('Não foi possível salvar. Verifique se o bairro já está cadastrado.'),
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: Parameters<typeof updateDeliveryZone>[1] }) =>
      updateDeliveryZone(id, payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['deliveryZones'] }),
  })

  const deleteMutation = useMutation({
    mutationFn: deleteDeliveryZone,
    onSuccess: () => {
      setListError(null)
      queryClient.invalidateQueries({ queryKey: ['deliveryZones'] })
    },
    onError: () => setListError('Não foi possível excluir a zona de entrega.'),
  })

  function openCreateForm() {
    setNeighborhood('')
    setFee('')
    setError(null)
    setIsCreating(true)
  }

  function openEditForm(zone: DeliveryZone) {
    setEditingZone(zone)
    setNeighborhood(zone.neighborhood)
    setFee(String(zone.fee))
    setError(null)
  }

  function closeForm() {
    setIsCreating(false)
    setEditingZone(null)
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    if (editingZone) {
      updateMutation.mutate(
        { id: editingZone.id, payload: { neighborhood, fee: Number(fee), active: editingZone.active } },
        { onSuccess: closeForm, onError: () => setError('Não foi possível salvar. Verifique se o bairro já está cadastrado.') },
      )
    } else {
      createMutation.mutate({ neighborhood, fee: Number(fee) })
    }
  }

  function toggleActive(zone: DeliveryZone) {
    updateMutation.mutate({ id: zone.id, payload: { neighborhood: zone.neighborhood, fee: zone.fee, active: !zone.active } })
  }

  function confirmDelete() {
    if (zoneToDelete) {
      setListError(null)
      deleteMutation.mutate(zoneToDelete.id)
    }
    setZoneToDelete(null)
  }

  const isFormOpen = isCreating || editingZone !== null

  return (
    <div>
      <SectionTabs tabs={MANAGEMENT_TABS} />

      <div className="mb-5 flex items-center justify-between gap-3 rounded-b-2xl border border-gray-200 bg-white p-5 shadow-sm dark:border-white/10 dark:bg-stone-900">
        <PageHeader icon={DeliveryRiderIcon} title="Zonas de entrega" />
        <Button type="button" onClick={openCreateForm} className="shrink-0 whitespace-nowrap">
          <Plus className="h-4 w-4" />
          <span className="hidden sm:inline">Nova zona</span>
        </Button>
      </div>

      {isLoading && <p className="text-sm text-gray-500 dark:text-stone-400">Carregando...</p>}

      {listError && <p className="mb-4 text-sm text-wine-600 dark:text-wine-400">{listError}</p>}

      {zones && zones.length === 0 && (
        <p className="text-sm text-gray-500 dark:text-stone-400">
          Nenhuma zona cadastrada ainda. Cadastre os bairros atendidos e a taxa de entrega de cada um — sem isso, o
          cliente não consegue fazer pedido de delivery pro bairro dele.
        </p>
      )}

      {zones && zones.length > 0 && (
        <>
          {/* Mobile: stacked cards, no horizontal scroll needed */}
          <div className="space-y-2 sm:hidden">
            {zones.map((zone) => (
              <div
                key={zone.id}
                className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-white/10 dark:bg-stone-900"
              >
                <div className="mb-2 flex items-start justify-between gap-2">
                  <span className="font-medium text-gray-800 dark:text-white">{zone.neighborhood}</span>
                  <Toggle checked={zone.active} onChange={() => toggleActive(zone)} />
                </div>
                <div className="mb-3 text-sm text-gray-600 dark:text-stone-400">
                  {currencyFormatter.format(zone.fee)}
                </div>
                <div className="flex items-center justify-end gap-1 border-t border-gray-100 pt-2 dark:border-white/10">
                  <button
                    type="button"
                    onClick={() => openEditForm(zone)}
                    title="Editar"
                    aria-label="Editar"
                    className="rounded-md p-2 text-gray-500 hover:bg-gray-100 hover:text-brand-700 dark:text-stone-400 dark:hover:bg-white/5 dark:hover:text-brand-400"
                  >
                    <Pencil className="h-[18px] w-[18px]" />
                  </button>
                  <button
                    type="button"
                    onClick={() => setZoneToDelete(zone)}
                    title="Excluir"
                    aria-label="Excluir"
                    className="rounded-md p-2 text-gray-500 hover:bg-gray-100 hover:text-wine-700 dark:text-stone-400 dark:hover:bg-white/5 dark:hover:text-wine-400"
                  >
                    <Trash2 className="h-[18px] w-[18px]" />
                  </button>
                </div>
              </div>
            ))}
          </div>

          {/* Desktop: table */}
          <div className="hidden overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm sm:block dark:border-white/10 dark:bg-stone-900">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <TableHead>
                  <tr>
                    <th className="px-4 py-2 font-medium">Bairro</th>
                    <th className="px-4 py-2 font-medium">Taxa</th>
                    <th className="px-4 py-2 font-medium">Ativa</th>
                    <th className="px-4 py-2" />
                  </tr>
                </TableHead>
                <tbody>
                  {zones.map((zone) => (
                    <TableRow key={zone.id}>
                      <td className="px-4 py-2 font-medium text-gray-800 dark:text-white">{zone.neighborhood}</td>
                      <td className="px-4 py-2 text-gray-600 dark:text-stone-400">{currencyFormatter.format(zone.fee)}</td>
                      <td className="px-4 py-2">
                        <Toggle checked={zone.active} onChange={() => toggleActive(zone)} />
                      </td>
                      <td className="px-4 py-2 text-right">
                        <div className="flex items-center justify-end gap-1">
                          <button
                            type="button"
                            onClick={() => openEditForm(zone)}
                            title="Editar"
                            aria-label="Editar"
                            className="rounded-md p-1.5 text-gray-500 hover:bg-gray-100 hover:text-brand-700 dark:text-stone-400 dark:hover:bg-white/5 dark:hover:text-brand-400"
                          >
                            <Pencil className="h-4 w-4" />
                          </button>
                          <button
                            type="button"
                            onClick={() => setZoneToDelete(zone)}
                            title="Excluir"
                            aria-label="Excluir"
                            className="rounded-md p-1.5 text-gray-500 hover:bg-gray-100 hover:text-wine-700 dark:text-stone-400 dark:hover:bg-white/5 dark:hover:text-wine-400"
                          >
                            <Trash2 className="h-4 w-4" />
                          </button>
                        </div>
                      </td>
                    </TableRow>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </>
      )}

      {isFormOpen && (
        <Modal title={editingZone ? `Editar ${editingZone.neighborhood}` : 'Nova zona de entrega'} onClose={closeForm}>
          <form onSubmit={handleSubmit}>
            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="zoneNeighborhood">
              Bairro
            </label>
            <input
              id="zoneNeighborhood"
              type="text"
              required
              maxLength={100}
              value={neighborhood}
              onChange={(e) => setNeighborhood(e.target.value)}
              placeholder="Ex: Centro"
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
            />

            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="zoneFee">
              Taxa de entrega
            </label>
            <input
              id="zoneFee"
              type="number"
              required
              min="0"
              step="0.01"
              value={fee}
              onChange={(e) => setFee(e.target.value)}
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
            />

            {error && <p className="mb-4 text-sm text-wine-600 dark:text-wine-400">{error}</p>}

            <Button type="submit" disabled={createMutation.isPending || updateMutation.isPending} className="w-full">
              Salvar
            </Button>
          </form>
        </Modal>
      )}

      {zoneToDelete && (
        <ConfirmDialog
          title="Excluir zona de entrega"
          message={`Excluir a zona "${zoneToDelete.neighborhood}"? Essa ação não pode ser desfeita.`}
          confirmLabel="Excluir"
          cancelLabel="Voltar"
          danger
          onConfirm={confirmDelete}
          onCancel={() => setZoneToDelete(null)}
        />
      )}
    </div>
  )
}
