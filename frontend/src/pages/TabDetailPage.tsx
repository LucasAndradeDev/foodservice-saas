import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
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
  Lock,
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
import { listCategories } from '../api/categories'
import { applyItemDiscount, createOrder, listOrders, transferItems, type DiscountType, type ItemStatus, type Order, type OrderItem } from '../api/orders'
import { listModifierGroups } from '../api/productModifiers'
import { listProducts } from '../api/products'
import { getMyRestaurant } from '../api/restaurant'
import { listTables } from '../api/tables'
import {
  addTableToTab,
  cancelTab,
  cancelTabPayment,
  computeDiscountAmount,
  getTab,
  listTabs,
  mergeTabs,
  PAYMENT_METHOD_LABELS,
  roundCurrency,
  unmergeTabs,
  type PaymentMethod,
  type Tab,
} from '../api/tabs'
import { useAuth } from '../auth/AuthContext'
import { Modal } from '../components/Modal'
import { getCategoryIcon } from './publicMenu/categoryIcons'
import { formatTableLabel } from '../utils/tableLabel'
import { minutesSince } from '../utils/time'
import { modifiersTotal, sameModifiers, type SelectedModifier } from '../utils/modifiers'

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
  PENDING: 'bg-gray-100 text-gray-600',
  PREPARING: 'bg-blue-100 text-blue-700',
  READY: 'bg-amber-100 text-amber-700',
  DELIVERED: 'bg-green-100 text-green-700',
  CANCELLED: 'bg-red-100 text-red-700 line-through',
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
  const [productId, setProductId] = useState('')
  const [quantity, setQuantity] = useState('1')
  const [observation, setObservation] = useState('')
  const [modifierSelections, setModifierSelections] = useState<Record<string, string[]>>({})
  const [error, setError] = useState<string | null>(null)
  const [isMerging, setIsMerging] = useState(false)
  const [pendingUndo, setPendingUndo] = useState<{ sourceTabId: string; label: string } | null>(null)
  const pendingUndoTimeoutRef = useRef<number | null>(null)
  const [discountingItem, setDiscountingItem] = useState<OrderItem | null>(null)
  const [discountKind, setDiscountKind] = useState<DiscountType>('FIXED')
  const [discountValue, setDiscountValue] = useState('')
  const [discountReason, setDiscountReason] = useState('')
  const [isCancellingPayment, setIsCancellingPayment] = useState(false)
  const [cancelPaymentReason, setCancelPaymentReason] = useState('')
  const [cancelPaymentMethod, setCancelPaymentMethod] = useState<PaymentMethod>('PIX')
  const [cancelServiceChargeInput, setCancelServiceChargeInput] = useState('')
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
    queryKey: ['products', productId, 'modifier-groups'],
    queryFn: () => listModifierGroups(productId),
    enabled: isAddingItem && !!productId,
  })

  useEffect(() => {
    setModifierSelections({})
  }, [productId])

  const createOrderMutation = useMutation({
    mutationFn: (items: DraftItem[]) =>
      createOrder(
        tabId!,
        items.map((item) => ({
          productId: item.productId,
          quantity: item.quantity,
          observation: item.observation || undefined,
          selectedOptionIds: item.selectedModifiers.map((m) => m.optionId),
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

  const cancelPaymentMutation = useMutation({
    mutationFn: ({
      reason,
      method,
      amount,
      chargePercentage,
    }: {
      reason: string
      method: PaymentMethod
      amount: number
      chargePercentage: number | null
    }) => cancelTabPayment(tabId!, reason, method, amount, chargePercentage ?? undefined),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tabs', tabId] })
      setIsCancellingPayment(false)
    },
    onError: () => setError('Não foi possível corrigir o pagamento desta comanda. Confira se o valor bate com o total.'),
  })

  function openCancelPaymentModal() {
    if (!tab) return
    setError(null)
    setCancelPaymentReason('')
    setCancelPaymentMethod(tab.paymentMethod ?? 'PIX')
    setCancelServiceChargeInput(tab.serviceChargePercentage != null ? String(tab.serviceChargePercentage) : '')
    setIsCancellingPayment(true)
  }

  function handleCancelPayment(event: FormEvent) {
    event.preventDefault()
    const reason = cancelPaymentReason.trim()
    if (!reason) return
    const chargePercentage = cancelServiceChargeInput.trim() ? Number(cancelServiceChargeInput) : null
    cancelPaymentMutation.mutate({ reason, method: cancelPaymentMethod, amount: cancelPaymentTotal, chargePercentage })
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
    setProductId(productsByCategory[0]?.products[0]?.id ?? '')
    setProductSearch('')
    setQuantity('1')
    setObservation('')
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

  function handleAddDraftItem(event: FormEvent) {
    event.preventDefault()
    const product = products?.find((p) => p.id === productId)
    if (!product || !areModifiersValid) return
    const trimmedObservation = observation.trim()
    const addedQuantity = Number(quantity)

    setDraftItems((prev) => {
      const existingIndex = prev.findIndex(
        (item) =>
          item.productId === product.id &&
          item.observation === trimmedObservation &&
          sameModifiers(item.selectedModifiers, selectedModifiers),
      )
      if (existingIndex !== -1) {
        return prev.map((item, index) =>
          index === existingIndex ? { ...item, quantity: item.quantity + addedQuantity } : item,
        )
      }
      return [
        ...prev,
        {
          productId: product.id,
          productName: product.name,
          unitPrice: product.price,
          quantity: addedQuantity,
          observation: trimmedObservation,
          selectedModifiers,
        },
      ]
    })
    setQuantity('1')
    setObservation('')
    setModifierSelections({})
  }

  function removeDraftItem(index: number) {
    setDraftItems((prev) => prev.filter((_, i) => i !== index))
  }

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
    const confirmed = window.confirm('Cancelar essa comanda? As mesas voltam a ficar livres.')
    if (confirmed) {
      setError(null)
      cancelMutation.mutate()
    }
  }

  function openMergeModal() {
    setError(null)
    setIsMerging(true)
  }

  function handleMergeTab(sourceTab: Tab) {
    const label = formatTableLabel(sourceTab.tables.map((t) => t.number))
    const confirmed = window.confirm(
      `Mesclar com ${label}? Os pedidos dessa comanda serão movidos pra cá, e ela será encerrada.`,
    )
    if (confirmed) {
      setError(null)
      mergeMutation.mutate(sourceTab.id)
    }
  }

  if (isTabLoading || isOrdersLoading || !tab) {
    return <p className="text-sm text-gray-500">Carregando...</p>
  }

  const allItems = orders?.flatMap((order) => order.items) ?? []
  const grandTotal = allItems
    .filter((item) => item.status !== 'CANCELLED')
    .reduce((sum, item) => sum + item.netSubtotal, 0)
  const tabDiscountAmount = computeDiscountAmount(tab.discountType, tab.discountValue, grandTotal)
  const grandTotalAfterDiscount = roundCurrency(grandTotal - tabDiscountAmount)
  const isOpen = tab.status === 'OPEN'

  const cancelServiceChargePercentage = cancelServiceChargeInput.trim() ? Number(cancelServiceChargeInput) : null
  const cancelAfterDiscount = grandTotalAfterDiscount
  const cancelServiceChargeAmount = roundCurrency((cancelAfterDiscount * (cancelServiceChargePercentage ?? 0)) / 100)
  const cancelPaymentTotal = roundCurrency(cancelAfterDiscount + cancelServiceChargeAmount)

  return (
    <div>
      <button
        type="button"
        onClick={() => navigate('/tables')}
        className="mb-4 flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 hover:underline"
      >
        <ArrowLeft className="h-4 w-4" />
        Voltar para Mesas
      </button>

      <div className="mb-6 flex flex-col gap-4 rounded-xl border border-gray-200 bg-white p-5 shadow-sm sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-center gap-3">
          <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-brand-50 text-brand-600">
            <Receipt className="h-5 w-5" />
          </div>
          <div>
            <h1 className="text-lg font-semibold text-gray-800">
              {formatTableLabel(tab.tables.map((t) => t.number))}
            </h1>
            <div className="mt-1 flex flex-wrap items-center gap-2 text-xs">
              <span
                className={`flex items-center gap-1 rounded-full px-2 py-0.5 font-medium ${
                  isOpen ? 'bg-green-100 text-green-700' : 'bg-gray-100 text-gray-600'
                }`}
              >
                {isOpen ? <Clock className="h-3 w-3" /> : <Lock className="h-3 w-3" />}
                {isOpen ? 'Aberta' : 'Fechada'}
              </span>
              {isOpen && (
                <span className="flex items-center gap-1 text-gray-400">há {minutesSince(tab.openedAt)} min</span>
              )}
            </div>
          </div>
        </div>

        {isOpen && canOrder && (
          <div className="flex flex-wrap gap-2">
            {orders?.length === 0 && (
              <button
                type="button"
                onClick={handleCancelTab}
                disabled={cancelMutation.isPending}
                className="flex items-center gap-1.5 rounded-md border border-red-300 px-3 py-1.5 text-sm text-red-700 hover:bg-red-50 disabled:opacity-50"
              >
                <Ban className="h-4 w-4" />
                Cancelar comanda
              </button>
            )}
            <button
              type="button"
              onClick={openMergeModal}
              className="flex items-center gap-1.5 rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-100"
            >
              <GitMerge className="h-4 w-4" />
              Mesclar comanda
            </button>
            {allItems.some((item) => item.status !== 'CANCELLED') && (
              <button
                type="button"
                onClick={toggleTransferSelectionMode}
                className={`flex items-center gap-1.5 rounded-md border px-3 py-1.5 text-sm ${
                  isSelectingForTransfer
                    ? 'border-brand-600 bg-brand-50 text-brand-700'
                    : 'border-gray-300 text-gray-700 hover:bg-gray-100'
                }`}
              >
                <ArrowRightLeft className="h-4 w-4" />
                {isSelectingForTransfer ? 'Cancelar seleção' : 'Transferir itens'}
              </button>
            )}
            <button
              type="button"
              onClick={openAddItemForm}
              className="flex items-center gap-1.5 rounded-md bg-brand-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-brand-700"
            >
              <Plus className="h-4 w-4" />
              Adicionar item
            </button>
          </div>
        )}

        {tab.status === 'CLOSED' && canDiscount && (
          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              onClick={openCancelPaymentModal}
              className="flex items-center gap-1.5 rounded-md border border-red-300 px-3 py-1.5 text-sm text-red-700 hover:bg-red-50"
            >
              <Pencil className="h-4 w-4" />
              Corrigir pagamento
            </button>
          </div>
        )}
      </div>

      {pendingUndo && (
        <div className="mb-4 flex items-center justify-between rounded-lg border border-blue-200 bg-blue-50 px-4 py-3 text-sm text-blue-800">
          <span className="flex items-center gap-2">
            <Undo2 className="h-4 w-4" />
            Mesclado com {pendingUndo.label}.
          </span>
          <button
            type="button"
            onClick={() => undoMergeMutation.mutate(pendingUndo.sourceTabId)}
            disabled={undoMergeMutation.isPending}
            className="font-medium text-blue-700 underline hover:text-blue-900 disabled:opacity-50"
          >
            Desfazer
          </button>
        </div>
      )}

      {pendingTransferUndo && (
        <div className="mb-4 flex items-center justify-between rounded-lg border border-blue-200 bg-blue-50 px-4 py-3 text-sm text-blue-800">
          <span className="flex items-center gap-2">
            <Undo2 className="h-4 w-4" />
            {pendingTransferUndo.itemIds.length} {pendingTransferUndo.itemIds.length === 1 ? 'item transferido' : 'itens transferidos'} pra {pendingTransferUndo.label}.
          </span>
          <button
            type="button"
            onClick={() => undoTransferMutation.mutate(pendingTransferUndo)}
            disabled={undoTransferMutation.isPending}
            className="font-medium text-blue-700 underline hover:text-blue-900 disabled:opacity-50"
          >
            Desfazer
          </button>
        </div>
      )}

      {isSelectingForTransfer && (
        <div className="mb-4 flex items-center justify-between rounded-lg border border-brand-200 bg-brand-50 px-4 py-3 text-sm text-brand-800">
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

      {error && draftItems.length === 0 && <p className="mb-4 text-sm text-red-600">{error}</p>}

      {draftItems.length > 0 && (
        <div className="mb-6 rounded-xl border border-gray-200 bg-white p-4 shadow-sm">
          <h2 className="mb-3 flex items-center gap-2 text-sm font-semibold text-gray-800">
            <ShoppingCart className="h-4 w-4 text-brand-600" />
            Novo pedido (ainda não enviado)
          </h2>
          <ul className="mb-3 divide-y divide-gray-100">
            {draftItems.map((item, index) => (
              <li key={index} className="flex flex-wrap items-center justify-between gap-2 py-2 text-sm">
                <div>
                  <span className="font-medium text-gray-800">
                    {item.quantity}x {item.productName}
                  </span>
                  {item.observation && <span className="ml-2 text-gray-500">({item.observation})</span>}
                  {item.selectedModifiers.length > 0 && (
                    <div className="mt-1 flex flex-wrap gap-1">
                      {item.selectedModifiers.map((modifier) => (
                        <span
                          key={modifier.optionId}
                          className="rounded-full bg-gray-100 px-2 py-0.5 text-xs text-gray-600"
                        >
                          {modifier.optionName}
                        </span>
                      ))}
                    </div>
                  )}
                </div>
                <div className="flex items-center gap-3">
                  <span className="text-gray-600">
                    {currencyFormatter.format((item.unitPrice + modifiersTotal(item.selectedModifiers)) * item.quantity)}
                  </span>
                  <button
                    type="button"
                    onClick={() => removeDraftItem(index)}
                    className="text-red-600 hover:underline"
                  >
                    Remover
                  </button>
                </div>
              </li>
            ))}
          </ul>

          {error && <p className="mb-3 text-sm text-red-600">{error}</p>}

          <button
            type="button"
            onClick={handleSendToKitchen}
            disabled={createOrderMutation.isPending}
            className="w-full rounded-md bg-brand-600 px-3 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50"
          >
            Enviar para Cozinha
          </button>
        </div>
      )}

      <div className="space-y-3">
        {orders && orders.length === 0 && draftItems.length === 0 && (
          <div className="flex flex-col items-center gap-2 rounded-xl border border-dashed border-gray-300 bg-gray-50 py-12 text-center">
            <ClipboardList className="h-8 w-8 text-gray-300" />
            <p className="text-sm text-gray-500">Nenhum pedido enviado ainda.</p>
          </div>
        )}

        {orders?.map((order: Order) => (
          <div key={order.id} className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm">
            <div className="mb-3 flex items-center justify-between border-b border-gray-100 pb-3 text-sm text-gray-500">
              <span className="flex items-center gap-1.5">
                <Clock className="h-3.5 w-3.5" />
                Pedido às {new Date(order.createdAt).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}
              </span>
              <div className="flex items-center gap-3">
                {order.printedAt && <span className="text-xs text-gray-400">Impresso</span>}
                <Link
                  to={`/orders/${order.id}/print`}
                  target="_blank"
                  className="flex items-center gap-1 text-brand-600 hover:underline"
                >
                  <Printer className="h-3.5 w-3.5" />
                  Imprimir
                </Link>
                <span className="font-medium text-gray-700">{currencyFormatter.format(order.total)}</span>
              </div>
            </div>
            <ul className="divide-y divide-gray-100">
              {order.items.map((item) => {
                const ItemStatusIcon = ITEM_STATUS_ICONS[item.status]
                return (
                  <li key={item.id} className="flex flex-wrap items-center justify-between gap-2 py-2 text-sm">
                    <div className="flex items-start gap-2">
                      {isSelectingForTransfer && item.status !== 'CANCELLED' && (
                        <input
                          type="checkbox"
                          checked={selectedItemIds.has(item.id)}
                          onChange={() => toggleItemSelected(item.id)}
                          className="mt-1 h-4 w-4 shrink-0 rounded border-gray-300 text-brand-600 focus:ring-brand-500"
                        />
                      )}
                      <div>
                        <span className="font-medium text-gray-800">
                          {item.quantity}x {item.productName}
                        </span>
                        {item.observation && <span className="ml-2 text-gray-500">({item.observation})</span>}
                        {item.modifiers.length > 0 && (
                          <div className="mt-1 flex flex-wrap gap-1">
                            {item.modifiers.map((modifier, index) => (
                              <span key={index} className="rounded-full bg-gray-100 px-2 py-0.5 text-xs text-gray-600">
                                {modifier.optionName}
                              </span>
                            ))}
                          </div>
                        )}
                        {item.discountType && (
                          <div className="mt-1 flex items-center gap-1 text-xs text-orange-600">
                            <Percent className="h-3 w-3" />
                            -{currencyFormatter.format(item.discountAmount)}
                            {item.discountReason && <span className="text-gray-400">({item.discountReason})</span>}
                          </div>
                        )}
                        {item.status === 'CANCELLED' && item.cancelledBy && (
                          <div className="mt-1 text-xs text-gray-400">
                            Cancelado por {item.cancelledBy}
                            {item.cancelledAt &&
                              ` às ${new Date(item.cancelledAt).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}`}
                          </div>
                        )}
                      </div>
                    </div>
                    <div className="flex items-center gap-3">
                      <span
                        className={`flex items-center gap-1 rounded-full px-2 py-0.5 text-xs ${ITEM_STATUS_STYLES[item.status]}`}
                      >
                        <ItemStatusIcon className="h-3 w-3" />
                        {ITEM_STATUS_LABELS[item.status]}
                      </span>
                      <span className="text-gray-600">
                        {item.discountType && (
                          <span className="mr-1 text-xs text-gray-400 line-through">
                            {currencyFormatter.format(item.subtotal)}
                          </span>
                        )}
                        {currencyFormatter.format(item.netSubtotal)}
                      </span>
                      {isOpen && canDiscount && item.status !== 'CANCELLED' && (
                        <button
                          type="button"
                          onClick={() => openDiscountModal(item)}
                          className="text-xs text-brand-600 hover:underline"
                        >
                          Desconto
                        </button>
                      )}
                    </div>
                  </li>
                )
              })}
            </ul>
          </div>
        ))}
      </div>

      {allItems.length > 0 && (
        <div className="mt-4 rounded-xl border border-gray-200 bg-white px-5 py-4 shadow-sm">
          {tab.discountType && (
            <>
              <div className="flex items-center justify-between text-sm text-gray-500">
                <span>Subtotal</span>
                <span>{currencyFormatter.format(grandTotal)}</span>
              </div>
              <div className="mt-1 flex items-center justify-between text-sm text-orange-600">
                <span>{tab.discountReason || 'Desconto'}</span>
                <span>-{currencyFormatter.format(tabDiscountAmount)}</span>
              </div>
              <div className="my-2 border-t border-gray-100" />
            </>
          )}
          <div className="flex items-center justify-between">
            <span className="flex items-center gap-2 text-sm font-medium text-gray-500">
              <Wallet className="h-4 w-4" />
              Total da comanda
            </span>
            <span className="text-xl font-semibold text-brand-700">
              {currencyFormatter.format(grandTotalAfterDiscount)}
            </span>
          </div>
        </div>
      )}

      {isAddingItem && (
        <Modal title="Adicionar item" onClose={() => setIsAddingItem(false)}>
          <form onSubmit={handleAddDraftItem}>
            <label className="mb-1 block text-sm font-medium text-gray-700" htmlFor="product">
              Produto
            </label>
            <div className="relative mb-2">
              <Search className="pointer-events-none absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2 text-gray-400" />
              <input
                type="text"
                placeholder="Buscar produto..."
                value={productSearch}
                onChange={(e) => setProductSearch(e.target.value)}
                className="w-full rounded-md border border-gray-300 py-2 pr-3 pl-9 text-sm focus:border-brand-500 focus:outline-none"
              />
            </div>
            <div className="mb-4 max-h-56 space-y-3 overflow-y-auto rounded-md border border-gray-200 p-2">
              {filteredProductsByCategory.length === 0 && (
                <p className="py-4 text-center text-sm text-gray-400">Nenhum produto encontrado.</p>
              )}
              {filteredProductsByCategory.map(({ category, products: categoryProducts }) => {
                const CategoryIcon = getCategoryIcon(category.name)
                return (
                  <div key={category.id}>
                    <div className="mb-1 flex items-center gap-1.5 px-1 text-xs font-semibold tracking-wide text-gray-400 uppercase">
                      <CategoryIcon className="h-3.5 w-3.5" />
                      {category.name}
                    </div>
                    <div className="space-y-1">
                      {categoryProducts.map((product) => {
                        const isSelected = product.id === productId
                        return (
                          <motion.button
                            key={product.id}
                            type="button"
                            whileTap={{ scale: 0.98 }}
                            onClick={() => setProductId(product.id)}
                            className={`flex w-full items-center justify-between rounded-md border px-3 py-2 text-left text-sm transition-colors ${
                              isSelected ? 'border-brand-600 bg-brand-50' : 'border-gray-200 hover:bg-gray-50'
                            }`}
                          >
                            <span className="flex items-center gap-2 text-gray-800">
                              {isSelected && <Check className="h-3.5 w-3.5 shrink-0 text-brand-600" />}
                              {product.name}
                            </span>
                            <span className="shrink-0 text-gray-500">{currencyFormatter.format(product.price)}</span>
                          </motion.button>
                        )
                      })}
                    </div>
                  </div>
                )
              })}
            </div>

            <label className="mb-1 block text-sm font-medium text-gray-700" htmlFor="quantity">
              Quantidade
            </label>
            <input
              id="quantity"
              type="number"
              required
              min="1"
              value={quantity}
              onChange={(e) => setQuantity(e.target.value)}
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none"
            />

            {modifierGroups && modifierGroups.length > 0 && (
              <div className="mb-4 space-y-4">
                {modifierGroups.map((group) => (
                  <div key={group.id}>
                    <div className="mb-1 flex items-center gap-2">
                      <span className="text-sm font-medium text-gray-700">{group.name}</span>
                      {group.required && (
                        <span className="rounded-full bg-amber-100 px-2 py-0.5 text-xs text-amber-700">
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
                                ? 'border-brand-600 bg-brand-50'
                                : 'border-gray-200 hover:bg-gray-50'
                            }`}
                          >
                            <span className="text-gray-800">{option.name}</span>
                            <span className="text-gray-500">
                              {option.priceDelta > 0 ? `+${currencyFormatter.format(option.priceDelta)}` : 'Grátis'}
                            </span>
                          </button>
                        )
                      })}
                    </div>
                  </div>
                ))}
              </div>
            )}

            <label className="mb-1 block text-sm font-medium text-gray-700" htmlFor="observation">
              Observação <span className="font-normal text-gray-400">(opcional)</span>
            </label>
            <input
              id="observation"
              type="text"
              maxLength={255}
              value={observation}
              onChange={(e) => setObservation(e.target.value)}
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none"
            />

            <button
              type="submit"
              disabled={!productId || !areModifiersValid}
              className="w-full rounded-md bg-brand-600 px-3 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50"
            >
              Adicionar à comanda
            </button>
          </form>
        </Modal>
      )}

      {isMerging && (
        <Modal title="Mesclar comanda" onClose={() => setIsMerging(false)}>
          {error && <p className="mb-3 text-sm text-red-600">{error}</p>}

          <p className="mb-1 flex items-center gap-1.5 text-xs font-semibold tracking-wide text-gray-400 uppercase">
            <Table2 className="h-3.5 w-3.5" />
            Mesas livres
          </p>
          {freeTables?.length === 0 && (
            <p className="mb-4 text-sm text-gray-500">Nenhuma mesa livre no momento.</p>
          )}
          <ul className="mb-4 divide-y divide-gray-100">
            {freeTables?.map((table) => (
              <li key={table.id} className="flex items-center justify-between py-2 text-sm">
                <span>Mesa {table.number}</span>
                <button
                  type="button"
                  onClick={() => addTableMutation.mutate(table.id)}
                  disabled={addTableMutation.isPending}
                  className="rounded-md border border-gray-300 px-3 py-1 text-xs text-gray-700 hover:bg-gray-100 disabled:opacity-50"
                >
                  Adicionar a esta comanda
                </button>
              </li>
            ))}
          </ul>

          <p className="mb-1 flex items-center gap-1.5 text-xs font-semibold tracking-wide text-gray-400 uppercase">
            <Combine className="h-3.5 w-3.5" />
            Outras comandas abertas
          </p>
          {otherOpenTabs?.filter((t) => t.id !== tabId).length === 0 && (
            <p className="text-sm text-gray-500">Nenhuma outra comanda aberta.</p>
          )}
          <ul className="divide-y divide-gray-100">
            {otherOpenTabs
              ?.filter((t) => t.id !== tabId)
              .map((otherTab) => (
                <li key={otherTab.id} className="flex items-center justify-between py-2 text-sm">
                  <span>{formatTableLabel(otherTab.tables.map((t) => t.number))}</span>
                  <button
                    type="button"
                    onClick={() => handleMergeTab(otherTab)}
                    disabled={mergeMutation.isPending}
                    className="rounded-md border border-gray-300 px-3 py-1 text-xs text-gray-700 hover:bg-gray-100 disabled:opacity-50"
                  >
                    Mesclar aqui
                  </button>
                </li>
              ))}
          </ul>
        </Modal>
      )}

      {isPickingTransferTarget && (
        <Modal title="Transferir itens" onClose={() => setIsPickingTransferTarget(false)}>
          {error && <p className="mb-3 text-sm text-red-600">{error}</p>}

          <p className="mb-3 text-sm text-gray-500">
            {selectedItemIds.size} {selectedItemIds.size === 1 ? 'item selecionado' : 'itens selecionados'}. Escolha
            pra qual comanda transferir.
          </p>

          <p className="mb-1 flex items-center gap-1.5 text-xs font-semibold tracking-wide text-gray-400 uppercase">
            <Combine className="h-3.5 w-3.5" />
            Outras comandas abertas
          </p>
          {otherOpenTabs?.filter((t) => t.id !== tabId).length === 0 && (
            <p className="text-sm text-gray-500">Nenhuma outra comanda aberta.</p>
          )}
          <ul className="divide-y divide-gray-100">
            {otherOpenTabs
              ?.filter((t) => t.id !== tabId)
              .map((otherTab) => (
                <li key={otherTab.id} className="flex items-center justify-between py-2 text-sm">
                  <span>{formatTableLabel(otherTab.tables.map((t) => t.number))}</span>
                  <button
                    type="button"
                    onClick={() => handleTransferToTab(otherTab)}
                    disabled={transferItemsMutation.isPending}
                    className="rounded-md border border-gray-300 px-3 py-1 text-xs text-gray-700 hover:bg-gray-100 disabled:opacity-50"
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
            <p className="mb-3 text-sm text-gray-500">
              Valor do item: {currencyFormatter.format(discountingItem.subtotal)}
            </p>

            <div className="mb-4 flex gap-2">
              <button
                type="button"
                onClick={() => setDiscountKind('FIXED')}
                className={`flex-1 rounded-md border px-3 py-2 text-sm ${
                  discountKind === 'FIXED' ? 'border-brand-600 bg-brand-50 text-brand-700' : 'border-gray-300 text-gray-600'
                }`}
              >
                Valor em R$
              </button>
              <button
                type="button"
                onClick={() => setDiscountKind('PERCENTAGE')}
                className={`flex-1 rounded-md border px-3 py-2 text-sm ${
                  discountKind === 'PERCENTAGE' ? 'border-brand-600 bg-brand-50 text-brand-700' : 'border-gray-300 text-gray-600'
                }`}
              >
                Percentual (%)
              </button>
            </div>

            <label className="mb-1 block text-sm font-medium text-gray-700" htmlFor="discount-value">
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
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none"
            />

            <label className="mb-1 block text-sm font-medium text-gray-700" htmlFor="discount-reason">
              Motivo <span className="font-normal text-gray-400">(opcional)</span>
            </label>
            <input
              id="discount-reason"
              type="text"
              maxLength={255}
              value={discountReason}
              onChange={(e) => setDiscountReason(e.target.value)}
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none"
            />

            {error && <p className="mb-3 text-sm text-red-600">{error}</p>}

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
              <button
                type="submit"
                disabled={itemDiscountMutation.isPending}
                className="flex-1 rounded-md bg-brand-600 px-3 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50"
              >
                Aplicar
              </button>
            </div>
          </form>
        </Modal>
      )}

      {isCancellingPayment && (
        <Modal title="Corrigir pagamento" onClose={() => setIsCancellingPayment(false)}>
          <form onSubmit={handleCancelPayment}>
            <p className="mb-3 text-sm text-gray-500">
              Substitui o pagamento registrado por um corrigido. A comanda continua fechada e a mesa não é mexida —
              use isso pra corrigir forma ou valor errados, não pra retomar o atendimento.
            </p>

            <label className="mb-1 block text-sm font-medium text-gray-700" htmlFor="cancel-payment-reason">
              Motivo
            </label>
            <input
              id="cancel-payment-reason"
              type="text"
              required
              maxLength={255}
              value={cancelPaymentReason}
              onChange={(e) => setCancelPaymentReason(e.target.value)}
              placeholder="Ex.: registrei a forma de pagamento errada"
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none"
            />

            <label className="mb-1 block text-sm font-medium text-gray-700" htmlFor="cancel-payment-method">
              Forma de pagamento correta
            </label>
            <select
              id="cancel-payment-method"
              value={cancelPaymentMethod}
              onChange={(e) => setCancelPaymentMethod(e.target.value as PaymentMethod)}
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none"
            >
              {(Object.keys(PAYMENT_METHOD_LABELS) as PaymentMethod[]).map((method) => (
                <option key={method} value={method}>
                  {PAYMENT_METHOD_LABELS[method]}
                </option>
              ))}
            </select>

            <label className="mb-1 block text-sm font-medium text-gray-700" htmlFor="cancel-service-charge">
              Taxa de serviço (%) <span className="font-normal text-gray-400">(opcional)</span>
            </label>
            <input
              id="cancel-service-charge"
              type="number"
              min="0"
              max="100"
              step="0.01"
              value={cancelServiceChargeInput}
              onChange={(e) => setCancelServiceChargeInput(e.target.value)}
              placeholder="Sem taxa de serviço"
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none"
            />

            <div className="mb-4 space-y-1 border-t border-gray-200 pt-3">
              {cancelServiceChargePercentage != null && (
                <div className="flex items-center justify-between text-sm text-gray-500">
                  <span>Subtotal</span>
                  <span>{currencyFormatter.format(cancelAfterDiscount)}</span>
                </div>
              )}
              <div className="flex items-center justify-between text-base font-semibold text-gray-800">
                <span>Total corrigido</span>
                <span>{currencyFormatter.format(cancelPaymentTotal)}</span>
              </div>
            </div>

            {error && <p className="mb-3 text-sm text-red-600">{error}</p>}

            <button
              type="submit"
              disabled={cancelPaymentMutation.isPending}
              className="w-full rounded-md bg-red-600 px-3 py-2 text-sm font-medium text-white hover:bg-red-700 disabled:opacity-50"
            >
              Confirmar correção
            </button>
          </form>
        </Modal>
      )}
    </div>
  )
}
