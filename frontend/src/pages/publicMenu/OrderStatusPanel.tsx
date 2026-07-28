import { AnimatePresence, motion } from 'framer-motion'
import { Ban, Check, Clock, Flame, PackageCheck, X } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import { useState } from 'react'
import type { ItemStatus } from '../../api/orders'
import type { PublicMenuOrderItem } from '../../api/publicMenu'

const STEPS: { status: ItemStatus; label: string; icon: LucideIcon }[] = [
  { status: 'PENDING', label: 'Recebido', icon: Clock },
  { status: 'PREPARING', label: 'Em preparo', icon: Flame },
  { status: 'READY', label: 'Pronto', icon: PackageCheck },
  { status: 'DELIVERED', label: 'Entregue', icon: Check },
]

const STEP_INDEX: Record<ItemStatus, number> = {
  PENDING: 0,
  PREPARING: 1,
  READY: 2,
  DELIVERED: 3,
  CANCELLED: -1,
}

interface OrderStatusPanelProps {
  items: PublicMenuOrderItem[]
  lifted: boolean
}

export function OrderStatusPanel({ items, lifted }: OrderStatusPanelProps) {
  const [isOpen, setIsOpen] = useState(false)

  if (items.length === 0) return null

  const activeItems = items.filter((item) => item.status !== 'CANCELLED')
  const summaryStepIndex =
    activeItems.length > 0 ? Math.min(...activeItems.map((item) => STEP_INDEX[item.status])) : -1
  const summary = summaryStepIndex >= 0 ? STEPS[summaryStepIndex] : null

  return (
    <>
      <button
        type="button"
        onClick={() => setIsOpen(true)}
        className={`fixed left-4 z-20 flex items-center gap-2 rounded-full border border-brand-600 bg-white/90 px-4 py-2 text-sm font-medium text-brand-600 shadow-xl backdrop-blur-md transition-colors duration-150 dark:border-brand-400 dark:bg-stone-900/90 dark:text-brand-400 ${
          lifted ? 'bottom-20' : 'bottom-4'
        }`}
      >
        {summary ? <summary.icon className="h-4 w-4" /> : <Ban className="h-4 w-4" />}
        {summary ? summary.label : 'Cancelado'} · {items.length} {items.length === 1 ? 'item' : 'itens'}
      </button>

      <AnimatePresence>
        {isOpen && (
          <>
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.2 }}
              onClick={() => setIsOpen(false)}
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
                <h2 className="text-lg font-semibold text-gray-800 dark:text-white">Seus pedidos</h2>
                <button
                  type="button"
                  onClick={() => setIsOpen(false)}
                  aria-label="Fechar"
                  className="text-gray-400 hover:text-gray-600 dark:text-stone-500 dark:hover:text-stone-300"
                >
                  <X className="h-5 w-5" />
                </button>
              </div>

              <ul className="divide-y divide-gray-100 dark:divide-white/10">
                {items.map((item) => (
                  <li key={item.id} className="py-3">
                    <div className="mb-2 flex items-center justify-between gap-2">
                      <span className="font-medium text-gray-800 dark:text-white">
                        {item.quantity}x {item.productName}
                      </span>
                      {item.status === 'CANCELLED' && (
                        <span className="flex items-center gap-1 rounded-full bg-red-100 px-2 py-0.5 text-xs text-red-700 dark:bg-red-500/10 dark:text-red-400">
                          <Ban className="h-3 w-3" />
                          Cancelado
                        </span>
                      )}
                    </div>
                    {item.status !== 'CANCELLED' && <ItemStepper currentIndex={STEP_INDEX[item.status]} />}
                  </li>
                ))}
              </ul>
            </motion.div>
          </>
        )}
      </AnimatePresence>
    </>
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
