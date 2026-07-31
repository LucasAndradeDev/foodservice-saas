import { AnimatePresence, motion } from 'framer-motion'

interface ConfirmDialogProps {
  title: string
  message: string
  confirmLabel?: string
  cancelLabel?: string
  danger?: boolean
  onConfirm: () => void
  onCancel: () => void
}

export function ConfirmDialog({
  title,
  message,
  confirmLabel = 'Confirmar',
  cancelLabel = 'Voltar',
  danger = false,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  return (
    <AnimatePresence>
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        exit={{ opacity: 0 }}
        transition={{ duration: 0.15 }}
        className="fixed inset-0 z-30 flex items-end justify-center bg-black/30 sm:items-center sm:p-4"
        onClick={onCancel}
      >
        <motion.div
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: 16 }}
          transition={{ duration: 0.2, ease: [0.16, 1, 0.3, 1] }}
          className="w-full rounded-t-xl bg-white p-6 shadow-lg sm:max-w-sm sm:rounded-lg dark:bg-stone-900"
          onClick={(event) => event.stopPropagation()}
        >
          <h2 className="text-lg font-semibold text-gray-800 dark:text-white">{title}</h2>
          <p className="mt-2 text-sm text-gray-600 dark:text-stone-400">{message}</p>

          <div className="mt-6 flex justify-end gap-3">
            <button
              type="button"
              onClick={onCancel}
              className="rounded-md border border-gray-200 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 dark:border-white/10 dark:text-stone-300 dark:hover:bg-white/5"
            >
              {cancelLabel}
            </button>
            <button
              type="button"
              onClick={onConfirm}
              className={`rounded-md px-4 py-2 text-sm font-medium text-white ${
                danger ? 'bg-brand-700 hover:bg-brand-800' : 'bg-brand-600 hover:bg-brand-700'
              }`}
            >
              {confirmLabel}
            </button>
          </div>
        </motion.div>
      </motion.div>
    </AnimatePresence>
  )
}
