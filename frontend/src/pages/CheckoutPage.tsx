import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query'
import { CheckCircle2, Clock, Percent, Printer, Wallet } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { listOrders, type DiscountType, type OrderItem } from '../api/orders'
import { applyTabDiscount, computeDiscountAmount, listTabs, payTab, roundCurrency, type PaymentMethod, type Tab } from '../api/tabs'
import { useAuth } from '../auth/AuthContext'
import { EmptyState } from '../components/EmptyState'
import { Modal } from '../components/Modal'
import { formatTableLabel } from '../utils/tableLabel'

const currencyFormatter = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

const PAYMENT_METHOD_LABELS: Record<PaymentMethod, string> = {
  PIX: 'Pix',
  CASH: 'Dinheiro',
  DEBIT_CARD: 'Cartão de débito',
  CREDIT_CARD: 'Cartão de crédito',
}

interface TabSummary {
  tab: Tab
  items: OrderItem[]
  isLoading: boolean
  isReady: boolean
  itemsTotal: number
  total: number
}

export function CheckoutPage() {
  const { user } = useAuth()
  const canPay = user?.role === 'OWNER' || user?.role === 'MANAGER' || user?.role === 'WAITER' || user?.role === 'CASHIER'
  const canDiscount = user?.role === 'OWNER' || user?.role === 'MANAGER'
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const { data: openTabs, isLoading: isTabsLoading } = useQuery({
    queryKey: ['tabs', 'OPEN'],
    queryFn: () => listTabs('OPEN'),
  })

  const orderQueries = useQueries({
    queries: (openTabs ?? []).map((tab) => ({
      queryKey: ['tabs', tab.id, 'orders'],
      queryFn: () => listOrders(tab.id),
    })),
  })

  const tabSummaries: TabSummary[] = (openTabs ?? []).map((tab, index) => {
    const orders = orderQueries[index]?.data
    const isLoading = orderQueries[index]?.isLoading ?? true
    const items = orders?.flatMap((order) => order.items) ?? []
    const pendingCount = items.filter((item) => item.status !== 'DELIVERED' && item.status !== 'CANCELLED').length
    const itemsTotal = roundCurrency(
      items.filter((item) => item.status !== 'CANCELLED').reduce((sum, item) => sum + item.netSubtotal, 0),
    )
    const total = roundCurrency(itemsTotal - computeDiscountAmount(tab.discountType, tab.discountValue, itemsTotal))
    return {
      tab,
      items,
      isLoading,
      isReady: !isLoading && items.length > 0 && pendingCount === 0,
      itemsTotal,
      total,
    }
  })

  const [selectedSummary, setSelectedSummary] = useState<TabSummary | null>(null)
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>('PIX')
  const [error, setError] = useState<string | null>(null)
  const [justPaidTabId, setJustPaidTabId] = useState<string | null>(null)
  const [isEditingDiscount, setIsEditingDiscount] = useState(false)
  const [discountKind, setDiscountKind] = useState<DiscountType>('FIXED')
  const [discountValue, setDiscountValue] = useState('')
  const [discountReason, setDiscountReason] = useState('')

  const payMutation = useMutation({
    mutationFn: ({ id, method, amount }: { id: string; method: PaymentMethod; amount: number }) =>
      payTab(id, method, amount),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: ['tabs'] })
      queryClient.invalidateQueries({ queryKey: ['tables'] })
      setJustPaidTabId(variables.id)
    },
    onError: () => setError('Não foi possível fechar a conta. Tente novamente.'),
  })

  const tabDiscountMutation = useMutation({
    mutationFn: ({ id, discountType, value, reason }: { id: string; discountType: DiscountType | null; value?: number; reason?: string }) =>
      applyTabDiscount(id, { discountType, discountValue: value, reason }),
    onSuccess: (updatedTab) => {
      queryClient.invalidateQueries({ queryKey: ['tabs', 'OPEN'] })
      setSelectedSummary((prev) => {
        if (!prev) return prev
        const total = roundCurrency(prev.itemsTotal - computeDiscountAmount(updatedTab.discountType, updatedTab.discountValue, prev.itemsTotal))
        return { ...prev, tab: updatedTab, total }
      })
      setIsEditingDiscount(false)
    },
    onError: () => setError('Não foi possível aplicar o desconto nesta comanda.'),
  })

  function handleCardClick(summary: TabSummary) {
    if (summary.isReady) {
      setSelectedSummary(summary)
      setPaymentMethod('PIX')
      setError(null)
      setIsEditingDiscount(false)
    } else {
      navigate(`/tabs/${summary.tab.id}`)
    }
  }

  function handleCloseModal() {
    setSelectedSummary(null)
    setJustPaidTabId(null)
  }

  function handleConfirmPayment() {
    if (!selectedSummary) return
    setError(null)
    payMutation.mutate({ id: selectedSummary.tab.id, method: paymentMethod, amount: selectedSummary.total })
  }

  function openDiscountForm() {
    if (!selectedSummary) return
    setError(null)
    setDiscountKind(selectedSummary.tab.discountType ?? 'FIXED')
    setDiscountValue(selectedSummary.tab.discountValue ? String(selectedSummary.tab.discountValue) : '')
    setDiscountReason(selectedSummary.tab.discountReason ?? '')
    setIsEditingDiscount(true)
  }

  function handleApplyTabDiscount(event: FormEvent) {
    event.preventDefault()
    if (!selectedSummary) return
    const value = Number(discountValue)
    if (!value || value <= 0) return
    tabDiscountMutation.mutate({ id: selectedSummary.tab.id, discountType: discountKind, value, reason: discountReason.trim() || undefined })
  }

  function handleRemoveTabDiscount() {
    if (!selectedSummary) return
    tabDiscountMutation.mutate({ id: selectedSummary.tab.id, discountType: null })
  }

  return (
    <div>
      <h1 className="mb-4 flex items-center gap-2 text-lg font-semibold text-gray-800">
        <Wallet className="h-5 w-5 text-brand-600" />
        Caixa
      </h1>

      {isTabsLoading && <p className="text-sm text-gray-500">Carregando...</p>}

      {openTabs && openTabs.length === 0 && <EmptyState icon={Wallet} message="Nenhuma comanda aberta." />}

      {tabSummaries.length > 0 && (
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {tabSummaries.map((summary) => (
            <button
              key={summary.tab.id}
              type="button"
              onClick={() => handleCardClick(summary)}
              className={`rounded-xl border-2 bg-white p-4 text-left shadow-sm transition hover:-translate-y-0.5 hover:shadow-md ${
                summary.isReady ? 'border-green-300 hover:bg-green-50' : 'border-gray-200 hover:bg-gray-50'
              }`}
            >
              <div className="text-base font-semibold text-gray-800">
                {formatTableLabel(summary.tab.tables.map((t) => t.number))}
              </div>
              {summary.isLoading ? (
                <div className="mt-1 text-sm text-gray-500">Carregando itens...</div>
              ) : summary.isReady ? (
                <>
                  <span className="mt-1 flex w-fit items-center gap-1 rounded-full bg-green-100 px-2 py-0.5 text-xs text-green-700">
                    <CheckCircle2 className="h-3 w-3" />
                    Pronta para fechar
                  </span>
                  <div className="mt-2 text-lg font-semibold text-gray-800">
                    {currencyFormatter.format(summary.total)}
                  </div>
                </>
              ) : (
                <span className="mt-1 flex w-fit items-center gap-1 rounded-full bg-amber-100 px-2 py-0.5 text-xs text-amber-700">
                  <Clock className="h-3 w-3" />
                  Itens ainda em preparo
                </span>
              )}
            </button>
          ))}
        </div>
      )}

      {selectedSummary && (
        <Modal title={`Fechar conta — ${formatTableLabel(selectedSummary.tab.tables.map((t) => t.number))}`} onClose={handleCloseModal}>
          {justPaidTabId === selectedSummary.tab.id ? (
            <>
              <p className="mb-4 flex items-center gap-1.5 text-sm font-medium text-green-700">
                <CheckCircle2 className="h-4 w-4" />
                Pagamento confirmado.
              </p>
              <Link
                to={`/tabs/${selectedSummary.tab.id}/print`}
                target="_blank"
                className="mb-2 flex w-full items-center justify-center gap-1.5 rounded-md bg-brand-600 px-3 py-2 text-center text-sm font-medium text-white hover:bg-brand-700"
              >
                <Printer className="h-4 w-4" />
                Imprimir recibo
              </Link>
              <button
                type="button"
                onClick={handleCloseModal}
                className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm text-gray-700 hover:bg-gray-100"
              >
                Fechar
              </button>
            </>
          ) : (
            <>
              <ul className="mb-4 divide-y divide-gray-100">
                {selectedSummary.items.map((item) => (
                  <li key={item.id} className="flex items-start justify-between gap-2 py-2 text-sm">
                    <div>
                      <span className={item.status === 'CANCELLED' ? 'text-gray-400 line-through' : 'text-gray-800'}>
                        {item.quantity}x {item.productName}
                      </span>
                      {item.modifiers.length > 0 && (
                        <div className="mt-1 flex flex-wrap gap-1">
                          {item.modifiers.map((modifier, index) => (
                            <span key={index} className="rounded-full bg-gray-100 px-2 py-0.5 text-xs text-gray-600">
                              {modifier.optionName}
                            </span>
                          ))}
                        </div>
                      )}
                      {item.observation && <div className="mt-1 text-xs text-gray-500">{item.observation}</div>}
                      {item.discountType && item.status !== 'CANCELLED' && (
                        <div className="mt-1 flex items-center gap-1 text-xs text-orange-600">
                          <Percent className="h-3 w-3" />
                          -{currencyFormatter.format(item.discountAmount)}
                          {item.discountReason && <span className="text-gray-400">({item.discountReason})</span>}
                        </div>
                      )}
                    </div>
                    <span className={item.status === 'CANCELLED' ? 'shrink-0 text-gray-400 line-through' : 'shrink-0 text-gray-600'}>
                      {item.discountType && item.status !== 'CANCELLED' && (
                        <span className="mr-1 text-xs text-gray-400 line-through">
                          {currencyFormatter.format(item.subtotal)}
                        </span>
                      )}
                      {currencyFormatter.format(item.status === 'CANCELLED' ? item.subtotal : item.netSubtotal)}
                    </span>
                  </li>
                ))}
              </ul>

              {canDiscount && (
                <div className="mb-4 rounded-lg border border-gray-200 bg-gray-50 p-3">
                  {!isEditingDiscount ? (
                    <div className="flex items-center justify-between">
                      {selectedSummary.tab.discountType ? (
                        <div className="text-sm text-orange-700">
                          <span className="flex items-center gap-1 font-medium">
                            <Percent className="h-3.5 w-3.5" />
                            Desconto na comanda: -
                            {currencyFormatter.format(computeDiscountAmount(selectedSummary.tab.discountType, selectedSummary.tab.discountValue, selectedSummary.itemsTotal))}
                          </span>
                          {selectedSummary.tab.discountReason && (
                            <span className="text-xs text-gray-500">{selectedSummary.tab.discountReason}</span>
                          )}
                        </div>
                      ) : (
                        <span className="text-sm text-gray-500">Nenhum desconto na comanda.</span>
                      )}
                      <button type="button" onClick={openDiscountForm} className="text-sm text-brand-600 hover:underline">
                        {selectedSummary.tab.discountType ? 'Editar' : 'Aplicar desconto'}
                      </button>
                    </div>
                  ) : (
                    <form onSubmit={handleApplyTabDiscount}>
                      <div className="mb-2 flex gap-2">
                        <button
                          type="button"
                          onClick={() => setDiscountKind('FIXED')}
                          className={`flex-1 rounded-md border px-2 py-1.5 text-xs ${
                            discountKind === 'FIXED' ? 'border-brand-600 bg-brand-50 text-brand-700' : 'border-gray-300 text-gray-600'
                          }`}
                        >
                          Valor em R$
                        </button>
                        <button
                          type="button"
                          onClick={() => setDiscountKind('PERCENTAGE')}
                          className={`flex-1 rounded-md border px-2 py-1.5 text-xs ${
                            discountKind === 'PERCENTAGE' ? 'border-brand-600 bg-brand-50 text-brand-700' : 'border-gray-300 text-gray-600'
                          }`}
                        >
                          Percentual (%)
                        </button>
                      </div>
                      <input
                        type="number"
                        required
                        min="0.01"
                        step="0.01"
                        max={discountKind === 'PERCENTAGE' ? 100 : undefined}
                        value={discountValue}
                        onChange={(e) => setDiscountValue(e.target.value)}
                        placeholder={discountKind === 'FIXED' ? 'Valor do desconto (R$)' : 'Percentual (%)'}
                        className="mb-2 w-full rounded-md border border-gray-300 px-3 py-1.5 text-sm focus:border-brand-500 focus:outline-none"
                      />
                      <input
                        type="text"
                        maxLength={255}
                        value={discountReason}
                        onChange={(e) => setDiscountReason(e.target.value)}
                        placeholder="Motivo (opcional)"
                        className="mb-2 w-full rounded-md border border-gray-300 px-3 py-1.5 text-sm focus:border-brand-500 focus:outline-none"
                      />
                      <div className="flex gap-2">
                        {selectedSummary.tab.discountType && (
                          <button
                            type="button"
                            onClick={handleRemoveTabDiscount}
                            disabled={tabDiscountMutation.isPending}
                            className="flex-1 rounded-md border border-red-300 px-2 py-1.5 text-xs text-red-700 hover:bg-red-50 disabled:opacity-50"
                          >
                            Remover
                          </button>
                        )}
                        <button
                          type="button"
                          onClick={() => setIsEditingDiscount(false)}
                          className="flex-1 rounded-md border border-gray-300 px-2 py-1.5 text-xs text-gray-600 hover:bg-gray-100"
                        >
                          Cancelar
                        </button>
                        <button
                          type="submit"
                          disabled={tabDiscountMutation.isPending}
                          className="flex-1 rounded-md bg-brand-600 px-2 py-1.5 text-xs font-medium text-white hover:bg-brand-700 disabled:opacity-50"
                        >
                          Aplicar
                        </button>
                      </div>
                    </form>
                  )}
                </div>
              )}

              <div className="mb-4 flex items-center justify-between border-t border-gray-200 pt-3 text-base font-semibold text-gray-800">
                <span>Total</span>
                <span>{currencyFormatter.format(selectedSummary.total)}</span>
              </div>

              <label className="mb-1 block text-sm font-medium text-gray-700" htmlFor="paymentMethod">
                Forma de pagamento
              </label>
              <select
                id="paymentMethod"
                value={paymentMethod}
                onChange={(e) => setPaymentMethod(e.target.value as PaymentMethod)}
                className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none"
              >
                {(Object.keys(PAYMENT_METHOD_LABELS) as PaymentMethod[]).map((method) => (
                  <option key={method} value={method}>
                    {PAYMENT_METHOD_LABELS[method]}
                  </option>
                ))}
              </select>

              {error && <p className="mb-4 text-sm text-red-600">{error}</p>}

              {canPay && (
                <button
                  type="button"
                  onClick={handleConfirmPayment}
                  disabled={payMutation.isPending}
                  className="w-full rounded-md bg-brand-600 px-3 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50"
                >
                  Confirmar pagamento
                </button>
              )}
            </>
          )}
        </Modal>
      )}
    </div>
  )
}
