import { AnimatePresence, motion } from 'framer-motion'
import { Check, ChevronDown, X } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { getCategoryIcon } from '../pages/publicMenu/categoryIcons'
import { computeAnchoredPanelPosition, type AnchoredPanelPosition } from '../utils/anchoredPanel'

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
  /** Heading shown in the mobile option sheet. */
  mobileTitle?: string
}

/**
 * Renders its option list in a portal — a bottom sheet on mobile, a panel anchored to the trigger's
 * measured position on desktop — instead of an absolutely-positioned panel nested under the trigger.
 * This component is used inside cards that clip their own content (`overflow-hidden`, for rounded
 * corners), where a nested panel would get cut off; a portal escapes that regardless of screen size.
 */
export function GroupedSelect({
  value,
  groups,
  onChange,
  placeholder,
  emptyMessage = 'Nenhuma opção disponível.',
  mobileTitle,
}: GroupedSelectProps) {
  const [isOpen, setIsOpen] = useState(false)
  const [anchor, setAnchor] = useState<AnchoredPanelPosition | null>(null)
  const triggerRef = useRef<HTMLButtonElement>(null)
  const selectedGroup = groups.find((group) => group.options.some((option) => option.value === value))
  const selected = selectedGroup?.options.find((option) => option.value === value)
  const SelectedIcon = selectedGroup ? getCategoryIcon(selectedGroup.label) : null

  function open() {
    const rect = triggerRef.current?.getBoundingClientRect()
    if (rect) setAnchor(computeAnchoredPanelPosition(rect, 320))
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
    const rowClass = size === 'lg' ? 'px-4 py-3.5 pl-10 text-base' : 'px-3 py-2 pl-8 text-sm'
    const headerClass = size === 'lg' ? 'px-4 py-2 text-sm' : 'px-3 py-1.5 text-xs'
    const headerTop = size === 'lg' ? 'top-[49px]' : 'top-0'
    return (
      <>
        {groups.length === 0 && <p className="px-4 py-3 text-sm text-gray-400 dark:text-stone-500">{emptyMessage}</p>}
        {groups.map((group, groupIndex) => {
          const GroupIcon = getCategoryIcon(group.label)
          return (
            <div key={group.label} className={groupIndex > 0 ? 'border-t border-gray-100 dark:border-white/10' : undefined}>
              <div
                className={`sticky ${headerTop} z-10 flex items-center gap-1.5 bg-gray-50/95 font-semibold tracking-wide text-gray-500 uppercase backdrop-blur-sm dark:bg-stone-800/95 dark:text-stone-400 ${headerClass}`}
              >
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
                    className={`flex w-full items-center justify-between gap-2 text-left ${rowClass} ${
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
