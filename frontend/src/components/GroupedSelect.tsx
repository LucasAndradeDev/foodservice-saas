import { Check, ChevronDown } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { getCategoryIcon } from '../pages/publicMenu/categoryIcons'

export interface GroupedSelectOption {
  value: string
  label: string
}

export interface GroupedSelectGroup {
  label: string
  options: GroupedSelectOption[]
}

interface GroupedSelectProps {
  value: string
  groups: GroupedSelectGroup[]
  onChange: (value: string) => void
  placeholder: string
  emptyMessage?: string
}

export function GroupedSelect({ value, groups, onChange, placeholder, emptyMessage = 'Nenhuma opção disponível.' }: GroupedSelectProps) {
  const [isOpen, setIsOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)
  const selectedGroup = groups.find((group) => group.options.some((option) => option.value === value))
  const selected = selectedGroup?.options.find((option) => option.value === value)
  const SelectedIcon = selectedGroup ? getCategoryIcon(selectedGroup.label) : null

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
        {SelectedIcon && <SelectedIcon className="h-4 w-4 shrink-0 text-gray-400 dark:text-stone-500" />}
        <span className={`min-w-0 flex-1 truncate ${selected ? 'text-gray-800 dark:text-white' : 'text-gray-400 dark:text-stone-500'}`}>
          {selected ? selected.label : placeholder}
        </span>
        <ChevronDown className="h-4 w-4 shrink-0 text-gray-400 dark:text-stone-500" />
      </button>

      {isOpen && (
        <div className="absolute z-20 mt-1 max-h-80 w-full overflow-y-auto rounded-lg border border-gray-200 bg-white py-1 shadow-lg dark:border-white/10 dark:bg-stone-800">
          {groups.length === 0 && <p className="px-3 py-2 text-sm text-gray-400 dark:text-stone-500">{emptyMessage}</p>}
          {groups.map((group, groupIndex) => {
            const GroupIcon = getCategoryIcon(group.label)
            return (
              <div key={group.label} className={groupIndex > 0 ? 'border-t border-gray-100 dark:border-white/10' : undefined}>
                <div className="sticky top-0 z-10 flex items-center gap-1.5 bg-gray-50/95 px-3 py-1.5 text-xs font-semibold tracking-wide text-gray-500 uppercase backdrop-blur-sm dark:bg-stone-800/95 dark:text-stone-400">
                  <GroupIcon className="h-3.5 w-3.5 shrink-0 text-brand-500 dark:text-brand-400" />
                  {group.label}
                </div>
                {group.options.map((option) => {
                  const isSelected = option.value === value
                  return (
                    <button
                      key={option.value}
                      type="button"
                      onClick={() => {
                        onChange(option.value)
                        setIsOpen(false)
                      }}
                      className={`flex w-full items-center justify-between gap-2 px-3 py-2 pl-8 text-left text-sm ${
                        isSelected
                          ? 'bg-brand-50 font-medium text-brand-700 dark:bg-brand-500/10 dark:text-brand-400'
                          : 'text-gray-700 hover:bg-gray-50 dark:text-stone-300 dark:hover:bg-white/5'
                      }`}
                    >
                      {option.label}
                      {isSelected && <Check className="h-4 w-4 shrink-0" />}
                    </button>
                  )
                })}
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
