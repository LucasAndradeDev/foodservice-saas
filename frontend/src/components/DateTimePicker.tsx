import { AnimatePresence, motion } from 'framer-motion'
import { CalendarClock, ChevronLeft, ChevronRight, X } from 'lucide-react'
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

interface DateTimePickerProps {
  /** '' or 'YYYY-MM-DDTHH:mm', the same shape a native datetime-local input produces. */
  value: string
  onChange: (value: string) => void
  /** 'YYYY-MM-DD' -- days before this are disabled. Defaults to today. */
  minDate?: string
  /** 'YYYY-MM-DD' -- which month the calendar opens on when value is still empty (e.g. the day
   * a caller is already browsing). Doesn't select a date by itself -- only nudges the starting
   * view so staff isn't stuck navigating months by hand to reach a day they were just looking at. */
  initialViewDate?: string
  placeholder?: string
  id?: string
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

function parseValue(value: string) {
  if (!value) return null
  const [datePart, timePart] = value.split('T')
  if (!datePart) return null
  return { date: parseDateInput(datePart), time: timePart ?? '' }
}

function combine(date: Date, time: string) {
  return `${toDateInputValue(date)}T${time}`
}

function formatDisplay(value: string) {
  const parsed = parseValue(value)
  if (!parsed) return ''
  const day = String(parsed.date.getDate()).padStart(2, '0')
  const month = String(parsed.date.getMonth() + 1).padStart(2, '0')
  const year = parsed.date.getFullYear()
  return parsed.time ? `${day}/${month}/${year} às ${parsed.time}` : `${day}/${month}/${year}`
}

function roundToNextQuarterHour(date: Date) {
  const ms = 15 * 60 * 1000
  return new Date(Math.ceil(date.getTime() / ms) * ms)
}

function toTimeValue(date: Date) {
  return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
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

export function DateTimePicker({
  value,
  onChange,
  minDate,
  initialViewDate,
  placeholder = 'Selecionar data e hora',
  id,
}: DateTimePickerProps) {
  const [isOpen, setIsOpen] = useState(false)
  const parsed = parseValue(value)
  const defaultViewDate = () => parsed?.date ?? (initialViewDate ? parseDateInput(initialViewDate) : new Date())
  const [viewDate, setViewDate] = useState(defaultViewDate)
  const [desktopPosition, setDesktopPosition] = useState({ top: 0, left: 0 })
  const containerRef = useRef<HTMLDivElement>(null)
  const triggerRef = useRef<HTMLButtonElement>(null)
  const desktopPopoverRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!isOpen) return
    setViewDate(defaultViewDate())
    // The popover is portaled to <body> with a fixed position (see below) so it isn't clipped by an
    // ancestor's overflow -- e.g. the scrollable form modal this picker normally sits inside.
    if (triggerRef.current) {
      const rect = triggerRef.current.getBoundingClientRect()
      const popoverWidth = 288 // w-72
      const left = Math.max(8, Math.min(rect.left, window.innerWidth - popoverWidth - 8))
      setDesktopPosition({ top: rect.bottom + 6, left })
    }
    // Only reset the view/position when the picker opens, not on every value change.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isOpen])

  useEffect(() => {
    if (!isOpen) return
    function handleClickOutside(event: MouseEvent) {
      const target = event.target as Node
      if (
        containerRef.current && !containerRef.current.contains(target) &&
        desktopPopoverRef.current && !desktopPopoverRef.current.contains(target)
      ) {
        setIsOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [isOpen])

  const minDateObj = parseDateInput(minDate ?? toDateInputValue(new Date()))

  function isDisabled(date: Date) {
    return date.getTime() < minDateObj.getTime()
  }

  function handleDayClick(date: Date) {
    if (isDisabled(date)) return
    const time = parsed?.time || toTimeValue(roundToNextQuarterHour(new Date()))
    onChange(combine(date, time))
  }

  function handleTimeChange(time: string) {
    const date = parsed?.date ?? viewDate
    onChange(combine(date, time))
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
            <div key={index} className={`flex items-center justify-center py-1 font-medium text-gray-400 dark:text-stone-500 ${weekdayTextClass}`}>
              {label}
            </div>
          ))}
          {cells.map(({ date, inMonth }, index) => {
            const disabled = isDisabled(date)
            const isToday = isSameDay(date, new Date())
            const isSelected = parsed !== null && isSameDay(date, parsed.date)
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
        <div className="mt-3">
          <label className="mb-1 block text-xs font-medium text-gray-500 dark:text-stone-400">Horário</label>
          <input
            type="time"
            value={parsed?.time ?? ''}
            onChange={(e) => handleTimeChange(e.target.value)}
            className="w-full rounded-lg border border-gray-200 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-900 dark:text-white"
          />
        </div>
      </>
    )
  }

  return (
    <div className="relative w-full" ref={containerRef}>
      <button
        id={id}
        ref={triggerRef}
        type="button"
        onClick={() => setIsOpen((open) => !open)}
        className="flex w-full items-center gap-2 rounded-xl border border-gray-200 px-3 py-2.5 text-left text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white"
      >
        <CalendarClock className="h-4 w-4 shrink-0 text-gray-400 dark:text-stone-500" />
        <span className={`truncate ${value ? '' : 'text-gray-400 dark:text-stone-500'}`}>
          {value ? formatDisplay(value) : placeholder}
        </span>
      </button>

      {createPortal(
        <AnimatePresence>
          {isOpen && (
            <motion.div
              ref={desktopPopoverRef}
              initial={{ opacity: 0, y: -4, scale: 0.97 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: -4, scale: 0.97 }}
              transition={{ duration: 0.15, ease: 'easeOut' }}
              style={{ top: desktopPosition.top, left: desktopPosition.left }}
              className="fixed z-50 hidden w-72 rounded-xl border border-gray-200 bg-white p-4 shadow-lg sm:block dark:border-white/10 dark:bg-stone-800"
            >
              {renderCalendar('sm')}
            </motion.div>
          )}
        </AnimatePresence>,
        document.body,
      )}

      {createPortal(
        <AnimatePresence>
          {isOpen && (
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.15 }}
              className="fixed inset-0 z-50 flex items-end bg-black/30 sm:hidden"
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
                  <h2 className="text-sm font-semibold text-gray-800 dark:text-white">Data e hora</h2>
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
                  onClick={() => setIsOpen(false)}
                  disabled={!value}
                  className="mt-4 w-full rounded-lg bg-brand-600 py-2.5 text-sm font-semibold text-white hover:bg-brand-700 disabled:opacity-50"
                >
                  Confirmar
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
