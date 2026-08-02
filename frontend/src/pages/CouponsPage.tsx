import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CheckCircle2, Circle, Clock, Filter, Pencil, Plus, Power, Ticket, Trash2, XCircle } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { createCoupon, deleteCoupon, listCoupons, updateCoupon, type Coupon } from '../api/coupons'
import type { DiscountType } from '../api/orders'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { Dropdown } from '../components/Dropdown'
import { Modal } from '../components/Modal'
import { SectionTabs } from '../components/SectionTabs'

const MANAGEMENT_TABS = [
  { to: '/settings', label: 'Geral' },
  { to: '/coupons', label: 'Cupons' },
  { to: '/happy-hour', label: 'Happy Hour' },
  { to: '/staff', label: 'Funcionários' },
]

const dateFormatter = new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: '2-digit', year: 'numeric' })

function formatDiscount(type: DiscountType, value: number) {
  return type === 'PERCENTAGE' ? `${value}%` : `R$ ${value.toFixed(2)}`
}

function formatUsage(coupon: Coupon) {
  return coupon.maxUses ? `${coupon.usedCount}/${coupon.maxUses}` : `${coupon.usedCount}`
}

type CouponStatusLabel = 'Ativo' | 'Inativo' | 'Expirado' | 'Esgotado'

const STATUS_FILTER_OPTIONS: { value: 'all' | CouponStatusLabel; label: string }[] = [
  { value: 'all', label: 'Todos' },
  { value: 'Ativo', label: 'Ativos' },
  { value: 'Inativo', label: 'Inativos' },
  { value: 'Expirado', label: 'Expirados' },
  { value: 'Esgotado', label: 'Esgotados' },
]

function getCouponStatus(coupon: Coupon): { label: CouponStatusLabel; className: string; icon: typeof CheckCircle2 } {
  if (!coupon.active) {
    return { label: 'Inativo', className: 'bg-gray-100 text-gray-500 dark:bg-white/10 dark:text-stone-400', icon: Circle }
  }
  if (coupon.expiresAt && new Date(coupon.expiresAt) < new Date()) {
    return { label: 'Expirado', className: 'bg-amber-100 text-amber-700 dark:bg-amber-500/10 dark:text-amber-400', icon: Clock }
  }
  if (coupon.maxUses && coupon.usedCount >= coupon.maxUses) {
    return { label: 'Esgotado', className: 'bg-red-100 text-red-700 dark:bg-red-500/10 dark:text-red-400', icon: XCircle }
  }
  return { label: 'Ativo', className: 'bg-green-100 text-green-700 dark:bg-green-500/10 dark:text-green-400', icon: CheckCircle2 }
}

export function CouponsPage() {
  const queryClient = useQueryClient()

  const { data: coupons, isLoading } = useQuery({
    queryKey: ['coupons'],
    queryFn: listCoupons,
  })

  const [editingCoupon, setEditingCoupon] = useState<Coupon | null>(null)
  const [isCreating, setIsCreating] = useState(false)
  const [code, setCode] = useState('')
  const [discountType, setDiscountType] = useState<DiscountType>('PERCENTAGE')
  const [discountValue, setDiscountValue] = useState('')
  const [expiresAt, setExpiresAt] = useState('')
  const [maxUses, setMaxUses] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [listError, setListError] = useState<string | null>(null)
  const [couponToDelete, setCouponToDelete] = useState<Coupon | null>(null)
  const [statusFilter, setStatusFilter] = useState<'all' | CouponStatusLabel>('all')

  const filteredCoupons = (coupons ?? []).filter(
    (coupon) => statusFilter === 'all' || getCouponStatus(coupon).label === statusFilter,
  )

  const createMutation = useMutation({
    mutationFn: createCoupon,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['coupons'] })
      closeForm()
    },
    onError: () => setError('Não foi possível salvar. Verifique se o código já está em uso.'),
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: Parameters<typeof updateCoupon>[1] }) =>
      updateCoupon(id, payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['coupons'] }),
  })

  const deleteMutation = useMutation({
    mutationFn: deleteCoupon,
    onSuccess: () => {
      setListError(null)
      queryClient.invalidateQueries({ queryKey: ['coupons'] })
    },
    onError: () => setListError('Não foi possível excluir o cupom.'),
  })

  function openCreateForm() {
    setCode('')
    setDiscountType('PERCENTAGE')
    setDiscountValue('')
    setExpiresAt('')
    setMaxUses('')
    setError(null)
    setIsCreating(true)
  }

  function openEditForm(coupon: Coupon) {
    setEditingCoupon(coupon)
    setDiscountType(coupon.discountType)
    setDiscountValue(String(coupon.discountValue))
    setExpiresAt(coupon.expiresAt ? coupon.expiresAt.slice(0, 10) : '')
    setMaxUses(coupon.maxUses ? String(coupon.maxUses) : '')
    setError(null)
  }

  function closeForm() {
    setIsCreating(false)
    setEditingCoupon(null)
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    if (editingCoupon) {
      updateMutation.mutate(
        {
          id: editingCoupon.id,
          payload: {
            discountType,
            discountValue: Number(discountValue),
            ...(expiresAt
              ? { expiresAt: new Date(expiresAt + 'T23:59:59').toISOString() }
              : { clearExpiresAt: true }),
            ...(maxUses ? { maxUses: Number(maxUses) } : { clearMaxUses: true }),
          },
        },
        { onSuccess: closeForm, onError: () => setError('Não foi possível salvar.') },
      )
    } else {
      createMutation.mutate({
        code,
        discountType,
        discountValue: Number(discountValue),
        expiresAt: expiresAt ? new Date(expiresAt + 'T23:59:59').toISOString() : undefined,
        maxUses: maxUses ? Number(maxUses) : undefined,
      })
    }
  }

  function toggleActive(coupon: Coupon) {
    updateMutation.mutate({ id: coupon.id, payload: { active: !coupon.active } })
  }

  function confirmDelete() {
    if (couponToDelete) {
      setListError(null)
      deleteMutation.mutate(couponToDelete.id)
    }
    setCouponToDelete(null)
  }

  const isFormOpen = isCreating || editingCoupon !== null

  return (
    <div>
      <SectionTabs tabs={MANAGEMENT_TABS} />

      <div className="mb-4 flex items-center justify-between gap-3 rounded-xl border border-gray-200 bg-white p-4 shadow-xs dark:border-white/10 dark:bg-stone-900">
        <h1 className="flex items-center gap-2 text-lg font-semibold text-gray-800 dark:text-white">
          <Ticket className="h-5 w-5 text-brand-600 dark:text-brand-400" />
          Cupons de desconto
        </h1>
        <button
          type="button"
          onClick={openCreateForm}
          className="flex items-center gap-1.5 rounded-lg bg-brand-600 px-3.5 py-2 text-sm font-medium text-white hover:bg-brand-700"
        >
          <Plus className="h-4 w-4" />
          Novo cupom
        </button>
      </div>

      {isLoading && <p className="text-sm text-gray-500 dark:text-stone-400">Carregando...</p>}

      {listError && <p className="mb-4 text-sm text-red-600 dark:text-red-400">{listError}</p>}

      {coupons && coupons.length === 0 && (
        <p className="text-sm text-gray-500 dark:text-stone-400">
          Nenhum cupom cadastrado. O cliente aplica o código sozinho no cardápio digital, no carrinho.
        </p>
      )}

      {coupons && coupons.length > 0 && (
        <>
          <div className="mb-4">
            <Dropdown
              value={statusFilter}
              options={STATUS_FILTER_OPTIONS}
              onChange={setStatusFilter}
              icon={Filter}
              panelClassName="w-44"
              mobileTitle="Filtrar por status"
            />
          </div>

          {filteredCoupons.length === 0 && (
            <p className="text-sm text-gray-500 dark:text-stone-400">Nenhum cupom com esse status.</p>
          )}

          {filteredCoupons.length > 0 && (
            <div className="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm dark:border-white/10 dark:bg-stone-900">
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead className="bg-gray-50 text-left text-gray-500 dark:bg-white/5 dark:text-stone-400">
                    <tr>
                      <th className="px-4 py-2 font-medium">Código</th>
                      <th className="px-4 py-2 font-medium">Desconto</th>
                      <th className="px-4 py-2 font-medium">Validade</th>
                      <th className="px-4 py-2 font-medium">Usos</th>
                      <th className="px-4 py-2 font-medium">Status</th>
                      <th className="px-4 py-2" />
                    </tr>
                  </thead>
                  <tbody>
                    {filteredCoupons.map((coupon) => {
                      const status = getCouponStatus(coupon)
                      const StatusIcon = status.icon
                      return (
                        <tr key={coupon.id} className="border-t border-gray-100 dark:border-white/10">
                          <td className="px-4 py-2 font-medium text-gray-800 dark:text-white">{coupon.code}</td>
                          <td className="px-4 py-2 text-gray-600 dark:text-stone-400">{formatDiscount(coupon.discountType, coupon.discountValue)}</td>
                          <td className="px-4 py-2 text-gray-600 dark:text-stone-400">
                            {coupon.expiresAt ? dateFormatter.format(new Date(coupon.expiresAt)) : 'Sem validade'}
                          </td>
                          <td className="px-4 py-2 text-gray-600 dark:text-stone-400">{formatUsage(coupon)}</td>
                          <td className="px-4 py-2">
                            <span className={`flex w-fit items-center gap-1 rounded-full px-2 py-0.5 text-xs ${status.className}`}>
                              <StatusIcon className="h-3 w-3" />
                              {status.label}
                            </span>
                          </td>
                          <td className="px-4 py-2 text-right">
                            <div className="flex items-center justify-end gap-1">
                              <button
                                type="button"
                                onClick={() => openEditForm(coupon)}
                                title="Editar"
                                aria-label="Editar"
                                className="rounded-md p-1.5 text-gray-500 hover:bg-gray-100 hover:text-brand-700 dark:text-stone-400 dark:hover:bg-white/5 dark:hover:text-brand-400"
                              >
                                <Pencil className="h-4 w-4" />
                              </button>
                              <button
                                type="button"
                                onClick={() => toggleActive(coupon)}
                                title={coupon.active ? 'Desativar' : 'Ativar'}
                                aria-label={coupon.active ? 'Desativar' : 'Ativar'}
                                className={`rounded-md p-1.5 hover:bg-gray-100 dark:hover:bg-white/5 ${coupon.active ? 'text-gray-500 hover:text-amber-700 dark:text-stone-400 dark:hover:text-amber-400' : 'text-gray-400 hover:text-green-700 dark:text-stone-500 dark:hover:text-green-400'}`}
                              >
                                <Power className="h-4 w-4" />
                              </button>
                              <button
                                type="button"
                                onClick={() => setCouponToDelete(coupon)}
                                title="Excluir"
                                aria-label="Excluir"
                                className="rounded-md p-1.5 text-gray-500 hover:bg-gray-100 hover:text-red-700 dark:text-stone-400 dark:hover:bg-white/5 dark:hover:text-red-400"
                              >
                                <Trash2 className="h-4 w-4" />
                              </button>
                            </div>
                          </td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </>
      )}

      {isFormOpen && (
        <Modal title={editingCoupon ? `Editar ${editingCoupon.code}` : 'Novo cupom'} onClose={closeForm}>
          <form onSubmit={handleSubmit}>
            {!editingCoupon && (
              <>
                <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="couponCode">
                  Código
                </label>
                <input
                  id="couponCode"
                  type="text"
                  required
                  maxLength={30}
                  value={code}
                  onChange={(e) => setCode(e.target.value.toUpperCase())}
                  placeholder="Ex: ANIVERSARIO10"
                  className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm uppercase focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
                />
              </>
            )}

            <div className="mb-4 grid grid-cols-2 gap-3">
              <div>
                <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="discountType">
                  Tipo
                </label>
                <select
                  id="discountType"
                  value={discountType}
                  onChange={(e) => setDiscountType(e.target.value as DiscountType)}
                  className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
                >
                  <option value="PERCENTAGE">Percentual</option>
                  <option value="FIXED">Valor fixo</option>
                </select>
              </div>
              <div>
                <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="discountValue">
                  Valor
                </label>
                <input
                  id="discountValue"
                  type="number"
                  required
                  min="0.01"
                  step="0.01"
                  max={discountType === 'PERCENTAGE' ? 100 : undefined}
                  value={discountValue}
                  onChange={(e) => setDiscountValue(e.target.value)}
                  className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
                />
              </div>
            </div>

            <div className="mb-4 grid grid-cols-2 gap-3">
              <div>
                <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="expiresAt">
                  Validade <span className="font-normal text-gray-400 dark:text-stone-500">(opcional)</span>
                </label>
                <input
                  id="expiresAt"
                  type="date"
                  value={expiresAt}
                  onChange={(e) => setExpiresAt(e.target.value)}
                  className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
                />
              </div>
              <div>
                <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="maxUses">
                  Limite de usos <span className="font-normal text-gray-400 dark:text-stone-500">(opcional)</span>
                </label>
                <input
                  id="maxUses"
                  type="number"
                  min="1"
                  step="1"
                  value={maxUses}
                  onChange={(e) => setMaxUses(e.target.value)}
                  className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
                />
              </div>
            </div>

            {editingCoupon && (
              <p className="mb-4 text-xs text-gray-400 dark:text-stone-500">
                Deixe validade ou limite em branco pra removê-los. O código não pode ser alterado — crie um novo
                cupom se precisar de outro.
              </p>
            )}

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

      {couponToDelete && (
        <ConfirmDialog
          title="Excluir cupom"
          message={`Excluir o cupom "${couponToDelete.code}"? Essa ação não pode ser desfeita.`}
          confirmLabel="Excluir"
          cancelLabel="Voltar"
          danger
          onConfirm={confirmDelete}
          onCancel={() => setCouponToDelete(null)}
        />
      )}
    </div>
  )
}
