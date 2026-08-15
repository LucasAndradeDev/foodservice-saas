import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import { AnimatePresence, motion } from 'framer-motion'
import { Check, CheckCircle2, ChevronDown, Clock, Copy, Loader2, Lock, Pencil, Percent, Plus, Printer, QrCode, Users, Wallet, X } from 'lucide-react'
import { QRCodeCanvas } from 'qrcode.react'
import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { listOrders, type DiscountType, type OrderItem } from '../api/orders'
import { getPixIntegrationStatus } from '../api/pixIntegration'
import {
  applyTabDiscount,
  cancelPixCharge,
  cancelPixChargeById,
  computeDiscountAmount,
  createPixCharge,
  getTab,
  listPixCharges,
  listTabs,
  registerPayments,
  PAYMENT_METHOD_LABELS,
  roundCurrency,
  type PaymentMethod,
  type PixCharge,
  type Tab,
} from '../api/tabs'
import { getMyRestaurant } from '../api/restaurant'
import { useAuth } from '../auth/AuthContext'
import { Button } from '../components/Button'
import { Dropdown, type DropdownOption } from '../components/Dropdown'
import { EmptyState } from '../components/EmptyState'
import { Modal } from '../components/Modal'
import { QrCodeCard } from '../components/QrCodeCard'
import { formatTableLabel } from '../utils/tableLabel'
import { feedbackUrl } from '../utils/publicMenuUrl'
import { PersonSplitPanel } from './checkout/PersonSplitPanel'

let entrySeq = 0
function nextEntryId() {
  entrySeq += 1
  return `entry-${entrySeq}`
}

interface PendingEntry {
  id: string
  method: PaymentMethod
  amount: string
  // Only ever set in split mode (pendingEntries.length > 1) - a single-payment Pix charge still
  // uses the separate top-level pixCharge state below, unchanged. Once set, this entry is
  // read-only and excluded from the manual "Confirmar pagamentos" submit - it resolves on its own
  // via webhook, same as the single-payment flow.
  pixCharge?: PixCharge
  // Set only when this entry came out of the "Dividir por pessoa" panel - shown instead of the
  // numbered "Pessoa N" placeholder. Never sent to the backend, purely a display label for this
  // checkout session.
  label?: string
}

const PAYMENT_METHOD_OPTIONS: DropdownOption<PaymentMethod>[] = (Object.keys(PAYMENT_METHOD_LABELS) as PaymentMethod[]).map((method) => ({
  value: method,
  label: PAYMENT_METHOD_LABELS[method],
}))

type SplitChoice = 'single' | '2' | '3' | '4' | 'person'

const SPLIT_OPTIONS: DropdownOption<SplitChoice>[] = [
  { value: 'single', label: 'Pagamento único' },
  { value: '2', label: 'Dividir em 2x' },
  { value: '3', label: 'Dividir em 3x' },
  { value: '4', label: 'Dividir em 4x' },
  { value: 'person', label: 'Dividir por pessoa...' },
]

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

/** Same idea as extractErrorMessage, but for creating a Pix charge specifically: the backend's
 * validation messages there are internal English text (e.g. "Requested amount exceeds the tab's
 * remaining uncommitted balance of 46.20"), never meant to reach a screen. That one case - the
 * requested amount exceeds what's actually left uncommitted right now - is also the one staff can
 * actually run into, so it's worth translating well instead of flattening it to one generic
 * sentence: the backend already computed the real number, use it. A sibling still-PENDING charge
 * having claimed everything (most often reconstructed from a previous visit to this same modal)
 * is the same condition with remaining at zero, so it gets its own wording instead of a confusing
 * "maior que R$ 0,00". Anything else falls back to the generic message, instead of leaking raw text. */
function extractPixChargeErrorMessage(err: unknown, fallback: string) {
  const backendMessage = isAxiosError(err) ? (err.response?.data?.message as string | undefined) : undefined
  const remainingMatch = backendMessage?.match(/remaining uncommitted balance of (-?\d+(?:\.\d+)?)/)
  if (remainingMatch) {
    const remaining = roundCurrency(Number(remainingMatch[1]))
    if (remaining <= 0) {
      return 'Já existe uma cobrança Pix pendente que cobre todo o valor restante da comanda. Cancele-a antes de gerar outra, ou aguarde a confirmação do pagamento.'
    }
    return `O valor digitado é maior que o restante disponível pra gerar Pix (${currencyFormatter.format(remaining)}). Ajuste o valor e tente novamente.`
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

  const { data: pixStatus } = useQuery({
    queryKey: ['pix-integration'],
    queryFn: getPixIntegrationStatus,
    enabled: canPay,
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
  const [pendingEntries, setPendingEntries] = useState<PendingEntry[]>([])
  const [error, setError] = useState<string | null>(null)
  const [justPaidTabId, setJustPaidTabId] = useState<string | null>(null)
  const [isEditingDiscount, setIsEditingDiscount] = useState(false)
  const [discountKind, setDiscountKind] = useState<DiscountType>('FIXED')
  const [discountValue, setDiscountValue] = useState('')
  const [discountReason, setDiscountReason] = useState('')
  const [serviceChargePercentage, setServiceChargePercentage] = useState<number | null>(null)
  const [isEditingServiceCharge, setIsEditingServiceCharge] = useState(false)
  const [serviceChargeInput, setServiceChargeInput] = useState('')
  const [pixCharge, setPixCharge] = useState<PixCharge | null>(null)
  const [brCodeCopied, setBrCodeCopied] = useState(false)
  const [copiedEntryId, setCopiedEntryId] = useState<string | null>(null)
  const [isPersonSplitOpen, setIsPersonSplitOpen] = useState(false)

  useEffect(() => {
    if (!selectedSummary) return
    function handleEscape(event: KeyboardEvent) {
      if (event.key !== 'Escape') return
      if (isEditingDiscount) setIsEditingDiscount(false)
      else if (isEditingServiceCharge) setIsEditingServiceCharge(false)
      else if (pixCharge) setPixCharge(null)
      else handleCloseModal()
    }
    document.addEventListener('keydown', handleEscape)
    return () => document.removeEventListener('keydown', handleEscape)
  }, [selectedSummary, isEditingDiscount, isEditingServiceCharge, pixCharge])

  const payMutation = useMutation({
    mutationFn: ({
      id,
      entries,
      chargePercentage,
    }: {
      id: string
      entries: { paymentMethod: PaymentMethod; amount: number }[]
      chargePercentage: number | null
    }) => registerPayments(id, entries, chargePercentage ?? undefined),
    onSuccess: (updatedTab, variables) => {
      queryClient.invalidateQueries({ queryKey: ['tabs'] })
      queryClient.invalidateQueries({ queryKey: ['tables'] })
      setSelectedSummary((prev) => (prev ? { ...prev, tab: updatedTab } : prev))
      if (updatedTab.status === 'CLOSED') {
        setJustPaidTabId(variables.id)
      } else {
        setPendingEntries([{ id: nextEntryId(), method: 'PIX', amount: String(updatedTab.remainingBalance ?? 0) }])
      }
    },
    onError: (err) => setError(extractErrorMessage(err, 'Não foi possível registrar o pagamento. Tente novamente.')),
  })

  const pixChargeMutation = useMutation({
    // Passes the Caixa's current service-charge edit along, same as registerPayments does -
    // honored server-side only for an OWNER/MANAGER caller, ignored otherwise.
    mutationFn: ({ tabId, serviceChargePercentage: pct }: { tabId: string; serviceChargePercentage: number | null }) =>
      createPixCharge(tabId, pct),
    // Creating the charge freezes the tab's billTotal server-side (TabService#freezeBillTotalForPixCharge)
    // so the QR code's amount can't drift - but selectedSummary is local state that only reflects
    // what was fetched before the charge existed. Without refetching here, hasLockedTotal stays
    // false and the discount/service-charge "Editar" controls remain visible and editable, letting
    // staff change the total on screen while the already-generated QR code still encodes the old one.
    onSuccess: async (charge, { tabId }) => {
      setPixCharge(charge)
      const updatedTab = await getTab(tabId)
      setSelectedSummary((prev) => (prev && prev.tab.id === tabId ? { ...prev, tab: updatedTab } : prev))
    },
    onError: (err) => setError(extractPixChargeErrorMessage(err, 'Não foi possível gerar a cobrança Pix. Tente novamente.')),
  })

  const cancelPixChargeMutation = useMutation({
    mutationFn: (tabId: string) => cancelPixCharge(tabId),
    onSuccess: async (_data, tabId) => {
      setPixCharge(null)
      const updatedTab = await getTab(tabId)
      setSelectedSummary((prev) => (prev && prev.tab.id === tabId ? { ...prev, tab: updatedTab } : prev))
    },
    onError: (err) => setError(extractErrorMessage(err, 'Não foi possível cancelar a cobrança Pix. Tente novamente.')),
  })

  // Confirmation is asynchronous (Woovi calls a webhook when the customer pays) - poll the tab
  // while a charge is outstanding and watch for it closing on its own, same idea as the digital
  // menu's order-status polling.
  const { data: polledTab } = useQuery({
    queryKey: ['tabs', selectedSummary?.tab.id, 'pix-poll'],
    queryFn: () => getTab(selectedSummary!.tab.id),
    enabled: !!pixCharge && !!selectedSummary,
    refetchInterval: 3000,
    refetchIntervalInBackground: true,
  })

  useEffect(() => {
    if (!pixCharge || !polledTab || polledTab.status !== 'CLOSED') return
    queryClient.invalidateQueries({ queryKey: ['tabs'] })
    queryClient.invalidateQueries({ queryKey: ['tables'] })
    setSelectedSummary((prev) => (prev && prev.tab.id === polledTab.id ? { ...prev, tab: polledTab } : prev))
    setJustPaidTabId(polledTab.id)
    setPixCharge(null)
  }, [pixCharge, polledTab, queryClient])

  // Split-payment counterpart of pixChargeMutation above: generates a QR code for ONE entry's own
  // share instead of the whole tab, so several people can each pay their own part via Pix at once.
  const entryPixChargeMutation = useMutation({
    mutationFn: ({ tabId, amount }: { tabId: string; entryId: string; amount: number }) =>
      createPixCharge(tabId, serviceChargePercentage, amount),
    onSuccess: (charge, { entryId }) => {
      setPendingEntries((prev) => prev.map((entry) => (entry.id === entryId ? { ...entry, pixCharge: charge } : entry)))
    },
    onError: (err) => setError(extractPixChargeErrorMessage(err, 'Não foi possível gerar a cobrança Pix para essa parcela. Tente novamente.')),
  })

  const entryCancelPixChargeMutation = useMutation({
    mutationFn: ({ tabId, chargeId }: { tabId: string; entryId: string; chargeId: string }) => cancelPixChargeById(tabId, chargeId),
    onSuccess: (_data, { entryId }) => {
      setPendingEntries((prev) => prev.map((entry) => (entry.id === entryId ? { ...entry, pixCharge: undefined } : entry)))
    },
    onError: (err) => setError(extractErrorMessage(err, 'Não foi possível cancelar a cobrança Pix dessa parcela. Tente novamente.')),
  })

  // Fallback for a split Pix share the customer paid through some channel outside the app (a Pix key
  // read out loud, a QR shared over WhatsApp, Woovi down, etc.) - registers it as a plain manual
  // payment, same as choosing "Dinheiro" would, without ever going through Woovi. Kept as a small,
  // separate action (never folded into the bulk "Confirmar pagamentos") so staff can't land on it by
  // accident while reaching for the real QR button next to it.
  const entryManualPixConfirmMutation = useMutation({
    mutationFn: ({ tabId, amount }: { tabId: string; entryId: string; amount: number }) =>
      registerPayments(tabId, [{ paymentMethod: 'PIX', amount }], hasLockedTotal ? undefined : (serviceChargePercentage ?? undefined)),
    onSuccess: (updatedTab, { entryId }) => {
      queryClient.invalidateQueries({ queryKey: ['tabs'] })
      queryClient.invalidateQueries({ queryKey: ['tables'] })
      setSelectedSummary((prev) => (prev && prev.tab.id === updatedTab.id ? { ...prev, tab: updatedTab } : prev))
      if (updatedTab.status === 'CLOSED') {
        setJustPaidTabId(updatedTab.id)
        setPendingEntries([])
      } else {
        setPendingEntries((prev) => prev.filter((entry) => entry.id !== entryId))
      }
    },
    onError: (err) => setError(extractErrorMessage(err, 'Não foi possível confirmar esse pagamento. Tente novamente.')),
  })

  const hasOutstandingEntryPixCharge = pendingEntries.some((entry) => entry.pixCharge)

  // Polls both the list of outstanding charges (to reconcile each entry's own QR code - matched by
  // id, never by amount, since two equal split shares would otherwise be indistinguishable) and the
  // tab itself (to notice it closing once every entry, Pix or manual, is accounted for).
  const { data: splitPixCharges } = useQuery({
    queryKey: ['tabs', selectedSummary?.tab.id, 'split-pix-poll'],
    queryFn: () => listPixCharges(selectedSummary!.tab.id),
    enabled: hasOutstandingEntryPixCharge && !!selectedSummary,
    refetchInterval: 3000,
    refetchIntervalInBackground: true,
  })

  const { data: splitPolledTab } = useQuery({
    queryKey: ['tabs', selectedSummary?.tab.id, 'split-tab-poll'],
    queryFn: () => getTab(selectedSummary!.tab.id),
    enabled: hasOutstandingEntryPixCharge && !!selectedSummary,
    refetchInterval: 3000,
    refetchIntervalInBackground: true,
  })

  useEffect(() => {
    if (!splitPixCharges) return
    setPendingEntries((prev) =>
      prev.map((entry) => {
        if (!entry.pixCharge) return entry
        const match = splitPixCharges.find((charge) => charge.id === entry.pixCharge!.id)
        // Not in the list anymore (and not caught by the tab-closed effect below, which would
        // have already cleared pendingEntries) means it was cancelled or expired outside this
        // screen - revert the entry to editable rather than leave it stuck showing a dead QR code.
        if (!match) return { ...entry, pixCharge: undefined }
        return match.status !== entry.pixCharge.status ? { ...entry, pixCharge: match } : entry
      }),
    )
  }, [splitPixCharges])

  useEffect(() => {
    if (!hasOutstandingEntryPixCharge || !splitPolledTab || splitPolledTab.status !== 'CLOSED') return
    queryClient.invalidateQueries({ queryKey: ['tabs'] })
    queryClient.invalidateQueries({ queryKey: ['tables'] })
    setSelectedSummary((prev) => (prev && prev.tab.id === splitPolledTab.id ? { ...prev, tab: splitPolledTab } : prev))
    setJustPaidTabId(splitPolledTab.id)
    setPendingEntries([])
  }, [hasOutstandingEntryPixCharge, splitPolledTab, queryClient])

  const tabDiscountMutation = useMutation({
    mutationFn: ({ id, discountType, value, reason }: { id: string; discountType: DiscountType | null; value?: number; reason?: string }) =>
      applyTabDiscount(id, { discountType, discountValue: value, reason }),
    onSuccess: (updatedTab) => {
      queryClient.invalidateQueries({ queryKey: ['tabs', 'OPEN'] })
      setSelectedSummary((prev) => {
        if (!prev) return prev
        const total = roundCurrency(prev.itemsTotal - computeDiscountAmount(updatedTab.discountType, updatedTab.discountValue, prev.itemsTotal))
        const chargeAmount = roundCurrency((total * (serviceChargePercentage ?? 0)) / 100)
        setPendingEntries([{ id: nextEntryId(), method: 'PIX', amount: String(roundCurrency(total + chargeAmount)) }])
        return { ...prev, tab: updatedTab, total }
      })
      setIsEditingDiscount(false)
    },
    onError: () => setError('Não foi possível aplicar o desconto nesta comanda.'),
  })

  async function handleCardClick(summary: TabSummary) {
    if (!summary.isReady) {
      navigate(`/tabs/${summary.tab.id}`)
      return
    }
    setSelectedSummary(summary)
    setError(null)
    setIsEditingDiscount(false)
    setIsEditingServiceCharge(false)
    setPixCharge(null)
    setIsPersonSplitOpen(false)
    setServiceChargeInput(String(restaurant?.serviceChargePercentage ?? 10))
    if (summary.tab.billTotal != null) {
      // A previous partial payment already locked this tab's total; the service charge can no
      // longer be changed, so mirror what the backend already froze instead of the restaurant default.
      setServiceChargePercentage(summary.tab.serviceChargePercentage)
      const remaining = summary.tab.remainingBalance ?? 0
      setPendingEntries([{ id: nextEntryId(), method: 'PIX', amount: String(remaining) }])
      // A Pix charge generated in an earlier visit to this modal (or orphaned by a page reload) still
      // commits part of the remaining balance server-side even though this fresh mount has no memory
      // of it - reconstruct it here instead of silently blocking new payment attempts with a
      // "remaining balance" the Caixa can't otherwise explain or act on.
      try {
        const charges = await listPixCharges(summary.tab.id)
        const pending = charges.filter((charge) => charge.status === 'PENDING')
        if (pending.length > 0) {
          const pendingSum = roundCurrency(pending.reduce((sum, charge) => sum + charge.amount, 0))
          const leftover = roundCurrency(remaining - pendingSum)
          const entries: PendingEntry[] = pending.map((charge) => ({
            id: nextEntryId(),
            method: 'PIX',
            amount: String(charge.amount),
            pixCharge: charge,
          }))
          if (leftover > 0.001) {
            entries.push({ id: nextEntryId(), method: 'PIX', amount: String(leftover) })
          }
          setPendingEntries(entries)
        }
      } catch {
        // Best-effort reconstruction - if it fails, the Caixa still sees the default single-entry
        // form reflecting the correct remaining balance and can cancel a stuck charge via
        // "Já recebi esse Pix por fora" or by contacting support if it truly can't be recovered.
      }
    } else {
      const defaultCharge = restaurant?.serviceChargeEnabled ? restaurant.serviceChargePercentage : null
      setServiceChargePercentage(defaultCharge)
      const chargeAmount = roundCurrency((summary.total * (defaultCharge ?? 0)) / 100)
      setPendingEntries([{ id: nextEntryId(), method: 'PIX', amount: String(roundCurrency(summary.total + chargeAmount)) }])
    }
  }

  function handleCloseModal() {
    setSelectedSummary(null)
    setJustPaidTabId(null)
    setPixCharge(null)
    setIsPersonSplitOpen(false)
  }

  function handleCopyBrCode() {
    if (!pixCharge) return
    navigator.clipboard.writeText(pixCharge.brCode)
    setBrCodeCopied(true)
    setTimeout(() => setBrCodeCopied(false), 2000)
  }

  function handleCopyEntryBrCode(entryId: string, brCode: string) {
    navigator.clipboard.writeText(brCode)
    setCopiedEntryId(entryId)
    setTimeout(() => setCopiedEntryId((prev) => (prev === entryId ? null : prev)), 2000)
  }

  const hasLockedTotal = selectedSummary?.tab.billTotal != null
  const serviceChargeAmount = selectedSummary
    ? hasLockedTotal
      ? (selectedSummary.tab.serviceChargeAmount ?? 0)
      : roundCurrency((selectedSummary.total * (serviceChargePercentage ?? 0)) / 100)
    : 0
  const finalTotal = selectedSummary
    ? hasLockedTotal
      ? (selectedSummary.tab.billTotal ?? 0)
      : roundCurrency(selectedSummary.total + serviceChargeAmount)
    : 0
  const amountPaid = selectedSummary?.tab.amountPaid ?? 0
  const remainingBalance = hasLockedTotal ? (selectedSummary?.tab.remainingBalance ?? 0) : finalTotal
  const isZeroBalance = remainingBalance <= 0.001
  const entriesSum = roundCurrency(pendingEntries.reduce((sum, entry) => sum + (Number(entry.amount) || 0), 0))
  const amountLeftToAllocate = roundCurrency(remainingBalance - entriesSum)
  // "Gerar QR Code Pix" only makes sense when the manual form below is actually set up to record a
  // single Pix payment - showing it while staff has "Dinheiro"/"Cartão" selected, or is splitting
  // across methods, offered an action unrelated to what they were about to register.
  const isSinglePixEntry = pendingEntries.length === 1 && pendingEntries[0].method === 'PIX'
  const isSplitMode = pendingEntries.length > 1
  // A split entry still set to Pix but without a generated charge yet isn't manually confirmable -
  // the only way to actually collect it is its own "Gerar QR Code Pix" button, never the bulk
  // "Confirmar pagamentos" below, so staff can't accidentally mark a Pix share as paid without a real
  // charge behind it.
  const isAwaitingSplitPixQr = (entry: PendingEntry) => isSplitMode && entry.method === 'PIX' && !entry.pixCharge
  // Entries with a generated Pix charge resolve on their own via webhook - only what's left (manual
  // methods, plus any split Pix share still waiting on its QR) is what "Confirmar pagamentos" below
  // actually submits.
  const manualEntries = pendingEntries.filter((entry) => !entry.pixCharge && !isAwaitingSplitPixQr(entry))
  const manualSplitEntriesSum = roundCurrency(manualEntries.reduce((sum, entry) => sum + (Number(entry.amount) || 0), 0))
  const hasManualEntriesToConfirm = manualEntries.length > 0

  const canConfirmPayment =
    !!selectedSummary &&
    justPaidTabId !== selectedSummary.tab.id &&
    canPay &&
    !payMutation.isPending &&
    (hasManualEntriesToConfirm || isZeroBalance) &&
    (isZeroBalance || (manualSplitEntriesSum > 0 && amountLeftToAllocate >= -0.001))

  // Enter confirms the payment while the form is valid -- skipped while a sub-form (discount/service
  // charge) is open so its own native Enter-to-submit isn't double-fired by this handler.
  useEffect(() => {
    if (!canConfirmPayment || isEditingDiscount || isEditingServiceCharge || pixCharge) return
    function handleEnter(event: KeyboardEvent) {
      if (event.key !== 'Enter') return
      const target = event.target as HTMLElement
      if (target.tagName === 'TEXTAREA') return
      event.preventDefault()
      handleRegisterPayments()
    }
    document.addEventListener('keydown', handleEnter)
    return () => document.removeEventListener('keydown', handleEnter)
  }, [canConfirmPayment, isEditingDiscount, isEditingServiceCharge, pixCharge])

  function handleSplitEqually(parts: number) {
    const share = roundCurrency(remainingBalance / parts)
    const entries: PendingEntry[] = Array.from({ length: parts }, (_, index) => ({
      id: nextEntryId(),
      method: 'PIX' as PaymentMethod,
      // Last share absorbs the rounding remainder so the sum always matches remainingBalance exactly.
      amount: String(index === parts - 1 ? roundCurrency(remainingBalance - share * (parts - 1)) : share),
    }))
    setPendingEntries(entries)
  }

  function handleSplitChoiceChange(choice: SplitChoice) {
    if (choice === 'single') {
      setPendingEntries([{ id: nextEntryId(), method: 'PIX', amount: String(remainingBalance) }])
    } else if (choice === 'person') {
      setIsPersonSplitOpen(true)
    } else {
      handleSplitEqually(Number(choice))
    }
  }

  function handlePersonSplitApply(entries: { name: string; amount: number }[]) {
    setPendingEntries(entries.map((entry) => ({ id: nextEntryId(), method: 'PIX' as PaymentMethod, amount: String(entry.amount), label: entry.name })))
    setIsPersonSplitOpen(false)
  }

  function addEntry() {
    setPendingEntries((prev) => [
      ...prev,
      { id: nextEntryId(), method: 'PIX', amount: amountLeftToAllocate > 0 ? String(amountLeftToAllocate) : '' },
    ])
  }

  function removeEntry(id: string) {
    setPendingEntries((prev) => prev.filter((entry) => entry.id !== id))
  }

  function updateEntryAmount(id: string, amount: string) {
    setPendingEntries((prev) => prev.map((entry) => (entry.id === id ? { ...entry, amount } : entry)))
  }

  function updateEntryMethod(id: string, method: PaymentMethod) {
    setPendingEntries((prev) => prev.map((entry) => (entry.id === id ? { ...entry, method } : entry)))
  }

  function handleRegisterPayments() {
    if (!selectedSummary) return
    const entries = manualEntries
      .map((entry) => ({ paymentMethod: entry.method, amount: Number(entry.amount) }))
      .filter((entry) => entry.amount > 0)
    if (entries.length === 0 && !isZeroBalance) return
    setError(null)
    payMutation.mutate({
      id: selectedSummary.tab.id,
      entries,
      chargePercentage: hasLockedTotal || isZeroBalance ? null : serviceChargePercentage,
    })
  }

  function handleApplyServiceCharge(event: FormEvent) {
    event.preventDefault()
    if (!selectedSummary) return
    const value = Number(serviceChargeInput)
    if (!value || value < 0 || value > 100) return
    setServiceChargePercentage(value)
    setIsEditingServiceCharge(false)
    const chargeAmount = roundCurrency((selectedSummary.total * value) / 100)
    setPendingEntries([{ id: nextEntryId(), method: 'PIX', amount: String(roundCurrency(selectedSummary.total + chargeAmount)) }])
  }

  function handleRemoveServiceCharge() {
    if (!selectedSummary) return
    setServiceChargePercentage(null)
    setIsEditingServiceCharge(false)
    setPendingEntries([{ id: nextEntryId(), method: 'PIX', amount: String(selectedSummary.total) }])
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
      <div className="mb-5 flex items-center gap-3 rounded-2xl border border-gray-200 bg-white p-5 shadow-sm dark:border-white/10 dark:bg-stone-900">
        <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-brand-600 text-white shadow-sm">
          <Wallet className="h-5 w-5" />
        </span>
        <h1 className="text-lg font-bold text-gray-900 dark:text-white">Fechar Conta</h1>
      </div>

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
                            {currencyFormatter.format(tab.billTotal ?? tab.amountPaid)}
                          </span>
                          <span className="flex items-center gap-1 text-xs font-medium text-gray-400 opacity-0 transition group-hover:text-brand-600 group-hover:opacity-100 dark:text-stone-500 dark:group-hover:text-brand-400">
                            <Pencil className="h-3.5 w-3.5" />
                            Ver pagamentos
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
                {selectedSummary.tab.billTotal ? 'Pagamento confirmado.' : 'Comanda fechada.'}
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
                  Ver comanda / pagamentos
                </Link>
              )}
              <Button type="button" variant="secondary" onClick={handleCloseModal} className="w-full">
                Fechar
              </Button>
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

              {canDiscount && !hasLockedTotal && (
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

              {canPay && !hasLockedTotal && !isZeroBalance && (
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

              <div className="mb-4 border-t border-gray-200 pt-3 dark:border-white/10">
                {serviceChargePercentage != null && !hasLockedTotal && (
                  <div className="mb-1 flex items-center justify-between text-sm text-gray-500 dark:text-stone-400">
                    <span>Subtotal</span>
                    <span>{currencyFormatter.format(selectedSummary.total)}</span>
                  </div>
                )}
                <div className="flex items-baseline justify-between">
                  <span className="text-sm font-medium text-gray-500 dark:text-stone-400">Total</span>
                  <span className="text-2xl font-bold tracking-tight text-gray-900 dark:text-white">
                    {currencyFormatter.format(finalTotal)}
                  </span>
                </div>
                {amountPaid > 0 && (
                  <div className="mt-3 grid grid-cols-2 gap-2 rounded-xl bg-gray-50 p-3 dark:bg-white/5">
                    <div>
                      <p className="text-xs font-medium text-gray-500 dark:text-stone-400">Já pago</p>
                      <p className="text-base font-semibold text-green-700 dark:text-green-400">
                        {currencyFormatter.format(amountPaid)}
                      </p>
                    </div>
                    <div className="text-right">
                      <p className="text-xs font-medium text-gray-500 dark:text-stone-400">Restante</p>
                      <p className="text-base font-semibold text-amber-700 dark:text-amber-400">
                        {currencyFormatter.format(remainingBalance)}
                      </p>
                    </div>
                  </div>
                )}
              </div>

              {selectedSummary.tab.payments.filter((p) => p.status === 'ACTIVE').length > 0 && (
                <ul className="mb-4 space-y-1.5 rounded-xl border border-gray-200 p-3 text-sm dark:border-white/10">
                  {selectedSummary.tab.payments
                    .filter((p) => p.status === 'ACTIVE')
                    .map((payment) => (
                      <li key={payment.id} className="flex items-center justify-between text-gray-600 dark:text-stone-400">
                        <span className="flex items-center gap-1.5">
                          <CheckCircle2 className="h-3.5 w-3.5 text-green-600 dark:text-green-400" />
                          {PAYMENT_METHOD_LABELS[payment.paymentMethod]}
                        </span>
                        <span className="font-medium text-gray-700 dark:text-stone-300">{currencyFormatter.format(payment.amount)}</span>
                      </li>
                    ))}
                </ul>
              )}

              {canPay && isZeroBalance && (
                <div className="mb-4 rounded-lg border border-gray-200 bg-gray-50 p-3 text-sm text-gray-600 dark:border-white/10 dark:bg-white/5 dark:text-stone-400">
                  Nenhum valor a cobrar nesta comanda. Feche pra liberar a mesa.
                </div>
              )}

              {canPay && !isZeroBalance && pixCharge && (
                <div className="mb-4 flex flex-col items-center gap-3 rounded-lg border border-gray-200 bg-gray-50 p-4 dark:border-white/10 dark:bg-white/5">
                  <div className="rounded-xl border border-gray-200 bg-white p-3 shadow-md">
                    <QRCodeCanvas value={pixCharge.brCode} size={168} />
                  </div>
                  <p className="flex items-center gap-1.5 text-sm text-gray-600 dark:text-stone-400">
                    <Loader2 className="h-4 w-4 animate-spin" />
                    Aguardando o cliente pagar...
                  </p>
                  <div className="w-full">
                    <p className="mb-1 text-xs font-semibold tracking-wide text-gray-400 uppercase dark:text-stone-500">
                      Pix copia e cola
                    </p>
                    <p className="truncate rounded-md bg-white px-3 py-2 text-xs text-gray-700 dark:bg-stone-800 dark:text-stone-300">
                      {pixCharge.brCode}
                    </p>
                  </div>
                  <div className="flex w-full gap-2">
                    <button
                      type="button"
                      onClick={handleCopyBrCode}
                      className="flex flex-1 items-center justify-center gap-1.5 rounded-md bg-brand-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-brand-700"
                    >
                      {brCodeCopied ? <Check className="h-4 w-4" /> : <Copy className="h-4 w-4" />}
                      {brCodeCopied ? 'Copiado!' : 'Copiar código'}
                    </button>
                    <button
                      type="button"
                      onClick={() => selectedSummary && cancelPixChargeMutation.mutate(selectedSummary.tab.id)}
                      disabled={cancelPixChargeMutation.isPending}
                      className="flex flex-1 items-center justify-center gap-1.5 rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-100 disabled:opacity-50 dark:border-white/10 dark:text-stone-300 dark:hover:bg-white/5"
                    >
                      {cancelPixChargeMutation.isPending ? 'Cancelando...' : 'Cancelar'}
                    </button>
                  </div>
                </div>
              )}

              {canPay && !isZeroBalance && !pixCharge && isPersonSplitOpen && (
                <PersonSplitPanel
                  items={selectedSummary.items}
                  remainingBalance={remainingBalance}
                  onApply={handlePersonSplitApply}
                  onCancel={() => setIsPersonSplitOpen(false)}
                />
              )}

              {canPay && !isZeroBalance && !pixCharge && !isPersonSplitOpen && (
                <>
                  {pixStatus?.configured && isSinglePixEntry && !hasOutstandingEntryPixCharge && (
                    <button
                      type="button"
                      onClick={() =>
                        pixChargeMutation.mutate({ tabId: selectedSummary.tab.id, serviceChargePercentage })
                      }
                      disabled={pixChargeMutation.isPending}
                      className="mb-3 flex w-full items-center justify-center gap-1.5 rounded-lg bg-brand-600 px-3 py-2.5 text-sm font-medium text-white shadow-sm transition hover:bg-brand-700 disabled:opacity-50"
                    >
                      <QrCode className="h-4 w-4" />
                      {pixChargeMutation.isPending ? 'Gerando QR Code...' : 'Gerar QR Code Pix'}
                    </button>
                  )}
                  {/* Once any entry has a generated Pix charge, its amount is committed server-side
                      (TabService#registerPayments now reserves PENDING pix charges out of the
                      remaining balance) - changing the split shape out from under it would orphan
                      that charge (still frozen, still payable) with nothing in the UI tracking it
                      anymore, so the whole split control and "+ add" are frozen right along with it. */}
                  <div className={`mb-3 ${hasOutstandingEntryPixCharge ? 'pointer-events-none opacity-50' : ''}`}>
                    <Dropdown<SplitChoice>
                      value={
                        pendingEntries.some((entry) => entry.label)
                          ? 'person'
                          : pendingEntries.length >= 2 && pendingEntries.length <= 4
                            ? (String(pendingEntries.length) as SplitChoice)
                            : 'single'
                      }
                      options={SPLIT_OPTIONS}
                      onChange={handleSplitChoiceChange}
                      icon={Users}
                    />
                  </div>

                  <ul className="mb-2 space-y-3">
                    {pendingEntries.map((entry, index) => {
                      const canGenerateEntryQr = isAwaitingSplitPixQr(entry) && pixStatus?.configured
                      const entryChargePaid = entry.pixCharge?.status === 'PAID'
                      return (
                        <li
                          key={entry.id}
                          className={
                            isSplitMode
                              ? 'rounded-xl border border-gray-200 bg-gray-50/60 p-3 dark:border-white/10 dark:bg-white/[0.03]'
                              : undefined
                          }
                        >
                          {isSplitMode && (
                            <div className="mb-2 flex items-center gap-2">
                              <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-brand-100 text-xs font-bold text-brand-700 dark:bg-brand-500/20 dark:text-brand-400">
                                {index + 1}
                              </span>
                              <span className="text-xs font-semibold tracking-wide text-gray-400 uppercase dark:text-stone-500">
                                {entry.label ?? `Pessoa ${index + 1}`}
                              </span>
                            </div>
                          )}
                          <div className="space-y-2">
                          {!entry.pixCharge && (
                            <div className="flex items-center gap-2">
                              <div className="flex-1">
                                <Dropdown<PaymentMethod>
                                  value={entry.method}
                                  options={PAYMENT_METHOD_OPTIONS}
                                  onChange={(method) => updateEntryMethod(entry.id, method)}
                                  fullWidth
                                  panelClassName="w-full"
                                  mobileTitle={`Forma de pagamento ${index + 1}`}
                                />
                              </div>
                              <input
                                aria-label={`Valor ${index + 1}`}
                                type="number"
                                min="0.01"
                                step="0.01"
                                value={entry.amount}
                                onChange={(e) => updateEntryAmount(entry.id, e.target.value)}
                                className="w-28 rounded-md border border-gray-300 px-2 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
                              />
                              {isSplitMode && (
                                <button
                                  type="button"
                                  onClick={() => removeEntry(entry.id)}
                                  aria-label="Remover este pagamento"
                                  className="shrink-0 text-gray-400 hover:text-red-600 dark:text-stone-500 dark:hover:text-red-400"
                                >
                                  <X className="h-4 w-4" />
                                </button>
                              )}
                            </div>
                          )}
                          {canGenerateEntryQr && (() => {
                            const isThisEntryGeneratingQr = entryPixChargeMutation.isPending && entryPixChargeMutation.variables?.entryId === entry.id
                            return (
                              <button
                                type="button"
                                onClick={() =>
                                  entryPixChargeMutation.mutate({
                                    tabId: selectedSummary.tab.id,
                                    entryId: entry.id,
                                    amount: Number(entry.amount),
                                  })
                                }
                                disabled={isThisEntryGeneratingQr || !(Number(entry.amount) > 0)}
                                className="flex w-full items-center justify-center gap-1.5 rounded-lg bg-brand-600 px-3 py-2.5 text-sm font-medium text-white shadow-sm transition hover:bg-brand-700 disabled:opacity-50"
                              >
                                <QrCode className="h-4 w-4" />
                                {isThisEntryGeneratingQr ? 'Gerando QR Code...' : 'Gerar QR Code Pix'}
                              </button>
                            )
                          })()}
                          {canGenerateEntryQr && (() => {
                            const isThisEntryConfirming =
                              entryManualPixConfirmMutation.isPending && entryManualPixConfirmMutation.variables?.entryId === entry.id
                            return (
                              <button
                                type="button"
                                onClick={() =>
                                  entryManualPixConfirmMutation.mutate({
                                    tabId: selectedSummary.tab.id,
                                    entryId: entry.id,
                                    amount: Number(entry.amount),
                                  })
                                }
                                disabled={isThisEntryConfirming || !(Number(entry.amount) > 0)}
                                className="mt-1.5 w-full text-center text-xs text-gray-400 hover:text-gray-600 hover:underline disabled:opacity-50 dark:text-stone-500 dark:hover:text-stone-300"
                              >
                                {isThisEntryConfirming ? 'Confirmando...' : 'Já recebi esse Pix por fora (confirmar sem QR)'}
                              </button>
                            )
                          })()}
                          {entry.pixCharge && (
                            <div className="flex flex-col items-center gap-3">
                              <div className="flex w-full items-center justify-between">
                                <span className="text-sm font-medium text-gray-700 dark:text-stone-300">
                                  Pix ({currencyFormatter.format(entry.pixCharge.amount)})
                                </span>
                                {entryChargePaid ? (
                                  <span className="flex items-center gap-1 text-sm font-medium text-green-700 dark:text-green-400">
                                    <Check className="h-4 w-4" />
                                    Pago
                                  </span>
                                ) : (
                                  <span className="flex items-center gap-1 text-sm text-gray-600 dark:text-stone-400">
                                    <Loader2 className="h-4 w-4 animate-spin" />
                                    Aguardando pagamento
                                  </span>
                                )}
                              </div>
                              <div className="rounded-xl border border-gray-200 bg-white p-3 shadow-md">
                                <QRCodeCanvas value={entry.pixCharge.brCode} size={168} />
                              </div>
                              {!entryChargePaid && (() => {
                                const isThisEntryCancelling =
                                  entryCancelPixChargeMutation.isPending && entryCancelPixChargeMutation.variables?.entryId === entry.id
                                return (
                                  <div className="flex w-full gap-2">
                                    <button
                                      type="button"
                                      onClick={() => handleCopyEntryBrCode(entry.id, entry.pixCharge!.brCode)}
                                      className="flex flex-1 items-center justify-center gap-1.5 rounded-md bg-brand-600 px-3 py-2 text-sm font-medium text-white hover:bg-brand-700"
                                    >
                                      {copiedEntryId === entry.id ? <Check className="h-4 w-4" /> : <Copy className="h-4 w-4" />}
                                      {copiedEntryId === entry.id ? 'Copiado!' : 'Copiar código'}
                                    </button>
                                    <button
                                      type="button"
                                      onClick={() =>
                                        entryCancelPixChargeMutation.mutate({
                                          tabId: selectedSummary.tab.id,
                                          entryId: entry.id,
                                          chargeId: entry.pixCharge!.id,
                                        })
                                      }
                                      disabled={isThisEntryCancelling}
                                      className="flex flex-1 items-center justify-center gap-1.5 rounded-md border border-gray-300 px-3 py-2 text-sm text-gray-700 hover:bg-gray-100 disabled:opacity-50 dark:border-white/10 dark:text-stone-300 dark:hover:bg-white/5"
                                    >
                                      {isThisEntryCancelling ? 'Cancelando...' : 'Cancelar'}
                                    </button>
                                  </div>
                                )
                              })()}
                            </div>
                          )}
                          </div>
                        </li>
                      )
                    })}
                  </ul>

                  <button
                    type="button"
                    onClick={addEntry}
                    disabled={hasOutstandingEntryPixCharge}
                    className="mb-3 flex items-center gap-1 text-sm font-medium text-brand-600 hover:underline disabled:pointer-events-none disabled:opacity-50 dark:text-brand-400"
                  >
                    <Plus className="h-3.5 w-3.5" />
                    Adicionar outro pagamento
                  </button>

                  <div className="mb-4 space-y-1.5 rounded-xl border border-gray-200 bg-gray-50 p-3.5 text-sm dark:border-white/10 dark:bg-white/5">
                    <div className="flex items-center justify-between font-semibold text-gray-800 dark:text-white">
                      <span>Você está registrando</span>
                      <span>{currencyFormatter.format(manualSplitEntriesSum)}</span>
                    </div>
                    {hasOutstandingEntryPixCharge && (
                      <div className="flex items-center justify-between text-xs text-gray-500 dark:text-stone-400">
                        <span>Aguardando confirmação via Pix</span>
                        <span>{currencyFormatter.format(roundCurrency(entriesSum - manualSplitEntriesSum))}</span>
                      </div>
                    )}
                    {amountLeftToAllocate > 0.001 && (
                      <div className="flex items-center justify-between text-xs text-amber-700 dark:text-amber-400">
                        <span>Fica faltando (comanda continua aberta)</span>
                        <span>{currencyFormatter.format(amountLeftToAllocate)}</span>
                      </div>
                    )}
                    {amountLeftToAllocate < -0.001 && (
                      <div className="flex items-center justify-between text-xs text-wine-600 dark:text-wine-400">
                        <span>Passou do restante em</span>
                        <span>{currencyFormatter.format(-amountLeftToAllocate)}</span>
                      </div>
                    )}
                  </div>
                </>
              )}

              {error && !isPersonSplitOpen && <p className="mb-4 text-sm text-wine-600 dark:text-wine-400">{error}</p>}

              {/* Nothing left to hand-confirm once every split entry has its own Pix charge - the
                  tab closes on its own as each one's webhook lands, same as the single-payment flow.
                  Also hidden while assigning items to people below - that panel has its own
                  Cancelar/Aplicar pair, and pendingEntries still holds the *previous* split shape
                  until Aplicar overwrites it, so this button would otherwise confirm stale amounts. */}
              {canPay && !pixCharge && !isPersonSplitOpen && (hasManualEntriesToConfirm || isZeroBalance) && (
                <Button
                  type="button"
                  onClick={handleRegisterPayments}
                  disabled={payMutation.isPending || (!isZeroBalance && (manualSplitEntriesSum <= 0 || amountLeftToAllocate < -0.001))}
                  className="w-full"
                >
                  {isZeroBalance
                    ? 'Fechar comanda'
                    : amountLeftToAllocate > 0.001
                      ? `Registrar ${manualEntries.length > 1 ? manualEntries.length + ' pagamentos parciais' : 'pagamento parcial'}`
                      : manualEntries.length > 1
                        ? `Confirmar ${manualEntries.length} pagamentos`
                        : 'Confirmar pagamento'}
                  {canConfirmPayment && !isEditingDiscount && !isEditingServiceCharge && (
                    <kbd className="ml-1 hidden rounded border border-white/40 px-1 text-[10px] font-normal opacity-70 sm:inline">↵</kbd>
                  )}
                </Button>
              )}
            </>
          )}
        </Modal>
      )}
    </div>
  )
}
