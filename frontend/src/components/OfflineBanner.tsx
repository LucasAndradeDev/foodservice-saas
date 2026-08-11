import { AnimatePresence, motion } from 'framer-motion'
import { WifiOff } from 'lucide-react'
import { useOnlineStatus } from '../hooks/useOnlineStatus'

export function OfflineBanner() {
  const isOnline = useOnlineStatus()

  return (
    <div className="pointer-events-none fixed inset-x-4 top-4 z-30 flex justify-center">
      <AnimatePresence>
        {!isOnline && (
          <motion.div
            initial={{ opacity: 0, y: -12, scale: 0.97 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -8, scale: 0.97 }}
            transition={{ duration: 0.2, ease: 'easeOut' }}
            className="pointer-events-auto flex items-center gap-2 rounded-full border border-wine-200 bg-wine-600 px-4 py-2 text-sm font-medium text-white shadow-xl dark:border-wine-500/30"
          >
            <WifiOff className="h-4 w-4 shrink-0" />
            Sem conexão — tentando reconectar...
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}
