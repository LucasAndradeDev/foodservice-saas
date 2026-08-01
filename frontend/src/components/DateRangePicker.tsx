import { AnimatePresence, motion } from 'framer-motion'
import { Calendar, ChevronLeft, ChevronRight, X } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'

const WEEKDAY_LABELS = ['D', 'S', 'T', 'Q', 'Q', 'S', 'S']
const MONTH_LABELS = [
  'janeiro',
  'fevereiro',
  'março',
  'abril',
  'maio',
  'junho',
  'julho',
  'agosto',
  'setembro',
  'outubro',
  'novembro',
  'dezembro',
]

interface DateRangePickerProps {
  start: string
  end: string
  onChange: (range: { start: string; end: string }) => void
  /** yyyy-mm-dd — dates after this are disabled. */
  maxDate?: string
}

function parseDateInput(value: string) {
  const [year, month, day] = value.split('-').map(Number)
  return new Date(year, month - 1, day)
}

function toDateInputValue(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function formatDateDisplay(value: string) {
  const [year, month, day] = value.split('-')
  return `${day}/${month}/${year}`
}

function isSameDay(a: Date, b: Date) {
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate()
}

interface CalendarCell {
  date: Date
  inMonth: boolean
}

function buildCalendarGrid(year: number, month: number): CalendarCell[] {
  const firstWeekday = new Date(year, month, 1).getDay()
  const cells: CalendarCell[] = []
  for (let i = firstWeekday; i > 0; i--) {
    cells.push({ date: new Date(year, month, 1 - i), inMonth: false })
  }
  const daysInMonth = new Date(year, month + 1, 0).getDate()
  for (let day = 1; day <= daysInMonth; day++) {
    cells.push({ date: new Date(year, month, day), inMonth: true })
  }
  while (cells.length < 42) {
    const last = cells[cells.length - 1].date
    cells.push({ date: new Date(last.getFullYear(), last.getMonth(), last.getDate() + 1), inMonth: false })
  }
  return cells
}

function getCellClasses({
  disabled,
  inMonth,
  isSelected,
  inRange,
  isToday,
}: {
  disabled: boolean
  inMonth: boolean
  isSelected: boolean
  inRange: boolean
  isToday: boolean
}) {
  if (isSelected) return 'bg-brand-600 text-white font-semibold hover:bg-brand-600'
  if (disabled) return 'cursor-default text-gray-300 dark:text-stone-700'
  if (inRange) {
    return 'bg-brand-50 text-gray-700 hover:bg-brand-100 dark:bg-brand-500/10 dark:text-stone-200 dark:hover:bg-brand-500/20'
  }
  const base = inMonth ? 'text-gray-700 dark:text-stone-300' : 'text-gray-300 dark:text-stone-600'
  const ring = isToday ? 'ring-1 ring-inset ring-brand-400' : ''
  return `${base} hover:bg-gray-100 dark:hover:bg-white/10 ${ring}`
}

export function DateRangePicker({ start, end, onChange, maxDate }: DateRangePickerProps) {
  const [isOpen, setIsOpen] = useState(false)
  const [viewDate, setViewDate] = useState(() => parseDateInput(start))
  const [pendingStart, setPendingStart] = useState<Date | null>(null)
  const containerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!isOpen) return
    setViewDate(parseDateInput(start))
    setPendingStart(null)
    // Only reset the view when the picker opens, not on every start change.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isOpen])

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
  const startDate = parseDateInput(start)
  const endDate = parseDateInput(end)

  function isDisabled(date: Date) {
    return maxDateObj !== null && date.getTime() > maxDateObj.getTime()
  }

  function handleDayClick(date: Date) {
    if (isDisabled(date)) return
    if (!pendingStart) {
      setPendingStart(date)
      onChange({ start: toDateInputValue(date), end: toDateInputValue(date) })
      return
    }
    const range = date.getTime() < pendingStart.getTime() ? { start: date, end: pendingStart } : { start: pendingStart, end: date }
    onChange({ start: toDateInputValue(range.start), end: toDateInputValue(range.end) })
    setPendingStart(null)
    setIsOpen(false)
  }

  function handleToday() {
    const now = maxDateObj ?? new Date()
    onChange({ start: toDateInputValue(now), end: toDateInputValue(now) })
    setPendingStart(null)
    setIsOpen(false)
  }

  function shiftMonth(delta: number) {
    setViewDate((current) => new Date(current.getFullYear(), current.getMonth() + delta, 1))
  }

  const cells = buildCalendarGrid(viewDate.getFullYear(), viewDate.getMonth())
  const monthLabel = `${MONTH_LABELS[viewDate.getMonth()]} de ${viewDate.getFullYear()}`
  const triggerLabel = start === end ? formatDateDisplay(start) : `${formatDateDisplay(start)} até ${formatDateDisplay(end)}`

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
            <div key={index} className={`flex items-center justify-center py-1 font-medium text-gray-400 dark:text-stone-500 ${weekdayTextClass}`}>
              {label}
            </div>
          ))}
          {cells.map(({ date, inMonth }, index) => {
            const disabled = isDisabled(date)
            const isToday = isSameDay(date, maxDateObj ?? new Date())
            const isSelected = isSameDay(date, startDate) || isSameDay(date, endDate)
            const inRange = date.getTime() > startDate.getTime() && date.getTime() < endDate.getTime()
            return (
              <button
                key={index}
                type="button"
                disabled={disabled}
                onClick={() => handleDayClick(date)}
                className={`aspect-square w-full rounded-full transition-colors ${cellTextClass} ${getCellClasses({ disabled, inMonth, isSelected, inRange, isToday })}`}
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
    <div className="relative inline-block" ref={containerRef}>
      <button
        type="button"
        onClick={() => setIsOpen((open) => !open)}
        className="flex w-full items-center gap-2 rounded-lg border border-gray-200 bg-gray-50 px-3.5 py-2 text-sm font-medium text-gray-700 shadow-sm hover:bg-gray-100 sm:w-auto dark:border-white/10 dark:bg-white/5 dark:text-stone-300 dark:hover:bg-white/10"
      >
        <Calendar className="h-4 w-4 shrink-0 text-gray-400 dark:text-stone-500" />
        <span className="truncate">{triggerLabel}</span>
      </button>

      <AnimatePresence>
        {isOpen && (
          <motion.div
            initial={{ opacity: 0, y: -4, scale: 0.97 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -4, scale: 0.97 }}
            transition={{ duration: 0.15, ease: 'easeOut' }}
            className="absolute right-0 z-10 mt-1.5 hidden w-72 rounded-xl border border-gray-200 bg-white p-4 shadow-lg sm:block dark:border-white/10 dark:bg-stone-800"
          >
            {renderCalendar('sm')}
            <button
              type="button"
              onClick={handleToday}
              className="mt-3 w-full rounded-lg border border-gray-200 py-1.5 text-sm font-medium text-gray-600 hover:bg-gray-50 dark:border-white/10 dark:text-stone-300 dark:hover:bg-white/5"
            >
              Hoje
            </button>
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
                className="w-full overflow-y-auto rounded-t-2xl bg-white p-4 pb-[calc(env(safe-area-inset-bottom)+1rem)] shadow-lg dark:bg-stone-900"
                onClick={(e) => e.stopPropagation()}
              >
                <div className="mb-3 flex items-center justify-between">
                  <h2 className="text-sm font-semibold text-gray-800 dark:text-white">Selecionar período</h2>
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
                <button
                  type="button"
                  onClick={handleToday}
                  className="mt-4 w-full rounded-lg border border-gray-200 py-2.5 text-sm font-medium text-gray-600 hover:bg-gray-50 dark:border-white/10 dark:text-stone-300 dark:hover:bg-white/5"
                >
                  Hoje
                </button>
              </motion.div>
            </motion.div>
          )}
        </AnimatePresence>,
        document.body,
      )}
    </div>
  )
}
