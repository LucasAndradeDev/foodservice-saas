import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import { motion } from 'framer-motion'
import {
  ArrowLeft,
  ArrowRightLeft,
  Ban,
  Check,
  Clock,
  ClipboardList,
  Combine,
  Flame,
  GitMerge,
  Layers,
  Lock,
  Minus,
  PackageCheck,
  Pencil,
  Percent,
  Plus,
  Printer,
  Receipt,
  Search,
  ShoppingCart,
  Table2,
  Undo2,
  Wallet,
  type LucideIcon,
} from 'lucide-react'
import { useEffect, useState, useRef, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { getComboComposition } from '../api/combos'
import { listCategories } from '../api/categories'
import { applyItemDiscount, createOrder, listOrders, transferItems, type DiscountType, type ItemStatus, type Order, type OrderItem } from '../api/orders'
import { listModifierGroups } from '../api/productModifiers'
import { listProducts, type Product } from '../api/products'
import { getMyRestaurant } from '../api/restaurant'
import { listTables } from '../api/tables'
import {
  addTableToTab,
  applyTabDiscount,
  cancelTab,
  computeDiscountAmount,
  getTab,
  listTabs,
  mergeTabs,
  PAYMENT_METHOD_LABELS,
  registerPayments,
  roundCurrency,
  unmergeTabs,
  voidPayment,
  type Payment,
  type PaymentMethod,
  type Tab,
} from '../api/tabs'
import { useAuth } from '../auth/AuthContext'
import { Button } from '../components/Button'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { Modal } from '../components/Modal'
import { getCategoryIcon } from './publicMenu/categoryIcons'
import { formatTableLabel } from '../utils/tableLabel'
import { minutesSince } from '../utils/time'
import { modifiersTotal, sameModifiers, type SelectedModifier } from '../utils/modifiers'
import { computeComboUnitPrice, sameComboSelections, type SelectedComboSlot } from '../utils/combos'

const UNDO_MERGE_WINDOW_MS = 20000

const currencyFormatter = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

const ITEM_STATUS_LABELS: Record<ItemStatus, string> = {
  PENDING: 'Pendente',
  PREPARING: 'Em preparo',
  READY: 'Pronto',
  DELIVERED: 'Entregue',
  CANCELLED: 'Cancelado',
}

const ITEM_STATUS_STYLES: Record<ItemStatus, string> = {
  PENDING: 'bg-gray-100 text-gray-600 dark:bg-white/10 dark:text-stone-400',
  PREPARING: 'bg-gold-100 text-gold-700 dark:bg-gold-500/10 dark:text-gold-400',
  READY: 'bg-teal-100 text-teal-700 dark:bg-teal-500/10 dark:text-teal-400',
  DELIVERED: 'bg-sage-100 text-sage-700 dark:bg-sage-500/10 dark:text-sage-400',
  CANCELLED: 'bg-wine-100 text-wine-700 line-through dark:bg-wine-500/10 dark:text-wine-400',
}

const ITEM_STATUS_ICONS: Record<ItemStatus, LucideIcon> = {
  PENDING: Clock,
  PREPARING: Flame,
  READY: PackageCheck,
  DELIVERED: Check,
  CANCELLED: Ban,
}

interface DraftItem {
  productId: string
  productName: string
  unitPrice: number
  quantity: number
  observation: string
  selectedModifiers: SelectedModifier[]
  comboSelections?: SelectedComboSlot[]
}

function extractErrorMessage(err: unknown, fallback: string) {
  if (isAxiosError(err) && err.response?.data?.message) {
    return err.response.data.message as string
  }
  return fallback
}

export function TabDetailPage() {
  const { tabId } = useParams<{ tabId: string }>()
  const navigate = useNavigate()
  const { user } = useAuth()
  const canOrder = user?.role === 'OWNER' || user?.role === 'MANAGER' || user?.role === 'WAITER' || user?.role === 'CASHIER'
  const canDiscount = user?.role === 'OWNER' || user?.role === 'MANAGER'
  const queryClient = useQueryClient()

  const { data: tab, isLoading: isTabLoading } = useQuery({
    queryKey: ['tabs', tabId],
    queryFn: () => getTab(tabId!),
    enabled: !!tabId,
  })

  const { data: orders, isLoading: isOrdersLoading } = useQuery({
    queryKey: ['tabs', tabId, 'orders'],
    queryFn: () => listOrders(tabId!),
    enabled: !!tabId,
  })

  const { data: products } = useQuery({
    queryKey: ['products', 'active'],
    queryFn: () => listProducts({ active: true }),
  })

  const { data: categories } = useQuery({
    queryKey: ['categories'],
    queryFn: () => listCategories(),
  })

  const productsByCategory = (categories ?? [])
    .map((category) => ({
      category,
      products: (products ?? []).filter((product) => product.categoryId === category.id),
    }))
    .filter((group) => group.products.length > 0)

  const [productSearch, setProductSearch] = useState('')
  const normalizedSearch = productSearch.trim().toLowerCase()
  const filteredProductsByCategory = normalizedSearch
    ? productsByCategory
        .map((group) => ({
          ...group,
          products: group.products.filter((product) => product.name.toLowerCase().includes(normalizedSearch)),
        }))
        .filter((group) => group.products.length > 0)
    : productsByCategory

  const { data: restaurant } = useQuery({
    queryKey: ['restaurant'],
    queryFn: getMyRestaurant,
  })

  const [isAddingItem, setIsAddingItem] = useState(false)
  const [draftItems, setDraftItems] = useState<DraftItem[]>([])
  const [configuringProduct, setConfiguringProduct] = useState<Product | null>(null)
  const [modifierSelections, setModifierSelections] = useState<Record<string, string[]>>({})
  const [comboSlotSelections, setComboSlotSelections] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)
  const [isMerging, setIsMerging] = useState(false)
  const [isConfirmingCancel, setIsConfirmingCancel] = useState(false)
  const [tabPendingMerge, setTabPendingMerge] = useState<Tab | null>(null)
  const [pendingUndo, setPendingUndo] = useState<{ sourceTabId: string; label: string } | null>(null)
  const pendingUndoTimeoutRef = useRef<number | null>(null)
  const [discountingItem, setDiscountingItem] = useState<OrderItem | null>(null)
  const [discountKind, setDiscountKind] = useState<DiscountType>('FIXED')
  const [discountValue, setDiscountValue] = useState('')
  const [discountReason, setDiscountReason] = useState('')
  const [voidingPayment, setVoidingPayment] = useState<Payment | null>(null)
  const [voidReason, setVoidReason] = useState('')
  const [isCompletingPayment, setIsCompletingPayment] = useState(false)
  const [completePaymentMethod, setCompletePaymentMethod] = useState<PaymentMethod>('PIX')
  const [completePaymentAmount, setCompletePaymentAmount] = useState('')
  const [isSelectingForTransfer, setIsSelectingForTransfer] = useState(false)
  const [selectedItemIds, setSelectedItemIds] = useState<Set<string>>(new Set())
  const [isPickingTransferTarget, setIsPickingTransferTarget] = useState(false)
  const [pendingTransferUndo, setPendingTransferUndo] = useState<{ itemIds: string[]; targetTabId: string; label: string } | null>(null)
  const pendingTransferUndoTimeoutRef = useRef<number | null>(null)

  const { data: freeTables } = useQuery({
    queryKey: ['tables', 'FREE'],
    queryFn: () => listTables({ status: 'FREE', active: true }),
    select: (data) => [...data].sort((a, b) => a.number - b.number),
    enabled: isMerging,
  })

  const { data: otherOpenTabs } = useQuery({
    queryKey: ['tabs', 'OPEN'],
    queryFn: () => listTabs('OPEN'),
    enabled: isMerging || isPickingTransferTarget,
  })

  const { data: modifierGroups } = useQuery({
    queryKey: ['products', configuringProduct?.id, 'modifier-groups'],
    queryFn: () => listModifierGroups(configuringProduct!.id),
    enabled: !!configuringProduct && configuringProduct.type !== 'COMBO',
  })

  const { data: comboComposition } = useQuery({
    queryKey: ['products', configuringProduct?.id, 'combo'],
    queryFn: () => getComboComposition(configuringProduct!.id),
    enabled: !!configuringProduct && configuringProduct.type === 'COMBO',
  })

  useEffect(() => {
    setModifierSelections({})
    setComboSlotSelections({})
  }, [configuringProduct?.id])

  const createOrderMutation = useMutation({
    mutationFn: (items: DraftItem[]) =>
      createOrder(
        tabId!,
        items.map((item) => ({
          productId: item.productId,
          quantity: item.quantity,
          observation: item.observation || undefined,
          selectedOptionIds: item.selectedModifiers.map((m) => m.optionId),
          slotSelections: item.comboSelections?.length
            ? item.comboSelections.map((s) => ({ slotId: s.slotId, selectedProductId: s.productId }))
            : undefined,
        })),
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tabs', tabId, 'orders'] })
      setDraftItems([])
    },
    onError: () => setError('Não foi possível enviar o pedido para a cozinha.'),
  })

  const cancelMutation = useMutation({
    mutationFn: () => cancelTab(tabId!),
    onSuccess: () => navigate('/tables'),
    onError: () => setError('Não foi possível cancelar a comanda.'),
  })

  const voidPaymentMutation = useMutation({
    mutationFn: ({ paymentId, reason }: { paymentId: string; reason: string }) => voidPayment(tabId!, paymentId, reason),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tabs', tabId] })
      setVoidingPayment(null)
      setVoidReason('')
    },
    onError: (err) => setError(extractErrorMessage(err, 'Não foi possível anular esse pagamento.')),
  })

  function openVoidPaymentModal(payment: Payment) {
    setError(null)
    setVoidReason('')
    setVoidingPayment(payment)
  }

  function handleVoidPayment(event: FormEvent) {
    event.preventDefault()
    if (!voidingPayment) return
    const reason = voidReason.trim()
    if (!reason) return
    voidPaymentMutation.mutate({ paymentId: voidingPayment.id, reason })
  }

  const completePaymentMutation = useMutation({
    mutationFn: ({ method, amount }: { method: PaymentMethod; amount: number }) =>
      registerPayments(tabId!, [{ paymentMethod: method, amount }]),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tabs', tabId] })
      setIsCompletingPayment(false)
    },
    onError: (err) => setError(extractErrorMessage(err, 'Não foi possível registrar o pagamento. Confira se o valor não excede o saldo em aberto.')),
  })

  function openCompletePaymentModal() {
    if (!tab) return
    setError(null)
    setCompletePaymentMethod('PIX')
    setCompletePaymentAmount(tab.remainingBalance != null ? String(tab.remainingBalance) : '')
    setIsCompletingPayment(true)
  }

  function handleCompletePayment(event: FormEvent) {
    event.preventDefault()
    const amount = Number(completePaymentAmount)
    if (!amount || amount <= 0) return
    completePaymentMutation.mutate({ method: completePaymentMethod, amount })
  }

  function invalidateTabQueries() {
    queryClient.invalidateQueries({ queryKey: ['tabs', tabId] })
    queryClient.invalidateQueries({ queryKey: ['tabs', tabId, 'orders'] })
    queryClient.invalidateQueries({ queryKey: ['tables'] })
    queryClient.invalidateQueries({ queryKey: ['tabs', 'OPEN'] })
  }

  const addTableMutation = useMutation({
    mutationFn: (tableId: string) => addTableToTab(tabId!, tableId),
    onSuccess: () => {
      invalidateTabQueries()
      setIsMerging(false)
    },
    onError: () => setError('Não foi possível adicionar essa mesa à comanda.'),
  })

  const mergeMutation = useMutation({
    mutationFn: (sourceTabId: string) => mergeTabs(tabId!, sourceTabId),
    onSuccess: (_data, sourceTabId) => {
      invalidateTabQueries()
      setIsMerging(false)

      const sourceTab = otherOpenTabs?.find((t) => t.id === sourceTabId)
      const label = sourceTab ? formatTableLabel(sourceTab.tables.map((t) => t.number)) : 'a comanda'
      if (pendingUndoTimeoutRef.current) window.clearTimeout(pendingUndoTimeoutRef.current)
      setPendingUndo({ sourceTabId, label })
      pendingUndoTimeoutRef.current = window.setTimeout(() => setPendingUndo(null), UNDO_MERGE_WINDOW_MS)
    },
    onError: () => setError('Não foi possível mesclar essa comanda.'),
  })

  const undoMergeMutation = useMutation({
    mutationFn: (sourceTabId: string) => unmergeTabs(tabId!, sourceTabId),
    onSuccess: () => {
      invalidateTabQueries()
      if (pendingUndoTimeoutRef.current) window.clearTimeout(pendingUndoTimeoutRef.current)
      setPendingUndo(null)
    },
    onError: () => setError('Não foi possível desfazer a mesclagem.'),
  })

  const transferItemsMutation = useMutation({
    mutationFn: ({ itemIds, targetTabId }: { itemIds: string[]; targetTabId: string }) => transferItems(itemIds, targetTabId),
    onSuccess: (_data, variables) => {
      invalidateTabQueries()
      queryClient.invalidateQueries({ queryKey: ['tabs', variables.targetTabId, 'orders'] })
      setIsPickingTransferTarget(false)
      setIsSelectingForTransfer(false)

      const targetTab = otherOpenTabs?.find((t) => t.id === variables.targetTabId)
      const label = targetTab ? formatTableLabel(targetTab.tables.map((t) => t.number)) : 'a comanda'
      if (pendingTransferUndoTimeoutRef.current) window.clearTimeout(pendingTransferUndoTimeoutRef.current)
      setPendingTransferUndo({ itemIds: variables.itemIds, targetTabId: variables.targetTabId, label })
      pendingTransferUndoTimeoutRef.current = window.setTimeout(() => setPendingTransferUndo(null), UNDO_MERGE_WINDOW_MS)
      setSelectedItemIds(new Set())
    },
    onError: () => setError('Não foi possível transferir os itens selecionados.'),
  })

  const undoTransferMutation = useMutation({
    mutationFn: (undo: { itemIds: string[]; targetTabId: string }) => transferItems(undo.itemIds, tabId!),
    onSuccess: (_data, undo) => {
      invalidateTabQueries()
      queryClient.invalidateQueries({ queryKey: ['tabs', undo.targetTabId, 'orders'] })
      if (pendingTransferUndoTimeoutRef.current) window.clearTimeout(pendingTransferUndoTimeoutRef.current)
      setPendingTransferUndo(null)
    },
    onError: () => setError('Não foi possível desfazer a transferência.'),
  })

  function toggleTransferSelectionMode() {
    setError(null)
    setIsSelectingForTransfer((prev) => !prev)
    setSelectedItemIds(new Set())
  }

  function toggleItemSelected(itemId: string) {
    setSelectedItemIds((prev) => {
      const next = new Set(prev)
      if (next.has(itemId)) next.delete(itemId)
      else next.add(itemId)
      return next
    })
  }

  function openTransferTargetPicker() {
    if (selectedItemIds.size === 0) return
    setError(null)
    setIsPickingTransferTarget(true)
  }

  function handleTransferToTab(targetTab: Tab) {
    transferItemsMutation.mutate({ itemIds: Array.from(selectedItemIds), targetTabId: targetTab.id })
  }

  const tabDiscountMutation = useMutation({
    mutationFn: () => applyTabDiscount(tabId!, { discountType: null }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tabs', tabId] })
    },
    onError: () => setError('Não foi possível remover o desconto desta comanda.'),
  })

  const itemDiscountMutation = useMutation({
    mutationFn: ({ itemId, discountType, value, reason }: { itemId: string; discountType: DiscountType | null; value?: number; reason?: string }) =>
      applyItemDiscount(itemId, { discountType, discountValue: value, reason }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tabs', tabId, 'orders'] })
      setDiscountingItem(null)
    },
    onError: () => setError('Não foi possível aplicar o desconto neste item.'),
  })

  function openDiscountModal(item: OrderItem) {
    setError(null)
    setDiscountingItem(item)
    setDiscountKind(item.discountType ?? 'FIXED')
    setDiscountValue(item.discountValue ? String(item.discountValue) : '')
    setDiscountReason(item.discountReason ?? '')
  }

  function handleApplyItemDiscount(event: FormEvent) {
    event.preventDefault()
    if (!discountingItem) return
    const value = Number(discountValue)
    if (!value || value <= 0) return
    itemDiscountMutation.mutate({
      itemId: discountingItem.id,
      discountType: discountKind,
      value,
      reason: discountReason.trim() || undefined,
    })
  }

  function handleRemoveItemDiscount() {
    if (!discountingItem) return
    itemDiscountMutation.mutate({ itemId: discountingItem.id, discountType: null })
  }

  function openAddItemForm() {
    setProductSearch('')
    setConfiguringProduct(null)
    setModifierSelections({})
    setError(null)
    setIsAddingItem(true)
  }

  function toggleModifierOption(groupId: string, optionId: string, isSingle: boolean) {
    setModifierSelections((prev) => {
      const current = prev[groupId] ?? []
      if (isSingle) {
        return { ...prev, [groupId]: current.includes(optionId) ? [] : [optionId] }
      }
      const next = current.includes(optionId) ? current.filter((id) => id !== optionId) : [...current, optionId]
      return { ...prev, [groupId]: next }
    })
  }

  const selectedModifiers: SelectedModifier[] = (modifierGroups ?? []).flatMap((group) =>
    (modifierSelections[group.id] ?? []).flatMap((optionId) => {
      const option = group.options.find((o) => o.id === optionId)
      if (!option) return []
      return [
        {
          groupId: group.id,
          groupName: group.name,
          optionId: option.id,
          optionName: option.name,
          priceDelta: option.priceDelta,
        },
      ]
    }),
  )

  const areModifiersValid = (modifierGroups ?? []).every(
    (group) => !group.required || (modifierSelections[group.id]?.length ?? 0) > 0,
  )

  function isPlainDraftEntry(item: DraftItem) {
    return item.observation === '' && item.selectedModifiers.length === 0
  }

  function addPlainProduct(product: Product) {
    setDraftItems((prev) => {
      const existingIndex = prev.findIndex((item) => item.productId === product.id && isPlainDraftEntry(item))
      if (existingIndex !== -1) {
        return prev.map((item, index) =>
          index === existingIndex ? { ...item, quantity: item.quantity + 1 } : item,
        )
      }
      return [
        ...prev,
        {
          productId: product.id,
          productName: product.name,
          unitPrice: product.price,
          quantity: 1,
          observation: '',
          selectedModifiers: [],
        },
      ]
    })
  }

  function adjustPlainProductQuantity(productId: string, delta: number) {
    setDraftItems((prev) =>
      prev
        .map((item) => (item.productId === productId && isPlainDraftEntry(item) ? { ...item, quantity: item.quantity + delta } : item))
        .filter((item) => item.quantity > 0),
    )
  }

  function handleProductTap(product: Product) {
    setError(null)
    if (product.type === 'COMBO' || product.hasModifierGroups) {
      setConfiguringProduct(product)
    } else {
      addPlainProduct(product)
    }
  }

  const isComboValid = (comboComposition?.slots ?? []).every((slot) => !slot.required || !!comboSlotSelections[slot.id])

  function handleConfirmCombo() {
    if (!configuringProduct || !comboComposition || !isComboValid) return
    const product = configuringProduct
    const selections: SelectedComboSlot[] = comboComposition.slots
      .filter((slot) => !!comboSlotSelections[slot.id])
      .map((slot) => {
        const option = slot.options.find((o) => o.productId === comboSlotSelections[slot.id])!
        return {
          slotId: slot.id,
          slotName: slot.name,
          productId: option.productId,
          productName: option.productName,
          unitPrice: option.unitPrice,
          quantity: option.quantity,
        }
      })
    const unitPrice = computeComboUnitPrice(comboComposition, comboSlotSelections)
    setDraftItems((prev) => {
      const existingIndex = prev.findIndex(
        (item) =>
          item.productId === product.id &&
          item.observation === '' &&
          sameComboSelections(item.comboSelections ?? [], selections),
      )
      if (existingIndex !== -1) {
        return prev.map((item, index) => (index === existingIndex ? { ...item, quantity: item.quantity + 1 } : item))
      }
      return [
        ...prev,
        {
          productId: product.id,
          productName: product.name,
          unitPrice,
          quantity: 1,
          observation: '',
          selectedModifiers: [],
          comboSelections: selections,
        },
      ]
    })
    setConfiguringProduct(null)
    setComboSlotSelections({})
  }

  function handleConfirmModifiers() {
    if (!configuringProduct || !areModifiersValid) return
    const product = configuringProduct
    setDraftItems((prev) => {
      const existingIndex = prev.findIndex(
        (item) => item.productId === product.id && item.observation === '' && sameModifiers(item.selectedModifiers, selectedModifiers),
      )
      if (existingIndex !== -1) {
        return prev.map((item, index) =>
          index === existingIndex ? { ...item, quantity: item.quantity + 1 } : item,
        )
      }
      return [
        ...prev,
        {
          productId: product.id,
          productName: product.name,
          unitPrice: product.price,
          quantity: 1,
          observation: '',
          selectedModifiers,
        },
      ]
    })
    setConfiguringProduct(null)
    setModifierSelections({})
  }

  function updateDraftQuantity(index: number, delta: number) {
    setDraftItems((prev) =>
      prev.map((item, i) => (i === index ? { ...item, quantity: item.quantity + delta } : item)).filter((item) => item.quantity > 0),
    )
  }

  function updateDraftObservation(index: number, observation: string) {
    setDraftItems((prev) => prev.map((item, i) => (i === index ? { ...item, observation } : item)))
  }

  function removeDraftItem(index: number) {
    setDraftItems((prev) => prev.filter((_, i) => i !== index))
  }

  const plainDraftQuantities = new Map<string, number>()
  draftItems.forEach((item) => {
    if (isPlainDraftEntry(item)) {
      plainDraftQuantities.set(item.productId, (plainDraftQuantities.get(item.productId) ?? 0) + item.quantity)
    }
  })

  function handleSendToKitchen() {
    setError(null)
    const printWindow = restaurant?.autoPrintKitchenTickets ? window.open('about:blank', '_blank') : null
    createOrderMutation.mutate(draftItems, {
      onSuccess: (createdOrder) => {
        if (printWindow) {
          printWindow.location.href = `/orders/${createdOrder.id}/print?auto=1`
        }
      },
    })
  }

  function handleCancelTab() {
    setIsConfirmingCancel(true)
  }

  function confirmCancelTab() {
    setIsConfirmingCancel(false)
    setError(null)
    cancelMutation.mutate()
  }

  function openMergeModal() {
    setError(null)
    setIsMerging(true)
  }

  function handleMergeTab(sourceTab: Tab) {
    setTabPendingMerge(sourceTab)
  }

  function confirmMergeTab() {
    if (!tabPendingMerge) return
    setError(null)
    mergeMutation.mutate(tabPendingMerge.id)
    setTabPendingMerge(null)
  }

  if (isTabLoading || isOrdersLoading || !tab) {
    return <p className="text-sm text-gray-500 dark:text-stone-400">Carregando...</p>
  }

  const allItems = orders?.flatMap((order) => order.items) ?? []
  const grandTotal = allItems
    .filter((item) => item.status !== 'CANCELLED')
    .reduce((sum, item) => sum + item.netSubtotal, 0)
  const tabDiscountAmount = computeDiscountAmount(tab.discountType, tab.discountValue, grandTotal)
  const grandTotalAfterDiscount = roundCurrency(grandTotal - tabDiscountAmount)
  const isOpen = tab.status === 'OPEN'
  const activePayments = tab.payments.filter((payment) => payment.status === 'ACTIVE')
  const hasOutstandingBalance = tab.status === 'CLOSED' && (tab.remainingBalance ?? 0) > 0

  return (
    <div>
      <button
        type="button"
        onClick={() => navigate('/tables')}
        className="mb-4 flex items-center gap-1 text-sm text-gray-500 dark:text-stone-400 hover:text-gray-700 dark:hover:text-stone-200 hover:underline"
      >
        <ArrowLeft className="h-4 w-4" />
        Voltar para Mesas
      </button>

      <div className="mb-6 flex flex-col gap-4 rounded-xl border border-gray-200 bg-white p-5 shadow-sm sm:flex-row sm:items-center sm:justify-between dark:border-white/10 dark:bg-stone-900">
        <div className="flex items-center gap-3">
          <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-brand-50 text-brand-600 dark:bg-brand-500/10 dark:text-brand-400">
            <Receipt className="h-5 w-5" />
          </div>
          <div>
            <h1 className="text-lg font-semibold text-gray-800 dark:text-white">
              {formatTableLabel(tab.tables.map((t) => t.number))}
            </h1>
            <div className="mt-1 flex flex-wrap items-center gap-2 text-xs">
              <span
                className={`flex items-center gap-1 rounded-full px-2 py-0.5 font-medium ${
                  isOpen
                    ? 'bg-green-100 text-green-700 dark:bg-green-500/10 dark:text-green-400'
                    : 'bg-gray-100 text-gray-600 dark:bg-white/10 dark:text-stone-400'
                }`}
              >
                {isOpen ? <Clock className="h-3 w-3" /> : <Lock className="h-3 w-3" />}
                {isOpen ? 'Aberta' : 'Fechada'}
              </span>
              {isOpen && (
                <span className="flex items-center gap-1 text-gray-400 dark:text-stone-500">
                  há {minutesSince(tab.openedAt)} min
                </span>
              )}
            </div>
          </div>
        </div>

        {isOpen && canOrder && (
          <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap sm:items-center sm:justify-end">
            <button
              type="button"
              onClick={openAddItemForm}
              className="flex items-center justify-center gap-2 rounded-xl bg-brand-600 px-4 py-3.5 text-base font-semibold text-white shadow-sm transition-all hover:bg-brand-700 hover:shadow-md active:scale-[0.98] sm:order-last sm:py-2.5 sm:text-sm"
            >
              <Plus className="h-5 w-5 sm:h-4 sm:w-4" />
              Adicionar item
            </button>

            <div className="flex flex-wrap gap-2">
              {allItems.some((item) => item.status !== 'CANCELLED') && (
                <button
                  type="button"
                  onClick={toggleTransferSelectionMode}
                  className={`flex flex-1 items-center justify-center gap-2 rounded-xl border px-4 py-3 text-base font-semibold transition-all active:scale-[0.98] sm:flex-none sm:py-2.5 sm:text-sm ${
                    isSelectingForTransfer
                      ? 'border-brand-600 bg-brand-50 text-brand-700 dark:border-brand-400 dark:bg-brand-500/10 dark:text-brand-400'
                      : 'border-gray-200 bg-white text-gray-700 hover:bg-gray-50 dark:border-white/10 dark:bg-white/5 dark:text-stone-300 dark:hover:bg-white/10'
                  }`}
                >
                  <ArrowRightLeft className="h-4 w-4" />
                  {isSelectingForTransfer ? 'Cancelar seleção' : 'Transferir itens'}
                </button>
              )}
              <button
                type="button"
                onClick={openMergeModal}
                className="flex flex-1 items-center justify-center gap-2 rounded-xl border border-gray-200 bg-white px-4 py-3 text-base font-semibold text-gray-700 transition-all hover:bg-gray-50 active:scale-[0.98] sm:flex-none sm:py-2.5 sm:text-sm dark:border-white/10 dark:bg-white/5 dark:text-stone-300 dark:hover:bg-white/10"
              >
                <GitMerge className="h-4 w-4" />
                Mesclar comanda
              </button>
              {orders?.length === 0 && (
                <button
                  type="button"
                  onClick={handleCancelTab}
                  disabled={cancelMutation.isPending}
                  className="flex items-center justify-center gap-2 rounded-xl px-3 py-3 text-base font-medium text-red-600 transition-all hover:bg-red-50 active:scale-[0.98] disabled:opacity-50 sm:py-2.5 sm:text-sm dark:text-red-400 dark:hover:bg-red-500/10"
                >
                  <Ban className="h-4 w-4" />
                  Cancelar comanda
                </button>
              )}
            </div>
          </div>
        )}

      </div>

      {pendingUndo && (
        <div className="mb-4 flex items-center justify-between rounded-lg border border-blue-200 bg-blue-50 px-4 py-3 text-sm text-blue-800 dark:border-blue-500/30 dark:bg-blue-500/10 dark:text-blue-400">
          <span className="flex items-center gap-2">
            <Undo2 className="h-4 w-4" />
            Mesclado com {pendingUndo.label}.
          </span>
          <button
            type="button"
            onClick={() => undoMergeMutation.mutate(pendingUndo.sourceTabId)}
            disabled={undoMergeMutation.isPending}
            className="font-medium text-blue-700 underline hover:text-blue-900 disabled:opacity-50 dark:text-blue-400 dark:hover:text-blue-300"
          >
            Desfazer
          </button>
        </div>
      )}

      {pendingTransferUndo && (
        <div className="mb-4 flex items-center justify-between rounded-lg border border-blue-200 bg-blue-50 px-4 py-3 text-sm text-blue-800 dark:border-blue-500/30 dark:bg-blue-500/10 dark:text-blue-400">
          <span className="flex items-center gap-2">
            <Undo2 className="h-4 w-4" />
            {pendingTransferUndo.itemIds.length} {pendingTransferUndo.itemIds.length === 1 ? 'item transferido' : 'itens transferidos'} pra {pendingTransferUndo.label}.
          </span>
          <button
            type="button"
            onClick={() => undoTransferMutation.mutate(pendingTransferUndo)}
            disabled={undoTransferMutation.isPending}
            className="font-medium text-blue-700 underline hover:text-blue-900 disabled:opacity-50 dark:text-blue-400 dark:hover:text-blue-300"
          >
            Desfazer
          </button>
        </div>
      )}

      {isSelectingForTransfer && (
        <div className="mb-4 flex items-center justify-between rounded-lg border border-brand-200 bg-brand-50 px-4 py-3 text-sm text-brand-800 dark:border-brand-500/30 dark:bg-brand-500/10 dark:text-brand-400">
          <span>
            {selectedItemIds.size === 0
              ? 'Selecione os itens que você quer transferir.'
              : `${selectedItemIds.size} ${selectedItemIds.size === 1 ? 'item selecionado' : 'itens selecionados'}.`}
          </span>
          <button
            type="button"
            onClick={openTransferTargetPicker}
            disabled={selectedItemIds.size === 0}
            className="rounded-md bg-brand-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-brand-700 disabled:opacity-50"
          >
            Transferir selecionados
          </button>
        </div>
      )}

      {error && draftItems.length === 0 && <p className="mb-4 text-sm text-wine-600 dark:text-wine-400">{error}</p>}

      {draftItems.length > 0 && (
        <div className="mb-6 rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-white/10 dark:bg-stone-900">
          <h2 className="mb-3 flex items-center gap-2 text-sm font-semibold text-gray-800 dark:text-white">
            <ShoppingCart className="h-4 w-4 text-brand-600 dark:text-brand-400" />
            Novo pedido (ainda não enviado)
          </h2>
          <ul className="mb-3 divide-y divide-gray-100 dark:divide-white/10">
            {draftItems.map((item, index) => (
              <li key={index} className="flex flex-col gap-2 py-2 text-sm">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <span className="font-medium text-gray-800 dark:text-white">{item.productName}</span>
                  <span className="text-gray-600 dark:text-stone-400">
                    {currencyFormatter.format((item.unitPrice + modifiersTotal(item.selectedModifiers)) * item.quantity)}
                  </span>
                </div>
                {item.selectedModifiers.length > 0 && (
                  <div className="flex flex-wrap gap-1">
                    {item.selectedModifiers.map((modifier) => (
                      <span
                        key={modifier.optionId}
                        className="rounded-full bg-gray-100 px-2 py-0.5 text-xs text-gray-600 dark:bg-white/10 dark:text-stone-400"
                      >
                        {modifier.optionName}
                      </span>
                    ))}
                  </div>
                )}
                {item.comboSelections && item.comboSelections.length > 0 && (
                  <div className="flex flex-wrap gap-1">
                    {item.comboSelections.map((selection) => (
                      <span
                        key={selection.slotId}
                        className="rounded-full bg-purple-100 px-2 py-0.5 text-xs text-purple-700 dark:bg-purple-500/10 dark:text-purple-400"
                      >
                        {selection.slotName}: {selection.productName}
                      </span>
                    ))}
                  </div>
                )}
                <div className="flex items-center gap-3">
                  <div className="flex items-center gap-1 rounded-full border border-gray-200 px-1 py-0.5 dark:border-white/10">
                    <button
                      type="button"
                      onClick={() => updateDraftQuantity(index, -1)}
                      aria-label="Diminuir quantidade"
                      className="flex h-6 w-6 items-center justify-center rounded-full text-gray-600 hover:bg-gray-100 dark:text-stone-300 dark:hover:bg-white/10"
                    >
                      <Minus className="h-3.5 w-3.5" />
                    </button>
                    <span className="w-5 text-center text-xs font-medium text-gray-800 dark:text-white">{item.quantity}</span>
                    <button
                      type="button"
                      onClick={() => updateDraftQuantity(index, 1)}
                      aria-label="Aumentar quantidade"
                      className="flex h-6 w-6 items-center justify-center rounded-full text-gray-600 hover:bg-gray-100 dark:text-stone-300 dark:hover:bg-white/10"
                    >
                      <Plus className="h-3.5 w-3.5" />
                    </button>
                  </div>
                  <input
                    type="text"
                    placeholder="Observação (opcional)"
                    maxLength={255}
                    value={item.observation}
                    onChange={(e) => updateDraftObservation(index, e.target.value)}
                    className="flex-1 rounded-md border border-gray-200 px-2 py-1 text-xs focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white"
                  />
                  <button
                    type="button"
                    onClick={() => removeDraftItem(index)}
                    className="shrink-0 text-red-600 hover:underline dark:text-red-400"
                  >
                    Remover
                  </button>
                </div>
              </li>
            ))}
          </ul>

          {error && <p className="mb-3 text-sm text-wine-600 dark:text-wine-400">{error}</p>}

          <Button type="button" onClick={handleSendToKitchen} disabled={createOrderMutation.isPending} className="w-full">
            Enviar para Cozinha
          </Button>
        </div>
      )}

      <div className="space-y-3">
        {orders && orders.length === 0 && draftItems.length === 0 && (
          <div className="flex flex-col items-center gap-2 rounded-xl border border-dashed border-gray-300 bg-gray-50 py-12 text-center dark:border-white/10 dark:bg-white/5">
            <ClipboardList className="h-8 w-8 text-gray-300 dark:text-stone-600" />
            <p className="text-sm text-gray-500 dark:text-stone-400">Nenhum pedido enviado ainda.</p>
          </div>
        )}

        {orders?.map((order: Order) => (
          <div key={order.id} className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-white/10 dark:bg-stone-900">
            <div className="mb-3 flex items-center justify-between border-b border-gray-100 pb-3 text-sm text-gray-500 dark:border-white/10 dark:text-stone-400">
              <span className="flex items-center gap-1.5">
                <Clock className="h-3.5 w-3.5" />
                Pedido às {new Date(order.createdAt).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}
              </span>
              <div className="flex items-center gap-3">
                {order.printedAt && <span className="text-xs text-gray-400 dark:text-stone-500">Impresso</span>}
                <Link
                  to={`/orders/${order.id}/print`}
                  target="_blank"
                  className="flex items-center gap-1 text-brand-600 hover:underline dark:text-brand-400"
                >
                  <Printer className="h-3.5 w-3.5" />
                  Imprimir
                </Link>
                <span className="font-medium text-gray-700 dark:text-stone-300">{currencyFormatter.format(order.total)}</span>
              </div>
            </div>
            <ul className="divide-y divide-gray-100 dark:divide-white/10">
              {order.items.map((item) => (
                <OrderItemRow
                  key={item.id}
                  item={item}
                  depth={0}
                  isSelectingForTransfer={isSelectingForTransfer}
                  selectedItemIds={selectedItemIds}
                  toggleItemSelected={toggleItemSelected}
                  isOpen={isOpen}
                  canDiscount={canDiscount}
                  openDiscountModal={openDiscountModal}
                />
              ))}
            </ul>
          </div>
        ))}
      </div>

      {allItems.length > 0 && (
        <div className="mt-4 rounded-xl border border-gray-200 bg-white px-5 py-4 shadow-sm dark:border-white/10 dark:bg-stone-900">
          {tab.discountType && (
            <>
              <div className="flex items-center justify-between text-sm text-gray-500 dark:text-stone-400">
                <span>Subtotal</span>
                <span>{currencyFormatter.format(grandTotal)}</span>
              </div>
              <div className="mt-1 flex items-center justify-between text-sm text-orange-600 dark:text-orange-400">
                <span className="flex items-center gap-2">
                  {tab.discountReason || 'Desconto'}
                  {isOpen && canDiscount && (
                    <button
                      type="button"
                      onClick={() => tabDiscountMutation.mutate()}
                      disabled={tabDiscountMutation.isPending}
                      className="text-xs font-medium text-red-600 underline hover:text-red-700 disabled:opacity-50 dark:text-red-400 dark:hover:text-red-300"
                    >
                      Remover
                    </button>
                  )}
                </span>
                <span>-{currencyFormatter.format(tabDiscountAmount)}</span>
              </div>
              <div className="my-2 border-t border-gray-100 dark:border-white/10" />
            </>
          )}
          <div className="flex items-center justify-between">
            <span className="flex items-center gap-2 text-sm font-medium text-gray-500 dark:text-stone-400">
              <Wallet className="h-4 w-4" />
              Total da comanda
            </span>
            <span className="text-xl font-semibold text-brand-700 dark:text-brand-400">
              {currencyFormatter.format(tab.billTotal ?? grandTotalAfterDiscount)}
            </span>
          </div>
          {tab.serviceChargePercentage != null && (
            <div className="mt-1 flex items-center justify-between text-sm text-gray-500 dark:text-stone-400">
              <span>Taxa de serviço ({tab.serviceChargePercentage}%)</span>
              <span>{currencyFormatter.format(tab.serviceChargeAmount ?? 0)}</span>
            </div>
          )}
        </div>
      )}

      {(activePayments.length > 0 || tab.payments.length > 0 || hasOutstandingBalance) && (
        <div className="mt-4 rounded-xl border border-gray-200 bg-white px-5 py-4 shadow-sm dark:border-white/10 dark:bg-stone-900">
          <h2 className="mb-3 flex items-center gap-2 text-sm font-semibold text-gray-800 dark:text-white">
            <Wallet className="h-4 w-4 text-brand-600 dark:text-brand-400" />
            Pagamentos
          </h2>
          <ul className="mb-3 divide-y divide-gray-100 dark:divide-white/10">
            {tab.payments.map((payment) => (
              <li key={payment.id} className="flex items-start justify-between gap-2 py-2 text-sm">
                <div>
                  <span
                    className={
                      payment.status === 'VOIDED'
                        ? 'text-gray-400 line-through dark:text-stone-600'
                        : 'text-gray-800 dark:text-white'
                    }
                  >
                    {PAYMENT_METHOD_LABELS[payment.paymentMethod]} — {currencyFormatter.format(payment.amount)}
                  </span>
                  <div className="mt-0.5 text-xs text-gray-400 dark:text-stone-500">
                    {new Date(payment.paidAt).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}
                    {payment.createdByName && ` · ${payment.createdByName}`}
                  </div>
                  {payment.status === 'VOIDED' && (
                    <div className="mt-0.5 text-xs text-red-500 dark:text-red-400">
                      Anulado{payment.voidedByName && ` por ${payment.voidedByName}`}
                      {payment.voidReason && `: ${payment.voidReason}`}
                    </div>
                  )}
                </div>
                {payment.status === 'ACTIVE' && canDiscount && (
                  <button
                    type="button"
                    onClick={() => openVoidPaymentModal(payment)}
                    className="shrink-0 text-xs text-red-600 hover:underline dark:text-red-400"
                  >
                    Anular
                  </button>
                )}
              </li>
            ))}
          </ul>
          {hasOutstandingBalance && (
            <div className="flex items-center justify-between text-sm font-semibold text-amber-700 dark:text-amber-400">
              <span>Saldo em aberto</span>
              <span>{currencyFormatter.format(tab.remainingBalance ?? 0)}</span>
            </div>
          )}
          {hasOutstandingBalance && canDiscount && (
            <button
              type="button"
              onClick={openCompletePaymentModal}
              className="mt-3 flex w-full items-center justify-center gap-1.5 rounded-md border border-brand-600 px-3 py-2 text-sm font-medium text-brand-600 hover:bg-brand-50 dark:border-brand-400 dark:text-brand-400 dark:hover:bg-brand-500/10"
            >
              <Pencil className="h-4 w-4" />
              Completar pagamento
            </button>
          )}
        </div>
      )}

      {isAddingItem && (
        <Modal title="Adicionar itens" onClose={() => setIsAddingItem(false)}>
          <p className="mb-3 text-xs text-gray-500 dark:text-stone-400">
            Toque nos produtos que quiser adicionar. Dá pra escolher vários de uma vez.
          </p>
          <div className="relative mb-3">
            <Search className="pointer-events-none absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2 text-gray-400 dark:text-stone-500" />
            <input
              type="text"
              placeholder="Buscar produto..."
              value={productSearch}
              onChange={(e) => setProductSearch(e.target.value)}
              className="w-full rounded-md border border-gray-300 py-2 pr-3 pl-9 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
            />
          </div>
          <div className="mb-4 max-h-96 space-y-4 overflow-y-auto rounded-md border border-gray-200 p-2 dark:border-white/10">
            {filteredProductsByCategory.length === 0 && (
              <p className="py-4 text-center text-sm text-gray-400 dark:text-stone-500">Nenhum produto encontrado.</p>
            )}
            {filteredProductsByCategory.map(({ category, products: categoryProducts }) => {
              const CategoryIcon = getCategoryIcon(category.name)
              return (
                <div key={category.id}>
                  <div className="mb-1 flex items-center gap-1.5 px-1 text-xs font-semibold tracking-wide text-gray-400 uppercase dark:text-stone-500">
                    <CategoryIcon className="h-3.5 w-3.5" />
                    {category.name}
                  </div>
                  <div className="space-y-1">
                    {categoryProducts.map((product) => {
                      const quantity = (product.hasModifierGroups || product.type === 'COMBO') ? 0 : (plainDraftQuantities.get(product.id) ?? 0)
                      return (
                        <div
                          key={product.id}
                          className="flex items-center justify-between gap-2 rounded-md border border-gray-200 px-3 py-2 text-sm dark:border-white/10"
                        >
                          <div className="min-w-0 flex-1">
                            <div className="truncate text-gray-800 dark:text-white">{product.name}</div>
                            <div className="flex items-center gap-1 text-xs text-gray-500 dark:text-stone-400">
                              {product.type === 'COMBO' ? (
                                <>
                                  <Layers className="h-3 w-3" />
                                  Combo · toque para montar
                                </>
                              ) : (
                                currencyFormatter.format(product.price)
                              )}
                            </div>
                          </div>
                          {quantity === 0 ? (
                            <motion.button
                              type="button"
                              whileTap={{ scale: 0.95 }}
                              onClick={() => handleProductTap(product)}
                              className="shrink-0 rounded-full border border-brand-600 px-3 py-1 text-xs font-medium text-brand-600 hover:bg-gray-50 dark:border-brand-400 dark:text-brand-400 dark:hover:bg-white/5"
                            >
                              Adicionar
                            </motion.button>
                          ) : (
                            <div className="flex shrink-0 items-center gap-3 rounded-full border border-brand-600 px-2 py-1 dark:border-brand-400">
                              <button
                                type="button"
                                onClick={() => adjustPlainProductQuantity(product.id, -1)}
                                aria-label="Diminuir quantidade"
                                className="flex h-5 w-5 items-center justify-center text-brand-600 dark:text-brand-400"
                              >
                                <Minus className="h-3.5 w-3.5" />
                              </button>
                              <span className="min-w-[1ch] text-center text-xs font-semibold text-gray-800 dark:text-white">
                                {quantity}
                              </span>
                              <button
                                type="button"
                                onClick={() => adjustPlainProductQuantity(product.id, 1)}
                                aria-label="Aumentar quantidade"
                                className="flex h-5 w-5 items-center justify-center text-brand-600 dark:text-brand-400"
                              >
                                <Plus className="h-3.5 w-3.5" />
                              </button>
                            </div>
                          )}
                        </div>
                      )
                    })}
                  </div>
                </div>
              )
            })}
          </div>

          {error && <p className="mb-3 text-sm text-wine-600 dark:text-wine-400">{error}</p>}

          <Button type="button" onClick={() => setIsAddingItem(false)} disabled={draftItems.length === 0} className="w-full">
            {draftItems.length === 0 ? 'Selecione ao menos um item' : 'Concluir seleção'}
          </Button>
        </Modal>
      )}

      {configuringProduct && configuringProduct.type === 'COMBO' && (
        <Modal title={configuringProduct.name} onClose={() => setConfiguringProduct(null)}>
          {comboComposition && (
            <div className="space-y-4">
              {comboComposition.fixedItems.length > 0 && (
                <div>
                  <span className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300">Itens inclusos</span>
                  <p className="text-sm text-gray-600 dark:text-stone-400">
                    {comboComposition.fixedItems.map((item) => `${item.quantity}x ${item.productName}`).join(', ')}
                  </p>
                </div>
              )}
              {comboComposition.slots.map((slot) => (
                <div key={slot.id}>
                  <div className="mb-1 flex items-center gap-2">
                    <span className="text-sm font-medium text-gray-700 dark:text-stone-300">{slot.name}</span>
                    {slot.required && (
                      <span className="rounded-full bg-amber-100 px-2 py-0.5 text-xs text-amber-700 dark:bg-amber-500/10 dark:text-amber-400">
                        Obrigatório
                      </span>
                    )}
                  </div>
                  <div className="space-y-1.5">
                    {slot.options.map((option) => {
                      const isSelected = comboSlotSelections[slot.id] === option.productId
                      return (
                        <button
                          key={option.id}
                          type="button"
                          onClick={() => setComboSlotSelections((prev) => ({ ...prev, [slot.id]: option.productId }))}
                          className={`flex w-full items-center justify-between rounded-md border px-3 py-2 text-left text-sm ${
                            isSelected
                              ? 'border-brand-600 bg-brand-50 dark:border-brand-400 dark:bg-brand-500/10'
                              : 'border-gray-200 hover:bg-gray-50 dark:border-white/10 dark:hover:bg-white/5'
                          }`}
                        >
                          <span className="text-gray-800 dark:text-white">{option.productName}</span>
                          <span className="text-gray-500 dark:text-stone-400">{currencyFormatter.format(option.unitPrice)}</span>
                        </button>
                      )
                    })}
                  </div>
                </div>
              ))}
            </div>
          )}

          <Button
            type="button"
            onClick={handleConfirmCombo}
            disabled={!comboComposition || !isComboValid}
            className="mt-5 w-full"
          >
            {comboComposition ? `Adicionar · ${currencyFormatter.format(computeComboUnitPrice(comboComposition, comboSlotSelections))}` : 'Adicionar'}
          </Button>
        </Modal>
      )}

      {configuringProduct && configuringProduct.type !== 'COMBO' && (
        <Modal title={configuringProduct.name} onClose={() => setConfiguringProduct(null)}>
          <div className="space-y-4">
            {(modifierGroups ?? []).map((group) => (
              <div key={group.id}>
                <div className="mb-1 flex items-center gap-2">
                  <span className="text-sm font-medium text-gray-700 dark:text-stone-300">{group.name}</span>
                  {group.required && (
                    <span className="rounded-full bg-amber-100 px-2 py-0.5 text-xs text-amber-700 dark:bg-amber-500/10 dark:text-amber-400">
                      Obrigatório
                    </span>
                  )}
                </div>
                <div className="space-y-1.5">
                  {group.options.map((option) => {
                    const isSelected = (modifierSelections[group.id] ?? []).includes(option.id)
                    return (
                      <button
                        key={option.id}
                        type="button"
                        onClick={() => toggleModifierOption(group.id, option.id, group.selectionType === 'SINGLE')}
                        className={`flex w-full items-center justify-between rounded-md border px-3 py-2 text-left text-sm ${
                          isSelected
                            ? 'border-brand-600 bg-brand-50 dark:border-brand-400 dark:bg-brand-500/10'
                            : 'border-gray-200 hover:bg-gray-50 dark:border-white/10 dark:hover:bg-white/5'
                        }`}
                      >
                        <span className="text-gray-800 dark:text-white">{option.name}</span>
                        <span className="text-gray-500 dark:text-stone-400">
                          {option.priceDelta > 0 ? `+${currencyFormatter.format(option.priceDelta)}` : 'Grátis'}
                        </span>
                      </button>
                    )
                  })}
                </div>
              </div>
            ))}
          </div>

          <Button type="button" onClick={handleConfirmModifiers} disabled={!areModifiersValid} className="mt-5 w-full">
            Adicionar
          </Button>
        </Modal>
      )}

      {isMerging && (
        <Modal title="Mesclar comanda" onClose={() => setIsMerging(false)}>
          {error && <p className="mb-3 text-sm text-wine-600 dark:text-wine-400">{error}</p>}

          <p className="mb-1 flex items-center gap-1.5 text-xs font-semibold tracking-wide text-gray-400 uppercase dark:text-stone-500">
            <Table2 className="h-3.5 w-3.5" />
            Mesas livres
          </p>
          {freeTables?.length === 0 && (
            <p className="mb-4 text-sm text-gray-500 dark:text-stone-400">Nenhuma mesa livre no momento.</p>
          )}
          <ul className="mb-4 divide-y divide-gray-100 dark:divide-white/10">
            {freeTables?.map((table) => (
              <li key={table.id} className="flex items-center justify-between gap-3 py-3 text-sm">
                <span>Mesa {table.number}</span>
                <button
                  type="button"
                  onClick={() => addTableMutation.mutate(table.id)}
                  disabled={addTableMutation.isPending}
                  className="rounded-xl border border-gray-200 px-4 py-2.5 text-sm font-semibold text-gray-700 transition hover:bg-gray-50 disabled:opacity-50 dark:border-white/10 dark:text-stone-300 dark:hover:bg-white/5"
                >
                  Adicionar a esta comanda
                </button>
              </li>
            ))}
          </ul>

          <p className="mb-1 flex items-center gap-1.5 text-xs font-semibold tracking-wide text-gray-400 uppercase dark:text-stone-500">
            <Combine className="h-3.5 w-3.5" />
            Outras comandas abertas
          </p>
          {otherOpenTabs?.filter((t) => t.id !== tabId).length === 0 && (
            <p className="text-sm text-gray-500 dark:text-stone-400">Nenhuma outra comanda aberta.</p>
          )}
          <ul className="divide-y divide-gray-100 dark:divide-white/10">
            {otherOpenTabs
              ?.filter((t) => t.id !== tabId)
              .map((otherTab) => (
                <li key={otherTab.id} className="flex items-center justify-between gap-3 py-3 text-sm">
                  <span>{formatTableLabel(otherTab.tables.map((t) => t.number))}</span>
                  <button
                    type="button"
                    onClick={() => handleMergeTab(otherTab)}
                    disabled={mergeMutation.isPending}
                    className="rounded-xl border border-gray-200 px-4 py-2.5 text-sm font-semibold text-gray-700 transition hover:bg-gray-50 disabled:opacity-50 dark:border-white/10 dark:text-stone-300 dark:hover:bg-white/5"
                  >
                    Mesclar aqui
                  </button>
                </li>
              ))}
          </ul>
        </Modal>
      )}

      {isConfirmingCancel && (
        <ConfirmDialog
          title="Cancelar comanda"
          message="Cancelar essa comanda? As mesas voltam a ficar livres."
          confirmLabel="Cancelar comanda"
          cancelLabel="Voltar"
          danger
          isLoading={cancelMutation.isPending}
          onConfirm={confirmCancelTab}
          onCancel={() => setIsConfirmingCancel(false)}
        />
      )}

      {tabPendingMerge && (
        <ConfirmDialog
          title="Mesclar comanda"
          message={`Mesclar com ${formatTableLabel(tabPendingMerge.tables.map((t) => t.number))}? Os pedidos dessa comanda serão movidos pra cá, e ela será encerrada.`}
          confirmLabel="Mesclar"
          cancelLabel="Voltar"
          isLoading={mergeMutation.isPending}
          onConfirm={confirmMergeTab}
          onCancel={() => setTabPendingMerge(null)}
        />
      )}

      {isPickingTransferTarget && (
        <Modal title="Transferir itens" onClose={() => setIsPickingTransferTarget(false)}>
          {error && <p className="mb-3 text-sm text-wine-600 dark:text-wine-400">{error}</p>}

          <p className="mb-3 text-sm text-gray-500 dark:text-stone-400">
            {selectedItemIds.size} {selectedItemIds.size === 1 ? 'item selecionado' : 'itens selecionados'}. Escolha
            pra qual comanda transferir.
          </p>

          <p className="mb-1 flex items-center gap-1.5 text-xs font-semibold tracking-wide text-gray-400 uppercase dark:text-stone-500">
            <Combine className="h-3.5 w-3.5" />
            Outras comandas abertas
          </p>
          {otherOpenTabs?.filter((t) => t.id !== tabId).length === 0 && (
            <p className="text-sm text-gray-500 dark:text-stone-400">Nenhuma outra comanda aberta.</p>
          )}
          <ul className="divide-y divide-gray-100 dark:divide-white/10">
            {otherOpenTabs
              ?.filter((t) => t.id !== tabId)
              .map((otherTab) => (
                <li key={otherTab.id} className="flex items-center justify-between gap-3 py-3 text-sm">
                  <span>{formatTableLabel(otherTab.tables.map((t) => t.number))}</span>
                  <button
                    type="button"
                    onClick={() => handleTransferToTab(otherTab)}
                    disabled={transferItemsMutation.isPending}
                    className="rounded-xl border border-gray-200 px-4 py-2.5 text-sm font-semibold text-gray-700 transition hover:bg-gray-50 disabled:opacity-50 dark:border-white/10 dark:text-stone-300 dark:hover:bg-white/5"
                  >
                    Transferir pra cá
                  </button>
                </li>
              ))}
          </ul>
        </Modal>
      )}

      {discountingItem && (
        <Modal title={`Desconto em ${discountingItem.productName}`} onClose={() => setDiscountingItem(null)}>
          <form onSubmit={handleApplyItemDiscount}>
            <p className="mb-3 text-sm text-gray-500 dark:text-stone-400">
              Valor do item: {currencyFormatter.format(discountingItem.subtotal)}
            </p>

            <div className="mb-4 flex gap-2">
              <button
                type="button"
                onClick={() => setDiscountKind('FIXED')}
                className={`flex-1 rounded-md border px-3 py-2 text-sm ${
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
                className={`flex-1 rounded-md border px-3 py-2 text-sm ${
                  discountKind === 'PERCENTAGE'
                    ? 'border-brand-600 bg-brand-50 text-brand-700 dark:border-brand-400 dark:bg-brand-500/10 dark:text-brand-400'
                    : 'border-gray-300 text-gray-600 dark:border-white/10 dark:text-stone-400'
                }`}
              >
                Percentual (%)
              </button>
            </div>

            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="discount-value">
              {discountKind === 'FIXED' ? 'Valor do desconto (R$)' : 'Percentual de desconto (%)'}
            </label>
            <input
              id="discount-value"
              type="number"
              required
              min="0.01"
              step="0.01"
              max={discountKind === 'PERCENTAGE' ? 100 : undefined}
              value={discountValue}
              onChange={(e) => setDiscountValue(e.target.value)}
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
            />

            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="discount-reason">
              Motivo <span className="font-normal text-gray-400 dark:text-stone-500">(opcional)</span>
            </label>
            <input
              id="discount-reason"
              type="text"
              maxLength={255}
              value={discountReason}
              onChange={(e) => setDiscountReason(e.target.value)}
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
            />

            {error && <p className="mb-3 text-sm text-wine-600 dark:text-wine-400">{error}</p>}

            <div className="flex gap-2">
              {discountingItem.discountType && (
                <button
                  type="button"
                  onClick={handleRemoveItemDiscount}
                  disabled={itemDiscountMutation.isPending}
                  className="flex-1 rounded-md border border-red-300 px-3 py-2 text-sm text-red-700 hover:bg-red-50 disabled:opacity-50"
                >
                  Remover desconto
                </button>
              )}
              <Button type="submit" disabled={itemDiscountMutation.isPending} className="flex-1">
                Aplicar
              </Button>
            </div>
          </form>
        </Modal>
      )}

      {voidingPayment && (
        <Modal title="Anular pagamento" onClose={() => setVoidingPayment(null)}>
          <form onSubmit={handleVoidPayment}>
            <p className="mb-3 text-sm text-gray-500 dark:text-stone-400">
              {PAYMENT_METHOD_LABELS[voidingPayment.paymentMethod]} — {currencyFormatter.format(voidingPayment.amount)}. Fica
              registrado no histórico como anulado, em vez de sumir — a mesa e o status da comanda não são mexidos.
            </p>

            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="void-payment-reason">
              Motivo
            </label>
            <input
              id="void-payment-reason"
              type="text"
              required
              maxLength={255}
              value={voidReason}
              onChange={(e) => setVoidReason(e.target.value)}
              placeholder="Ex.: registrei a forma de pagamento errada"
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
            />

            {error && <p className="mb-3 text-sm text-wine-600 dark:text-wine-400">{error}</p>}

            <Button type="submit" variant="danger" disabled={voidPaymentMutation.isPending} className="w-full">
              Confirmar anulação
            </Button>
          </form>
        </Modal>
      )}

      {isCompletingPayment && (
        <Modal title="Completar pagamento" onClose={() => setIsCompletingPayment(false)}>
          <form onSubmit={handleCompletePayment}>
            <p className="mb-3 text-sm text-gray-500 dark:text-stone-400">
              Saldo em aberto: {currencyFormatter.format(tab.remainingBalance ?? 0)}
            </p>

            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="complete-payment-method">
              Forma de pagamento
            </label>
            <select
              id="complete-payment-method"
              value={completePaymentMethod}
              onChange={(e) => setCompletePaymentMethod(e.target.value as PaymentMethod)}
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
            >
              {(Object.keys(PAYMENT_METHOD_LABELS) as PaymentMethod[]).map((method) => (
                <option key={method} value={method}>
                  {PAYMENT_METHOD_LABELS[method]}
                </option>
              ))}
            </select>

            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="complete-payment-amount">
              Valor
            </label>
            <input
              id="complete-payment-amount"
              type="number"
              required
              min="0.01"
              step="0.01"
              max={tab.remainingBalance ?? undefined}
              value={completePaymentAmount}
              onChange={(e) => setCompletePaymentAmount(e.target.value)}
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
            />

            {error && <p className="mb-3 text-sm text-wine-600 dark:text-wine-400">{error}</p>}

            <Button type="submit" disabled={completePaymentMutation.isPending} className="w-full">
              Registrar pagamento
            </Button>
          </form>
        </Modal>
      )}
    </div>
  )
}

interface OrderItemRowProps {
  item: OrderItem
  depth: number
  isSelectingForTransfer: boolean
  selectedItemIds: Set<string>
  toggleItemSelected: (itemId: string) => void
  isOpen: boolean
  canDiscount: boolean
  openDiscountModal: (item: OrderItem) => void
}

function OrderItemRow({
  item,
  depth,
  isSelectingForTransfer,
  selectedItemIds,
  toggleItemSelected,
  isOpen,
  canDiscount,
  openDiscountModal,
}: OrderItemRowProps) {
  const ItemStatusIcon = ITEM_STATUS_ICONS[item.status]

  return (
    <li className={depth > 0 ? 'ml-4 border-l border-gray-200 pl-3 dark:border-white/10' : undefined}>
      <div className="flex flex-wrap items-center justify-between gap-2 py-2 text-sm">
        <div className="flex items-start gap-2">
          {depth === 0 && !item.isComboHeader && isSelectingForTransfer && item.status !== 'CANCELLED' && (
            <input
              type="checkbox"
              checked={selectedItemIds.has(item.id)}
              onChange={() => toggleItemSelected(item.id)}
              className="mt-1 h-4 w-4 shrink-0 rounded border-gray-300 text-brand-600 focus:ring-brand-500 dark:border-white/20 dark:bg-stone-800"
            />
          )}
          <div>
            <span className="font-medium text-gray-800 dark:text-white">
              {item.quantity}x {item.productName}
            </span>
            {item.isComboHeader && (
              <span className="ml-2 inline-flex items-center gap-1 rounded-full bg-purple-100 px-2 py-0.5 text-xs text-purple-700 dark:bg-purple-500/10 dark:text-purple-400">
                <Layers className="h-3 w-3" />
                Combo
              </span>
            )}
            {item.observation && <span className="ml-2 text-gray-500 dark:text-stone-400">({item.observation})</span>}
            {item.modifiers.length > 0 && (
              <div className="mt-1 flex flex-wrap gap-1">
                {item.modifiers.map((modifier, index) => (
                  <span key={index} className="rounded-full bg-gray-100 px-2 py-0.5 text-xs text-gray-600 dark:bg-white/10 dark:text-stone-400">
                    {modifier.optionName}
                  </span>
                ))}
              </div>
            )}
            {item.discountType && (
              <div className="mt-1 flex items-center gap-1 text-xs text-orange-600 dark:text-orange-400">
                <Percent className="h-3 w-3" />
                -{currencyFormatter.format(item.discountAmount)}
                {item.discountReason && <span className="text-gray-400 dark:text-stone-500">({item.discountReason})</span>}
              </div>
            )}
            {item.status === 'CANCELLED' && item.cancelledBy && (
              <div className="mt-1 text-xs text-gray-400 dark:text-stone-500">
                Cancelado por {item.cancelledBy}
                {item.cancelledAt &&
                  ` às ${new Date(item.cancelledAt).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}`}
              </div>
            )}
          </div>
        </div>
        <div className="flex items-center gap-3">
          <span className={`flex items-center gap-1 rounded-full px-2 py-0.5 text-xs ${ITEM_STATUS_STYLES[item.status]}`}>
            <ItemStatusIcon className="h-3 w-3" />
            {ITEM_STATUS_LABELS[item.status]}
          </span>
          <span className="text-gray-600 dark:text-stone-400">
            {item.discountType && (
              <span className="mr-1 text-xs text-gray-400 line-through dark:text-stone-500">
                {currencyFormatter.format(item.subtotal)}
              </span>
            )}
            {currencyFormatter.format(item.netSubtotal)}
          </span>
          {depth === 0 && !item.isComboHeader && isOpen && canDiscount && item.status !== 'CANCELLED' && (
            <button
              type="button"
              onClick={() => openDiscountModal(item)}
              className="text-xs text-brand-600 hover:underline dark:text-brand-400"
            >
              Desconto
            </button>
          )}
        </div>
      </div>
      {item.children.length > 0 && (
        <ul className="divide-y divide-gray-100 dark:divide-white/10">
          {item.children.map((child) => (
            <OrderItemRow
              key={child.id}
              item={child}
              depth={depth + 1}
              isSelectingForTransfer={isSelectingForTransfer}
              selectedItemIds={selectedItemIds}
              toggleItemSelected={toggleItemSelected}
              isOpen={isOpen}
              canDiscount={canDiscount}
              openDiscountModal={openDiscountModal}
            />
          ))}
        </ul>
      )}
    </li>
  )
}
