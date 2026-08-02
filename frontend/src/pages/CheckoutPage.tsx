import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import { AnimatePresence, motion } from 'framer-motion'
import { CheckCircle2, ChevronDown, Clock, Lock, Pencil, Percent, Printer, Wallet } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { listOrders, type DiscountType, type OrderItem } from '../api/orders'
import {
  applyTabDiscount,
  computeDiscountAmount,
  listTabs,
  payTab,
  PAYMENT_METHOD_LABELS,
  roundCurrency,
  type PaymentMethod,
  type Tab,
} from '../api/tabs'
import { getMyRestaurant } from '../api/restaurant'
import { useAuth } from '../auth/AuthContext'
import { EmptyState } from '../components/EmptyState'
import { Modal } from '../components/Modal'
import { QrCodeCard } from '../components/QrCodeCard'
import { formatTableLabel } from '../utils/tableLabel'
import { feedbackUrl } from '../utils/publicMenuUrl'

const currencyFormatter = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })
const timeFormatter = new Intl.DateTimeFormat('pt-BR', { hour: '2-digit', minute: '2-digit' })

function isSameLocalDay(a: Date, b: Date) {
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate()
}

function extractErrorMessage(err: unknown, fallback: string) {
  if (isAxiosError(err) && err.response?.data?.message) {
    return err.response.data.message as string
  }
  return fallback
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

  const { data: closedTabs } = useQuery({
    queryKey: ['tabs', 'CLOSED'],
    queryFn: () => listTabs('CLOSED'),
    enabled: canDiscount,
  })

  const closedToday = (closedTabs ?? [])
    .filter((tab) => tab.closedAt && isSameLocalDay(new Date(tab.closedAt), new Date()))
    .sort((a, b) => (b.closedAt ?? '').localeCompare(a.closedAt ?? ''))

  const { data: restaurant } = useQuery({
    queryKey: ['restaurant'],
    queryFn: getMyRestaurant,
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

  const [showClosedToday, setShowClosedToday] = useState(false)
  const [selectedSummary, setSelectedSummary] = useState<TabSummary | null>(null)
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>('PIX')
  const [error, setError] = useState<string | null>(null)
  const [justPaidTabId, setJustPaidTabId] = useState<string | null>(null)
  const [isEditingDiscount, setIsEditingDiscount] = useState(false)
  const [discountKind, setDiscountKind] = useState<DiscountType>('FIXED')
  const [discountValue, setDiscountValue] = useState('')
  const [discountReason, setDiscountReason] = useState('')
  const [serviceChargePercentage, setServiceChargePercentage] = useState<number | null>(null)
  const [isEditingServiceCharge, setIsEditingServiceCharge] = useState(false)
  const [serviceChargeInput, setServiceChargeInput] = useState('')

  const payMutation = useMutation({
    mutationFn: ({ id, method, amount, chargePercentage }: { id: string; method: PaymentMethod; amount: number; chargePercentage: number | null }) =>
      payTab(id, method, amount, chargePercentage ?? undefined),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: ['tabs'] })
      queryClient.invalidateQueries({ queryKey: ['tables'] })
      setJustPaidTabId(variables.id)
    },
    onError: (err) => setError(extractErrorMessage(err, 'Não foi possível fechar a conta. Tente novamente.')),
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
      const defaultCharge = restaurant?.serviceChargeEnabled ? restaurant.serviceChargePercentage : null
      setServiceChargePercentage(defaultCharge)
      setServiceChargeInput(String(restaurant?.serviceChargePercentage ?? 10))
      setIsEditingServiceCharge(false)
    } else {
      navigate(`/tabs/${summary.tab.id}`)
    }
  }

  function handleCloseModal() {
    setSelectedSummary(null)
    setJustPaidTabId(null)
  }

  const serviceChargeAmount = selectedSummary
    ? roundCurrency((selectedSummary.total * (serviceChargePercentage ?? 0)) / 100)
    : 0
  const finalTotal = selectedSummary ? roundCurrency(selectedSummary.total + serviceChargeAmount) : 0

  function handleConfirmPayment() {
    if (!selectedSummary) return
    setError(null)
    payMutation.mutate({ id: selectedSummary.tab.id, method: paymentMethod, amount: finalTotal, chargePercentage: serviceChargePercentage })
  }

  function handleApplyServiceCharge(event: FormEvent) {
    event.preventDefault()
    const value = Number(serviceChargeInput)
    if (!value || value < 0 || value > 100) return
    setServiceChargePercentage(value)
    setIsEditingServiceCharge(false)
  }

  function handleRemoveServiceCharge() {
    setServiceChargePercentage(null)
    setIsEditingServiceCharge(false)
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
      <h1 className="mb-4 flex items-center gap-2 rounded-xl border border-gray-200 bg-white p-4 text-lg font-semibold text-gray-800 shadow-xs dark:border-white/10 dark:bg-stone-900 dark:text-white">
        <Wallet className="h-5 w-5 text-brand-600 dark:text-brand-400" />
        Fechar Conta
      </h1>

      {isTabsLoading && <p className="text-sm text-gray-500 dark:text-stone-400">Carregando...</p>}

      {openTabs && openTabs.length === 0 && <EmptyState icon={Wallet} message="Nenhuma comanda aberta." />}

      {tabSummaries.length > 0 && (
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {tabSummaries.map((summary) => (
            <button
              key={summary.tab.id}
              type="button"
              onClick={() => handleCardClick(summary)}
              className={`rounded-xl border-2 bg-white p-4 text-left shadow-sm transition hover:-translate-y-0.5 hover:shadow-md dark:bg-stone-900 ${
                summary.isReady
                  ? 'border-green-300 hover:bg-green-50 dark:border-green-500/30 dark:hover:bg-green-500/10'
                  : 'border-gray-200 hover:bg-gray-50 dark:border-white/10 dark:hover:bg-white/5'
              }`}
            >
              <div className="text-base font-semibold text-gray-800 dark:text-white">
                {formatTableLabel(summary.tab.tables.map((t) => t.number))}
              </div>
              {summary.isLoading ? (
                <div className="mt-1 text-sm text-gray-500 dark:text-stone-400">Carregando itens...</div>
              ) : summary.isReady ? (
                <>
                  <span className="mt-1 flex w-fit items-center gap-1 rounded-full bg-green-100 px-2 py-0.5 text-xs text-green-700 dark:bg-green-500/10 dark:text-green-400">
                    <CheckCircle2 className="h-3 w-3" />
                    Pronta para fechar
                  </span>
                  <div className="mt-2 text-lg font-semibold text-gray-800 dark:text-white">
                    {currencyFormatter.format(summary.total)}
                  </div>
                </>
              ) : (
                <span className="mt-1 flex w-fit items-center gap-1 rounded-full bg-amber-100 px-2 py-0.5 text-xs text-amber-700 dark:bg-amber-500/10 dark:text-amber-400">
                  <Clock className="h-3 w-3" />
                  Itens ainda em preparo
                </span>
              )}
            </button>
          ))}
        </div>
      )}

      {canDiscount && closedToday.length > 0 && (
        <div className="mt-8">
          <button
            type="button"
            onClick={() => setShowClosedToday((prev) => !prev)}
            className="flex w-full items-center justify-between rounded-xl border border-gray-200 bg-white px-4 py-3 text-left shadow-sm transition hover:bg-gray-50 dark:border-white/10 dark:bg-stone-900 dark:hover:bg-white/5"
          >
            <span className="flex items-center gap-2 text-sm font-semibold text-gray-700 dark:text-stone-300">
              <Lock className="h-4 w-4 text-gray-400 dark:text-stone-500" />
              Comandas fechadas hoje
              <span className="rounded-full bg-gray-100 px-2 py-0.5 text-xs font-medium text-gray-500 dark:bg-white/10 dark:text-stone-400">
                {closedToday.length}
              </span>
            </span>
            <motion.span animate={{ rotate: showClosedToday ? 180 : 0 }} transition={{ duration: 0.2 }}>
              <ChevronDown className="h-4 w-4 text-gray-400 dark:text-stone-500" />
            </motion.span>
          </button>

          <AnimatePresence initial={false}>
            {showClosedToday && (
              <motion.div
                initial={{ height: 0, opacity: 0 }}
                animate={{ height: 'auto', opacity: 1 }}
                exit={{ height: 0, opacity: 0 }}
                transition={{ duration: 0.2 }}
                className="overflow-hidden"
              >
                <div className="mt-3 grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
                  {closedToday.map((tab) => (
                    <motion.div key={tab.id} whileHover={{ y: -2 }} transition={{ duration: 0.15 }}>
                      <Link
                        to={`/tabs/${tab.id}`}
                        className="group flex h-full flex-col rounded-xl border border-gray-200 bg-white p-4 shadow-sm transition hover:border-gray-300 hover:shadow-md dark:border-white/10 dark:bg-stone-900 dark:hover:border-white/20"
                      >
                        <div className="flex items-start justify-between gap-2">
                          <span className="text-base font-semibold text-gray-800 dark:text-white">
                            {formatTableLabel(tab.tables.map((t) => t.number))}
                          </span>
                          <span className="flex items-center gap-1 rounded-full bg-gray-100 px-2 py-0.5 text-xs text-gray-500 dark:bg-white/10 dark:text-stone-400">
                            <Lock className="h-3 w-3" />
                            Fechada
                          </span>
                        </div>
                        <span className="mt-1 flex items-center gap-1 text-xs text-gray-400 dark:text-stone-500">
                          <Clock className="h-3 w-3" />
                          {tab.closedAt && timeFormatter.format(new Date(tab.closedAt))}
                        </span>
                        <div className="mt-3 flex flex-1 items-end justify-between">
                          <span className="text-lg font-semibold text-gray-800 dark:text-white">
                            {tab.paidAmount != null && currencyFormatter.format(tab.paidAmount)}
                          </span>
                          <span className="flex items-center gap-1 text-xs font-medium text-gray-400 opacity-0 transition group-hover:text-red-600 group-hover:opacity-100 dark:text-stone-500 dark:group-hover:text-red-400">
                            <Pencil className="h-3.5 w-3.5" />
                            Corrigir pagamento
                          </span>
                        </div>
                      </Link>
                    </motion.div>
                  ))}
                </div>
              </motion.div>
            )}
          </AnimatePresence>
        </div>
      )}

      {selectedSummary && (
        <Modal title={`Fechar conta — ${formatTableLabel(selectedSummary.tab.tables.map((t) => t.number))}`} onClose={handleCloseModal}>
          {justPaidTabId === selectedSummary.tab.id ? (
            <>
              <p className="mb-4 flex items-center gap-1.5 text-sm font-medium text-green-700 dark:text-green-400">
                <CheckCircle2 className="h-4 w-4" />
                Pagamento confirmado.
              </p>
              {selectedSummary.tab.tables.length === 0 && restaurant?.slug && (
                <div className="mb-4">
                  <QrCodeCard
                    title="Avalie o atendimento"
                    url={feedbackUrl(restaurant.slug, selectedSummary.tab.id)}
                    helperText="Peça pro cliente escanear antes de ir embora"
                  />
                </div>
              )}
              <Link
                to={`/tabs/${selectedSummary.tab.id}/print`}
                target="_blank"
                className="mb-2 flex w-full items-center justify-center gap-1.5 rounded-md bg-brand-600 px-3 py-2 text-center text-sm font-medium text-white hover:bg-brand-700"
              >
                <Printer className="h-4 w-4" />
                Imprimir recibo
              </Link>
              {canDiscount && (
                <Link
                  to={`/tabs/${selectedSummary.tab.id}`}
                  className="mb-2 flex w-full items-center justify-center gap-1.5 rounded-md border border-gray-300 px-3 py-2 text-center text-sm text-gray-700 hover:bg-gray-100 dark:border-white/10 dark:text-stone-300 dark:hover:bg-white/5"
                >
                  Ver comanda / corrigir pagamento
                </Link>
              )}
              <button
                type="button"
                onClick={handleCloseModal}
                className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm text-gray-700 hover:bg-gray-100 dark:border-white/10 dark:text-stone-300 dark:hover:bg-white/5"
              >
                Fechar
              </button>
            </>
          ) : (
            <>
              <ul className="mb-4 divide-y divide-gray-100 dark:divide-white/10">
                {selectedSummary.items.map((item) => (
                  <li key={item.id} className="flex items-start justify-between gap-2 py-2 text-sm">
                    <div>
                      <span
                        className={
                          item.status === 'CANCELLED'
                            ? 'text-gray-400 line-through dark:text-stone-600'
                            : 'text-gray-800 dark:text-white'
                        }
                      >
                        {item.quantity}x {item.productName}
                      </span>
                      {item.modifiers.length > 0 && (
                        <div className="mt-1 flex flex-wrap gap-1">
                          {item.modifiers.map((modifier, index) => (
                            <span
                              key={index}
                              className="rounded-full bg-gray-100 px-2 py-0.5 text-xs text-gray-600 dark:bg-white/10 dark:text-stone-400"
                            >
                              {modifier.optionName}
                            </span>
                          ))}
                        </div>
                      )}
                      {item.observation && (
                        <div className="mt-1 text-xs text-gray-500 dark:text-stone-400">{item.observation}</div>
                      )}
                      {item.discountType && item.status !== 'CANCELLED' && (
                        <div className="mt-1 flex items-center gap-1 text-xs text-orange-600 dark:text-orange-400">
                          <Percent className="h-3 w-3" />
                          -{currencyFormatter.format(item.discountAmount)}
                          {item.discountReason && (
                            <span className="text-gray-400 dark:text-stone-500">({item.discountReason})</span>
                          )}
                        </div>
                      )}
                    </div>
                    <span
                      className={
                        item.status === 'CANCELLED'
                          ? 'shrink-0 text-gray-400 line-through dark:text-stone-600'
                          : 'shrink-0 text-gray-600 dark:text-stone-300'
                      }
                    >
                      {item.discountType && item.status !== 'CANCELLED' && (
                        <span className="mr-1 text-xs text-gray-400 line-through dark:text-stone-500">
                          {currencyFormatter.format(item.subtotal)}
                        </span>
                      )}
                      {currencyFormatter.format(item.status === 'CANCELLED' ? item.subtotal : item.netSubtotal)}
                    </span>
                  </li>
                ))}
              </ul>

              {canDiscount && (
                <div className="mb-4 rounded-lg border border-gray-200 bg-gray-50 p-3 dark:border-white/10 dark:bg-white/5">
                  {!isEditingDiscount ? (
                    <div className="flex items-center justify-between">
                      {selectedSummary.tab.discountType ? (
                        <div className="text-sm text-orange-700 dark:text-orange-400">
                          <span className="flex items-center gap-1 font-medium">
                            <Percent className="h-3.5 w-3.5" />
                            Desconto na comanda: -
                            {currencyFormatter.format(computeDiscountAmount(selectedSummary.tab.discountType, selectedSummary.tab.discountValue, selectedSummary.itemsTotal))}
                          </span>
                          {selectedSummary.tab.discountReason && (
                            <span className="text-xs text-gray-500 dark:text-stone-400">
                              {selectedSummary.tab.discountReason}
                            </span>
                          )}
                        </div>
                      ) : (
                        <span className="text-sm text-gray-500 dark:text-stone-400">Nenhum desconto na comanda.</span>
                      )}
                      <button
                        type="button"
                        onClick={openDiscountForm}
                        className="text-sm text-brand-600 hover:underline dark:text-brand-400"
                      >
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
                            discountKind === 'FIXED'
                              ? 'border-brand-600 bg-brand-50 text-brand-700 dark:border-brand-400 dark:bg-brand-500/10 dark:text-brand-400'
                              : 'border-gray-300 text-gray-600 dark:border-white/10 dark:text-stone-400'
                          }`}
                        >
                          Valor em R$
                        </button>
                        <button
                          type="button"
                          onClick={() => setDiscountKind('PERCENTAGE')}
                          className={`flex-1 rounded-md border px-2 py-1.5 text-xs ${
                            discountKind === 'PERCENTAGE'
                              ? 'border-brand-600 bg-brand-50 text-brand-700 dark:border-brand-400 dark:bg-brand-500/10 dark:text-brand-400'
                              : 'border-gray-300 text-gray-600 dark:border-white/10 dark:text-stone-400'
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
                        className="mb-2 w-full rounded-md border border-gray-300 px-3 py-1.5 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
                      />
                      <input
                        type="text"
                        maxLength={255}
                        value={discountReason}
                        onChange={(e) => setDiscountReason(e.target.value)}
                        placeholder="Motivo (opcional)"
                        className="mb-2 w-full rounded-md border border-gray-300 px-3 py-1.5 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
                      />
                      <div className="flex gap-2">
                        {selectedSummary.tab.discountType && (
                          <button
                            type="button"
                            onClick={handleRemoveTabDiscount}
                            disabled={tabDiscountMutation.isPending}
                            className="flex-1 rounded-md border border-red-300 px-2 py-1.5 text-xs text-red-700 hover:bg-red-50 disabled:opacity-50 dark:border-red-500/30 dark:text-red-400 dark:hover:bg-red-500/10"
                          >
                            Remover
                          </button>
                        )}
                        <button
                          type="button"
                          onClick={() => setIsEditingDiscount(false)}
                          className="flex-1 rounded-md border border-gray-300 px-2 py-1.5 text-xs text-gray-600 hover:bg-gray-100 dark:border-white/10 dark:text-stone-400 dark:hover:bg-white/5"
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

              {canPay && (
                <div className="mb-4 rounded-lg border border-gray-200 bg-gray-50 p-3 dark:border-white/10 dark:bg-white/5">
                  {!isEditingServiceCharge || !canDiscount ? (
                    <div className="flex items-center justify-between">
                      {serviceChargePercentage != null ? (
                        <span className="text-sm text-gray-700 dark:text-stone-300">
                          Taxa de serviço ({serviceChargePercentage}%): {currencyFormatter.format(serviceChargeAmount)}
                        </span>
                      ) : (
                        <span className="text-sm text-gray-500 dark:text-stone-400">Sem taxa de serviço.</span>
                      )}
                      {canDiscount && (
                        <button
                          type="button"
                          onClick={() => setIsEditingServiceCharge(true)}
                          className="text-sm text-brand-600 hover:underline dark:text-brand-400"
                        >
                          {serviceChargePercentage != null ? 'Editar' : 'Adicionar'}
                        </button>
                      )}
                    </div>
                  ) : (
                    <form onSubmit={handleApplyServiceCharge}>
                      <input
                        type="number"
                        required
                        min="0"
                        max="100"
                        step="0.01"
                        value={serviceChargeInput}
                        onChange={(e) => setServiceChargeInput(e.target.value)}
                        placeholder="Percentual (%)"
                        className="mb-2 w-full rounded-md border border-gray-300 px-3 py-1.5 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
                      />
                      <div className="flex gap-2">
                        {serviceChargePercentage != null && (
                          <button
                            type="button"
                            onClick={handleRemoveServiceCharge}
                            className="flex-1 rounded-md border border-red-300 px-2 py-1.5 text-xs text-red-700 hover:bg-red-50 dark:border-red-500/30 dark:text-red-400 dark:hover:bg-red-500/10"
                          >
                            Remover
                          </button>
                        )}
                        <button
                          type="button"
                          onClick={() => setIsEditingServiceCharge(false)}
                          className="flex-1 rounded-md border border-gray-300 px-2 py-1.5 text-xs text-gray-600 hover:bg-gray-100 dark:border-white/10 dark:text-stone-400 dark:hover:bg-white/5"
                        >
                          Cancelar
                        </button>
                        <button
                          type="submit"
                          className="flex-1 rounded-md bg-brand-600 px-2 py-1.5 text-xs font-medium text-white hover:bg-brand-700"
                        >
                          Aplicar
                        </button>
                      </div>
                    </form>
                  )}
                </div>
              )}

              <div className="mb-4 space-y-1 border-t border-gray-200 pt-3 dark:border-white/10">
                {serviceChargePercentage != null && (
                  <div className="flex items-center justify-between text-sm text-gray-500 dark:text-stone-400">
                    <span>Subtotal</span>
                    <span>{currencyFormatter.format(selectedSummary.total)}</span>
                  </div>
                )}
                <div className="flex items-center justify-between text-base font-semibold text-gray-800 dark:text-white">
                  <span>Total</span>
                  <span>{currencyFormatter.format(finalTotal)}</span>
                </div>
              </div>

              <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="paymentMethod">
                Forma de pagamento
              </label>
              <select
                id="paymentMethod"
                value={paymentMethod}
                onChange={(e) => setPaymentMethod(e.target.value as PaymentMethod)}
                className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
              >
                {(Object.keys(PAYMENT_METHOD_LABELS) as PaymentMethod[]).map((method) => (
                  <option key={method} value={method}>
                    {PAYMENT_METHOD_LABELS[method]}
                  </option>
                ))}
              </select>

              {error && <p className="mb-4 text-sm text-red-600 dark:text-red-400">{error}</p>}

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
