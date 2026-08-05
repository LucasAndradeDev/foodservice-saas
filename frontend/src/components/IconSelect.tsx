import { AnimatePresence, motion } from 'framer-motion'
import { Check, ChevronDown, X, type LucideIcon } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { computeAnchoredPanelPosition, type AnchoredPanelPosition } from '../utils/anchoredPanel'

export interface IconSelectOption {
  value: string
  label: string
  icon: LucideIcon
}

interface IconSelectProps {
  value: string
  options: IconSelectOption[]
  onChange: (value: string) => void
  placeholder: string
  emptyMessage?: string
  /** Heading shown in the mobile option sheet. */
  mobileTitle?: string
}

/**
 * Renders its option list in a portal — a bottom sheet on mobile, a panel anchored to the trigger's
 * measured position on desktop — instead of an absolutely-positioned panel nested under the trigger.
 * This component is used inside cards that clip their own content (`overflow-hidden`, for rounded
 * corners), where a nested panel would get cut off; a portal escapes that regardless of screen size.
 */
export function IconSelect({
  value,
  options,
  onChange,
  placeholder,
  emptyMessage = 'Nenhuma opção disponível.',
  mobileTitle,
}: IconSelectProps) {
  const [isOpen, setIsOpen] = useState(false)
  const [anchor, setAnchor] = useState<AnchoredPanelPosition | null>(null)
  const triggerRef = useRef<HTMLButtonElement>(null)
  const selected = options.find((option) => option.value === value)
  const SelectedIcon = selected?.icon

  function open() {
    const rect = triggerRef.current?.getBoundingClientRect()
    if (rect) setAnchor(computeAnchoredPanelPosition(rect, 288))
    setIsOpen(true)
  }

  useEffect(() => {
    if (!isOpen) return
    function close() {
      setIsOpen(false)
    }
    window.addEventListener('scroll', close, true)
    window.addEventListener('resize', close)
    return () => {
      window.removeEventListener('scroll', close, true)
      window.removeEventListener('resize', close)
    }
  }, [isOpen])

  function renderOptionList(size: 'sm' | 'lg') {
    const rowClass = size === 'lg' ? 'px-4 py-3.5 text-base' : 'px-3 py-2 text-sm'
    const iconClass = size === 'lg' ? 'h-5 w-5' : 'h-4 w-4'
    return (
      <>
        {options.length === 0 && <p className="px-4 py-3 text-sm text-gray-400 dark:text-stone-500">{emptyMessage}</p>}
        {options.map((option) => {
          const isSelected = option.value === value
          const Icon = option.icon
          return (
            <button
              key={option.value}
              type="button"
              onClick={() => {
                onChange(option.value)
                setIsOpen(false)
              }}
              className={`flex w-full items-center gap-2 text-left ${rowClass} ${
                isSelected
                  ? 'bg-brand-50 font-medium text-brand-700 dark:bg-brand-500/10 dark:text-brand-400'
                  : 'text-gray-700 hover:bg-gray-50 dark:text-stone-300 dark:hover:bg-white/5'
              }`}
            >
              <Icon className={`shrink-0 ${iconClass} ${isSelected ? 'text-brand-500 dark:text-brand-400' : 'text-gray-400 dark:text-stone-500'}`} />
              <span className="flex-1 truncate">{option.label}</span>
              {isSelected && <Check className={`shrink-0 ${iconClass}`} />}
            </button>
          )
        })}
      </>
    )
  }

  return (
    <div className="relative min-w-0 flex-1">
      <button
        ref={triggerRef}
        type="button"
        onClick={open}
        className="flex w-full items-center gap-2 rounded-md border border-gray-300 bg-white px-3 py-2 text-left text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:focus:border-brand-400"
      >
        {SelectedIcon && <SelectedIcon className="h-4 w-4 shrink-0 text-gray-400 dark:text-stone-500" />}
        <span className={`min-w-0 flex-1 truncate ${selected ? 'text-gray-800 dark:text-white' : 'text-gray-400 dark:text-stone-500'}`}>
          {selected ? selected.label : placeholder}
        </span>
        <ChevronDown className="h-4 w-4 shrink-0 text-gray-400 dark:text-stone-500" />
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
                className="fixed inset-0 z-30 flex items-end bg-black/30 md:hidden"
                onClick={() => setIsOpen(false)}
              >
                <motion.div
                  initial={{ y: '100%' }}
                  animate={{ y: 0 }}
                  exit={{ y: '100%' }}
                  transition={{ duration: 0.2, ease: 'easeOut' }}
                  className="max-h-[75vh] w-full overflow-y-auto rounded-t-2xl bg-white pb-[env(safe-area-inset-bottom)] shadow-lg dark:bg-stone-900"
                  onClick={(e) => e.stopPropagation()}
                >
                  <div className="sticky top-0 z-20 flex items-center justify-between border-b border-gray-100 bg-white px-4 py-3 dark:border-white/10 dark:bg-stone-900">
                    <h2 className="text-sm font-semibold text-gray-800 dark:text-white">{mobileTitle ?? placeholder}</h2>
                    <button
                      type="button"
                      onClick={() => setIsOpen(false)}
                      aria-label="Fechar"
                      className="text-gray-400 hover:text-gray-600 dark:text-stone-500 dark:hover:text-stone-300"
                    >
                      <X className="h-5 w-5" />
                    </button>
                  </div>
                  <div className="py-1">{renderOptionList('lg')}</div>
                </motion.div>
              </motion.div>
            )}
          </AnimatePresence>

          {/* Desktop: panel anchored to the trigger's measured position */}
          {isOpen && anchor && (
            <div className="fixed inset-0 z-30 hidden md:block" onClick={() => setIsOpen(false)}>
              <div
                style={{ top: anchor.top, bottom: anchor.bottom, left: anchor.left, width: anchor.width, maxHeight: anchor.maxHeight }}
                className="fixed overflow-y-auto rounded-lg border border-gray-200 bg-white py-1 shadow-lg dark:border-white/10 dark:bg-stone-800"
                onClick={(e) => e.stopPropagation()}
              >
                {renderOptionList('sm')}
              </div>
            </div>
          )}
        </>,
        document.body,
      )}
    </div>
  )
}
