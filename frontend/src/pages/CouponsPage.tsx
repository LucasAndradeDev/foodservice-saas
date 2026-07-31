import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CheckCircle2, Circle, Clock, Pencil, Plus, Power, Ticket, XCircle } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { createCoupon, listCoupons, updateCoupon, type Coupon } from '../api/coupons'
import type { DiscountType } from '../api/orders'
import { Modal } from '../components/Modal'

const dateFormatter = new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: '2-digit', year: 'numeric' })

function formatDiscount(type: DiscountType, value: number) {
  return type === 'PERCENTAGE' ? `${value}%` : `R$ ${value.toFixed(2)}`
}

function formatUsage(coupon: Coupon) {
  return coupon.maxUses ? `${coupon.usedCount}/${coupon.maxUses}` : `${coupon.usedCount}`
}

function getCouponStatus(coupon: Coupon) {
  if (!coupon.active) {
    return { label: 'Inativo', className: 'bg-gray-400 text-white', icon: Circle }
  }
  if (coupon.expiresAt && new Date(coupon.expiresAt) < new Date()) {
    return { label: 'Expirado', className: 'bg-amber-500 text-white', icon: Clock }
  }
  if (coupon.maxUses && coupon.usedCount >= coupon.maxUses) {
    return { label: 'Esgotado', className: 'bg-red-600 text-white', icon: XCircle }
  }
  return { label: 'Ativo', className: 'bg-green-600 text-white', icon: CheckCircle2 }
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

  const isFormOpen = isCreating || editingCoupon !== null

  return (
    <div>
      <div className="mb-4 flex items-center justify-between gap-3 rounded-xl border border-gray-200 bg-white p-4 shadow-xs">
        <h1 className="flex items-center gap-2 text-lg font-semibold text-gray-800">
          <Ticket className="h-5 w-5 text-brand-600" />
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

      {isLoading && <p className="text-sm text-gray-500">Carregando...</p>}

      {coupons && coupons.length === 0 && (
        <p className="text-sm text-gray-500">
          Nenhum cupom cadastrado. O cliente aplica o código sozinho no cardápio digital, no carrinho.
        </p>
      )}

      {coupons && coupons.length > 0 && (
        <div className="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 text-left text-gray-500">
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
                {coupons.map((coupon) => {
                  const status = getCouponStatus(coupon)
                  const StatusIcon = status.icon
                  return (
                  <tr key={coupon.id} className="border-t border-gray-100">
                    <td className="px-4 py-2 font-medium text-gray-800">{coupon.code}</td>
                    <td className="px-4 py-2 text-gray-600">{formatDiscount(coupon.discountType, coupon.discountValue)}</td>
                    <td className="px-4 py-2 text-gray-600">
                      {coupon.expiresAt ? dateFormatter.format(new Date(coupon.expiresAt)) : 'Sem validade'}
                    </td>
                    <td className="px-4 py-2 text-gray-600">{formatUsage(coupon)}</td>
                    <td className="px-4 py-2">
                      <span className={`flex w-fit items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold ${status.className}`}>
                        <StatusIcon className="h-3.5 w-3.5" />
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
                          className="rounded-md p-1.5 text-gray-500 hover:bg-gray-100 hover:text-brand-700"
                        >
                          <Pencil className="h-4 w-4" />
                        </button>
                        <button
                          type="button"
                          onClick={() => toggleActive(coupon)}
                          title={coupon.active ? 'Desativar' : 'Ativar'}
                          aria-label={coupon.active ? 'Desativar' : 'Ativar'}
                          className={`rounded-md p-1.5 hover:bg-gray-100 ${coupon.active ? 'text-gray-500 hover:text-amber-700' : 'text-gray-400 hover:text-green-700'}`}
                        >
                          <Power className="h-4 w-4" />
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

      {isFormOpen && (
        <Modal title={editingCoupon ? `Editar ${editingCoupon.code}` : 'Novo cupom'} onClose={closeForm}>
          <form onSubmit={handleSubmit}>
            {!editingCoupon && (
              <>
                <label className="mb-1 block text-sm font-medium text-gray-700" htmlFor="couponCode">
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
                  className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm uppercase focus:border-brand-500 focus:outline-none"
                />
              </>
            )}

            <div className="mb-4 grid grid-cols-2 gap-3">
              <div>
                <label className="mb-1 block text-sm font-medium text-gray-700" htmlFor="discountType">
                  Tipo
                </label>
                <select
                  id="discountType"
                  value={discountType}
                  onChange={(e) => setDiscountType(e.target.value as DiscountType)}
                  className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none"
                >
                  <option value="PERCENTAGE">Percentual</option>
                  <option value="FIXED">Valor fixo</option>
                </select>
              </div>
              <div>
                <label className="mb-1 block text-sm font-medium text-gray-700" htmlFor="discountValue">
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
                  className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none"
                />
              </div>
            </div>

            <div className="mb-4 grid grid-cols-2 gap-3">
              <div>
                <label className="mb-1 block text-sm font-medium text-gray-700" htmlFor="expiresAt">
                  Validade <span className="font-normal text-gray-400">(opcional)</span>
                </label>
                <input
                  id="expiresAt"
                  type="date"
                  value={expiresAt}
                  onChange={(e) => setExpiresAt(e.target.value)}
                  className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none"
                />
              </div>
              <div>
                <label className="mb-1 block text-sm font-medium text-gray-700" htmlFor="maxUses">
                  Limite de usos <span className="font-normal text-gray-400">(opcional)</span>
                </label>
                <input
                  id="maxUses"
                  type="number"
                  min="1"
                  step="1"
                  value={maxUses}
                  onChange={(e) => setMaxUses(e.target.value)}
                  className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none"
                />
              </div>
            </div>

            {editingCoupon && (
              <p className="mb-4 text-xs text-gray-400">
                Deixe validade ou limite em branco pra removê-los. O código não pode ser alterado — crie um novo
                cupom se precisar de outro.
              </p>
            )}

            {error && <p className="mb-4 text-sm text-red-600">{error}</p>}

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
    </div>
  )
}
