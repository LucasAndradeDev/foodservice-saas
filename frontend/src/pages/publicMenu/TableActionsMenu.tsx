import { AnimatePresence, motion } from 'framer-motion'
import {
  Ban,
  Bell,
  Check,
  ChevronLeft,
  ChevronRight,
  Clock,
  Flame,
  ListChecks,
  PackageCheck,
  QrCode,
  RotateCcw,
  Wallet,
  X,
} from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import type { TableRequestType } from '../../api/tableRequests'
import type { ItemStatus } from '../../api/orders'
import type { PublicMenuOrderItem } from '../../api/publicMenu'

interface StatusTint {
  border: string
  bg: string
  iconBg: string
  iconText: string
}

const GOLD_TINT: StatusTint = {
  border: 'border-gold-200 dark:border-gold-500/30',
  bg: 'bg-gold-100 dark:bg-gold-500/10',
  iconBg: 'bg-gold-200 dark:bg-gold-500/20',
  iconText: 'text-gold-700 dark:text-gold-400',
}

const BRAND_TINT: StatusTint = {
  border: 'border-brand-200 dark:border-brand-500/30',
  bg: 'bg-brand-50 dark:bg-brand-500/10',
  iconBg: 'bg-brand-100 dark:bg-brand-500/20',
  iconText: 'text-brand-700 dark:text-brand-400',
}

const SAGE_TINT: StatusTint = {
  border: 'border-sage-200 dark:border-sage-500/30',
  bg: 'bg-sage-100 dark:bg-sage-500/10',
  iconBg: 'bg-sage-200 dark:bg-sage-500/20',
  iconText: 'text-sage-700 dark:text-sage-400',
}

const WINE_TINT: StatusTint = {
  border: 'border-wine-200 dark:border-wine-500/30',
  bg: 'bg-wine-100 dark:bg-wine-500/10',
  iconBg: 'bg-wine-200 dark:bg-wine-500/20',
  iconText: 'text-wine-700 dark:text-wine-400',
}

const STEPS: { status: ItemStatus; label: string; icon: LucideIcon; tint: StatusTint }[] = [
  { status: 'PENDING', label: 'Recebido', icon: Clock, tint: GOLD_TINT },
  { status: 'PREPARING', label: 'Em preparo', icon: Flame, tint: GOLD_TINT },
  { status: 'READY', label: 'Pronto', icon: PackageCheck, tint: BRAND_TINT },
  { status: 'DELIVERED', label: 'Entregue', icon: Check, tint: SAGE_TINT },
]

const STEP_INDEX: Record<ItemStatus, number> = {
  PENDING: 0,
  PREPARING: 1,
  READY: 2,
  DELIVERED: 3,
  CANCELLED: -1,
}

interface TableActionsMenuProps {
  items: PublicMenuOrderItem[]
  lifted: boolean
  hasLastOrder: boolean
  onReorder: () => void
  canRequestBill: boolean
  canPayWithPix: boolean
  requestedTypes: Set<TableRequestType>
  isPending: boolean
  pendingType: TableRequestType | undefined
  onRequest: (type: TableRequestType) => void
  onPayWithPix: () => void
}

export function TableActionsMenu({
  items,
  lifted,
  hasLastOrder,
  onReorder,
  canRequestBill,
  canPayWithPix,
  requestedTypes,
  isPending,
  pendingType,
  onRequest,
  onPayWithPix,
}: TableActionsMenuProps) {
  const [isMenuOpen, setIsMenuOpen] = useState(false)
  const [isOrderDetailOpen, setIsOrderDetailOpen] = useState(false)
  const [statusToast, setStatusToast] = useState<{ label: string; icon: LucideIcon } | null>(null)
  const seenStatusesRef = useRef<Map<string, ItemStatus> | null>(null)

  const activeItems = items.filter((item) => item.status !== 'CANCELLED')
  const summaryStepIndex =
    activeItems.length > 0 ? Math.min(...activeItems.map((item) => STEP_INDEX[item.status])) : -1
  const summary = items.length > 0 ? (summaryStepIndex >= 0 ? STEPS[summaryStepIndex] : null) : null
  const OrderSummaryIcon = summary ? summary.icon : Ban
  const orderSummaryTint = summary ? summary.tint : WINE_TINT
  const orderSummaryLabel = summary ? summary.label : 'Cancelado'
  const orderSummaryCount = `${items.length} ${items.length === 1 ? 'item' : 'itens'}`

  useEffect(() => {
    if (seenStatusesRef.current === null) {
      seenStatusesRef.current = new Map(items.map((item) => [item.id, item.status]))
      return
    }
    const changedItem = items.find((item) => seenStatusesRef.current!.get(item.id) !== item.status)
    seenStatusesRef.current = new Map(items.map((item) => [item.id, item.status]))
    if (changedItem) {
      const stepIndex = STEP_INDEX[changedItem.status]
      const step = stepIndex >= 0 ? STEPS[stepIndex] : null
      setStatusToast({ label: step ? step.label : 'Cancelado', icon: step ? step.icon : Ban })
    }
  }, [items])

  useEffect(() => {
    if (!statusToast) return
    const timer = setTimeout(() => setStatusToast(null), 6000)
    return () => clearTimeout(timer)
  }, [statusToast])

  const closeAll = () => {
    setIsMenuOpen(false)
    setIsOrderDetailOpen(false)
  }

  const openFromToast = () => {
    setStatusToast(null)
    setIsMenuOpen(true)
  }

  return (
    <>
      <motion.button
        type="button"
        whileTap={{ scale: 0.96 }}
        onClick={() => setIsMenuOpen(true)}
        className={`fixed right-4 z-20 flex items-center gap-2 rounded-full bg-gradient-to-r from-brand-600 to-brand-500 py-2 pl-2 pr-4 text-sm font-semibold text-white shadow-xl shadow-brand-900/30 ring-1 ring-white/10 dark:shadow-black/50 ${
          lifted ? 'bottom-20' : 'bottom-4'
        }`}
      >
        <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-white/15">
          <ListChecks className="h-4 w-4" />
        </span>
        Ações da mesa
      </motion.button>

      <AnimatePresence>
        {statusToast && (
          <motion.button
            type="button"
            onClick={openFromToast}
            initial={{ opacity: 0, y: -12 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -12 }}
            transition={{ duration: 0.2 }}
            className="fixed inset-x-4 top-4 z-30 mx-auto flex w-fit max-w-md items-center gap-3 rounded-2xl bg-brand-600 py-3 pl-4 pr-5 text-left text-white shadow-xl shadow-brand-900/30 ring-1 ring-white/10"
          >
            <statusToast.icon className="h-5 w-5 shrink-0" />
            <span className="flex flex-col">
              <span className="text-sm font-semibold">Seu pedido foi atualizado</span>
              <span className="text-xs text-white/80">{statusToast.label} · toque para ver</span>
            </span>
          </motion.button>
        )}
      </AnimatePresence>

      <AnimatePresence>
        {(isMenuOpen || isOrderDetailOpen) && (
          <>
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.2 }}
              onClick={closeAll}
              className="fixed inset-0 z-30 bg-black/40"
            />
            <motion.div
              initial={{ y: '100%' }}
              animate={{ y: 0 }}
              exit={{ y: '100%' }}
              transition={{ duration: 0.25, ease: 'easeOut' }}
              className="fixed inset-x-0 bottom-0 z-40 max-h-[85vh] overflow-y-auto rounded-t-3xl bg-white p-4 shadow-2xl dark:bg-stone-900"
            >
              <div className="mx-auto mb-3 h-1 w-10 rounded-full bg-gray-300 dark:bg-stone-700" />

              <div className="mb-4 flex items-center justify-between">
                <div className="flex items-center gap-2">
                  {isOrderDetailOpen && (
                    <button
                      type="button"
                      onClick={() => setIsOrderDetailOpen(false)}
                      aria-label="Voltar"
                      className="text-gray-400 hover:text-gray-600 dark:text-stone-500 dark:hover:text-stone-300"
                    >
                      <ChevronLeft className="h-5 w-5" />
                    </button>
                  )}
                  <h2 className="text-lg font-semibold text-gray-800 dark:text-white">
                    {isOrderDetailOpen ? 'Seus pedidos' : 'Ações da mesa'}
                  </h2>
                </div>
                <button
                  type="button"
                  onClick={closeAll}
                  aria-label="Fechar"
                  className="text-gray-400 hover:text-gray-600 dark:text-stone-500 dark:hover:text-stone-300"
                >
                  <X className="h-5 w-5" />
                </button>
              </div>

              {isOrderDetailOpen ? (
                <>
                  {hasLastOrder && (
                    <button
                      type="button"
                      onClick={() => {
                        onReorder()
                        closeAll()
                      }}
                      className="mb-3 flex w-full items-center justify-center gap-2 rounded-xl border border-brand-600 px-3 py-2 text-sm font-medium text-brand-600 hover:bg-brand-50 dark:border-brand-400 dark:text-brand-400 dark:hover:bg-white/5"
                    >
                      <RotateCcw className="h-4 w-4" />
                      Pedir de novo
                    </button>
                  )}

                  <ul className="divide-y divide-gray-100 dark:divide-white/10">
                    {items.map((item) => (
                      <li key={item.id} className="py-3">
                        <div className="mb-2 flex items-center justify-between gap-2">
                          <span className="font-medium text-gray-800 dark:text-white">
                            {item.quantity}x {item.productName}
                          </span>
                          {item.status === 'CANCELLED' && (
                            <span className="flex items-center gap-1 rounded-full bg-wine-100 px-2 py-0.5 text-xs text-wine-700 dark:bg-wine-500/10 dark:text-wine-400">
                              <Ban className="h-3 w-3" />
                              Cancelado
                            </span>
                          )}
                        </div>
                        {item.status !== 'CANCELLED' && <ItemStepper currentIndex={STEP_INDEX[item.status]} />}
                      </li>
                    ))}
                  </ul>
                </>
              ) : (
                <div className="flex flex-col gap-5">
                  {items.length > 0 && (
                    <button
                      type="button"
                      onClick={() => setIsOrderDetailOpen(true)}
                      className={`flex items-center gap-3 rounded-2xl border px-4 py-3 text-left transition-transform active:scale-[0.98] ${orderSummaryTint.border} ${orderSummaryTint.bg}`}
                    >
                      <span
                        className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-full ${orderSummaryTint.iconBg} ${orderSummaryTint.iconText}`}
                      >
                        <OrderSummaryIcon className="h-5 w-5" />
                      </span>
                      <span className="flex-1">
                        <span className="block text-sm font-semibold text-gray-800 dark:text-white">{orderSummaryLabel}</span>
                        <span className="block text-xs text-gray-500 dark:text-stone-400">{orderSummaryCount}</span>
                      </span>
                      <ChevronRight className="h-4 w-4 shrink-0 text-gray-400 dark:text-stone-500" />
                    </button>
                  )}

                  {(canPayWithPix || canRequestBill) && (
                    <div className="flex flex-col gap-2">
                      <span className="px-1 text-xs font-semibold uppercase tracking-wide text-gray-400 dark:text-stone-500">
                        Fechar a conta
                      </span>
                      {canPayWithPix && (
                        <ActionRow
                          icon={QrCode}
                          label="Pagar com Pix"
                          subtitle="Pagamento instantâneo, sem esperar o garçom"
                          variant="primary"
                          onClick={() => {
                            onPayWithPix()
                            setIsMenuOpen(false)
                          }}
                        />
                      )}
                      {canRequestBill && (
                        <ActionRow
                          icon={Wallet}
                          label="Pedir a conta"
                          subtitle="Fechar pagando em dinheiro ou cartão"
                          confirmed={requestedTypes.has('REQUEST_BILL')}
                          confirmedLabel="Conta pedida! O garçom já foi avisado."
                          disabled={isPending && pendingType === 'REQUEST_BILL'}
                          onClick={() => onRequest('REQUEST_BILL')}
                        />
                      )}
                    </div>
                  )}

                  <div className="flex flex-col gap-2">
                    <span className="px-1 text-xs font-semibold uppercase tracking-wide text-gray-400 dark:text-stone-500">
                      Precisa de algo?
                    </span>
                    <ActionRow
                      icon={Bell}
                      label="Chamar garçom"
                      subtitle="Ele vem até a mesa"
                      confirmed={requestedTypes.has('CALL_WAITER')}
                      confirmedLabel="Garçom chamado! Ele já está a caminho."
                      disabled={isPending && pendingType === 'CALL_WAITER'}
                      onClick={() => onRequest('CALL_WAITER')}
                    />
                  </div>
                </div>
              )}
            </motion.div>
          </>
        )}
      </AnimatePresence>
    </>
  )
}

interface ActionRowProps {
  icon: LucideIcon
  label: string
  subtitle: string
  variant?: 'primary' | 'secondary'
  confirmed?: boolean
  confirmedLabel?: string
  disabled?: boolean
  onClick: () => void
}

function ActionRow({
  icon: Icon,
  label,
  subtitle,
  variant = 'secondary',
  confirmed = false,
  confirmedLabel,
  disabled = false,
  onClick,
}: ActionRowProps) {
  if (confirmed) {
    return (
      <div className="flex items-center gap-3 rounded-2xl border border-sage-600 bg-sage-100 px-4 py-3 dark:border-sage-500 dark:bg-sage-500/10">
        <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-sage-200 text-sage-700 dark:bg-sage-500/20 dark:text-sage-400">
          <Check className="h-5 w-5" />
        </span>
        <span className="text-sm font-semibold text-sage-700 dark:text-sage-400">{confirmedLabel}</span>
      </div>
    )
  }

  const isPrimary = variant === 'primary'
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className={`flex items-center gap-3 rounded-2xl border px-4 py-3 text-left transition-transform active:scale-[0.98] disabled:opacity-60 ${
        isPrimary
          ? 'border-brand-600 bg-brand-600 hover:bg-brand-700'
          : 'border-gray-200 bg-white hover:bg-gray-50 dark:border-white/10 dark:bg-stone-900 dark:hover:bg-white/5'
      }`}
    >
      <span
        className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-full ${
          isPrimary ? 'bg-white/15 text-white' : 'bg-gray-100 text-gray-600 dark:bg-white/10 dark:text-stone-300'
        }`}
      >
        <Icon className="h-5 w-5" />
      </span>
      <span className="flex-1">
        <span className={`block text-sm font-semibold ${isPrimary ? 'text-white' : 'text-gray-800 dark:text-white'}`}>
          {label}
        </span>
        <span className={`block text-xs ${isPrimary ? 'text-white/80' : 'text-gray-500 dark:text-stone-400'}`}>
          {subtitle}
        </span>
      </span>
    </button>
  )
}

function ItemStepper({ currentIndex }: { currentIndex: number }) {
  const CurrentIcon = STEPS[currentIndex].icon
  return (
    <div className="flex items-center gap-3">
      <div className="flex flex-1 items-center">
        {STEPS.map((step, index) => (
          <div key={step.status} className="flex flex-1 items-center last:flex-none">
            <div
              className={`h-2.5 w-2.5 shrink-0 rounded-full ${
                index <= currentIndex ? 'bg-brand-600 dark:bg-brand-400' : 'bg-gray-200 dark:bg-stone-700'
              }`}
            />
            {index < STEPS.length - 1 && (
              <div
                className={`h-0.5 flex-1 ${
                  index < currentIndex ? 'bg-brand-600 dark:bg-brand-400' : 'bg-gray-200 dark:bg-stone-700'
                }`}
              />
            )}
          </div>
        ))}
      </div>
      <span className="flex shrink-0 items-center gap-1 text-xs font-medium text-brand-600 dark:text-brand-400">
        <CurrentIcon className="h-3.5 w-3.5" />
        {STEPS[currentIndex].label}
      </span>
    </div>
  )
}
