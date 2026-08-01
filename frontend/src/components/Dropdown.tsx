import { AnimatePresence, motion } from 'framer-motion'
import { Check, ChevronDown, type LucideIcon } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'

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
}

export function Dropdown<T extends string>({
  value,
  options,
  onChange,
  icon: Icon,
  panelClassName = 'w-48',
  compact = false,
  fullWidth = false,
  mobileTitle,
}: DropdownProps<T>) {
  const [isOpen, setIsOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)
  const selectedOption = options.find((option) => option.value === value) ?? options[0]
  const currentLabel = selectedOption?.label
  const TriggerIcon = selectedOption?.icon ?? Icon

  useEffect(() => {
    if (!isOpen) return
    function handleClickOutside(event: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setIsOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [isOpen])

  return (
    <div className="relative inline-block" ref={containerRef}>
      <button
        type="button"
        onClick={() => setIsOpen((open) => !open)}
        className={
          compact
            ? 'flex w-full items-center gap-1 rounded-md border border-gray-200 bg-white px-2 py-1.5 text-xs font-medium text-gray-700 shadow-sm hover:bg-gray-50 sm:py-1 sm:text-[11px] dark:border-white/10 dark:bg-stone-800 dark:text-stone-200 dark:hover:bg-white/10'
            : `flex w-full items-center gap-2 rounded-lg border border-gray-200 bg-white px-3.5 py-2 text-sm font-medium text-gray-700 shadow-sm hover:bg-gray-50 dark:border-white/10 dark:bg-stone-900 dark:text-stone-300 dark:hover:bg-white/5 ${fullWidth ? '' : 'sm:w-auto'}`
        }
      >
        {TriggerIcon && <TriggerIcon className={compact ? 'h-3 w-3 shrink-0 text-gray-400 dark:text-stone-500' : 'h-4 w-4 shrink-0 text-gray-400 dark:text-stone-500'} />}
        <span className="flex-1 truncate text-left">{currentLabel}</span>
        <motion.span animate={{ rotate: isOpen ? 180 : 0 }} transition={{ duration: 0.15 }}>
          <ChevronDown className={compact ? 'h-3 w-3 shrink-0 text-gray-400 dark:text-stone-500' : 'h-4 w-4 text-gray-400 dark:text-stone-500'} />
        </motion.span>
      </button>

      <AnimatePresence>
        {isOpen && (
          <motion.div
            initial={{ opacity: 0, y: -4, scale: 0.97 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -4, scale: 0.97 }}
            transition={{ duration: 0.15, ease: 'easeOut' }}
            className={`absolute z-10 mt-1.5 hidden overflow-hidden rounded-lg border border-gray-200 bg-white py-1 shadow-lg sm:block dark:border-white/10 dark:bg-stone-800 ${compact ? 'text-xs' : 'text-sm'} ${panelClassName}`}
          >
            {options.map((option) => {
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
                  className={`flex w-full items-center justify-between gap-2 text-left whitespace-nowrap ${compact ? 'px-2.5 py-1.5' : 'px-3 py-2'} ${
                    isSelected
                      ? 'font-medium text-brand-700 dark:text-brand-400'
                      : 'text-gray-700 hover:bg-gray-50 dark:text-stone-300 dark:hover:bg-white/5'
                  }`}
                >
                  <span className="flex min-w-0 items-center gap-2">
                    {OptionIcon && <OptionIcon className="h-4 w-4 shrink-0 text-gray-400 dark:text-stone-500" />}
                    <span className="truncate">{option.label}</span>
                  </span>
                  {isSelected && <Check className="h-4 w-4 shrink-0" />}
                </button>
              )
            })}
          </motion.div>
        )}
      </AnimatePresence>

      {createPortal(
        <AnimatePresence>
          {isOpen && (
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.15 }}
              className="fixed inset-0 z-20 flex items-end bg-black/30 sm:hidden"
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
                {mobileTitle && (
                  <div className="border-b border-gray-100 px-4 py-3 text-sm font-semibold text-gray-800 dark:border-white/10 dark:text-white">
                    {mobileTitle}
                  </div>
                )}
                <div className="py-1">
                  {options.map((option) => {
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
                        className={`flex w-full items-center justify-between gap-2 px-4 py-3.5 text-left text-base ${
                          isSelected
                            ? 'font-medium text-brand-700 dark:text-brand-400'
                            : 'text-gray-700 dark:text-stone-300'
                        }`}
                      >
                        <span className="flex min-w-0 items-center gap-2.5">
                          {OptionIcon && <OptionIcon className="h-5 w-5 shrink-0 text-gray-400 dark:text-stone-500" />}
                          <span className="truncate">{option.label}</span>
                        </span>
                        {isSelected && <Check className="h-5 w-5 shrink-0" />}
                      </button>
                    )
                  })}
                </div>
              </motion.div>
            </motion.div>
          )}
        </AnimatePresence>,
        document.body,
      )}
    </div>
  )
}
