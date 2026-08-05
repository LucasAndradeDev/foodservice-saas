import { AnimatePresence, motion } from 'framer-motion'
import { Calendar, ChevronLeft, ChevronRight, X } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import {
  buildCalendarGrid,
  formatDateDisplay,
  isSameDay,
  MONTH_LABELS,
  parseDateInput,
  toDateInputValue,
  WEEKDAY_LABELS,
} from '../utils/calendarGrid'

interface DatePickerProps {
  /** yyyy-mm-dd, or '' when optional and unset. */
  value: string
  onChange: (value: string) => void
  /** yyyy-mm-dd — dates after this are disabled. */
  maxDate?: string
  className?: string
  /** Shown on the trigger when value is ''. */
  placeholder?: string
  /** Shows a "Remover data" action to clear an optional date back to ''. */
  allowClear?: boolean
}

function getCellClasses({
  disabled,
  inMonth,
  isSelected,
  isToday,
}: {
  disabled: boolean
  inMonth: boolean
  isSelected: boolean
  isToday: boolean
}) {
  if (isSelected) return 'bg-brand-600 text-white font-semibold hover:bg-brand-600'
  if (disabled) return 'cursor-default text-gray-300 dark:text-stone-700'
  const base = inMonth ? 'text-gray-700 dark:text-stone-300' : 'text-gray-300 dark:text-stone-600'
  const ring = isToday ? 'ring-1 ring-inset ring-brand-400' : ''
  return `${base} hover:bg-gray-100 dark:hover:bg-white/10 ${ring}`
}

/** Single-date picker, styled to match DateRangePicker's calendar — including its mobile bottom sheet. */
export function DatePicker({ value, onChange, maxDate, className, placeholder, allowClear }: DatePickerProps) {
  const [isOpen, setIsOpen] = useState(false)
  const [viewDate, setViewDate] = useState(() => parseDateInput(value || maxDate || toDateInputValue(new Date())))
  const containerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!isOpen) return
    setViewDate(parseDateInput(value || maxDate || toDateInputValue(new Date())))
  }, [isOpen, value, maxDate])

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

  const maxDateObj = maxDate ? parseDateInput(maxDate) : null
  const selectedDate = value ? parseDateInput(value) : null

  function isDisabled(date: Date) {
    return maxDateObj !== null && date.getTime() > maxDateObj.getTime()
  }

  function handleDayClick(date: Date) {
    if (isDisabled(date)) return
    onChange(toDateInputValue(date))
    setIsOpen(false)
  }

  function handleToday() {
    const now = maxDateObj ?? new Date()
    onChange(toDateInputValue(now))
    setIsOpen(false)
  }

  function handleClear() {
    onChange('')
    setIsOpen(false)
  }

  function shiftMonth(delta: number) {
    setViewDate((current) => new Date(current.getFullYear(), current.getMonth() + delta, 1))
  }

  const cells = buildCalendarGrid(viewDate.getFullYear(), viewDate.getMonth())
  const monthLabel = `${MONTH_LABELS[viewDate.getMonth()]} de ${viewDate.getFullYear()}`

  function renderCalendar(size: 'sm' | 'lg') {
    const cellTextClass = size === 'lg' ? 'text-base' : 'text-sm'
    const gapClass = size === 'lg' ? 'gap-1.5' : 'gap-1'
    const weekdayTextClass = size === 'lg' ? 'text-sm' : 'text-xs'
    return (
      <>
        <div className="mb-3 flex items-center justify-between">
          <span className="text-sm font-semibold text-gray-800 capitalize dark:text-white">{monthLabel}</span>
          <div className="flex items-center gap-1">
            <button
              type="button"
              onClick={() => shiftMonth(-1)}
              aria-label="Mês anterior"
              className="rounded-md p-1.5 text-gray-500 hover:bg-gray-100 dark:text-stone-400 dark:hover:bg-white/10"
            >
              <ChevronLeft className="h-4 w-4" />
            </button>
            <button
              type="button"
              onClick={() => shiftMonth(1)}
              aria-label="Próximo mês"
              className="rounded-md p-1.5 text-gray-500 hover:bg-gray-100 dark:text-stone-400 dark:hover:bg-white/10"
            >
              <ChevronRight className="h-4 w-4" />
            </button>
          </div>
        </div>
        <div className={`grid grid-cols-7 ${gapClass}`}>
          {WEEKDAY_LABELS.map((label, index) => (
            <div
              key={index}
              className={`flex items-center justify-center py-1 font-medium text-gray-400 dark:text-stone-500 ${weekdayTextClass}`}
            >
              {label}
            </div>
          ))}
          {cells.map(({ date, inMonth }, index) => {
            const disabled = isDisabled(date)
            const isToday = isSameDay(date, maxDateObj ?? new Date())
            const isSelected = selectedDate !== null && isSameDay(date, selectedDate)
            return (
              <button
                key={index}
                type="button"
                disabled={disabled}
                onClick={() => handleDayClick(date)}
                className={`aspect-square w-full rounded-full transition-colors ${cellTextClass} ${getCellClasses({ disabled, inMonth, isSelected, isToday })}`}
              >
                {date.getDate()}
              </button>
            )
          })}
        </div>
      </>
    )
  }

  return (
    <div className={`relative inline-block ${className ?? ''}`} ref={containerRef}>
      <button
        type="button"
        onClick={() => setIsOpen((open) => !open)}
        className="flex w-full items-center gap-2 rounded-xl border border-gray-200 bg-white px-3.5 py-2 text-sm font-medium text-gray-700 shadow-sm hover:bg-gray-50 dark:border-white/10 dark:bg-stone-800 dark:text-stone-200 dark:hover:bg-white/5"
      >
        <Calendar className="h-4 w-4 shrink-0 text-gray-400 dark:text-stone-500" />
        <span className={value ? undefined : 'text-gray-400 dark:text-stone-500'}>
          {value ? formatDateDisplay(value) : (placeholder ?? 'Selecionar data')}
        </span>
      </button>

      <AnimatePresence>
        {isOpen && (
          <motion.div
            initial={{ opacity: 0, y: -4, scale: 0.97 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -4, scale: 0.97 }}
            transition={{ duration: 0.15, ease: 'easeOut' }}
            className="absolute left-1/2 z-20 mt-1.5 hidden w-72 -translate-x-1/2 rounded-xl border border-gray-200 bg-white p-4 shadow-lg sm:block dark:border-white/10 dark:bg-stone-800"
          >
            {renderCalendar('sm')}
            <div className="mt-3 flex gap-2">
              <button
                type="button"
                onClick={handleToday}
                className="flex-1 rounded-lg border border-gray-200 py-1.5 text-sm font-medium text-gray-600 hover:bg-gray-50 dark:border-white/10 dark:text-stone-300 dark:hover:bg-white/5"
              >
                Hoje
              </button>
              {allowClear && value && (
                <button
                  type="button"
                  onClick={handleClear}
                  className="flex-1 rounded-lg border border-gray-200 py-1.5 text-sm font-medium text-gray-600 hover:bg-gray-50 dark:border-white/10 dark:text-stone-300 dark:hover:bg-white/5"
                >
                  Remover data
                </button>
              )}
            </div>
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
                className="max-h-[75vh] w-full overflow-y-auto rounded-t-2xl bg-white p-4 pb-[calc(env(safe-area-inset-bottom)+1rem)] shadow-lg dark:bg-stone-900"
                onClick={(e) => e.stopPropagation()}
              >
                <div className="mb-3 flex items-center justify-between">
                  <h2 className="text-sm font-semibold text-gray-800 dark:text-white">Selecionar data</h2>
                  <button
                    type="button"
                    onClick={() => setIsOpen(false)}
                    aria-label="Fechar"
                    className="text-gray-400 hover:text-gray-600 dark:text-stone-500 dark:hover:text-stone-300"
                  >
                    <X className="h-5 w-5" />
                  </button>
                </div>
                {renderCalendar('lg')}
                <div className="mt-4 flex gap-2">
                  <button
                    type="button"
                    onClick={handleToday}
                    className="flex-1 rounded-lg border border-gray-200 py-2.5 text-sm font-medium text-gray-600 hover:bg-gray-50 dark:border-white/10 dark:text-stone-300 dark:hover:bg-white/5"
                  >
                    Hoje
                  </button>
                  {allowClear && value && (
                    <button
                      type="button"
                      onClick={handleClear}
                      className="flex-1 rounded-lg border border-gray-200 py-2.5 text-sm font-medium text-gray-600 hover:bg-gray-50 dark:border-white/10 dark:text-stone-300 dark:hover:bg-white/5"
                    >
                      Remover data
                    </button>
                  )}
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
