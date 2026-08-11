import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { BarChart3, CheckCircle2, Circle, Clock, Filter, KeyRound, Pencil, Plus, Store, Ticket, Users } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import type { UserRole } from '../auth/types'
import { createUser, listUsers, sendPasswordResetLink, updateUser, type StaffMember } from '../api/users'
import { useAuth } from '../auth/AuthContext'
import { Button } from '../components/Button'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { Dropdown } from '../components/Dropdown'
import { Modal } from '../components/Modal'
import { SectionTabs } from '../components/SectionTabs'
import { Table, TableHead, TableRow } from '../components/Table'

const MANAGEMENT_TABS = [
  { to: '/settings', label: 'Geral', icon: Store },
  { to: '/coupons', label: 'Cupons', icon: Ticket },
  { to: '/happy-hour', label: 'Happy Hour', icon: Clock },
  { to: '/staff', label: 'Funcionários', icon: Users },
]

const ROLE_LABELS: Record<UserRole, string> = {
  OWNER: 'Proprietário',
  MANAGER: 'Gerente',
  WAITER: 'Garçom',
  KITCHEN: 'Cozinha',
  CASHIER: 'Caixa',
}

const ASSIGNABLE_ROLES: Record<'OWNER' | 'MANAGER', UserRole[]> = {
  OWNER: ['MANAGER', 'WAITER', 'KITCHEN', 'CASHIER'],
  MANAGER: ['WAITER', 'KITCHEN', 'CASHIER'],
}

const STATUS_FILTER_OPTIONS: { value: 'active' | 'inactive' | 'all'; label: string }[] = [
  { value: 'active', label: 'Ativos' },
  { value: 'inactive', label: 'Inativos' },
  { value: 'all', label: 'Todos' },
]

export function StaffPage() {
  const { user } = useAuth()
  const queryClient = useQueryClient()
  const assignableRoles = user?.role === 'OWNER' ? ASSIGNABLE_ROLES.OWNER : ASSIGNABLE_ROLES.MANAGER

  const [statusFilter, setStatusFilter] = useState<'active' | 'inactive' | 'all'>('active')

  const { data: staff, isLoading } = useQuery({
    queryKey: ['users', statusFilter],
    queryFn: () => listUsers(statusFilter === 'all' ? undefined : statusFilter === 'active'),
  })

  const [isCreating, setIsCreating] = useState(false)
  const [editingStaff, setEditingStaff] = useState<StaffMember | null>(null)
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [role, setRole] = useState<UserRole>(assignableRoles[0])
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
    onError: () => setError('Não foi possível criar. Verifique se o email já está em uso.'),
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

  function canEditRow(row: StaffMember) {
    if (row.id === user?.id) return false
    if (row.role === 'OWNER') return false
    if (user?.role === 'MANAGER' && row.role === 'MANAGER') return false
    return true
  }

  function openCreateForm() {
    setName('')
    setEmail('')
    setRole(assignableRoles[0])
    setError(null)
    setIsCreating(true)
  }

  function openEditForm(row: StaffMember) {
    setEditingStaff(row)
    setName(row.name)
    setRole(row.role)
    setActive(row.active)
    setError(null)
  }

  function closeForm() {
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
    createMutation.mutate({ name, email, role })
  }

  function handleEditSubmit(event: FormEvent) {
    event.preventDefault()
    if (!editingStaff) return
    setError(null)
    updateMutation.mutate({ id: editingStaff.id, payload: { name, role, active } })
  }

  return (
    <div>
      <SectionTabs tabs={MANAGEMENT_TABS} />

      <div className="mb-5 flex flex-col gap-4 rounded-b-2xl border border-gray-200 bg-white p-5 shadow-sm dark:border-white/10 dark:bg-stone-900 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex min-w-0 items-center justify-between gap-3 sm:justify-start">
          <div className="flex min-w-0 items-center gap-3">
            <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-brand-600 text-white shadow-sm">
              <Users className="h-5 w-5" />
            </span>
            <h1 className="truncate text-lg font-bold text-gray-900 dark:text-white">Funcionários</h1>
          </div>
          {/* On mobile the "+" sits beside the title; on sm+ it moves into the row below, next to the filter. */}
          <div className="sm:hidden">
            <Button type="button" onClick={openCreateForm} className="shrink-0 whitespace-nowrap">
              <Plus className="h-4 w-4" />
            </Button>
          </div>
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
          <div className="hidden sm:block">
            <Button type="button" onClick={openCreateForm} className="shrink-0 whitespace-nowrap">
              <Plus className="h-4 w-4" />
              <span>Novo funcionário</span>
            </Button>
          </div>
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
                  <span
                    className={`flex items-center gap-1 rounded-full px-2 py-0.5 text-xs ${
                      row.active
                        ? 'bg-sage-100 text-sage-700 dark:bg-sage-500/10 dark:text-sage-400'
                        : 'bg-gray-100 text-gray-500 dark:bg-white/10 dark:text-stone-400'
                    }`}
                  >
                    {row.active ? <CheckCircle2 className="h-3 w-3" /> : <Circle className="h-3 w-3" />}
                    {row.active ? 'Ativo' : 'Inativo'}
                  </span>
                </div>
                <div className="mb-2 text-sm text-gray-500 dark:text-stone-400">
                  {row.email} · {ROLE_LABELS[row.role]}
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
                    <td className="px-4 py-2 text-gray-600 dark:text-stone-400">{ROLE_LABELS[row.role]}</td>
                    <td className="px-4 py-2">
                      <span
                        className={`flex w-fit items-center gap-1 rounded-full px-2 py-0.5 text-xs ${
                          row.active
                            ? 'bg-sage-100 text-sage-700 dark:bg-sage-500/10 dark:text-sage-400'
                            : 'bg-gray-100 text-gray-500 dark:bg-white/10 dark:text-stone-400'
                        }`}
                      >
                        {row.active ? <CheckCircle2 className="h-3 w-3" /> : <Circle className="h-3 w-3" />}
                        {row.active ? 'Ativo' : 'Inativo'}
                      </span>
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

      {isCreating && (
        <Modal title="Novo funcionário" onClose={closeForm}>
          <form onSubmit={handleCreateSubmit}>
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

            <p className="mb-4 text-xs text-gray-500 dark:text-stone-400">
              Vamos mandar um email pro funcionário com um link pra ele definir a própria senha.
            </p>

            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="staffRole">
              Papel
            </label>
            <select
              id="staffRole"
              value={role}
              onChange={(e) => setRole(e.target.value as UserRole)}
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
            >
              {assignableRoles.map((option) => (
                <option key={option} value={option}>
                  {ROLE_LABELS[option]}
                </option>
              ))}
            </select>

            {error && <p className="mb-4 text-sm text-wine-600 dark:text-wine-400">{error}</p>}

            <Button type="submit" disabled={createMutation.isPending} className="w-full">
              Cadastrar
            </Button>
          </form>
        </Modal>
      )}

      {editingStaff && (
        <Modal title={`Editar ${editingStaff.name}`} onClose={closeForm}>
          <form onSubmit={handleEditSubmit}>
            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="editStaffName">
              Nome
            </label>
            <input
              id="editStaffName"
              type="text"
              required
              maxLength={100}
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
            />

            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="editStaffRole">
              Papel
            </label>
            <select
              id="editStaffRole"
              value={role}
              onChange={(e) => setRole(e.target.value as UserRole)}
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
            >
              {assignableRoles.map((option) => (
                <option key={option} value={option}>
                  {ROLE_LABELS[option]}
                </option>
              ))}
            </select>

            <label className="mb-4 flex items-center gap-2 text-sm text-gray-700 dark:text-stone-300">
              <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
              Funcionário ativo
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
