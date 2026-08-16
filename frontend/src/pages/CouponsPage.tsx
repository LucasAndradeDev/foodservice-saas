import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Bike, CheckCircle2, Circle, Clock, Filter, Pencil, Plus, Power, Store, Ticket, Trash2, Users, XCircle } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { createCoupon, deleteCoupon, listCoupons, updateCoupon, type Coupon } from '../api/coupons'
import type { DiscountType } from '../api/orders'
import { Badge, type BadgeTone } from '../components/Badge'
import { Button } from '../components/Button'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { DatePicker } from '../components/DatePicker'
import { Dropdown } from '../components/Dropdown'
import { TableHead, TableRow } from '../components/Table'
import { Modal } from '../components/Modal'
import { PageHeader } from '../components/PageHeader'
import { SectionTabs } from '../components/SectionTabs'

const MANAGEMENT_TABS = [
  { to: '/settings', label: 'Geral', icon: Store },
  { to: '/coupons', label: 'Cupons', icon: Ticket },
  { to: '/happy-hour', label: 'Happy Hour', icon: Clock },
  { to: '/delivery-zones', label: 'Entrega', icon: Bike },
  { to: '/staff', label: 'Funcionários', icon: Users },
]

const dateFormatter = new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: '2-digit', year: 'numeric' })

function formatDiscount(type: DiscountType, value: number) {
  return type === 'PERCENTAGE' ? `${value}%` : `R$ ${value.toFixed(2)}`
}

function formatUsage(coupon: Coupon) {
  return coupon.maxUses ? `${coupon.usedCount}/${coupon.maxUses}` : `${coupon.usedCount}`
}

type CouponStatusLabel = 'Ativo' | 'Inativo' | 'Expirado' | 'Esgotado'

const DISCOUNT_TYPE_OPTIONS: { value: DiscountType; label: string }[] = [
  { value: 'PERCENTAGE', label: 'Percentual' },
  { value: 'FIXED', label: 'Valor fixo' },
]

const STATUS_FILTER_OPTIONS: { value: 'all' | CouponStatusLabel; label: string }[] = [
  { value: 'all', label: 'Todos' },
  { value: 'Ativo', label: 'Ativos' },
  { value: 'Inativo', label: 'Inativos' },
  { value: 'Expirado', label: 'Expirados' },
  { value: 'Esgotado', label: 'Esgotados' },
]

function getCouponStatus(coupon: Coupon): { label: CouponStatusLabel; tone: BadgeTone; icon: typeof CheckCircle2 } {
  if (!coupon.active) {
    return { label: 'Inativo', tone: 'neutral', icon: Circle }
  }
  if (coupon.expiresAt && new Date(coupon.expiresAt) < new Date()) {
    return { label: 'Expirado', tone: 'attention', icon: Clock }
  }
  if (coupon.maxUses && coupon.usedCount >= coupon.maxUses) {
    return { label: 'Esgotado', tone: 'critical', icon: XCircle }
  }
  return { label: 'Ativo', tone: 'free', icon: CheckCircle2 }
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

      <div className="mb-5 flex items-center justify-between gap-3 rounded-b-2xl border border-gray-200 bg-white p-5 shadow-sm dark:border-white/10 dark:bg-stone-900">
        <PageHeader icon={Ticket} title="Cupons" />
        <Button type="button" onClick={openCreateForm} className="shrink-0 whitespace-nowrap">
          <Plus className="h-4 w-4" />
          <span className="hidden sm:inline">Novo cupom</span>
        </Button>
      </div>

      {isLoading && <p className="text-sm text-gray-500 dark:text-stone-400">Carregando...</p>}

      {listError && <p className="mb-4 text-sm text-wine-600 dark:text-wine-400">{listError}</p>}

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
            <>
              {/* Mobile: stacked cards, no horizontal scroll needed */}
              <div className="space-y-2 sm:hidden">
                {filteredCoupons.map((coupon) => {
                  const status = getCouponStatus(coupon)
                  const StatusIcon = status.icon
                  return (
                    <div
                      key={coupon.id}
                      className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-white/10 dark:bg-stone-900"
                    >
                      <div className="mb-2 flex items-start justify-between gap-2">
                        <span className="font-medium text-gray-800 dark:text-white">{coupon.code}</span>
                        <Badge tone={status.tone} className="shrink-0">
                          <StatusIcon className="h-3 w-3" />
                          {status.label}
                        </Badge>
                      </div>
                      <div className="mb-3 grid grid-cols-3 gap-2 text-sm">
                        <div>
                          <div className="text-xs text-gray-400 dark:text-stone-500">Desconto</div>
                          <div className="text-gray-700 dark:text-stone-300">
                            {formatDiscount(coupon.discountType, coupon.discountValue)}
                          </div>
                        </div>
                        <div>
                          <div className="text-xs text-gray-400 dark:text-stone-500">Validade</div>
                          <div className="text-gray-700 dark:text-stone-300">
                            {coupon.expiresAt ? dateFormatter.format(new Date(coupon.expiresAt)) : 'Sem validade'}
                          </div>
                        </div>
                        <div>
                          <div className="text-xs text-gray-400 dark:text-stone-500">Usos</div>
                          <div className="text-gray-700 dark:text-stone-300">{formatUsage(coupon)}</div>
                        </div>
                      </div>
                      <div className="flex items-center justify-end gap-1 border-t border-gray-100 pt-2 dark:border-white/10">
                        <button
                          type="button"
                          onClick={() => openEditForm(coupon)}
                          title="Editar"
                          aria-label="Editar"
                          className="rounded-md p-2 text-gray-500 hover:bg-gray-100 hover:text-brand-700 dark:text-stone-400 dark:hover:bg-white/5 dark:hover:text-brand-400"
                        >
                          <Pencil className="h-[18px] w-[18px]" />
                        </button>
                        <button
                          type="button"
                          onClick={() => toggleActive(coupon)}
                          title={coupon.active ? 'Desativar' : 'Ativar'}
                          aria-label={coupon.active ? 'Desativar' : 'Ativar'}
                          className={`rounded-md p-2 hover:bg-gray-100 dark:hover:bg-white/5 ${coupon.active ? 'text-gray-500 hover:text-gold-700 dark:text-stone-400 dark:hover:text-gold-400' : 'text-gray-400 hover:text-sage-700 dark:text-stone-500 dark:hover:text-sage-400'}`}
                        >
                          <Power className="h-[18px] w-[18px]" />
                        </button>
                        <button
                          type="button"
                          onClick={() => setCouponToDelete(coupon)}
                          title="Excluir"
                          aria-label="Excluir"
                          className="rounded-md p-2 text-gray-500 hover:bg-gray-100 hover:text-wine-700 dark:text-stone-400 dark:hover:bg-white/5 dark:hover:text-wine-400"
                        >
                          <Trash2 className="h-[18px] w-[18px]" />
                        </button>
                      </div>
                    </div>
                  )
                })}
              </div>

              {/* Desktop: table */}
              <div className="hidden overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm sm:block dark:border-white/10 dark:bg-stone-900">
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <TableHead>
                    <tr>
                      <th className="px-4 py-2 font-medium">Código</th>
                      <th className="px-4 py-2 font-medium">Desconto</th>
                      <th className="px-4 py-2 font-medium">Validade</th>
                      <th className="px-4 py-2 font-medium">Usos</th>
                      <th className="px-4 py-2 font-medium">Status</th>
                      <th className="px-4 py-2" />
                    </tr>
                  </TableHead>
                  <tbody>
                    {filteredCoupons.map((coupon) => {
                      const status = getCouponStatus(coupon)
                      const StatusIcon = status.icon
                      return (
                        <TableRow key={coupon.id}>
                          <td className="px-4 py-2 font-medium text-gray-800 dark:text-white">{coupon.code}</td>
                          <td className="px-4 py-2 text-gray-600 dark:text-stone-400">{formatDiscount(coupon.discountType, coupon.discountValue)}</td>
                          <td className="px-4 py-2 text-gray-600 dark:text-stone-400">
                            {coupon.expiresAt ? dateFormatter.format(new Date(coupon.expiresAt)) : 'Sem validade'}
                          </td>
                          <td className="px-4 py-2 text-gray-600 dark:text-stone-400">{formatUsage(coupon)}</td>
                          <td className="px-4 py-2">
                            <Badge tone={status.tone}>
                              <StatusIcon className="h-3 w-3" />
                              {status.label}
                            </Badge>
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
                                className={`rounded-md p-1.5 hover:bg-gray-100 dark:hover:bg-white/5 ${coupon.active ? 'text-gray-500 hover:text-gold-700 dark:text-stone-400 dark:hover:text-gold-400' : 'text-gray-400 hover:text-sage-700 dark:text-stone-500 dark:hover:text-sage-400'}`}
                              >
                                <Power className="h-4 w-4" />
                              </button>
                              <button
                                type="button"
                                onClick={() => setCouponToDelete(coupon)}
                                title="Excluir"
                                aria-label="Excluir"
                                className="rounded-md p-1.5 text-gray-500 hover:bg-gray-100 hover:text-wine-700 dark:text-stone-400 dark:hover:bg-white/5 dark:hover:text-wine-400"
                              >
                                <Trash2 className="h-4 w-4" />
                              </button>
                            </div>
                          </td>
                        </TableRow>
                      )
                    })}
                  </tbody>
                </table>
              </div>
              </div>
            </>
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
                <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300">Tipo</label>
                <Dropdown<DiscountType>
                  value={discountType}
                  onChange={setDiscountType}
                  options={DISCOUNT_TYPE_OPTIONS}
                  fullWidth
                  mobileTitle="Tipo de desconto"
                />
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
                <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300">
                  Validade <span className="font-normal text-gray-400 dark:text-stone-500">(opcional)</span>
                </label>
                <DatePicker value={expiresAt} onChange={setExpiresAt} placeholder="Sem validade" allowClear className="block w-full" />
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

            {error && <p className="mb-4 text-sm text-wine-600 dark:text-wine-400">{error}</p>}

            <Button type="submit" disabled={createMutation.isPending || updateMutation.isPending} className="w-full">
              Salvar
            </Button>
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
