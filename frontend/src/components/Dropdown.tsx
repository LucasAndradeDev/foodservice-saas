import { AnimatePresence, motion } from 'framer-motion'
import { Check, ChevronDown, type LucideIcon } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'

export interface DropdownOption<T extends string> {
  value: T
  label: string
}

interface DropdownProps<T extends string> {
  value: T
  options: DropdownOption<T>[]
  onChange: (value: T) => void
  icon?: LucideIcon
  panelClassName?: string
}

export function Dropdown<T extends string>({ value, options, onChange, icon: Icon, panelClassName = 'w-48' }: DropdownProps<T>) {
  const [isOpen, setIsOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)
  const currentLabel = options.find((option) => option.value === value)?.label ?? options[0]?.label

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
        className="flex w-full items-center gap-2 rounded-lg border border-gray-200 bg-white px-3.5 py-2 text-sm font-medium text-gray-700 shadow-sm hover:bg-gray-50 sm:w-auto dark:border-white/10 dark:bg-stone-900 dark:text-stone-300 dark:hover:bg-white/5"
      >
        {Icon && <Icon className="h-4 w-4 text-gray-400 dark:text-stone-500" />}
        <span className="flex-1 text-left whitespace-nowrap">{currentLabel}</span>
        <motion.span animate={{ rotate: isOpen ? 180 : 0 }} transition={{ duration: 0.15 }}>
          <ChevronDown className="h-4 w-4 text-gray-400 dark:text-stone-500" />
        </motion.span>
      </button>

      <AnimatePresence>
        {isOpen && (
          <motion.div
            initial={{ opacity: 0, y: -4, scale: 0.97 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -4, scale: 0.97 }}
            transition={{ duration: 0.15, ease: 'easeOut' }}
            className={`absolute z-10 mt-1.5 overflow-hidden rounded-lg border border-gray-200 bg-white py-1 text-sm shadow-lg dark:border-white/10 dark:bg-stone-800 ${panelClassName}`}
          >
            {options.map((option) => {
              const isSelected = option.value === value
              return (
                <button
                  key={option.value}
                  type="button"
                  onClick={() => {
                    onChange(option.value)
                    setIsOpen(false)
                  }}
                  className={`flex w-full items-center justify-between gap-2 px-3 py-2 text-left whitespace-nowrap ${
                    isSelected
                      ? 'font-medium text-brand-700 dark:text-brand-400'
                      : 'text-gray-700 hover:bg-gray-50 dark:text-stone-300 dark:hover:bg-white/5'
                  }`}
                >
                  {option.label}
                  {isSelected && <Check className="h-4 w-4 shrink-0" />}
                </button>
              )
            })}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}
