import { Check, ChevronDown, type LucideIcon } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'

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
}

export function IconSelect({ value, options, onChange, placeholder, emptyMessage = 'Nenhuma opção disponível.' }: IconSelectProps) {
  const [isOpen, setIsOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)
  const selected = options.find((option) => option.value === value)
  const SelectedIcon = selected?.icon

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
    <div className="relative min-w-0 flex-1" ref={containerRef}>
      <button
        type="button"
        onClick={() => setIsOpen((open) => !open)}
        className="flex w-full items-center gap-2 rounded-md border border-gray-300 bg-white px-3 py-2 text-left text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:focus:border-brand-400"
      >
        {SelectedIcon && <SelectedIcon className="h-4 w-4 shrink-0 text-brand-500 dark:text-brand-400" />}
        <span className={`min-w-0 flex-1 truncate ${selected ? 'text-gray-800 dark:text-white' : 'text-gray-400 dark:text-stone-500'}`}>
          {selected ? selected.label : placeholder}
        </span>
        <ChevronDown className="h-4 w-4 shrink-0 text-gray-400 dark:text-stone-500" />
      </button>

      {isOpen && (
        <div className="absolute z-20 mt-1 max-h-72 w-full overflow-y-auto rounded-lg border border-gray-200 bg-white py-1 shadow-lg dark:border-white/10 dark:bg-stone-800">
          {options.length === 0 && <p className="px-3 py-2 text-sm text-gray-400 dark:text-stone-500">{emptyMessage}</p>}
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
                className={`flex w-full items-center gap-2 px-3 py-2 text-left text-sm ${
                  isSelected
                    ? 'bg-brand-50 font-medium text-brand-700 dark:bg-brand-500/10 dark:text-brand-400'
                    : 'text-gray-700 hover:bg-gray-50 dark:text-stone-300 dark:hover:bg-white/5'
                }`}
              >
                <Icon className={`h-4 w-4 shrink-0 ${isSelected ? 'text-brand-500 dark:text-brand-400' : 'text-gray-400 dark:text-stone-500'}`} />
                <span className="flex-1 truncate">{option.label}</span>
                {isSelected && <Check className="h-4 w-4 shrink-0" />}
              </button>
            )
          })}
        </div>
      )}
    </div>
  )
}
