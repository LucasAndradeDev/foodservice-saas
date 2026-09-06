import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AnimatePresence, motion } from 'framer-motion'
import { AlertTriangle, Check, CheckCircle2, Clock, Pencil, Plus, Route, Store, Ticket, Trash2, Users } from 'lucide-react'
import { useEffect, useState, type FormEvent } from 'react'
import {
  createDeliveryZone,
  deleteDeliveryZone,
  listDeliveryZones,
  updateDeliveryZone,
  type DeliveryZone,
} from '../api/deliveryZones'
import { getMyRestaurant, updateMyRestaurant } from '../api/restaurant'
import { Badge } from '../components/Badge'
import { Button } from '../components/Button'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { CurrencyInput } from '../components/CurrencyInput'
import { TableHead, TableRow } from '../components/Table'
import { DeliveryRiderIcon } from '../components/DeliveryRiderIcon'
import { Modal } from '../components/Modal'
import { PageHeader } from '../components/PageHeader'
import { SectionTabs } from '../components/SectionTabs'
import { Toggle } from '../components/Toggle'

const CURRENCY_INPUT_CLASS =
  'w-full rounded-lg border border-gray-300 px-3 py-2.5 text-sm font-medium focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400'

// Illustrative distances for the live pricing preview - not tied to anything real, just enough to
// show what a nearby vs. farther customer would pay as the owner types.
const PREVIEW_DISTANCES_KM = [1, 3, 5]

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

  // Same cache key as RestaurantSettingsPage, which owns editing `address` - this page only
  // reads it (to show whether it geocoded) and writes the two distance-fee fields.
  const { data: restaurant } = useQuery({
    queryKey: ['restaurant'],
    queryFn: getMyRestaurant,
  })

  const [deliveryBaseFee, setDeliveryBaseFee] = useState<number | null>(null)
  const [deliveryFeePerKm, setDeliveryFeePerKm] = useState<number | null>(null)
  const [distanceFeeSaved, setDistanceFeeSaved] = useState(false)

  useEffect(() => {
    if (!restaurant) return
    setDeliveryBaseFee(restaurant.deliveryBaseFee)
    setDeliveryFeePerKm(restaurant.deliveryFeePerKm)
  }, [restaurant])

  const distanceFeeMutation = useMutation({
    mutationFn: updateMyRestaurant,
    onSuccess: (data) => {
      queryClient.setQueryData(['restaurant'], data)
      setDistanceFeeSaved(true)
      window.setTimeout(() => setDistanceFeeSaved(false), 2000)
    },
  })

  function handleSaveDistanceFee(event: FormEvent) {
    event.preventDefault()
    if (deliveryBaseFee == null || deliveryFeePerKm == null) return
    distanceFeeMutation.mutate({ deliveryBaseFee, deliveryFeePerKm })
  }

  const distanceFeeComplete = deliveryBaseFee != null && deliveryFeePerKm != null
  const distanceModeConfigured = restaurant?.deliveryBaseFee != null && restaurant?.deliveryFeePerKm != null
  const locationConfirmed = restaurant?.latitude != null && restaurant?.longitude != null
  const distanceStatus: 'inactive' | 'pending-location' | 'active' = !distanceModeConfigured
    ? 'inactive'
    : locationConfirmed
      ? 'active'
      : 'pending-location'

  const [editingZone, setEditingZone] = useState<DeliveryZone | null>(null)
  const [isCreating, setIsCreating] = useState(false)
  const [neighborhood, setNeighborhood] = useState('')
  const [fee, setFee] = useState<number | null>(null)
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
    setFee(null)
    setError(null)
    setIsCreating(true)
  }

  function openEditForm(zone: DeliveryZone) {
    setEditingZone(zone)
    setNeighborhood(zone.neighborhood)
    setFee(zone.fee)
    setError(null)
  }

  function closeForm() {
    setIsCreating(false)
    setEditingZone(null)
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (fee == null) return
    setError(null)
    if (editingZone) {
      updateMutation.mutate(
        { id: editingZone.id, payload: { neighborhood, fee, active: editingZone.active } },
        { onSuccess: closeForm, onError: () => setError('Não foi possível salvar. Verifique se o bairro já está cadastrado.') },
      )
    } else {
      createMutation.mutate({ neighborhood, fee })
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

      <div className="mb-5 flex flex-wrap items-center justify-between gap-3 rounded-b-2xl border border-gray-200 bg-white p-5 shadow-sm dark:border-white/10 dark:bg-stone-900">
        <div className="flex flex-wrap items-center gap-3">
          <PageHeader icon={DeliveryRiderIcon} title="Zonas de entrega" />
          <Badge tone={distanceStatus === 'active' ? 'neutral' : 'free'}>
            {distanceStatus === 'active' ? 'Alternativa' : 'Método atual'}
          </Badge>
        </div>
        <Button type="button" onClick={openCreateForm} className="shrink-0 whitespace-nowrap">
          <Plus className="h-4 w-4" />
          <span className="hidden sm:inline">Nova zona</span>
        </Button>
      </div>

      <div className="mb-5 overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-sm dark:border-white/10 dark:bg-stone-900">
        <div className="flex flex-wrap items-center justify-between gap-2 border-b border-gray-100 px-5 py-4 dark:border-white/10">
          <div className="flex items-center gap-2">
            <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-brand-50 text-brand-600 dark:bg-brand-500/10 dark:text-brand-400">
              <Route className="h-4 w-4" />
            </span>
            <div>
              <h2 className="text-sm font-semibold text-gray-800 dark:text-white">Taxa por distância</h2>
              <p className="text-xs text-gray-500 dark:text-stone-400">Método prioritário de cálculo de frete</p>
            </div>
          </div>
          {distanceStatus === 'active' && (
            <Badge tone="free" dot>
              Ativo
            </Badge>
          )}
          {distanceStatus === 'pending-location' && (
            <Badge tone="attention" dot>
              Aguardando localização
            </Badge>
          )}
          {distanceStatus === 'inactive' && <Badge tone="neutral">Não configurado</Badge>}
        </div>

        <div className="p-5">
          <p className="mb-4 text-sm text-gray-500 dark:text-stone-400">
            Calcula a entrega pela distância até o cliente — o endereço dele é localizado automaticamente, sem
            precisar cadastrar bairro por bairro. As zonas por bairro logo abaixo passam a ser a alternativa,
            usada só quando essa distância não estiver disponível.
          </p>

          <form onSubmit={handleSaveDistanceFee} className="flex flex-wrap items-end gap-3">
            <div className="w-32">
              <label className="mb-1 block text-xs font-medium text-gray-700 dark:text-stone-300" htmlFor="deliveryBaseFee">
                Taxa base
              </label>
              <CurrencyInput
                id="deliveryBaseFee"
                value={deliveryBaseFee}
                onChange={setDeliveryBaseFee}
                className={CURRENCY_INPUT_CLASS}
              />
            </div>
            <div className="w-32">
              <label className="mb-1 block text-xs font-medium text-gray-700 dark:text-stone-300" htmlFor="deliveryFeePerKm">
                Valor por km
              </label>
              <CurrencyInput
                id="deliveryFeePerKm"
                value={deliveryFeePerKm}
                onChange={setDeliveryFeePerKm}
                className={CURRENCY_INPUT_CLASS}
              />
            </div>
            <Button type="submit" disabled={!distanceFeeComplete || distanceFeeMutation.isPending} className="overflow-hidden">
              <AnimatePresence mode="wait" initial={false}>
                {distanceFeeSaved ? (
                  <motion.span
                    key="saved"
                    initial={{ opacity: 0, y: 6 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0, y: -6 }}
                    transition={{ duration: 0.15 }}
                    className="flex items-center gap-1.5"
                  >
                    <Check className="h-4 w-4" />
                    Salvo
                  </motion.span>
                ) : (
                  <motion.span
                    key="save"
                    initial={{ opacity: 0, y: 6 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0, y: -6 }}
                    transition={{ duration: 0.15 }}
                  >
                    {distanceFeeMutation.isPending ? 'Salvando...' : 'Salvar'}
                  </motion.span>
                )}
              </AnimatePresence>
            </Button>
          </form>

          {distanceFeeComplete && (
            <div className="mt-4 flex flex-wrap items-center gap-x-5 gap-y-2 rounded-xl bg-gray-50 px-4 py-3 dark:bg-white/5">
              <span className="text-xs font-medium text-gray-500 dark:text-stone-400">Prévia:</span>
              {PREVIEW_DISTANCES_KM.map((km) => (
                <div key={km} className="flex items-baseline gap-1.5">
                  <span className="text-xs text-gray-400 dark:text-stone-500">{km} km</span>
                  <span className="text-sm font-semibold text-gray-800 dark:text-white">
                    {currencyFormatter.format(deliveryBaseFee! + deliveryFeePerKm! * km)}
                  </span>
                </div>
              ))}
            </div>
          )}

          {distanceModeConfigured && (
            <div
              className={`mt-3 flex items-center gap-1.5 text-xs font-medium ${
                locationConfirmed ? 'text-sage-600 dark:text-sage-400' : 'text-amber-600 dark:text-amber-400'
              }`}
            >
              {locationConfirmed ? (
                <>
                  <CheckCircle2 className="h-3.5 w-3.5 shrink-0" />
                  Localização do restaurante confirmada.
                </>
              ) : (
                <>
                  <AlertTriangle className="h-3.5 w-3.5 shrink-0" />
                  Não conseguimos localizar o endereço do restaurante — confira em Configurações Gerais.
                </>
              )}
            </div>
          )}
        </div>
      </div>

      {isLoading && <p className="text-sm text-gray-500 dark:text-stone-400">Carregando...</p>}

      {listError && <p className="mb-4 text-sm text-wine-600 dark:text-wine-400">{listError}</p>}

      {zones && zones.length === 0 && (
        <p className="text-sm text-gray-500 dark:text-stone-400">
          Nenhuma zona cadastrada ainda. Cadastre os bairros atendidos e a taxa de entrega de cada um — usada como
          alternativa quando a taxa por distância acima não estiver disponível (ou como método único, se você não
          configurá-la).
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
            <CurrencyInput
              id="zoneFee"
              value={fee}
              onChange={setFee}
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
            />

            {error && <p className="mb-4 text-sm text-wine-600 dark:text-wine-400">{error}</p>}

            <Button type="submit" disabled={fee == null || createMutation.isPending || updateMutation.isPending} className="w-full">
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
