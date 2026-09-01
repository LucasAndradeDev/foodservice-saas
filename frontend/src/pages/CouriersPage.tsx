import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Bike, CheckCircle2, Circle, Clock, KeyRound, Pencil, Plus, Store, Ticket, Users } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import {
  COURIER_VEHICLE_TYPE_LABELS,
  createUser,
  listUsers,
  sendPasswordResetLink,
  updateUser,
  type CourierVehicleType,
  type StaffMember,
} from '../api/users'
import { Badge } from '../components/Badge'
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
  { to: '/couriers', label: 'Entregadores', icon: Bike },
  { to: '/staff', label: 'Funcionários', icon: Users },
]

const VEHICLE_TYPE_OPTIONS: CourierVehicleType[] = ['MOTORCYCLE', 'BICYCLE', 'CAR', 'ON_FOOT']

export function CouriersPage() {
  const queryClient = useQueryClient()

  const { data: couriers, isLoading } = useQuery({
    queryKey: ['users', 'COURIER'],
    queryFn: () => listUsers({ role: 'COURIER' }),
  })

  const [editingCourier, setEditingCourier] = useState<StaffMember | null>(null)
  const [isCreating, setIsCreating] = useState(false)
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [phone, setPhone] = useState('')
  const [vehicleType, setVehicleType] = useState<CourierVehicleType>('MOTORCYCLE')
  const [notes, setNotes] = useState('')
  const [active, setActive] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [sendingLinkFor, setSendingLinkFor] = useState<StaffMember | null>(null)
  const [sendLinkError, setSendLinkError] = useState<string | null>(null)
  const [linkSentFor, setLinkSentFor] = useState<string | null>(null)

  const createMutation = useMutation({
    mutationFn: createUser,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] })
      closeForm()
    },
    onError: () => setError('Não foi possível salvar o entregador. Verifique se o email já está em uso.'),
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: Parameters<typeof updateUser>[1] }) =>
      updateUser(id, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] })
      closeForm()
    },
    onError: () => setError('Não foi possível salvar as alterações.'),
  })

  const sendResetLinkMutation = useMutation({
    mutationFn: (id: string) => sendPasswordResetLink(id),
    onSuccess: () => {
      setLinkSentFor(sendingLinkFor?.name ?? null)
      setSendingLinkFor(null)
      setSendLinkError(null)
    },
    onError: () => setSendLinkError('Não foi possível enviar o link. Tente de novo.'),
  })

  function openCreateForm() {
    setName('')
    setEmail('')
    setPhone('')
    setVehicleType('MOTORCYCLE')
    setNotes('')
    setError(null)
    setIsCreating(true)
  }

  function openEditForm(courier: StaffMember) {
    setEditingCourier(courier)
    setName(courier.name)
    setPhone(courier.phone ?? '')
    setVehicleType(courier.vehicleType ?? 'MOTORCYCLE')
    setNotes(courier.notes ?? '')
    setActive(courier.active)
    setError(null)
  }

  function closeForm() {
    setIsCreating(false)
    setEditingCourier(null)
  }

  function openSendLinkConfirm(courier: StaffMember) {
    setLinkSentFor(null)
    setSendLinkError(null)
    setSendingLinkFor(courier)
  }

  function handleCreateSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    createMutation.mutate({ name, email, role: 'COURIER', phone, vehicleType, notes: notes || undefined })
  }

  function handleEditSubmit(event: FormEvent) {
    event.preventDefault()
    if (!editingCourier) return
    setError(null)
    updateMutation.mutate({
      id: editingCourier.id,
      payload: { name, phone, vehicleType, notes: notes || undefined, active },
    })
  }

  function toggleActive(courier: StaffMember) {
    updateMutation.mutate({ id: courier.id, payload: { active: !courier.active } })
  }

  return (
    <div>
      <SectionTabs tabs={MANAGEMENT_TABS} />

      <div className="mb-5 flex items-center justify-between gap-3 rounded-b-2xl border border-gray-200 bg-white p-5 shadow-sm dark:border-white/10 dark:bg-stone-900">
        <PageHeader icon={Bike} title="Entregadores" />
        <Button type="button" onClick={openCreateForm} className="shrink-0 whitespace-nowrap">
          <Plus className="h-4 w-4" />
          <span className="hidden sm:inline">Novo entregador</span>
        </Button>
      </div>

      {linkSentFor && (
        <p className="mb-4 text-sm text-sage-700 dark:text-sage-400">Link de senha enviado pro email de {linkSentFor}.</p>
      )}

      {isLoading && <p className="text-sm text-gray-500 dark:text-stone-400">Carregando...</p>}

      {couriers && couriers.length === 0 && (
        <p className="text-sm text-gray-500 dark:text-stone-400">
          Nenhum entregador cadastrado ainda. Cadastre quem faz as entregas pra poder atribuir cada pedido a um
          entregador na tela de Delivery - ele recebe um email pra criar a própria senha e acessar a tela dele.
        </p>
      )}

      {couriers && couriers.length > 0 && (
        <>
          {/* Mobile: stacked cards, no horizontal scroll needed */}
          <div className="space-y-2 sm:hidden">
            {couriers.map((courier) => (
              <div
                key={courier.id}
                className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-white/10 dark:bg-stone-900"
              >
                <div className="mb-2 flex items-start justify-between gap-2">
                  <span className="font-medium text-gray-800 dark:text-white">{courier.name}</span>
                  <Toggle checked={courier.active} onChange={() => toggleActive(courier)} />
                </div>
                <div className="mb-1 text-sm text-gray-600 dark:text-stone-400">
                  {courier.email} · {courier.phone}
                </div>
                <div className="mb-3 text-xs text-gray-400 dark:text-stone-500">
                  {courier.vehicleType && COURIER_VEHICLE_TYPE_LABELS[courier.vehicleType]}
                  {courier.notes && ` · ${courier.notes}`}
                </div>
                <div className="flex items-center justify-end gap-1 border-t border-gray-100 pt-2 dark:border-white/10">
                  <button
                    type="button"
                    onClick={() => openSendLinkConfirm(courier)}
                    title="Enviar link de senha por email"
                    aria-label="Enviar link de senha por email"
                    className="rounded-md p-2 text-gray-500 hover:bg-gray-100 hover:text-brand-700 dark:text-stone-400 dark:hover:bg-white/5 dark:hover:text-brand-400"
                  >
                    <KeyRound className="h-[18px] w-[18px]" />
                  </button>
                  <button
                    type="button"
                    onClick={() => openEditForm(courier)}
                    title="Editar"
                    aria-label="Editar"
                    className="rounded-md p-2 text-gray-500 hover:bg-gray-100 hover:text-brand-700 dark:text-stone-400 dark:hover:bg-white/5 dark:hover:text-brand-400"
                  >
                    <Pencil className="h-[18px] w-[18px]" />
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
                    <th className="px-4 py-2 font-medium">Nome</th>
                    <th className="px-4 py-2 font-medium">Email</th>
                    <th className="px-4 py-2 font-medium">Telefone</th>
                    <th className="px-4 py-2 font-medium">Veículo</th>
                    <th className="px-4 py-2 font-medium">Status</th>
                    <th className="px-4 py-2" />
                  </tr>
                </TableHead>
                <tbody>
                  {couriers.map((courier) => (
                    <TableRow key={courier.id}>
                      <td className="px-4 py-2 font-medium text-gray-800 dark:text-white">
                        {courier.name}
                        {courier.notes && (
                          <span className="block text-xs font-normal text-gray-400 dark:text-stone-500">
                            {courier.notes}
                          </span>
                        )}
                      </td>
                      <td className="px-4 py-2 text-gray-600 dark:text-stone-400">{courier.email}</td>
                      <td className="px-4 py-2 text-gray-600 dark:text-stone-400">{courier.phone}</td>
                      <td className="px-4 py-2 text-gray-600 dark:text-stone-400">
                        {courier.vehicleType && COURIER_VEHICLE_TYPE_LABELS[courier.vehicleType]}
                      </td>
                      <td className="px-4 py-2">
                        <div className="flex items-center gap-2">
                          <Toggle checked={courier.active} onChange={() => toggleActive(courier)} />
                          <Badge tone={courier.active ? 'free' : 'neutral'}>
                            {courier.active ? <CheckCircle2 className="h-3 w-3" /> : <Circle className="h-3 w-3" />}
                            {courier.active ? 'Ativo' : 'Inativo'}
                          </Badge>
                        </div>
                      </td>
                      <td className="px-4 py-2 text-right">
                        <div className="flex items-center justify-end gap-1">
                          <button
                            type="button"
                            onClick={() => openSendLinkConfirm(courier)}
                            title="Enviar link de senha por email"
                            aria-label="Enviar link de senha por email"
                            className="rounded-md p-1.5 text-gray-500 hover:bg-gray-100 hover:text-brand-700 dark:text-stone-400 dark:hover:bg-white/5 dark:hover:text-brand-400"
                          >
                            <KeyRound className="h-4 w-4" />
                          </button>
                          <button
                            type="button"
                            onClick={() => openEditForm(courier)}
                            title="Editar"
                            aria-label="Editar"
                            className="rounded-md p-1.5 text-gray-500 hover:bg-gray-100 hover:text-brand-700 dark:text-stone-400 dark:hover:bg-white/5 dark:hover:text-brand-400"
                          >
                            <Pencil className="h-4 w-4" />
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

      {isCreating && (
        <Modal title="Novo entregador" onClose={closeForm}>
          <form onSubmit={handleCreateSubmit}>
            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="courierName">
              Nome
            </label>
            <input
              id="courierName"
              type="text"
              required
              maxLength={100}
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Ex: João"
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
            />

            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="courierEmail">
              Email
            </label>
            <input
              id="courierEmail"
              type="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
            />

            <p className="mb-4 text-xs text-gray-500 dark:text-stone-400">
              Vamos mandar um email pro entregador com um link pra ele definir a própria senha e acessar a tela dele.
            </p>

            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="courierPhone">
              Telefone
            </label>
            <input
              id="courierPhone"
              type="tel"
              required
              maxLength={20}
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
              placeholder="(11) 91234-5678"
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
            />

            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="courierVehicleType">
              Veículo
            </label>
            <select
              id="courierVehicleType"
              value={vehicleType}
              onChange={(e) => setVehicleType(e.target.value as CourierVehicleType)}
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
            >
              {VEHICLE_TYPE_OPTIONS.map((option) => (
                <option key={option} value={option}>
                  {COURIER_VEHICLE_TYPE_LABELS[option]}
                </option>
              ))}
            </select>

            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="courierNotes">
              Observações (opcional)
            </label>
            <input
              id="courierNotes"
              type="text"
              maxLength={255}
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder="Ex: só disponível fim de semana"
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
            />

            {error && <p className="mb-4 text-sm text-wine-600 dark:text-wine-400">{error}</p>}

            <Button type="submit" disabled={createMutation.isPending} className="w-full">
              Cadastrar
            </Button>
          </form>
        </Modal>
      )}

      {editingCourier && (
        <Modal title={`Editar ${editingCourier.name}`} onClose={closeForm}>
          <form onSubmit={handleEditSubmit}>
            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="editCourierName">
              Nome
            </label>
            <input
              id="editCourierName"
              type="text"
              required
              maxLength={100}
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
            />

            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="editCourierPhone">
              Telefone
            </label>
            <input
              id="editCourierPhone"
              type="tel"
              required
              maxLength={20}
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
            />

            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="editCourierVehicleType">
              Veículo
            </label>
            <select
              id="editCourierVehicleType"
              value={vehicleType}
              onChange={(e) => setVehicleType(e.target.value as CourierVehicleType)}
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
            >
              {VEHICLE_TYPE_OPTIONS.map((option) => (
                <option key={option} value={option}>
                  {COURIER_VEHICLE_TYPE_LABELS[option]}
                </option>
              ))}
            </select>

            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="editCourierNotes">
              Observações (opcional)
            </label>
            <input
              id="editCourierNotes"
              type="text"
              maxLength={255}
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
            />

            <label className="mb-4 flex items-center gap-2 text-sm text-gray-700 dark:text-stone-300">
              <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
              Entregador ativo
            </label>

            {error && <p className="mb-4 text-sm text-wine-600 dark:text-wine-400">{error}</p>}

            <Button type="submit" disabled={updateMutation.isPending} className="w-full">
              Salvar
            </Button>
          </form>
        </Modal>
      )}

      {sendingLinkFor && (
        <ConfirmDialog
          title="Enviar link de senha"
          message={
            sendLinkError ??
            `Vamos mandar um email pra ${sendingLinkFor.email} com um link pra ${sendingLinkFor.name} definir uma senha nova.`
          }
          confirmLabel="Enviar"
          isLoading={sendResetLinkMutation.isPending}
          onConfirm={() => sendResetLinkMutation.mutate(sendingLinkFor.id)}
          onCancel={() => setSendingLinkFor(null)}
        />
      )}
    </div>
  )
}
