import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AnimatePresence, motion, type PanInfo } from 'framer-motion'
import {
  AlertTriangle,
  Bell,
  CalendarClock,
  CheckCircle2,
  Clock,
  Coffee,
  Flame,
  Layers,
  MoreVertical,
  Move,
  Plus,
  Table2,
  Users,
  UtensilsCrossed,
  Wallet,
  X,
  type LucideIcon,
} from 'lucide-react'
import { Fragment, useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import { createPortal } from 'react-dom'
import { useNavigate } from 'react-router-dom'
import {
  acknowledgeTableRequest,
  listPendingTableRequests,
  type TableRequest,
  type TableRequestType,
} from '../api/tableRequests'
import {
  createTable,
  createTablesBulk,
  deleteTable,
  listTables,
  updateTable,
  updateTableStatus,
  type RestaurantTable,
  type TableStatus,
} from '../api/tables'
import { listDiningAreas } from '../api/diningAreas'
import { listTabs, openTab, type Tab } from '../api/tabs'
import { getMyRestaurant } from '../api/restaurant'
import { useAuth } from '../auth/AuthContext'
import { Button } from '../components/Button'
import { Dropdown } from '../components/Dropdown'
import { Modal } from '../components/Modal'
import { QrCodeCard } from '../components/QrCodeCard'
import { minutesSince } from '../utils/time'
import { publicMenuUrl } from '../utils/publicMenuUrl'

const STATUS_LABELS: Record<TableStatus, string> = {
  FREE: 'Livre',
  OCCUPIED: 'Ocupada',
  CLOSING: 'Fechando',
  RESERVED: 'Reservada',
}

// Statuses staff can pick by hand -- RESERVED is computed from active reservations (see TableService)
// and rejected by the API if sent directly, so it's excluded from this list.
const EDITABLE_STATUSES: TableStatus[] = ['FREE', 'OCCUPIED', 'CLOSING']

const STATUS_CARD_STYLES: Record<TableStatus, string> = {
  FREE: 'border-sage-200 bg-white hover:border-sage-300 dark:border-sage-500/20 dark:bg-stone-900 dark:hover:border-sage-500/40',
  OCCUPIED: 'border-brand-200 bg-gradient-to-br from-brand-50 to-white dark:border-brand-500/20 dark:from-brand-500/10 dark:to-stone-900',
  CLOSING: 'border-gold-200 bg-gradient-to-br from-gold-100 to-white dark:border-gold-500/20 dark:from-gold-500/10 dark:to-stone-900',
  RESERVED: 'border-teal-200 bg-gradient-to-br from-teal-100 to-white dark:border-teal-500/20 dark:from-teal-500/10 dark:to-stone-900',
}

const STATUS_BADGE_STYLES: Record<TableStatus, string> = {
  FREE: 'bg-sage-100 text-sage-700 dark:bg-sage-500/20 dark:text-sage-400',
  OCCUPIED: 'bg-brand-100 text-brand-700 dark:bg-brand-500/20 dark:text-brand-400',
  CLOSING: 'bg-gold-100 text-gold-700 dark:bg-gold-500/20 dark:text-gold-400',
  RESERVED: 'bg-teal-100 text-teal-700 dark:bg-teal-500/20 dark:text-teal-400',
}

const STATUS_LABEL_STYLES: Record<TableStatus, string> = {
  FREE: 'text-sage-700 dark:text-sage-400',
  OCCUPIED: 'text-brand-700 dark:text-brand-400',
  CLOSING: 'text-gold-700 dark:text-gold-400',
  RESERVED: 'text-teal-700 dark:text-teal-400',
}

const STATUS_ICONS: Record<TableStatus, LucideIcon> = {
  FREE: CheckCircle2,
  OCCUPIED: Flame,
  CLOSING: Clock,
  RESERVED: CalendarClock,
}

const POLL_INTERVAL_MS = 4000

const TABLE_REQUEST_ICONS: Record<TableRequestType, LucideIcon> = {
  CALL_WAITER: Bell,
  REQUEST_BILL: Wallet,
}

const TABLE_REQUEST_LABELS: Record<TableRequestType, string> = {
  CALL_WAITER: 'Chamando',
  REQUEST_BILL: 'Conta',
}

interface StatPillProps {
  icon: LucideIcon
  label: string
  value: number
  badgeClassName: string
}

function StatPill({ icon: Icon, label, value, badgeClassName }: StatPillProps) {
  return (
    <div className="flex items-center gap-3 rounded-xl border border-gray-200 bg-white px-4 py-2.5 shadow-sm dark:border-white/10 dark:bg-stone-900">
      <span className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-lg ${badgeClassName}`}>
        <Icon className="h-4 w-4" />
      </span>
      <div className="leading-tight">
        <div className="text-base font-bold text-gray-900 dark:text-white">{value}</div>
        <div className="text-[11px] font-medium text-gray-500 dark:text-stone-400">{label}</div>
      </div>
    </div>
  )
}

export function TablesPage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const canManage = user?.role === 'OWNER' || user?.role === 'MANAGER'
  const canChangeStatus = canManage || user?.role === 'WAITER'
  const canOpenTab = canManage || user?.role === 'WAITER' || user?.role === 'CASHIER'
  const queryClient = useQueryClient()

  const { data: tables, isLoading } = useQuery({
    queryKey: ['tables'],
    queryFn: () => listTables(),
    select: (data) => [...data].sort((a, b) => a.number - b.number),
    refetchInterval: POLL_INTERVAL_MS,
    refetchIntervalInBackground: true,
  })

  const { data: openTabs } = useQuery({
    queryKey: ['tabs', 'OPEN'],
    queryFn: () => listTabs('OPEN'),
    enabled: canOpenTab,
    refetchInterval: POLL_INTERVAL_MS,
    refetchIntervalInBackground: true,
  })

  const { data: restaurant } = useQuery({
    queryKey: ['restaurant'],
    queryFn: getMyRestaurant,
  })

  const { data: areas } = useQuery({
    queryKey: ['dining-areas'],
    queryFn: listDiningAreas,
  })

  const tableForgottenWarningThreshold = restaurant?.tableForgottenWarningThresholdMinutes ?? 30
  const tableForgottenCriticalThreshold = restaurant?.tableForgottenCriticalThresholdMinutes ?? 60

  function getTableDelay(tab: Tab | undefined, status: TableStatus) {
    if (!tab || status !== 'OCCUPIED') return { level: 'none' as const, minutesWithoutOrder: 0 }
    const minutesWithoutOrder = minutesSince(tab.lastOrderAt ?? tab.openedAt)
    const level =
      minutesWithoutOrder >= tableForgottenCriticalThreshold
        ? ('critical' as const)
        : minutesWithoutOrder >= tableForgottenWarningThreshold
          ? ('warning' as const)
          : ('none' as const)
    return { level, minutesWithoutOrder }
  }

  const { data: pendingRequests } = useQuery({
    queryKey: ['tableRequests', 'pending'],
    queryFn: listPendingTableRequests,
    enabled: canChangeStatus,
    refetchInterval: POLL_INTERVAL_MS,
    refetchIntervalInBackground: true,
  })

  const pendingRequestsByTable = useMemo(() => {
    const map = new Map<string, TableRequest[]>()
    pendingRequests?.forEach((request) => {
      const list = map.get(request.tableId) ?? []
      list.push(request)
      map.set(request.tableId, list)
    })
    return map
  }, [pendingRequests])

  const acknowledgeMutation = useMutation({
    mutationFn: acknowledgeTableRequest,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['tableRequests'] }),
  })

  const tableTabMap = useMemo(() => {
    const map = new Map<string, Tab>()
    openTabs?.forEach((tab) => {
      tab.tables.forEach((table) => map.set(table.id, tab))
    })
    return map
  }, [openTabs])

  const counterTabs = useMemo(() => openTabs?.filter((tab) => tab.tables.length === 0) ?? [], [openTabs])

  const statusCounts = useMemo(() => {
    const counts: Record<TableStatus, number> = { FREE: 0, OCCUPIED: 0, CLOSING: 0, RESERVED: 0 }
    tables?.forEach((table) => {
      counts[table.status] += 1
    })
    return counts
  }, [tables])

  const groupedTables = useMemo(() => {
    if (!tables) return []
    const byArea = new Map<string, RestaurantTable[]>()
    tables.forEach((table) => {
      const key = table.areaId ?? 'none'
      const list = byArea.get(key) ?? []
      list.push(table)
      byArea.set(key, list)
    })
    const groups: { id: string; name: string; tables: RestaurantTable[] }[] = []
    areas?.forEach((area) => {
      const areaTables = byArea.get(area.id)
      if (areaTables && areaTables.length > 0) {
        groups.push({ id: area.id, name: area.name, tables: areaTables })
      }
    })
    const noAreaTables = byArea.get('none')
    if (noAreaTables && noAreaTables.length > 0) {
      groups.push({ id: 'none', name: 'Sem área', tables: noAreaTables })
    }
    return groups
  }, [tables, areas])
  const showGroupHeaders = groupedTables.length > 1

  const [isOrganizingAreas, setIsOrganizingAreas] = useState(false)
  const [dragHoverGroupId, setDragHoverGroupId] = useState<string | null>(null)
  const groupRefs = useRef<Map<string, HTMLDivElement>>(new Map())

  const organizeGroups = useMemo(() => {
    if (!tables) return []
    const byArea = new Map<string, RestaurantTable[]>()
    tables.forEach((table) => {
      const key = table.areaId ?? 'none'
      const list = byArea.get(key) ?? []
      list.push(table)
      byArea.set(key, list)
    })
    const groups = (areas ?? []).map((area) => ({
      id: area.id,
      name: area.name,
      tables: byArea.get(area.id) ?? [],
    }))
    groups.push({ id: 'none', name: 'Sem área', tables: byArea.get('none') ?? [] })
    return groups
  }, [tables, areas])

  const displayGroups = isOrganizingAreas ? organizeGroups : groupedTables

  const areaMoveOptions = useMemo(
    () => [...(areas ?? []).map((area) => ({ value: area.id, label: area.name })), { value: 'none', label: 'Sem área' }],
    [areas],
  )

  function findGroupAtPoint(x: number, y: number): string | null {
    for (const [groupId, el] of groupRefs.current.entries()) {
      const rect = el.getBoundingClientRect()
      if (x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom) {
        return groupId
      }
    }
    return null
  }

  const [isCreating, setIsCreating] = useState(false)
  const [isBulkCreating, setIsBulkCreating] = useState(false)
  const [isSelectingTables, setIsSelectingTables] = useState(false)
  const [isMultiTableMode, setIsMultiTableMode] = useState(false)
  const [isMoreMenuOpen, setIsMoreMenuOpen] = useState(false)
  const moreMenuRef = useRef<HTMLDivElement>(null)
  const [selectedTableIds, setSelectedTableIds] = useState<Set<string>>(new Set())
  const [selectedTable, setSelectedTable] = useState<RestaurantTable | null>(null)
  const [numberInput, setNumberInput] = useState('')
  const [createAreaId, setCreateAreaId] = useState('')
  const [quantityInput, setQuantityInput] = useState('')
  const [editNumber, setEditNumber] = useState('')
  const [editStatus, setEditStatus] = useState<TableStatus>('FREE')
  const [editActive, setEditActive] = useState(true)
  const [editAreaId, setEditAreaId] = useState('')
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!isMoreMenuOpen) return
    function handleClickOutside(event: MouseEvent) {
      if (moreMenuRef.current && !moreMenuRef.current.contains(event.target as Node)) {
        setIsMoreMenuOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [isMoreMenuOpen])

  useEffect(() => {
    if (!selectedTable) return
    function handleEscape(event: KeyboardEvent) {
      if (event.key === 'Escape') closeTableModal()
    }
    document.addEventListener('keydown', handleEscape)
    return () => document.removeEventListener('keydown', handleEscape)
  }, [selectedTable])

  function invalidate() {
    queryClient.invalidateQueries({ queryKey: ['tables'] })
  }

  const createMutation = useMutation({
    mutationFn: ({ number, areaId }: { number?: number; areaId: string | null }) => createTable(number, areaId),
    onSuccess: () => {
      invalidate()
      setIsCreating(false)
    },
    onError: () => setError('Não foi possível criar a mesa. Verifique se o número já está em uso.'),
  })

  const bulkMutation = useMutation({
    mutationFn: createTablesBulk,
    onSuccess: () => {
      invalidate()
      setIsBulkCreating(false)
    },
    onError: () => setError('Não foi possível criar as mesas.'),
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: Parameters<typeof updateTable>[1] }) =>
      updateTable(id, payload),
    onSuccess: invalidate,
  })

  const statusMutation = useMutation({
    mutationFn: ({ id, status }: { id: string; status: TableStatus }) => updateTableStatus(id, status),
    onSuccess: invalidate,
  })

  const deleteMutation = useMutation({
    mutationFn: deleteTable,
    onSuccess: () => {
      invalidate()
      closeTableModal()
    },
    onError: () => setError('Não foi possível excluir a mesa.'),
  })

  const openTabMutation = useMutation({
    mutationFn: openTab,
    onSuccess: (tab) => {
      invalidate()
      queryClient.invalidateQueries({ queryKey: ['tabs'] })
      setIsSelectingTables(false)
      setIsMultiTableMode(false)
      setSelectedTableIds(new Set())
      navigate(`/tabs/${tab.id}`)
    },
    onError: () => setError('Não foi possível abrir a comanda. Verifique se as mesas selecionadas estão livres.'),
  })

  function openCreateForm() {
    setNumberInput('')
    setCreateAreaId('')
    setError(null)
    setIsCreating(true)
  }

  function openBulkForm() {
    setQuantityInput('')
    setError(null)
    setIsBulkCreating(true)
  }

  function openTableModal(table: RestaurantTable) {
    setSelectedTable(table)
    setEditNumber(String(table.number))
    // RESERVED is a read-only computed overlay on top of FREE (see TableService) -- there's no such
    // status to hand-pick in the dropdown, so default the editable field to what it actually is.
    setEditStatus(table.status === 'RESERVED' ? 'FREE' : table.status)
    setEditActive(table.active)
    setEditAreaId(table.areaId ?? '')
    setError(null)
  }

  function closeTableModal() {
    setSelectedTable(null)
  }

  function handleDelete() {
    if (!selectedTable) return
    const confirmed = window.confirm(
      `Excluir a Mesa ${selectedTable.number}? Essa ação não pode ser desfeita.`,
    )
    if (confirmed) {
      setError(null)
      deleteMutation.mutate(selectedTable.id)
    }
  }

  function handleCreateSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    createMutation.mutate({
      number: numberInput ? Number(numberInput) : undefined,
      areaId: createAreaId || null,
    })
  }

  function handleBulkSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    bulkMutation.mutate(Number(quantityInput))
  }

  function handleTableSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    if (!selectedTable) return

    if (canManage) {
      const areaPayload = editAreaId ? { areaId: editAreaId } : { clearArea: true }
      updateMutation.mutate(
        { id: selectedTable.id, payload: { number: Number(editNumber), active: editActive, ...areaPayload } },
        { onError: () => setError('Não foi possível salvar. Verifique se o número já está em uso.') },
      )
    }
    const originalEditableStatus = selectedTable.status === 'RESERVED' ? 'FREE' : selectedTable.status
    if (editStatus !== originalEditableStatus) {
      statusMutation.mutate({ id: selectedTable.id, status: editStatus })
    }
    closeTableModal()
  }

  function startSelectingTables() {
    setError(null)
    setSelectedTableIds(new Set())
    setIsMultiTableMode(false)
    setIsSelectingTables(true)
  }

  function cancelSelectingTables() {
    setIsSelectingTables(false)
    setIsMultiTableMode(false)
    setSelectedTableIds(new Set())
  }

  function enableMultiTableMode() {
    setError(null)
    setSelectedTableIds(new Set())
    setIsMultiTableMode(true)
  }

  function toggleTableSelection(table: RestaurantTable) {
    if (table.status !== 'FREE') return
    setSelectedTableIds((prev) => {
      const next = new Set(prev)
      if (next.has(table.id)) {
        next.delete(table.id)
      } else {
        next.add(table.id)
      }
      return next
    })
  }

  function confirmOpenTab() {
    setError(null)
    openTabMutation.mutate(Array.from(selectedTableIds))
  }

  function openCounterTab() {
    setError(null)
    openTabMutation.mutate([])
  }

  function handleTableClick(table: RestaurantTable) {
    if (isOrganizingAreas) return
    if (isSelectingTables) {
      if (table.status !== 'FREE') return
      if (isMultiTableMode) {
        toggleTableSelection(table)
      } else {
        setError(null)
        openTabMutation.mutate([table.id])
      }
      return
    }
    const tab = tableTabMap.get(table.id)
    if (tab) {
      navigate(`/tabs/${tab.id}`)
      return
    }
    openTableModal(table)
  }

  function moveTableToArea(table: RestaurantTable, targetGroupId: string) {
    const currentGroupId = table.areaId ?? 'none'
    if (targetGroupId === currentGroupId) return
    const payload = targetGroupId === 'none' ? { clearArea: true } : { areaId: targetGroupId }
    updateMutation.mutate({ id: table.id, payload })
  }

  function handleTableDragEnd(table: RestaurantTable, info: PanInfo) {
    const targetGroupId = findGroupAtPoint(info.point.x, info.point.y)
    setDragHoverGroupId(null)
    if (!targetGroupId) return
    moveTableToArea(table, targetGroupId)
  }

  return (
    <div className={isSelectingTables ? 'pb-24' : undefined}>
      <div className="mb-5 flex flex-col gap-4 rounded-2xl border border-gray-200 bg-white p-5 shadow-sm dark:border-white/10 dark:bg-stone-900 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-center gap-3">
          <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-brand-600 text-white shadow-sm">
            <Table2 className="h-5 w-5" />
          </span>
          <h1 className="text-lg font-bold text-gray-900 dark:text-white">Mesas</h1>
        </div>

        {!isSelectingTables && !isOrganizingAreas && (
          <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
            {canOpenTab && (
              <button
                type="button"
                onClick={startSelectingTables}
                className="flex items-center justify-center gap-2 rounded-xl bg-brand-600 px-4 py-3.5 text-base font-semibold text-white shadow-sm transition-all hover:bg-brand-700 hover:shadow-md active:scale-[0.98] sm:py-2.5 sm:text-sm"
              >
                <Users className="h-5 w-5 sm:h-4 sm:w-4" />
                Abrir comanda
              </button>
            )}
            <div className="flex gap-2">
              {canOpenTab && (
                <button
                  type="button"
                  onClick={openCounterTab}
                  disabled={openTabMutation.isPending}
                  className="flex flex-1 items-center justify-center gap-2 rounded-xl border border-gray-200 bg-white px-4 py-3.5 text-base font-semibold text-gray-700 transition-all hover:bg-gray-50 active:scale-[0.98] disabled:opacity-50 sm:flex-none sm:py-2.5 sm:text-sm dark:border-white/10 dark:bg-white/5 dark:text-stone-300 dark:hover:bg-white/10"
                >
                  <Coffee className="h-5 w-5 sm:h-4 sm:w-4" />
                  Balcão
                </button>
              )}
              {canManage && (
                <div className="relative" ref={moreMenuRef}>
                  <button
                    type="button"
                    onClick={() => setIsMoreMenuOpen((open) => !open)}
                    title="Mais ações"
                    aria-label="Mais ações"
                    className="rounded-xl border border-gray-200 p-3.5 text-gray-500 hover:bg-gray-50 hover:text-gray-700 sm:p-2.5 dark:border-white/10 dark:text-stone-400 dark:hover:bg-white/5 dark:hover:text-stone-200"
                  >
                    <MoreVertical className="h-5 w-5" />
                  </button>

                  <AnimatePresence>
                    {isMoreMenuOpen && (
                      <motion.div
                        initial={{ opacity: 0, y: -4, scale: 0.97 }}
                        animate={{ opacity: 1, y: 0, scale: 1 }}
                        exit={{ opacity: 0, y: -4, scale: 0.97 }}
                        transition={{ duration: 0.15, ease: 'easeOut' }}
                        className="absolute right-0 z-10 mt-1.5 hidden w-48 overflow-hidden rounded-lg border border-gray-200 bg-white py-1 text-sm shadow-lg sm:block dark:border-white/10 dark:bg-stone-800"
                      >
                        <button
                          type="button"
                          onClick={() => {
                            openBulkForm()
                            setIsMoreMenuOpen(false)
                          }}
                          className="flex w-full items-center gap-2 px-3 py-2 text-left text-gray-700 hover:bg-gray-50 dark:text-stone-300 dark:hover:bg-white/5"
                        >
                          <Layers className="h-4 w-4 text-gray-400 dark:text-stone-500" />
                          Criar em lote
                        </button>
                        <button
                          type="button"
                          onClick={() => {
                            openCreateForm()
                            setIsMoreMenuOpen(false)
                          }}
                          className="flex w-full items-center gap-2 px-3 py-2 text-left text-gray-700 hover:bg-gray-50 dark:text-stone-300 dark:hover:bg-white/5"
                        >
                          <Plus className="h-4 w-4 text-gray-400 dark:text-stone-500" />
                          Nova mesa
                        </button>
                        {areas && areas.length > 0 && (
                          <button
                            type="button"
                            onClick={() => {
                              setIsOrganizingAreas(true)
                              setIsMoreMenuOpen(false)
                            }}
                            className="flex w-full items-center gap-2 px-3 py-2 text-left text-gray-700 hover:bg-gray-50 dark:text-stone-300 dark:hover:bg-white/5"
                          >
                            <Move className="h-4 w-4 text-gray-400 dark:text-stone-500" />
                            Organizar mesas
                          </button>
                        )}
                      </motion.div>
                    )}
                  </AnimatePresence>

                  {createPortal(
                    <AnimatePresence>
                      {isMoreMenuOpen && (
                        <motion.div
                          initial={{ opacity: 0 }}
                          animate={{ opacity: 1 }}
                          exit={{ opacity: 0 }}
                          transition={{ duration: 0.15 }}
                          className="fixed inset-0 z-40 flex items-end bg-black/30 sm:hidden"
                          onClick={() => setIsMoreMenuOpen(false)}
                        >
                          <motion.div
                            initial={{ y: '100%' }}
                            animate={{ y: 0 }}
                            exit={{ y: '100%' }}
                            transition={{ duration: 0.2, ease: 'easeOut' }}
                            className="w-full overflow-hidden rounded-t-2xl bg-white pb-[env(safe-area-inset-bottom)] shadow-lg dark:bg-stone-900"
                            onClick={(e) => e.stopPropagation()}
                          >
                            <div className="border-b border-gray-100 px-4 py-3 text-sm font-semibold text-gray-800 dark:border-white/10 dark:text-white">
                              Mais ações
                            </div>
                            <div className="py-1">
                              <button
                                type="button"
                                onClick={() => {
                                  openBulkForm()
                                  setIsMoreMenuOpen(false)
                                }}
                                className="flex w-full items-center gap-2.5 px-4 py-3.5 text-left text-base text-gray-700 dark:text-stone-300"
                              >
                                <Layers className="h-5 w-5 shrink-0 text-gray-400 dark:text-stone-500" />
                                Criar em lote
                              </button>
                              <button
                                type="button"
                                onClick={() => {
                                  openCreateForm()
                                  setIsMoreMenuOpen(false)
                                }}
                                className="flex w-full items-center gap-2.5 px-4 py-3.5 text-left text-base text-gray-700 dark:text-stone-300"
                              >
                                <Plus className="h-5 w-5 shrink-0 text-gray-400 dark:text-stone-500" />
                                Nova mesa
                              </button>
                              {areas && areas.length > 0 && (
                                <button
                                  type="button"
                                  onClick={() => {
                                    setIsOrganizingAreas(true)
                                    setIsMoreMenuOpen(false)
                                  }}
                                  className="flex w-full items-center gap-2.5 px-4 py-3.5 text-left text-base text-gray-700 dark:text-stone-300"
                                >
                                  <Move className="h-5 w-5 shrink-0 text-gray-400 dark:text-stone-500" />
                                  Organizar mesas
                                </button>
                              )}
                            </div>
                          </motion.div>
                        </motion.div>
                      )}
                    </AnimatePresence>,
                    document.body,
                  )}
                </div>
              )}
            </div>
          </div>
        )}
      </div>

      {tables && tables.length > 0 && (
        <div className="mb-6 flex flex-wrap gap-3">
          <StatPill icon={CheckCircle2} label="livres" value={statusCounts.FREE} badgeClassName={STATUS_BADGE_STYLES.FREE} />
          <StatPill icon={Flame} label="ocupadas" value={statusCounts.OCCUPIED} badgeClassName={STATUS_BADGE_STYLES.OCCUPIED} />
          {statusCounts.CLOSING > 0 && (
            <StatPill icon={Clock} label="fechando" value={statusCounts.CLOSING} badgeClassName={STATUS_BADGE_STYLES.CLOSING} />
          )}
          {statusCounts.RESERVED > 0 && (
            <StatPill icon={CalendarClock} label="reservadas" value={statusCounts.RESERVED} badgeClassName={STATUS_BADGE_STYLES.RESERVED} />
          )}
        </div>
      )}


      {isOrganizingAreas && (
        <div className="mb-4 flex flex-col gap-3 rounded-2xl border border-brand-200 bg-brand-50 px-5 py-4 text-sm shadow-sm sm:flex-row sm:items-center sm:justify-between dark:border-brand-500/30 dark:bg-brand-500/10">
          <span className="flex items-center gap-2 text-brand-800 dark:text-brand-400">
            <Move className="h-4 w-4" />
            Arraste uma mesa ou use o menu no card pra escolher a área. A mudança é salva na hora.
          </span>
          <Button type="button" onClick={() => setIsOrganizingAreas(false)}>
            Concluir
          </Button>
        </div>
      )}

      {error && !selectedTable && <p className="mb-4 text-sm text-wine-600 dark:text-wine-400">{error}</p>}

      {canOpenTab && counterTabs.length > 0 && (
        <div className="mb-6">
          <p className="mb-2 text-xs font-semibold tracking-wide text-gray-400 uppercase dark:text-stone-500">
            Comandas de balcão abertas
          </p>
          <div className="flex flex-wrap gap-2">
            {counterTabs.map((tab) => (
              <button
                key={tab.id}
                type="button"
                onClick={() => navigate(`/tabs/${tab.id}`)}
                className="flex items-center gap-2.5 rounded-full border border-gray-200 bg-white py-2 pl-2 pr-4 text-sm font-medium text-gray-700 shadow-sm transition-all hover:-translate-y-0.5 hover:shadow-md dark:border-white/10 dark:bg-stone-900 dark:text-stone-300"
              >
                <span className="flex h-7 w-7 items-center justify-center rounded-full bg-brand-100 text-brand-600 dark:bg-brand-500/20 dark:text-brand-400">
                  <Coffee className="h-3.5 w-3.5" />
                </span>
                Balcão · aberta às{' '}
                {new Date(tab.openedAt).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}
                <span className="text-gray-400 dark:text-stone-500">· há {minutesSince(tab.openedAt)} min</span>
              </button>
            ))}
          </div>
        </div>
      )}

      {isLoading && <p className="text-sm text-gray-500 dark:text-stone-400">Carregando...</p>}

      {tables && tables.length === 0 && (
        <div className="flex flex-col items-center gap-3 rounded-2xl border border-dashed border-gray-300 bg-gray-50 py-16 text-center dark:border-white/10 dark:bg-white/5">
          <span className="flex h-14 w-14 items-center justify-center rounded-full bg-white text-gray-300 shadow-sm dark:bg-stone-800 dark:text-stone-600">
            <UtensilsCrossed className="h-7 w-7" />
          </span>
          <p className="text-sm text-gray-500 dark:text-stone-400">Nenhuma mesa cadastrada ainda.</p>
          {canManage && (
            <Button type="button" onClick={openCreateForm} className="mt-1">
              <Plus className="h-4 w-4" />
              Criar primeira mesa
            </Button>
          )}
        </div>
      )}

      {tables && tables.length > 0 && (
        <div className="space-y-6">
          {displayGroups.map((group, groupIndex) => (
            <Fragment key={group.id}>
              {groupIndex > 0 && showGroupHeaders && <hr className="border-gray-200 dark:border-white/10" />}
              <div
                ref={(el) => {
                  if (el) groupRefs.current.set(group.id, el)
                  else groupRefs.current.delete(group.id)
                }}
                className={`rounded-xl transition-colors ${
                  isOrganizingAreas && dragHoverGroupId === group.id
                    ? 'bg-brand-50 ring-2 ring-brand-400 dark:bg-brand-500/10'
                    : ''
                }`}
              >
              {(showGroupHeaders || isOrganizingAreas) && (
                <div className="mb-3 flex items-center gap-2 px-1">
                  <span className="h-1.5 w-1.5 rounded-full bg-brand-500" />
                  <p className="text-xs font-bold tracking-wider text-gray-500 uppercase dark:text-stone-400">
                    {group.name}
                  </p>
                  <span className="text-[11px] font-medium text-gray-400 dark:text-stone-600">({group.tables.length})</span>
                </div>
              )}
              {isOrganizingAreas && group.tables.length === 0 ? (
                <div className="flex items-center justify-center rounded-xl border border-dashed border-gray-300 py-6 text-xs text-gray-400 dark:border-white/10 dark:text-stone-500">
                  Arraste mesas aqui
                </div>
              ) : (
                <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 sm:gap-4 md:grid-cols-4 lg:grid-cols-6">
                  {group.tables.map((table) => {
                  const isSelected = selectedTableIds.has(table.id)
                  const isSelectableNow = !isSelectingTables || table.status === 'FREE'
                  const tab = tableTabMap.get(table.id)
                  const StatusIcon = STATUS_ICONS[table.status]
                  const tableRequests = pendingRequestsByTable.get(table.id) ?? []
                  const { level: delayLevel, minutesWithoutOrder } = getTableDelay(tab, table.status)
                  const canDrag = isOrganizingAreas && canManage
                  return (
                    <motion.div
                      key={table.id}
                      className="relative h-full"
                      layout={canDrag}
                      drag={canDrag}
                      dragSnapToOrigin
                      dragMomentum={false}
                      whileDrag={{ scale: 1.05, zIndex: 50 }}
                      onDrag={(_, info) => setDragHoverGroupId(findGroupAtPoint(info.point.x, info.point.y))}
                      onDragEnd={(_, info) => handleTableDragEnd(table, info)}
                    >
                      {canDrag ? (
                        <div
                          className={`flex h-full w-full flex-col items-center justify-center gap-2 rounded-2xl border-2 p-3 text-center shadow-sm ${STATUS_CARD_STYLES[table.status]} ${!table.active ? 'opacity-50 grayscale' : ''}`}
                        >
                          <span className={`flex h-8 w-8 items-center justify-center rounded-full ${STATUS_BADGE_STYLES[table.status]}`}>
                            <StatusIcon className="h-4 w-4" />
                          </span>
                          <div className="text-xl font-extrabold leading-none text-gray-900 dark:text-white">{table.number}</div>
                          <div className="mt-1 w-full" onPointerDown={(e) => e.stopPropagation()} onClick={(e) => e.stopPropagation()}>
                            <Dropdown
                              compact
                              value={table.areaId ?? 'none'}
                              options={areaMoveOptions}
                              onChange={(targetGroupId) => moveTableToArea(table, targetGroupId)}
                              panelClassName="w-36"
                              mobileTitle={`Mesa ${table.number} · Escolher área`}
                            />
                          </div>
                        </div>
                      ) : (
                        <button
                          type="button"
                          disabled={(!canChangeStatus && !canManage && !canOpenTab) || !isSelectableNow}
                          onClick={() => handleTableClick(table)}
                          className={`group flex h-full w-full flex-col items-center justify-center gap-2 rounded-2xl border-2 p-4 text-center shadow-sm transition-all hover:-translate-y-1 hover:shadow-lg active:scale-[0.97] disabled:cursor-default disabled:opacity-40 disabled:hover:translate-y-0 disabled:hover:shadow-sm ${STATUS_CARD_STYLES[table.status]} ${!table.active ? 'opacity-50 grayscale' : ''} ${isSelected ? 'ring-2 ring-brand-500 ring-offset-2' : ''}`}
                        >
                          <span className={`flex h-9 w-9 items-center justify-center rounded-full transition-transform group-hover:scale-105 ${STATUS_BADGE_STYLES[table.status]}`}>
                            <StatusIcon className="h-5 w-5" />
                          </span>
                          <div className="text-2xl font-extrabold leading-none text-gray-900 dark:text-white">{table.number}</div>
                          <div className={`text-[10px] font-bold tracking-wider uppercase ${STATUS_LABEL_STYLES[table.status]}`}>
                            {STATUS_LABELS[table.status]}
                          </div>
                          {tab && table.status === 'OCCUPIED' && (
                            delayLevel !== 'none' ? (
                              <div
                                className={`flex items-center gap-1 text-[11px] font-semibold ${
                                  delayLevel === 'critical'
                                    ? 'text-red-900 dark:text-red-300'
                                    : 'text-amber-900 dark:text-amber-300'
                                }`}
                              >
                                <AlertTriangle className="h-3 w-3" />
                                sem pedido há {minutesWithoutOrder} min
                              </div>
                            ) : (
                              <div className="text-[11px] opacity-75">há {minutesSince(tab.openedAt)} min</div>
                            )
                          )}
                          {!table.active && <div className="text-[11px]">Inativa</div>}
                        </button>
                      )}
                      {!isOrganizingAreas && tableRequests.length > 0 && (
                        <div className="absolute -right-2 -top-2 z-10 flex flex-col items-end gap-1">
                          {tableRequests.map((request) => {
                            const RequestIcon = TABLE_REQUEST_ICONS[request.type]
                            return (
                              <button
                                key={request.id}
                                type="button"
                                onClick={(e) => {
                                  e.stopPropagation()
                                  acknowledgeMutation.mutate(request.id)
                                }}
                                title="Marcar como atendido"
                                className="flex animate-pulse items-center gap-1 rounded-full border border-amber-300 bg-amber-100 px-2 py-1 text-[10px] font-semibold text-amber-800 shadow hover:bg-amber-200"
                              >
                                <RequestIcon className="h-3 w-3" />
                                {TABLE_REQUEST_LABELS[request.type]}
                              </button>
                            )
                          })}
                        </div>
                      )}
                    </motion.div>
                  )
                })}
              </div>
              )}
              </div>
            </Fragment>
          ))}
        </div>
      )}

      {isCreating && (
        <Modal title="Nova mesa" onClose={() => setIsCreating(false)}>
          <form onSubmit={handleCreateSubmit}>
            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="tableNumber">
              Número <span className="font-normal text-gray-400 dark:text-stone-500">(opcional, automático se vazio)</span>
            </label>
            <input
              id="tableNumber"
              type="number"
              min="1"
              value={numberInput}
              onChange={(e) => setNumberInput(e.target.value)}
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
            />

            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="tableAreaCreate">
              Área <span className="font-normal text-gray-400 dark:text-stone-500">(opcional)</span>
            </label>
            <select
              id="tableAreaCreate"
              value={createAreaId}
              onChange={(e) => setCreateAreaId(e.target.value)}
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
            >
              <option value="">Sem área</option>
              {areas?.map((area) => (
                <option key={area.id} value={area.id}>
                  {area.name}
                </option>
              ))}
            </select>

            {error && <p className="mb-4 text-sm text-wine-600 dark:text-wine-400">{error}</p>}

            <Button type="submit" disabled={createMutation.isPending} className="w-full">
              Salvar
            </Button>
          </form>
        </Modal>
      )}

      {isBulkCreating && (
        <Modal title="Criar mesas em lote" onClose={() => setIsBulkCreating(false)}>
          <form onSubmit={handleBulkSubmit}>
            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="quantity">
              Quantidade
            </label>
            <input
              id="quantity"
              type="number"
              required
              min="1"
              max="200"
              value={quantityInput}
              onChange={(e) => setQuantityInput(e.target.value)}
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
            />
            <p className="mb-4 text-xs text-gray-500 dark:text-stone-400">
              As mesas serão numeradas em sequência, a partir do próximo número disponível.
            </p>

            {error && <p className="mb-4 text-sm text-wine-600 dark:text-wine-400">{error}</p>}

            <Button type="submit" disabled={bulkMutation.isPending} className="w-full">
              Criar
            </Button>
          </form>
        </Modal>
      )}

      <AnimatePresence>
        {selectedTable && (
          <>
            <motion.div
              key="table-panel-backdrop"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              onClick={closeTableModal}
              className="fixed inset-0 z-30 bg-black/30 sm:hidden"
            />
            <motion.aside
              key="table-panel"
              initial={{ x: '100%' }}
              animate={{ x: 0 }}
              exit={{ x: '100%' }}
              transition={{ type: 'spring', stiffness: 380, damping: 38 }}
              className="fixed inset-y-0 right-0 z-40 flex w-full flex-col border-l border-gray-200 bg-white shadow-2xl dark:border-white/10 dark:bg-stone-900 sm:w-[420px]"
            >
              <div className="flex items-center justify-between gap-3 border-b border-gray-100 px-6 py-5 dark:border-white/10">
                <div className="flex items-center gap-3">
                  <span
                    className={`flex h-12 w-12 items-center justify-center rounded-2xl text-lg font-extrabold ${STATUS_BADGE_STYLES[selectedTable.status]}`}
                  >
                    {selectedTable.number}
                  </span>
                  <div>
                    <p className="text-base font-bold text-gray-900 dark:text-white">Mesa {selectedTable.number}</p>
                    <p className={`text-xs font-bold tracking-wide uppercase ${STATUS_LABEL_STYLES[selectedTable.status]}`}>
                      {STATUS_LABELS[selectedTable.status]}
                    </p>
                  </div>
                </div>
                <button
                  type="button"
                  onClick={closeTableModal}
                  aria-label="Fechar"
                  className="rounded-lg p-2 text-gray-400 hover:bg-gray-100 hover:text-gray-600 dark:text-stone-500 dark:hover:bg-white/5 dark:hover:text-stone-300"
                >
                  <X className="h-5 w-5" />
                </button>
              </div>

              <div className="flex-1 overflow-y-auto px-6 py-5">
                <form onSubmit={handleTableSubmit}>
                  <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="editNumber">
                    Número
                  </label>
                  <input
                    id="editNumber"
                    type="number"
                    min="1"
                    disabled={!canManage}
                    value={editNumber}
                    onChange={(e) => setEditNumber(e.target.value)}
                    className="mb-4 w-full rounded-xl border border-gray-200 px-3 py-2.5 text-sm focus:border-brand-500 focus:outline-none disabled:bg-gray-50 disabled:text-gray-500 dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400 dark:disabled:bg-white/5 dark:disabled:text-stone-500"
                  />

                  <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="editStatus">
                    Status
                  </label>
                  <select
                    id="editStatus"
                    disabled={!canChangeStatus}
                    value={editStatus}
                    onChange={(e) => setEditStatus(e.target.value as TableStatus)}
                    className="mb-4 w-full rounded-xl border border-gray-200 px-3 py-2.5 text-sm focus:border-brand-500 focus:outline-none disabled:bg-gray-50 disabled:text-gray-500 dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400 dark:disabled:bg-white/5 dark:disabled:text-stone-500"
                  >
                    {EDITABLE_STATUSES.map((status) => (
                      <option key={status} value={status}>
                        {STATUS_LABELS[status]}
                      </option>
                    ))}
                  </select>

                  <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="editArea">
                    Área
                  </label>
                  <select
                    id="editArea"
                    disabled={!canManage}
                    value={editAreaId}
                    onChange={(e) => setEditAreaId(e.target.value)}
                    className="mb-4 w-full rounded-xl border border-gray-200 px-3 py-2.5 text-sm focus:border-brand-500 focus:outline-none disabled:bg-gray-50 disabled:text-gray-500 dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400 dark:disabled:bg-white/5 dark:disabled:text-stone-500"
                  >
                    <option value="">Sem área</option>
                    {areas?.map((area) => (
                      <option key={area.id} value={area.id}>
                        {area.name}
                      </option>
                    ))}
                  </select>

                  {canManage && (
                    <label className="mb-4 flex items-center gap-2 text-sm text-gray-700 dark:text-stone-300">
                      <input
                        type="checkbox"
                        checked={editActive}
                        onChange={(e) => setEditActive(e.target.checked)}
                      />
                      Mesa ativa
                    </label>
                  )}

                  {error && <p className="mb-4 text-sm text-wine-600 dark:text-wine-400">{error}</p>}

                  {(canManage || canChangeStatus) && (
                    <Button
                      type="submit"
                      disabled={updateMutation.isPending || statusMutation.isPending}
                      className="w-full"
                    >
                      Salvar
                    </Button>
                  )}

                  {canManage && selectedTable.status === 'FREE' && (
                    <button
                      type="button"
                      onClick={handleDelete}
                      disabled={deleteMutation.isPending}
                      className="mt-2 w-full rounded-xl border border-red-200 px-3 py-2.5 text-sm font-semibold text-red-700 transition hover:bg-red-50 disabled:opacity-50 dark:border-red-500/30 dark:text-red-400 dark:hover:bg-red-500/10"
                    >
                      Excluir mesa
                    </button>
                  )}
                </form>

                {restaurant?.slug && (
                  <div className="mt-6 border-t border-gray-100 pt-6 dark:border-white/10">
                    <QrCodeCard
                      title={`Autoatendimento · Mesa ${selectedTable.number}`}
                      url={publicMenuUrl(restaurant.slug, selectedTable.id)}
                      helperText="Clique com o botão direito pra salvar e imprimir nessa mesa"
                    />
                  </div>
                )}
              </div>
            </motion.aside>
          </>
        )}
      </AnimatePresence>

      <AnimatePresence>
        {isSelectingTables && (
          <motion.div
            key="table-selection-toolbar"
            initial={{ y: 24, opacity: 0 }}
            animate={{ y: 0, opacity: 1 }}
            exit={{ y: 24, opacity: 0 }}
            transition={{ type: 'spring', stiffness: 420, damping: 34 }}
            className="fixed inset-x-0 bottom-20 z-30 flex justify-center px-4 sm:bottom-6"
          >
            <div className="flex w-full max-w-lg flex-col items-center gap-3 rounded-2xl border border-gray-200/70 bg-white/95 px-5 py-4 text-center shadow-2xl shadow-black/10 backdrop-blur-md dark:border-white/10 dark:bg-stone-900/95 sm:flex-row sm:justify-between sm:text-left">
              <div className="flex items-center gap-3">
                <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-brand-100 text-brand-700 dark:bg-brand-500/20 dark:text-brand-400">
                  <Users className="h-4 w-4" />
                </span>
                <span className="text-sm font-medium text-gray-800 dark:text-white">
                  {isMultiTableMode
                    ? `Selecione as mesas que vão juntar na comanda (${selectedTableIds.size} selecionada${selectedTableIds.size === 1 ? '' : 's'})`
                    : 'Toque numa mesa livre pra abrir a comanda'}
                </span>
              </div>
              <div className="flex shrink-0 gap-2">
                <button
                  type="button"
                  onClick={cancelSelectingTables}
                  className="rounded-full px-4 py-3 text-base font-medium text-gray-500 hover:bg-gray-100 sm:py-2 sm:text-sm dark:text-stone-400 dark:hover:bg-white/5"
                >
                  Cancelar
                </button>
                {isMultiTableMode ? (
                  <button
                    type="button"
                    onClick={confirmOpenTab}
                    disabled={selectedTableIds.size === 0 || openTabMutation.isPending}
                    className="rounded-full bg-brand-600 px-4 py-3 text-base font-medium text-white shadow-sm hover:bg-brand-700 disabled:opacity-50 sm:py-2 sm:text-sm"
                  >
                    Abrir comanda
                  </button>
                ) : (
                  <button
                    type="button"
                    onClick={enableMultiTableMode}
                    className="rounded-full border border-brand-300 bg-white px-4 py-3 text-base font-medium text-brand-700 hover:bg-brand-50 sm:py-2 sm:text-sm dark:border-brand-500/30 dark:bg-transparent dark:text-brand-400 dark:hover:bg-brand-500/10"
                  >
                    Juntar várias mesas
                  </button>
                )}
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}
