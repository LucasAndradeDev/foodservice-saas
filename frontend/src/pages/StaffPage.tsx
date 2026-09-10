import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import { BarChart3, CheckCircle2, Circle, Clock, Filter, KeyRound, Pencil, Plus, Store, Ticket, Users } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import type { UserRole } from '../auth/types'
import {
  COURIER_VEHICLE_TYPE_LABELS,
  createUser,
  listUsers,
  sendPasswordResetLink,
  updateUser,
  type CourierVehicleType,
  type StaffMember,
} from '../api/users'
import { useAuth } from '../auth/AuthContext'
import { Badge } from '../components/Badge'
import { Button } from '../components/Button'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { DeliveryRiderIcon } from '../components/DeliveryRiderIcon'
import { Dropdown, type DropdownOption } from '../components/Dropdown'
import { Modal } from '../components/Modal'
import { PageHeader } from '../components/PageHeader'
import { SectionTabs } from '../components/SectionTabs'
import { Table, TableHead, TableRow } from '../components/Table'
import { formatBrazilianPhone } from '../utils/phone'

const MANAGEMENT_TABS = [
  { to: '/settings', label: 'Geral', icon: Store },
  { to: '/coupons', label: 'Cupons', icon: Ticket },
  { to: '/happy-hour', label: 'Happy Hour', icon: Clock },
  { to: '/delivery-zones', label: 'Entrega', icon: DeliveryRiderIcon },
  { to: '/staff', label: 'Funcionários', icon: Users },
]

const ROLE_LABELS: Record<UserRole, string> = {
  OWNER: 'Proprietário',
  MANAGER: 'Gerente',
  WAITER: 'Garçom',
  KITCHEN: 'Cozinha',
  CASHIER: 'Caixa',
  COURIER: 'Entregador',
}

const ASSIGNABLE_ROLES: Record<'OWNER' | 'MANAGER', UserRole[]> = {
  OWNER: ['MANAGER', 'WAITER', 'KITCHEN', 'CASHIER', 'COURIER'],
  MANAGER: ['WAITER', 'KITCHEN', 'CASHIER', 'COURIER'],
}

const VEHICLE_TYPE_OPTIONS: CourierVehicleType[] = ['MOTORCYCLE', 'BICYCLE', 'CAR', 'ON_FOOT']

const VEHICLE_TYPE_DROPDOWN_OPTIONS: DropdownOption<CourierVehicleType>[] = VEHICLE_TYPE_OPTIONS.map((option) => ({
  value: option,
  label: COURIER_VEHICLE_TYPE_LABELS[option],
}))

const STATUS_FILTER_OPTIONS: { value: 'active' | 'inactive' | 'all'; label: string }[] = [
  { value: 'active', label: 'Ativos' },
  { value: 'inactive', label: 'Inativos' },
  { value: 'all', label: 'Todos' },
]

// Waiters are by far the most commonly hired role in bulk, so default to it instead of
// whatever happens to be first in ASSIGNABLE_ROLES - MANAGER for an OWNER's dropdown.
function defaultRole(assignableRoles: UserRole[]): UserRole {
  return assignableRoles.includes('WAITER') ? 'WAITER' : assignableRoles[0]
}

// The backend rejects create/update for several distinct reasons (email already in use, courier
// missing phone/vehicle, deactivating the last active OWNER...) - surfacing its actual message
// instead of one fixed guess so the real cause is visible (finding #10, 2026-09-07 review).
function extractErrorMessage(err: unknown, fallback: string) {
  if (isAxiosError(err) && err.response?.data?.message) {
    return err.response.data.message as string
  }
  return fallback
}

export function StaffPage() {
  const { user } = useAuth()
  const canManage = user?.role === 'OWNER' || user?.role === 'MANAGER'
  const queryClient = useQueryClient()
  const assignableRoles = user?.role === 'OWNER' ? ASSIGNABLE_ROLES.OWNER : ASSIGNABLE_ROLES.MANAGER
  const roleOptions: DropdownOption<UserRole>[] = assignableRoles.map((option) => ({ value: option, label: ROLE_LABELS[option] }))

  const [statusFilter, setStatusFilter] = useState<'active' | 'inactive' | 'all'>('active')

  const { data: staff, isLoading } = useQuery({
    queryKey: ['users', statusFilter],
    queryFn: () => listUsers({ active: statusFilter === 'all' ? undefined : statusFilter === 'active' }),
  })

  const [isCreating, setIsCreating] = useState(false)
  const [editingStaff, setEditingStaff] = useState<StaffMember | null>(null)
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [role, setRole] = useState<UserRole>(defaultRole(assignableRoles))
  const [phone, setPhone] = useState('')
  const [vehicleType, setVehicleType] = useState<CourierVehicleType>('MOTORCYCLE')
  const [active, setActive] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [sendingLinkFor, setSendingLinkFor] = useState<StaffMember | null>(null)
  const [sendLinkError, setSendLinkError] = useState<string | null>(null)
  const [linkSentFor, setLinkSentFor] = useState<string | null>(null)

  const createMutation = useMutation({
    mutationFn: createUser,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] })
      resetFields()
      closeForm()
    },
    onError: (err) => setError(extractErrorMessage(err, 'Não foi possível criar. Verifique se o email já está em uso.')),
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: Parameters<typeof updateUser>[1] }) =>
      updateUser(id, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] })
      closeForm()
    },
    onError: (err) => setError(extractErrorMessage(err, 'Não foi possível salvar as alterações.')),
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

  function canEditRow(row: StaffMember) {
    if (!canManage) return false
    if (row.id === user?.id) return false
    if (row.role === 'OWNER') return false
    if (user?.role === 'MANAGER' && row.role === 'MANAGER') return false
    return true
  }

  function resetFields() {
    setName('')
    setEmail('')
    setRole(defaultRole(assignableRoles))
    setPhone('')
    setVehicleType('MOTORCYCLE')
  }

  function openCreateForm() {
    setError(null)
    setIsCreating(true)
  }

  function openEditForm(row: StaffMember) {
    setEditingStaff(row)
    setName(row.name)
    setEmail(row.email)
    setRole(row.role)
    setPhone(row.phone ?? '')
    setVehicleType(row.vehicleType ?? 'MOTORCYCLE')
    setActive(row.active)
    setError(null)
  }

  // Closing (backdrop click, X, or Esc) only hides the modal - it never wipes what the user
  // typed, so an accidental dismiss during "Novo funcionário" doesn't lose the draft. The one
  // exception is leaving an edit in progress, where we clear so those field values don't leak
  // into the next "Novo funcionário" draft.
  function closeForm() {
    if (editingStaff) resetFields()
    setIsCreating(false)
    setEditingStaff(null)
  }

  function openSendLinkConfirm(row: StaffMember) {
    setLinkSentFor(null)
    setSendLinkError(null)
    setSendingLinkFor(row)
  }

  function handleCreateSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    createMutation.mutate(
      role === 'COURIER' ? { name, email, role, phone, vehicleType } : { name, email, role },
    )
  }

  function handleEditSubmit(event: FormEvent) {
    event.preventDefault()
    if (!editingStaff) return
    setError(null)
    updateMutation.mutate({
      id: editingStaff.id,
      payload: role === 'COURIER' ? { name, email, role, active, phone, vehicleType } : { name, email, role, active },
    })
  }

  const isFormOpen = isCreating || editingStaff !== null

  return (
    <div>
      <SectionTabs tabs={MANAGEMENT_TABS} />

      <div className="mb-5 flex flex-col gap-4 rounded-b-2xl border border-gray-200 bg-white p-5 shadow-sm dark:border-white/10 dark:bg-stone-900 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex min-w-0 items-center justify-between gap-3 sm:justify-start">
          <PageHeader icon={Users} title="Funcionários" />
          {/* On mobile the "+" sits beside the title; on sm+ it moves into the row below, next to the filter. */}
          {canManage && (
            <div className="sm:hidden">
              <Button type="button" onClick={openCreateForm} className="shrink-0 whitespace-nowrap">
                <Plus className="h-4 w-4" />
              </Button>
            </div>
          )}
        </div>
        <div className="flex items-center gap-2">
          <Dropdown
            value={statusFilter}
            options={STATUS_FILTER_OPTIONS}
            onChange={setStatusFilter}
            icon={Filter}
            panelClassName="w-40"
            mobileTitle="Filtrar por status"
          />
          {canManage && (
            <div className="hidden sm:block">
              <Button type="button" onClick={openCreateForm} className="shrink-0 whitespace-nowrap">
                <Plus className="h-4 w-4" />
                <span>Novo funcionário</span>
              </Button>
            </div>
          )}
        </div>
      </div>

      {linkSentFor && (
        <p className="mb-4 text-sm text-sage-700 dark:text-sage-400">Link de senha enviado pro email de {linkSentFor}.</p>
      )}

      {isLoading && <p className="text-sm text-gray-500 dark:text-stone-400">Carregando...</p>}

      {staff && staff.length === 0 && (
        <p className="text-sm text-gray-500 dark:text-stone-400">
          {statusFilter === 'active' && 'Nenhum funcionário ativo.'}
          {statusFilter === 'inactive' && 'Nenhum funcionário inativo.'}
          {statusFilter === 'all' && 'Nenhum funcionário cadastrado.'}
        </p>
      )}

      {staff && staff.length > 0 && (
        <>
          {/* Mobile: stacked cards */}
          <div className="space-y-2 sm:hidden">
            {staff.map((row) => (
              <div
                key={row.id}
                className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-white/10 dark:bg-stone-900"
              >
                <div className="mb-1 flex items-center justify-between">
                  <span className="font-medium text-gray-800 dark:text-white">
                    {row.name} {row.id === user?.id && <span className="text-gray-400 dark:text-stone-500">(você)</span>}
                  </span>
                  <Badge tone={row.active ? 'free' : 'neutral'} className="shrink-0">
                    {row.active ? <CheckCircle2 className="h-3 w-3" /> : <Circle className="h-3 w-3" />}
                    {row.active ? 'Ativo' : 'Inativo'}
                  </Badge>
                </div>
                <div className="mb-2 text-sm text-gray-500 dark:text-stone-400">
                  {row.email} · {ROLE_LABELS[row.role]}
                  {row.role === 'COURIER' && row.vehicleType && ` · ${COURIER_VEHICLE_TYPE_LABELS[row.vehicleType]}`}
                </div>
                {(canEditRow(row) || row.role === 'WAITER') && (
                  <div className="flex justify-end gap-1">
                    {row.role === 'WAITER' && (
                      <Link
                        to={`/reports?waiter=${row.id}`}
                        title="Ver desempenho"
                        aria-label="Ver desempenho"
                        className="rounded-md p-2 text-gray-500 hover:bg-gray-100 hover:text-brand-700 dark:text-stone-400 dark:hover:bg-white/5 dark:hover:text-brand-400"
                      >
                        <BarChart3 className="h-[18px] w-[18px]" />
                      </Link>
                    )}
                    {canEditRow(row) && (
                      <>
                        <button
                          type="button"
                          onClick={() => openSendLinkConfirm(row)}
                          title="Enviar link de senha por email"
                          aria-label="Enviar link de senha por email"
                          className="rounded-md p-2 text-gray-500 hover:bg-gray-100 hover:text-brand-700 dark:text-stone-400 dark:hover:bg-white/5 dark:hover:text-brand-400"
                        >
                          <KeyRound className="h-[18px] w-[18px]" />
                        </button>
                        <button
                          type="button"
                          onClick={() => openEditForm(row)}
                          title="Editar"
                          aria-label="Editar"
                          className="rounded-md p-2 text-gray-500 hover:bg-gray-100 hover:text-brand-700 dark:text-stone-400 dark:hover:bg-white/5 dark:hover:text-brand-400"
                        >
                          <Pencil className="h-[18px] w-[18px]" />
                        </button>
                      </>
                    )}
                  </div>
                )}
              </div>
            ))}
          </div>

          {/* Desktop: table */}
          <div className="hidden sm:block">
            <Table>
              <TableHead>
                <tr>
                  <th className="px-4 py-2 font-medium">Nome</th>
                  <th className="px-4 py-2 font-medium">Email</th>
                  <th className="px-4 py-2 font-medium">Papel</th>
                  <th className="px-4 py-2 font-medium">Status</th>
                  <th className="px-4 py-2" />
                </tr>
              </TableHead>
              <tbody>
                {staff.map((row) => (
                  <TableRow key={row.id}>
                    <td className="px-4 py-2 text-gray-800 dark:text-white">
                      {row.name} {row.id === user?.id && <span className="text-gray-400 dark:text-stone-500">(você)</span>}
                    </td>
                    <td className="px-4 py-2 text-gray-600 dark:text-stone-400">{row.email}</td>
                    <td className="px-4 py-2 text-gray-600 dark:text-stone-400">
                      {ROLE_LABELS[row.role]}
                      {row.role === 'COURIER' && row.vehicleType && (
                        <span className="block text-xs text-gray-400 dark:text-stone-500">
                          {COURIER_VEHICLE_TYPE_LABELS[row.vehicleType]}
                        </span>
                      )}
                    </td>
                    <td className="px-4 py-2">
                      <Badge tone={row.active ? 'free' : 'neutral'}>
                        {row.active ? <CheckCircle2 className="h-3 w-3" /> : <Circle className="h-3 w-3" />}
                        {row.active ? 'Ativo' : 'Inativo'}
                      </Badge>
                    </td>
                    <td className="px-4 py-2 text-right">
                      <div className="flex justify-end gap-1">
                        {row.role === 'WAITER' && (
                          <Link
                            to={`/reports?waiter=${row.id}`}
                            title="Ver desempenho"
                            aria-label="Ver desempenho"
                            className="rounded-md p-1.5 text-gray-500 hover:bg-gray-100 hover:text-brand-700 dark:text-stone-400 dark:hover:bg-white/5 dark:hover:text-brand-400"
                          >
                            <BarChart3 className="h-4 w-4" />
                          </Link>
                        )}
                        {canEditRow(row) && (
                          <>
                            <button
                              type="button"
                              onClick={() => openSendLinkConfirm(row)}
                              title="Enviar link de senha por email"
                              aria-label="Enviar link de senha por email"
                              className="rounded-md p-1.5 text-gray-500 hover:bg-gray-100 hover:text-brand-700 dark:text-stone-400 dark:hover:bg-white/5 dark:hover:text-brand-400"
                            >
                              <KeyRound className="h-4 w-4" />
                            </button>
                            <button
                              type="button"
                              onClick={() => openEditForm(row)}
                              title="Editar"
                              aria-label="Editar"
                              className="rounded-md p-1.5 text-gray-500 hover:bg-gray-100 hover:text-brand-700 dark:text-stone-400 dark:hover:bg-white/5 dark:hover:text-brand-400"
                            >
                              <Pencil className="h-4 w-4" />
                            </button>
                          </>
                        )}
                      </div>
                    </td>
                  </TableRow>
                ))}
              </tbody>
            </Table>
          </div>
        </>
      )}

      {isFormOpen && (
        <Modal title={editingStaff ? `Editar ${editingStaff.name}` : 'Novo funcionário'} onClose={closeForm}>
          <form onSubmit={editingStaff ? handleEditSubmit : handleCreateSubmit}>
            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="staffName">
              Nome
            </label>
            <input
              id="staffName"
              type="text"
              required
              maxLength={100}
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
            />

            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="staffEmail">
              Email
            </label>
            <input
              id="staffEmail"
              type="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
            />

            {!editingStaff && (
              <p className="mb-4 text-xs text-gray-500 dark:text-stone-400">
                Vamos mandar um email pro funcionário com um link pra ele definir a própria senha.
              </p>
            )}

            {editingStaff && email !== editingStaff.email && (
              <p className="mb-4 text-xs text-gray-500 dark:text-stone-400">
                O funcionário vai precisar confirmar o novo email de novo.
              </p>
            )}

            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300">Papel</label>
            <div className="mb-4">
              <Dropdown<UserRole> value={role} onChange={setRole} options={roleOptions} fullWidth mobileTitle="Papel" />
            </div>

            {role === 'COURIER' && (
              <>
                <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="staffPhone">
                  Telefone
                </label>
                <input
                  id="staffPhone"
                  type="tel"
                  required
                  maxLength={20}
                  value={phone}
                  onChange={(e) => setPhone(formatBrazilianPhone(e.target.value))}
                  placeholder="(11) 91234-5678"
                  className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
                />

                <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300">Veículo</label>
                <div className="mb-4">
                  <Dropdown<CourierVehicleType>
                    value={vehicleType}
                    onChange={setVehicleType}
                    options={VEHICLE_TYPE_DROPDOWN_OPTIONS}
                    fullWidth
                    mobileTitle="Veículo"
                  />
                </div>
              </>
            )}

            {editingStaff && (
              <label className="mb-4 flex items-center gap-2 text-sm text-gray-700 dark:text-stone-300">
                <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
                Funcionário ativo
              </label>
            )}

            {error && <p className="mb-4 text-sm text-wine-600 dark:text-wine-400">{error}</p>}

            <Button type="submit" disabled={createMutation.isPending || updateMutation.isPending} className="w-full">
              {editingStaff ? 'Salvar' : 'Cadastrar'}
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
