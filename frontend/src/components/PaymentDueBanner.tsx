import { AnimatePresence, motion } from 'framer-motion'
import { AlertTriangle, X } from 'lucide-react'
import { useState } from 'react'
import { useAuth } from '../auth/AuthContext'
import { SupportModal } from './SupportModal'

const WARNING_THRESHOLD_DAYS = 5

function daysUntil(dateString: string): number {
  const [year, month, day] = dateString.split('-').map(Number)
  const due = new Date(year, month - 1, day)
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return Math.round((due.getTime() - today.getTime()) / 86400000)
}

export function PaymentDueBanner() {
  const { user, restaurant } = useAuth()
  const [isDismissed, setIsDismissed] = useState(false)
  const [isSupportOpen, setIsSupportOpen] = useState(false)

  const canSeeBanner = user?.role === 'OWNER' || user?.role === 'MANAGER'
  const remaining = restaurant?.paymentDueDate ? daysUntil(restaurant.paymentDueDate) : null
  const shouldShow = canSeeBanner && remaining !== null && remaining <= WARNING_THRESHOLD_DAYS && !isDismissed

  const message =
    remaining !== null && remaining < 0
      ? 'Pagamento atrasado. Entre em contato para evitar a suspensão do acesso.'
      : remaining === 0
        ? 'Seu pagamento vence hoje. Entre em contato pra evitar a suspensão do acesso.'
        : `Seu pagamento vence em ${remaining} dia${remaining === 1 ? '' : 's'}. Entre em contato pra evitar a suspensão do acesso.`

  return (
    <>
      <div className="pointer-events-none fixed inset-x-4 bottom-36 z-20 flex justify-center sm:inset-x-auto sm:right-6 sm:bottom-24 sm:justify-end">
        <AnimatePresence>
          {shouldShow && (
            <motion.div
              initial={{ opacity: 0, y: 16, scale: 0.97 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: 8, scale: 0.97 }}
              transition={{ duration: 0.2, ease: 'easeOut' }}
              className="pointer-events-auto flex w-full max-w-sm items-start gap-3 rounded-xl border border-gray-200 bg-white p-4 shadow-xl dark:border-white/10 dark:bg-stone-900"
            >
              <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-wine-100 text-wine-600 dark:bg-wine-500/15 dark:text-wine-400">
                <AlertTriangle className="h-4 w-4" />
              </span>

              <div className="min-w-0 flex-1">
                <p className="text-sm font-semibold text-gray-800 dark:text-white">Pagamento pendente</p>
                <p className="mt-0.5 text-sm text-gray-500 dark:text-stone-400">{message}</p>
                <button
                  type="button"
                  onClick={() => setIsSupportOpen(true)}
                  className="mt-3 w-full rounded-lg bg-brand-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition hover:bg-brand-700 active:scale-[0.98]"
                >
                  Fale conosco
                </button>
              </div>

              <button
                type="button"
                onClick={() => setIsDismissed(true)}
                aria-label="Fechar"
                className="shrink-0 rounded-md p-1 text-gray-400 transition hover:bg-gray-100 hover:text-gray-600 dark:text-stone-500 dark:hover:bg-white/5 dark:hover:text-stone-300"
              >
                <X className="h-4 w-4" />
              </button>
            </motion.div>
          )}
        </AnimatePresence>
      </div>

      {isSupportOpen && <SupportModal onClose={() => setIsSupportOpen(false)} />}
    </>
  )
}
