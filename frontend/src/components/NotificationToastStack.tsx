import { AnimatePresence, motion } from 'framer-motion'
import { Bell } from 'lucide-react'

export interface ToastItem {
  id: string
  message: string
  to: string
}

interface NotificationToastStackProps {
  toasts: ToastItem[]
  onDismiss: (id: string) => void
  onNavigate: (to: string) => void
}

export function NotificationToastStack({ toasts, onDismiss, onNavigate }: NotificationToastStackProps) {
  return (
    <div className="pointer-events-none fixed inset-x-0 top-4 z-50 flex flex-col items-center gap-2 px-4 sm:inset-x-auto sm:right-4 sm:items-end">
      <AnimatePresence>
        {toasts.map((toast) => (
          <motion.button
            key={toast.id}
            type="button"
            layout
            initial={{ opacity: 0, y: -16, scale: 0.95 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -8, scale: 0.95 }}
            transition={{ duration: 0.2, ease: 'easeOut' }}
            onClick={() => {
              onNavigate(toast.to)
              onDismiss(toast.id)
            }}
            className="pointer-events-auto flex w-full max-w-xs items-center gap-2 rounded-lg border border-gray-200 bg-white px-4 py-3 text-left text-sm font-medium text-gray-800 shadow-lg hover:bg-gray-50 dark:border-white/10 dark:bg-stone-900 dark:text-white dark:hover:bg-white/5"
          >
            <Bell className="h-4 w-4 shrink-0 text-brand-600 dark:text-brand-400" />
            <span className="flex-1">{toast.message}</span>
          </motion.button>
        ))}
      </AnimatePresence>
    </div>
  )
}
