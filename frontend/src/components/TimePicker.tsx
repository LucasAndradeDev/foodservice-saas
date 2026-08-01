import { AnimatePresence, motion } from 'framer-motion'
import { Check, Clock } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'

const TIME_OPTIONS = Array.from({ length: 24 * 4 }, (_, i) => {
  const hours = String(Math.floor(i / 4)).padStart(2, '0')
  const minutes = String((i % 4) * 15).padStart(2, '0')
  return `${hours}:${minutes}`
})

interface TimePickerProps {
  id?: string
  value: string
  onChange: (value: string) => void
}

export function TimePicker({ id, value, onChange }: TimePickerProps) {
  const [isOpen, setIsOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)
  const desktopListRef = useRef<HTMLDivElement>(null)
  const mobileListRef = useRef<HTMLDivElement>(null)

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

  useEffect(() => {
    if (!isOpen) return
    const timeout = window.setTimeout(() => {
      ;[desktopListRef.current, mobileListRef.current].forEach((list) => {
        list?.querySelector('[data-selected="true"]')?.scrollIntoView({ block: 'center' })
      })
    }, 0)
    return () => window.clearTimeout(timeout)
  }, [isOpen])

  function renderOptions(size: 'sm' | 'lg') {
    return TIME_OPTIONS.map((time) => {
      const isSelected = time === value
      return (
        <button
          key={time}
          type="button"
          data-selected={isSelected}
          onClick={() => {
            onChange(time)
            setIsOpen(false)
          }}
          className={`flex w-full items-center justify-between gap-2 text-left ${
            size === 'lg' ? 'px-4 py-3 text-base' : 'px-3 py-1.5 text-sm'
          } ${
            isSelected
              ? 'font-medium text-brand-700 dark:text-brand-400'
              : 'text-gray-700 hover:bg-gray-50 dark:text-stone-300 dark:hover:bg-white/5'
          }`}
        >
          {time}
          {isSelected && <Check className={size === 'lg' ? 'h-5 w-5 shrink-0' : 'h-4 w-4 shrink-0'} />}
        </button>
      )
    })
  }

  return (
    <div className="relative" ref={containerRef}>
      <button
        type="button"
        id={id}
        onClick={() => setIsOpen((open) => !open)}
        className="flex w-full items-center justify-between gap-2 rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
      >
        {value}
        <Clock className="h-4 w-4 shrink-0 text-gray-400 dark:text-stone-500" />
      </button>

      <AnimatePresence>
        {isOpen && (
          <motion.div
            initial={{ opacity: 0, y: -4, scale: 0.97 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -4, scale: 0.97 }}
            transition={{ duration: 0.15, ease: 'easeOut' }}
            ref={desktopListRef}
            className="absolute z-30 mt-1.5 hidden max-h-52 w-full overflow-y-auto rounded-lg border border-gray-200 bg-white py-1 shadow-lg sm:block dark:border-white/10 dark:bg-stone-800"
          >
            {renderOptions('sm')}
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
              className="fixed inset-0 z-40 flex items-end bg-black/30 sm:hidden"
              onClick={() => setIsOpen(false)}
            >
              <motion.div
                initial={{ y: '100%' }}
                animate={{ y: 0 }}
                exit={{ y: '100%' }}
                transition={{ duration: 0.2, ease: 'easeOut' }}
                ref={mobileListRef}
                className="max-h-[75vh] w-full overflow-y-auto rounded-t-2xl bg-white pb-[env(safe-area-inset-bottom)] shadow-lg dark:bg-stone-900"
                onClick={(e) => e.stopPropagation()}
              >
                <div className="border-b border-gray-100 px-4 py-3 text-sm font-semibold text-gray-800 dark:border-white/10 dark:text-white">
                  Selecionar horário
                </div>
                <div className="py-1">{renderOptions('lg')}</div>
              </motion.div>
            </motion.div>
          )}
        </AnimatePresence>,
        document.body,
      )}
    </div>
  )
}
