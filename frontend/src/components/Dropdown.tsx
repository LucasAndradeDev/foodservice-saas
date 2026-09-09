import { AnimatePresence, motion } from 'framer-motion'
import { Check, ChevronDown, type LucideIcon } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { computeAnchoredPanelPosition, type AnchoredPanelPosition } from '../utils/anchoredPanel'

export interface DropdownOption<T extends string> {
  value: T
  label: string
  icon?: LucideIcon
}

interface DropdownProps<T extends string> {
  value: T
  options: DropdownOption<T>[]
  onChange: (value: T) => void
  icon?: LucideIcon
  panelClassName?: string
  /** Smaller trigger/panel, for embedding in tight spaces like cards. */
  compact?: boolean
  /** Trigger spans the full width of its container instead of shrinking to content — for form fields. */
  fullWidth?: boolean
  /** Heading shown in the full-screen mobile sheet. */
  mobileTitle?: string
  disabled?: boolean
  /** Extra classes appended to the trigger button, e.g. to bump touch-target size on mobile. */
  className?: string
}

/**
 * Renders its option list in a portal — a bottom sheet on mobile, a panel anchored to the trigger's
 * measured position on desktop — instead of an absolutely-positioned panel nested under the trigger.
 * That matters for triggers embedded in a scrolling container (a modal body, a card with overflow
 * clipping): a nested panel gets cut off by the ancestor's overflow box, while a portal escapes it.
 */
export function Dropdown<T extends string>({
  value,
  options,
  onChange,
  icon: Icon,
  panelClassName = 'w-48',
  compact = false,
  fullWidth = false,
  mobileTitle,
  disabled = false,
  className = '',
}: DropdownProps<T>) {
  const [isOpen, setIsOpen] = useState(false)
  const [anchor, setAnchor] = useState<AnchoredPanelPosition | null>(null)
  const triggerRef = useRef<HTMLButtonElement>(null)
  const mobilePanelRef = useRef<HTMLDivElement>(null)
  const desktopPanelRef = useRef<HTMLDivElement>(null)
  const selectedOption = options.find((option) => option.value === value) ?? options[0]
  const currentLabel = selectedOption?.label
  const TriggerIcon = selectedOption?.icon ?? Icon

  function toggle() {
    if (!isOpen) {
      const rect = triggerRef.current?.getBoundingClientRect()
      if (rect) setAnchor(computeAnchoredPanelPosition(rect, 320))
    }
    setIsOpen((open) => !open)
  }

  useEffect(() => {
    if (!isOpen) return
    function close(event: Event) {
      // Scrolling inside the option list itself also fires this window-level capture
      // listener (the portal panel is still a descendant of window in the DOM), so
      // ignore those and only close on scroll/resize happening outside the panel.
      const target = event.target
      if (target instanceof Node && (mobilePanelRef.current?.contains(target) || desktopPanelRef.current?.contains(target))) {
        return
      }
      setIsOpen(false)
    }
    window.addEventListener('scroll', close, true)
    window.addEventListener('resize', close)
    return () => {
      window.removeEventListener('scroll', close, true)
      window.removeEventListener('resize', close)
    }
  }, [isOpen])

  function renderOptions(size: 'compact' | 'normal' | 'mobile') {
    const rowClass =
      size === 'mobile' ? 'px-4 py-3.5 text-base' : size === 'compact' ? 'px-2.5 py-1.5' : 'px-3 py-2'
    const iconClass = size === 'mobile' ? 'h-5 w-5' : size === 'compact' ? 'h-3 w-3' : 'h-4 w-4'
    const checkClass = size === 'mobile' ? 'h-5 w-5' : 'h-4 w-4'
    return options.map((option) => {
      const isSelected = option.value === value
      const OptionIcon = option.icon
      return (
        <button
          key={option.value}
          type="button"
          onClick={() => {
            onChange(option.value)
            setIsOpen(false)
          }}
          className={`flex w-full items-center justify-between gap-2 text-left whitespace-nowrap ${rowClass} ${
            isSelected
              ? 'font-medium text-brand-700 dark:text-brand-400'
              : 'text-gray-700 hover:bg-gray-50 dark:text-stone-300 dark:hover:bg-white/5'
          }`}
        >
          <span className="flex min-w-0 items-center gap-2">
            {OptionIcon && <OptionIcon className={`shrink-0 text-gray-400 dark:text-stone-500 ${iconClass}`} />}
            <span className="truncate">{option.label}</span>
          </span>
          {isSelected && <Check className={`shrink-0 ${checkClass}`} />}
        </button>
      )
    })
  }

  return (
    <div className={fullWidth ? 'block w-full' : 'inline-block'}>
      <button
        ref={triggerRef}
        type="button"
        disabled={disabled}
        onClick={toggle}
        className={
          compact
            ? `flex w-full items-center gap-1 rounded-md border border-gray-200 bg-white px-2 py-1.5 text-xs font-medium text-gray-700 shadow-sm hover:bg-gray-50 disabled:cursor-default disabled:bg-gray-50 disabled:text-gray-500 disabled:hover:bg-gray-50 sm:py-1 sm:text-[11px] dark:border-white/10 dark:bg-stone-800 dark:text-stone-200 dark:hover:bg-white/10 dark:disabled:bg-white/5 dark:disabled:text-stone-500 dark:disabled:hover:bg-white/5 ${className}`
            : `flex w-full items-center gap-2 rounded-lg border border-gray-200 bg-white px-3.5 py-2 text-sm font-medium text-gray-700 shadow-sm hover:bg-gray-50 disabled:cursor-default disabled:bg-gray-50 disabled:text-gray-500 disabled:hover:bg-gray-50 dark:border-white/10 dark:bg-stone-900 dark:text-stone-300 dark:hover:bg-white/5 dark:disabled:bg-white/5 dark:disabled:text-stone-500 dark:disabled:hover:bg-white/5 ${fullWidth ? '' : 'sm:w-auto'} ${className}`
        }
      >
        {TriggerIcon && <TriggerIcon className={compact ? 'h-3 w-3 shrink-0 text-gray-400 dark:text-stone-500' : 'h-4 w-4 shrink-0 text-gray-400 dark:text-stone-500'} />}
        <span className="flex-1 truncate text-left">{currentLabel}</span>
        <motion.span animate={{ rotate: isOpen ? 180 : 0 }} transition={{ duration: 0.15 }}>
          <ChevronDown className={compact ? 'h-3 w-3 shrink-0 text-gray-400 dark:text-stone-500' : 'h-4 w-4 text-gray-400 dark:text-stone-500'} />
        </motion.span>
      </button>

      {createPortal(
        <>
          {/* Mobile: full-screen bottom sheet */}
          <AnimatePresence>
            {isOpen && (
              <motion.div
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                transition={{ duration: 0.15 }}
                className="fixed inset-0 z-30 flex items-end bg-black/30 sm:hidden"
                onClick={() => setIsOpen(false)}
              >
                <motion.div
                  ref={mobilePanelRef}
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
                  <div className="py-1">{renderOptions('mobile')}</div>
                </motion.div>
              </motion.div>
            )}
          </AnimatePresence>

          {/* Desktop: panel anchored to the trigger's measured position, escaping any clipping ancestor */}
          {isOpen && anchor && (
            <div className="fixed inset-0 z-30 hidden sm:block" onClick={() => setIsOpen(false)}>
              <motion.div
                ref={desktopPanelRef}
                initial={{ opacity: 0, y: -4, scale: 0.97 }}
                animate={{ opacity: 1, y: 0, scale: 1 }}
                exit={{ opacity: 0, y: -4, scale: 0.97 }}
                transition={{ duration: 0.15, ease: 'easeOut' }}
                style={{
                  top: anchor.top,
                  bottom: anchor.bottom,
                  left: anchor.left,
                  width: fullWidth ? anchor.width : undefined,
                  maxHeight: anchor.maxHeight,
                }}
                className={`fixed overflow-y-auto rounded-lg border border-gray-200 bg-white py-1 shadow-lg dark:border-white/10 dark:bg-stone-800 ${compact ? 'text-xs' : 'text-sm'} ${fullWidth ? '' : panelClassName}`}
                onClick={(e) => e.stopPropagation()}
              >
                {renderOptions(compact ? 'compact' : 'normal')}
              </motion.div>
            </div>
          )}
        </>,
        document.body,
      )}
    </div>
  )
}
