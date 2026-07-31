import { useState } from 'react'
import type { DayOfWeek, PeakHourCell } from '../../api/reports'

const DAY_ORDER: DayOfWeek[] = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']

const DAY_LABELS: Record<DayOfWeek, string> = {
  MONDAY: 'Seg',
  TUESDAY: 'Ter',
  WEDNESDAY: 'Qua',
  THURSDAY: 'Qui',
  FRIDAY: 'Sex',
  SATURDAY: 'Sáb',
  SUNDAY: 'Dom',
}

const DAY_FULL_LABELS: Record<DayOfWeek, string> = {
  MONDAY: 'Segunda-feira',
  TUESDAY: 'Terça-feira',
  WEDNESDAY: 'Quarta-feira',
  THURSDAY: 'Quinta-feira',
  FRIDAY: 'Sexta-feira',
  SATURDAY: 'Sábado',
  SUNDAY: 'Domingo',
}

const HOURS = Array.from({ length: 24 }, (_, hour) => hour)

function formatDate(isoDate: string) {
  const [year, month, day] = isoDate.split('-')
  return `${day}/${month}/${year}`
}

function sampleLabel(sampleCount: number) {
  return `${sampleCount} ${sampleCount === 1 ? 'dia' : 'dias'}`
}

// brand-50 -> brand-700, matches the app's sequential ramp (see src/index.css).
// The 2px gap between grid cells (not fill contrast) is what keeps a zero-value
// cell visibly bounded — bumping the light end to pass the contrast-vs-surface
// check on its own crushed the low end and made every cell read as "occupied."
const SCALE_START = { r: 0xfd, g: 0xf4, b: 0xf1 }
const SCALE_END = { r: 0x93, g: 0x34, b: 0x1a }

function cellColor(intensity: number) {
  const r = Math.round(SCALE_START.r + (SCALE_END.r - SCALE_START.r) * intensity)
  const g = Math.round(SCALE_START.g + (SCALE_END.g - SCALE_START.g) * intensity)
  const b = Math.round(SCALE_START.b + (SCALE_END.b - SCALE_START.b) * intensity)
  return `rgb(${r}, ${g}, ${b})`
}

interface PeakHoursHeatmapProps {
  title: string
  cells: PeakHourCell[]
  metric: 'avgOccupiedTables' | 'avgOrderCount'
  unitLabel: string
  rangeStart: string
  rangeEnd: string
}

interface HoveredCell {
  day: DayOfWeek
  hour: number
  value: number
  sampleCount: number
}

export function PeakHoursHeatmap({ title, cells, metric, unitLabel, rangeStart, rangeEnd }: PeakHoursHeatmapProps) {
  const [hovered, setHovered] = useState<HoveredCell | null>(null)
  const valueByKey = new Map(cells.map((cell) => [`${cell.dayOfWeek}-${cell.hour}`, cell[metric]]))
  const sampleCountByDay = new Map(cells.map((cell) => [cell.dayOfWeek, cell.sampleCount]))
  const maxValue = Math.max(0, ...cells.map((cell) => cell[metric]))

  return (
    <div className="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm dark:border-white/10 dark:bg-stone-900">
      <div className="border-b border-gray-100 px-4 py-3 dark:border-white/10">
        <h2 className="text-sm font-medium text-gray-700 dark:text-stone-300">{title}</h2>
        <p className="mt-0.5 text-xs text-gray-400 dark:text-stone-500">
          {rangeStart === rangeEnd ? formatDate(rangeStart) : `${formatDate(rangeStart)} a ${formatDate(rangeEnd)}`}
        </p>

        <div className="mt-3 flex items-center gap-2">
          <span className="text-xs text-gray-500 dark:text-stone-400">0</span>
          <div className="relative h-2.5 flex-1 rounded-full" style={{ background: `linear-gradient(to right, ${cellColor(0)}, ${cellColor(1)})` }}>
            {[0.25, 0.5, 0.75].map((stop) => (
              <span
                key={stop}
                className="absolute top-0 h-full w-px bg-white/70"
                style={{ left: `${stop * 100}%` }}
              />
            ))}
          </div>
          <span className="whitespace-nowrap text-xs text-gray-500 dark:text-stone-400">
            {maxValue.toFixed(1)} {unitLabel}
          </span>
        </div>

        <div className="mt-2 flex h-8 items-center rounded-lg bg-gray-50 px-2.5 text-xs text-gray-600 dark:bg-white/5 dark:text-stone-400">
          {hovered ? (
            <span>
              <span className="font-medium text-gray-800 dark:text-white">{DAY_FULL_LABELS[hovered.day]}</span>
              {`, ${hovered.hour}h — `}
              <span className="font-medium text-gray-800 dark:text-white">
                {hovered.value.toFixed(1)} {unitLabel}
              </span>
              {` · média de ${sampleLabel(hovered.sampleCount)} no período`}
            </span>
          ) : (
            <span className="text-gray-400 dark:text-stone-500">
              Passe o mouse ou navegue pelo grid para ver o detalhe de cada célula
            </span>
          )}
        </div>
      </div>

      <div className="overflow-x-auto p-3">
        <div className="grid min-w-[420px] gap-0.5" style={{ gridTemplateColumns: '2.5rem repeat(7, 1fr)' }}>
          <div />
          {DAY_ORDER.map((day) => {
            const sampleCount = sampleCountByDay.get(day) ?? 0
            return (
              <div
                key={day}
                className={`pb-1 text-center text-xs font-medium ${sampleCount === 0 ? 'text-gray-300 dark:text-stone-600' : 'text-gray-500 dark:text-stone-400'}`}
              >
                {DAY_LABELS[day]}
                <span className="block text-[10px] font-normal text-gray-400 dark:text-stone-500">({sampleCount}x)</span>
              </div>
            )
          })}

          {HOURS.map((hour) => (
            <div key={hour} className="contents">
              <div
                className={`flex items-center justify-end pr-2 text-xs text-gray-400 dark:text-stone-500 ${
                  hour % 6 === 0 ? 'pt-1.5 font-medium text-gray-500 dark:text-stone-400' : ''
                }`}
              >
                {hour}h
              </div>
              {DAY_ORDER.map((day) => {
                const value = valueByKey.get(`${day}-${hour}`) ?? 0
                const sampleCount = sampleCountByDay.get(day) ?? 0
                const intensity = maxValue > 0 ? value / maxValue : 0
                const textIsLight = intensity > 0.6
                const isHovered = hovered?.day === day && hovered?.hour === hour
                return (
                  <button
                    key={day}
                    type="button"
                    aria-label={`${DAY_FULL_LABELS[day]}, ${hour}h: ${value.toFixed(1)} ${unitLabel}, média de ${sampleLabel(sampleCount)} no período`}
                    onMouseEnter={() => setHovered({ day, hour, value, sampleCount })}
                    onMouseLeave={() => setHovered(null)}
                    onFocus={() => setHovered({ day, hour, value, sampleCount })}
                    onBlur={() => setHovered(null)}
                    className={`flex h-6 items-center justify-center rounded text-[11px] ring-1 ring-inset ring-black/5 transition-transform focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 ${
                      textIsLight ? 'text-white' : 'text-gray-700'
                    } ${isHovered ? 'z-10 scale-110 shadow-sm ring-2 ring-brand-500' : ''} ${
                      hour % 6 === 0 ? 'mt-1' : ''
                    }`}
                    style={{ backgroundColor: cellColor(intensity) }}
                  >
                    {value > 0 ? value.toFixed(1) : ''}
                  </button>
                )
              })}
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
