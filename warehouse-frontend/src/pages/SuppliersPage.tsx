import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CheckCircle2, Circle, Pencil, Plus, Power, Truck } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { createSupplier, listSuppliers, updateSupplier, type Supplier } from '../api/suppliers'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { EmptyState } from '../components/EmptyState'
import { Modal } from '../components/Modal'
import { Table, TableHead, TableRow } from '../components/Table'

export function SuppliersPage() {
  const queryClient = useQueryClient()

  const { data: suppliers, isLoading } = useQuery({
    queryKey: ['suppliers'],
    queryFn: () => listSuppliers(),
  })

  const [editingSupplier, setEditingSupplier] = useState<Supplier | null>(null)
  const [isCreating, setIsCreating] = useState(false)
  const [name, setName] = useState('')
  const [contact, setContact] = useState('')
  const [error, setError] = useState<string | null>(null)

  const createMutation = useMutation({
    mutationFn: createSupplier,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['suppliers'] })
      closeForm()
    },
    onError: () => setError('Não foi possível salvar. Verifique se o nome já está em uso.'),
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: Parameters<typeof updateSupplier>[1] }) =>
      updateSupplier(id, payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['suppliers'] }),
  })

  function openCreateForm() {
    setName('')
    setContact('')
    setError(null)
    setIsCreating(true)
  }

  function openEditForm(supplier: Supplier) {
    setEditingSupplier(supplier)
    setName(supplier.name)
    setContact(supplier.contact ?? '')
    setError(null)
  }

  function closeForm() {
    setIsCreating(false)
    setEditingSupplier(null)
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    const payload = { name, contact: contact === '' ? undefined : contact }
    if (editingSupplier) {
      updateMutation.mutate(
        { id: editingSupplier.id, payload },
        { onSuccess: closeForm, onError: () => setError('Não foi possível salvar. Verifique se o nome já está em uso.') },
      )
    } else {
      createMutation.mutate(payload)
    }
  }

  function toggleActive(supplier: Supplier) {
    updateMutation.mutate({ id: supplier.id, payload: { active: !supplier.active } })
  }

  const isFormOpen = isCreating || editingSupplier !== null

  return (
    <div>
      <div className="mb-4 flex items-center justify-between gap-3 rounded-xl border border-gray-200 bg-white p-4 shadow-xs dark:border-white/10 dark:bg-stone-900">
        <div className="flex min-w-0 items-center gap-3">
          <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-teal-600 text-white shadow-sm">
            <Truck className="h-5 w-5" />
          </span>
          <h1 className="truncate text-lg font-bold text-gray-900 dark:text-white">Fornecedores</h1>
        </div>
        <Button type="button" onClick={openCreateForm} className="shrink-0 whitespace-nowrap">
          <Plus className="h-4 w-4" />
          <span className="hidden sm:inline">Novo fornecedor</span>
        </Button>
      </div>

      {isLoading && <p className="text-sm text-gray-500 dark:text-stone-400">Carregando...</p>}

      {suppliers && suppliers.length === 0 && (
        <EmptyState icon={Truck} message="Nenhum fornecedor cadastrado ainda." />
      )}

      {suppliers && suppliers.length > 0 && (
        <>
          {/* Mobile: stacked cards */}
          <div className="space-y-2 sm:hidden">
            {suppliers.map((supplier) => (
              <Card key={supplier.id} className="p-4">
                <div className="mb-2 flex items-start justify-between gap-2">
                  <div>
                    <p className="font-medium text-gray-800 dark:text-white">{supplier.name}</p>
                    {supplier.contact && <p className="text-xs text-gray-500 dark:text-stone-400">{supplier.contact}</p>}
                  </div>
                  <SupplierStatusBadge supplier={supplier} />
                </div>
                <SupplierActionButtons
                  supplier={supplier}
                  onEdit={() => openEditForm(supplier)}
                  onToggleActive={() => toggleActive(supplier)}
                  align="end"
                />
              </Card>
            ))}
          </div>

          {/* Desktop: table */}
          <div className="hidden sm:block">
            <Table>
              <TableHead>
                <tr>
                  <th className="px-4 py-2 font-medium">Nome</th>
                  <th className="px-4 py-2 font-medium">Contato</th>
                  <th className="px-4 py-2 font-medium">Status</th>
                  <th className="px-4 py-2" />
                </tr>
              </TableHead>
              <tbody>
                {suppliers.map((supplier) => (
                  <TableRow key={supplier.id}>
                    <td className="px-4 py-2 text-gray-800 dark:text-white">{supplier.name}</td>
                    <td className="px-4 py-2 text-gray-800 dark:text-white">{supplier.contact ?? '-'}</td>
                    <td className="px-4 py-2">
                      <SupplierStatusBadge supplier={supplier} />
                    </td>
                    <td className="px-4 py-2 text-right">
                      <SupplierActionButtons
                        supplier={supplier}
                        onEdit={() => openEditForm(supplier)}
                        onToggleActive={() => toggleActive(supplier)}
                        align="end"
                      />
                    </td>
                  </TableRow>
                ))}
              </tbody>
            </Table>
          </div>
        </>
      )}

      {isFormOpen && (
        <Modal title={editingSupplier ? 'Editar fornecedor' : 'Novo fornecedor'} onClose={closeForm}>
          <form onSubmit={handleSubmit}>
            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="supplierName">
              Nome
            </label>
            <input
              id="supplierName"
              type="text"
              required
              maxLength={100}
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-teal-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-teal-400"
            />

            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="supplierContact">
              Contato <span className="font-normal text-gray-400 dark:text-stone-500">(opcional)</span>
            </label>
            <input
              id="supplierContact"
              type="text"
              maxLength={200}
              value={contact}
              onChange={(e) => setContact(e.target.value)}
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-teal-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-teal-400"
            />

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

function SupplierStatusBadge({ supplier }: { supplier: Supplier }) {
  return (
    <span
      className={`flex w-fit items-center gap-1 rounded-full px-2 py-0.5 text-xs ${
        supplier.active
          ? 'bg-sage-100 text-sage-700 dark:bg-sage-500/10 dark:text-sage-400'
          : 'bg-gray-100 text-gray-500 dark:bg-white/10 dark:text-stone-400'
      }`}
    >
      {supplier.active ? <CheckCircle2 className="h-3 w-3" /> : <Circle className="h-3 w-3" />}
      {supplier.active ? 'Ativo' : 'Inativo'}
    </span>
  )
}

interface SupplierActionButtonsProps {
  supplier: Supplier
  onEdit: () => void
  onToggleActive: () => void
  align?: 'start' | 'end'
}

function SupplierActionButtons({ supplier, onEdit, onToggleActive, align = 'start' }: SupplierActionButtonsProps) {
  return (
    <div className={`flex items-center gap-1 ${align === 'end' ? 'justify-end' : ''}`}>
      <button
        type="button"
        onClick={onEdit}
        title="Editar"
        aria-label="Editar"
        className="rounded-md p-2 text-gray-500 hover:bg-gray-100 hover:text-teal-700 dark:text-stone-400 dark:hover:bg-white/5 dark:hover:text-teal-400"
      >
        <Pencil className="h-[18px] w-[18px]" />
      </button>
      <button
        type="button"
        onClick={onToggleActive}
        title={supplier.active ? 'Desativar' : 'Ativar'}
        aria-label={supplier.active ? 'Desativar' : 'Ativar'}
        className={`rounded-md p-2 hover:bg-gray-100 dark:hover:bg-white/5 ${supplier.active ? 'text-gray-500 hover:text-amber-700 dark:text-stone-400 dark:hover:text-amber-400' : 'text-gray-400 hover:text-green-700 dark:text-stone-500 dark:hover:text-green-400'}`}
      >
        <Power className="h-[18px] w-[18px]" />
      </button>
    </div>
  )
}
