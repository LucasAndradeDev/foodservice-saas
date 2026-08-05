import { AnimatePresence, motion } from 'framer-motion'
import type { LucideIcon } from 'lucide-react'
import { useEffect, useLayoutEffect, useRef, useState, type RefObject } from 'react'
import { createPortal } from 'react-dom'
import { Link } from 'react-router-dom'

export interface ActionMenuItem {
  key: string
  label: string
  icon: LucideIcon
  onClick?: () => void
  /** Renders as a router Link instead of a button. */
  to?: string
  tone?: 'default' | 'danger' | 'warning'
}

export type ActionMenuEntry = ActionMenuItem | { divider: true }

interface ActionMenuProps {
  isOpen: boolean
  onClose: () => void
  triggerRef: RefObject<HTMLElement | null>
  items: ActionMenuEntry[]
  align?: 'start' | 'end'
  /** Heading shown in the mobile bottom sheet. */
  mobileTitle?: string
  width?: number
}

const TONE_CLASSES: Record<NonNullable<ActionMenuItem['tone']>, string> = {
  default: 'text-gray-700 hover:bg-gray-50 dark:text-stone-300 dark:hover:bg-white/5',
  warning: 'text-amber-700 hover:bg-amber-50 dark:text-amber-400 dark:hover:bg-amber-500/10',
  danger: 'text-red-600 hover:bg-red-50 dark:text-red-400 dark:hover:bg-red-500/10',
}

const TONE_ICON_CLASSES: Record<NonNullable<ActionMenuItem['tone']>, string> = {
  default: 'text-gray-400 dark:text-stone-500',
  warning: 'text-amber-500 dark:text-amber-400',
  danger: 'text-red-500 dark:text-red-400',
}

/**
 * Dropdown menu with two renderings sharing the same `items`: a small anchored panel next to
 * the trigger on desktop, and a full-width bottom sheet on mobile — the pattern already used by
 * Dropdown/DateRangePicker. Handles its own positioning and outside-click/close.
 */
export function ActionMenu({ isOpen, onClose, triggerRef, items, align = 'start', mobileTitle, width = 240 }: ActionMenuProps) {
  const desktopPanelRef = useRef<HTMLDivElement>(null)
  const [position, setPosition] = useState<{ top: number; left: number } | null>(null)

  useEffect(() => {
    if (!isOpen) return
    function handleClickOutside(event: MouseEvent) {
      const target = event.target as Node
      if (triggerRef.current?.contains(target)) return
      if (desktopPanelRef.current?.contains(target)) return
      onClose()
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isOpen])

  useLayoutEffect(() => {
    if (!isOpen || !triggerRef.current) return
    const estimatedMenuHeight = items.length * 40 + 16
    const rect = triggerRef.current.getBoundingClientRect()
    const openUpward = window.innerHeight - rect.bottom < estimatedMenuHeight
    const left = align === 'end' ? rect.right - width : rect.left
    setPosition({
      top: openUpward ? rect.top - estimatedMenuHeight : rect.bottom + 4,
      left: Math.min(Math.max(left, 8), window.innerWidth - width - 8),
    })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isOpen, align])

  function runAndClose(action?: () => void) {
    action?.()
    onClose()
  }

  function renderEntries(size: 'sm' | 'lg') {
    return items.map((entry, index) => {
      if ('divider' in entry) {
        return <div key={`divider-${index}`} className="my-1 border-t border-gray-100 dark:border-white/10" />
      }
      const Icon = entry.icon
      const tone = entry.tone ?? 'default'
      const sizeClasses = size === 'lg' ? 'gap-3 px-4 py-3.5 text-base' : 'gap-2 px-3 py-2 text-sm'
      const iconSizeClasses = size === 'lg' ? 'h-5 w-5' : 'h-4 w-4'
      const className = `flex w-full items-center whitespace-nowrap text-left ${sizeClasses} ${TONE_CLASSES[tone]}`
      const content = (
        <>
          <Icon className={`shrink-0 ${iconSizeClasses} ${TONE_ICON_CLASSES[tone]}`} />
          {entry.label}
        </>
      )
      if (entry.to) {
        return (
          <Link key={entry.key} to={entry.to} onClick={onClose} className={className}>
            {content}
          </Link>
        )
      }
      return (
        <button key={entry.key} type="button" onClick={() => runAndClose(entry.onClick)} className={className}>
          {content}
        </button>
      )
    })
  }

  return (
    <>
      {isOpen &&
        position &&
        createPortal(
          <div
            ref={desktopPanelRef}
            style={{ top: position.top, left: position.left, width }}
            className="fixed z-50 hidden overflow-hidden rounded-md border border-gray-200 bg-white py-1 shadow-lg sm:block dark:border-white/10 dark:bg-stone-800"
          >
            {renderEntries('sm')}
          </div>,
          document.body,
        )}

      {createPortal(
        <AnimatePresence>
          {isOpen && (
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.15 }}
              className="fixed inset-0 z-50 flex items-end bg-black/30 sm:hidden"
              onClick={onClose}
            >
              <motion.div
                initial={{ y: '100%' }}
                animate={{ y: 0 }}
                exit={{ y: '100%' }}
                transition={{ duration: 0.2, ease: 'easeOut' }}
                className="max-h-[75vh] w-full overflow-y-auto rounded-t-2xl bg-white pb-[env(safe-area-inset-bottom)] shadow-lg dark:bg-stone-900"
                onClick={(e) => e.stopPropagation()}
              >
                {mobileTitle && (
                  <div className="border-b border-gray-100 px-4 py-3 text-sm font-semibold text-gray-800 dark:border-white/10 dark:text-white">
                    {mobileTitle}
                  </div>
                )}
                <div className="py-1">{renderEntries('lg')}</div>
              </motion.div>
            </motion.div>
          )}
        </AnimatePresence>,
        document.body,
      )}
    </>
  )
}
