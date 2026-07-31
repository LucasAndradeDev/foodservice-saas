import { motion } from 'framer-motion'
import type { LucideIcon } from 'lucide-react'

interface EmptyStateProps {
  icon: LucideIcon
  message: string
}

export function EmptyState({ icon: Icon, message }: EmptyStateProps) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3 }}
      className="flex flex-col items-center gap-3 rounded-xl border border-dashed border-gray-200 py-12 text-center dark:border-white/10"
    >
      <motion.div
        animate={{ y: [0, -8, 0] }}
        transition={{ duration: 2.4, repeat: Infinity, ease: 'easeInOut' }}
        className="rounded-full bg-gray-50 p-4 text-gray-300 dark:bg-white/5 dark:text-stone-600"
      >
        <Icon className="h-8 w-8" />
      </motion.div>
      <p className="text-sm text-gray-500 dark:text-stone-400">{message}</p>
    </motion.div>
  )
}
