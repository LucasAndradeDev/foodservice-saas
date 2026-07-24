import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import type { UserRole } from '../auth/types'
import { createUser, listUsers, updateUser, type StaffMember } from '../api/users'
import { useAuth } from '../auth/AuthContext'
import { Modal } from '../components/Modal'

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

export function StaffPage() {
  const { user } = useAuth()
  const queryClient = useQueryClient()
  const assignableRoles = user?.role === 'OWNER' ? ASSIGNABLE_ROLES.OWNER : ASSIGNABLE_ROLES.MANAGER

  const { data: staff, isLoading } = useQuery({
    queryKey: ['users'],
    queryFn: () => listUsers(),
  })

  const [isCreating, setIsCreating] = useState(false)
  const [editingStaff, setEditingStaff] = useState<StaffMember | null>(null)
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [role, setRole] = useState<UserRole>(assignableRoles[0])
  const [active, setActive] = useState(true)
  const [error, setError] = useState<string | null>(null)

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

  function canEditRow(row: StaffMember) {
    if (row.id === user?.id) return false
    if (row.role === 'OWNER') return false
    if (user?.role === 'MANAGER' && row.role === 'MANAGER') return false
    return true
  }

  function openCreateForm() {
    setName('')
    setEmail('')
    setPassword('')
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

  function handleCreateSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    createMutation.mutate({ name, email, password, role })
  }

  function handleEditSubmit(event: FormEvent) {
    event.preventDefault()
    if (!editingStaff) return
    setError(null)
    updateMutation.mutate({ id: editingStaff.id, payload: { name, role, active } })
  }

  return (
    <div>
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-lg font-semibold text-gray-800">Funcionários</h1>
        <button
          type="button"
          onClick={openCreateForm}
          className="rounded-md bg-gray-900 px-3 py-1.5 text-sm font-medium text-white hover:bg-gray-800"
        >
          Novo funcionário
        </button>
      </div>

      {isLoading && <p className="text-sm text-gray-500">Carregando...</p>}

      {staff && (
        <div className="overflow-hidden rounded-lg border border-gray-200 bg-white">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 text-left text-gray-500">
              <tr>
                <th className="px-4 py-2 font-medium">Nome</th>
                <th className="px-4 py-2 font-medium">Email</th>
                <th className="px-4 py-2 font-medium">Papel</th>
                <th className="px-4 py-2 font-medium">Status</th>
                <th className="px-4 py-2" />
              </tr>
            </thead>
            <tbody>
              {staff.map((row) => (
                <tr key={row.id} className="border-t border-gray-100">
                  <td className="px-4 py-2 text-gray-800">
                    {row.name} {row.id === user?.id && <span className="text-gray-400">(você)</span>}
                  </td>
                  <td className="px-4 py-2 text-gray-600">{row.email}</td>
                  <td className="px-4 py-2 text-gray-600">{ROLE_LABELS[row.role]}</td>
                  <td className="px-4 py-2">
                    <span
                      className={`rounded-full px-2 py-0.5 text-xs ${
                        row.active ? 'bg-green-100 text-green-700' : 'bg-gray-100 text-gray-500'
                      }`}
                    >
                      {row.active ? 'Ativo' : 'Inativo'}
                    </span>
                  </td>
                  <td className="px-4 py-2 text-right">
                    {canEditRow(row) && (
                      <button
                        type="button"
                        onClick={() => openEditForm(row)}
                        className="text-gray-600 hover:underline"
                      >
                        Editar
                      </button>
                    )}
                  </td>
                </tr>
              ))}
              {staff.length === 0 && (
                <tr>
                  <td colSpan={5} className="px-4 py-6 text-center text-gray-500">
                    Nenhum funcionário cadastrado.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      {isCreating && (
        <Modal title="Novo funcionário" onClose={closeForm}>
          <form onSubmit={handleCreateSubmit}>
            <label className="mb-1 block text-sm font-medium text-gray-700" htmlFor="staffName">
              Nome
            </label>
            <input
              id="staffName"
              type="text"
              required
              maxLength={100}
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-gray-500 focus:outline-none"
            />

            <label className="mb-1 block text-sm font-medium text-gray-700" htmlFor="staffEmail">
              Email
            </label>
            <input
              id="staffEmail"
              type="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-gray-500 focus:outline-none"
            />

            <label className="mb-1 block text-sm font-medium text-gray-700" htmlFor="staffPassword">
              Senha
            </label>
            <input
              id="staffPassword"
              type="password"
              required
              minLength={6}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-gray-500 focus:outline-none"
            />

            <label className="mb-1 block text-sm font-medium text-gray-700" htmlFor="staffRole">
              Papel
            </label>
            <select
              id="staffRole"
              value={role}
              onChange={(e) => setRole(e.target.value as UserRole)}
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-gray-500 focus:outline-none"
            >
              {assignableRoles.map((option) => (
                <option key={option} value={option}>
                  {ROLE_LABELS[option]}
                </option>
              ))}
            </select>

            {error && <p className="mb-4 text-sm text-red-600">{error}</p>}

            <button
              type="submit"
              disabled={createMutation.isPending}
              className="w-full rounded-md bg-gray-900 px-3 py-2 text-sm font-medium text-white hover:bg-gray-800 disabled:opacity-50"
            >
              Cadastrar
            </button>
          </form>
        </Modal>
      )}

      {editingStaff && (
        <Modal title={`Editar ${editingStaff.name}`} onClose={closeForm}>
          <form onSubmit={handleEditSubmit}>
            <label className="mb-1 block text-sm font-medium text-gray-700" htmlFor="editStaffName">
              Nome
            </label>
            <input
              id="editStaffName"
              type="text"
              required
              maxLength={100}
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-gray-500 focus:outline-none"
            />

            <label className="mb-1 block text-sm font-medium text-gray-700" htmlFor="editStaffRole">
              Papel
            </label>
            <select
              id="editStaffRole"
              value={role}
              onChange={(e) => setRole(e.target.value as UserRole)}
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-gray-500 focus:outline-none"
            >
              {assignableRoles.map((option) => (
                <option key={option} value={option}>
                  {ROLE_LABELS[option]}
                </option>
              ))}
            </select>

            <label className="mb-4 flex items-center gap-2 text-sm text-gray-700">
              <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
              Funcionário ativo
            </label>

            {error && <p className="mb-4 text-sm text-red-600">{error}</p>}

            <button
              type="submit"
              disabled={updateMutation.isPending}
              className="w-full rounded-md bg-gray-900 px-3 py-2 text-sm font-medium text-white hover:bg-gray-800 disabled:opacity-50"
            >
              Salvar
            </button>
          </form>
        </Modal>
      )}
    </div>
  )
}
