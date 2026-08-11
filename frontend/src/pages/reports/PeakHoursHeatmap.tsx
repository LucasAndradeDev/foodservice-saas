import { Flame, Moon, MousePointerClick, Sun, Sunrise, Sunset } from 'lucide-react'
import { useState } from 'react'
import type { DayOfWeek, PeakHourCell } from '../../api/reports'
import { Card } from '../../components/Card'

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

// Groups the hour axis into readable chunks instead of 24 bare rows —
// the label itself is the orientation cue, so the grid reads without instructions.
const PERIODS: Partial<Record<number, { label: string; icon: typeof Moon }>> = {
  0: { label: 'Madrugada', icon: Moon },
  6: { label: 'Manhã', icon: Sunrise },
  12: { label: 'Tarde', icon: Sun },
  18: { label: 'Noite', icon: Sunset },
}

const LEGEND_STOPS = [0, 0.25, 0.5, 0.75, 1]

function formatDate(isoDate: string) {
  const [year, month, day] = isoDate.split('-')
  return `${day}/${month}/${year}`
}

function sampleLabel(sampleCount: number) {
  return `${sampleCount} ${sampleCount === 1 ? 'dia' : 'dias'}`
}

// brand-50 -> brand-700, matches the app's sequential ramp (see src/index.css).
// The gap between grid cells (not fill contrast) is what keeps a zero-value
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

interface CellDetail {
  day: DayOfWeek
  hour: number
  value: number
  sampleCount: number
}

export function PeakHoursHeatmapSkeleton() {
  return (
    <Card>
      <div className="h-[3px] w-full rounded-t-xl bg-gray-100 dark:bg-white/5" />
      <div className="border-b border-gray-100 px-5 py-4 dark:border-white/10">
        <div className="flex items-center gap-3">
          <div className="h-9 w-9 animate-pulse rounded-lg bg-gray-100 dark:bg-white/5" />
          <div className="space-y-2">
            <div className="h-3.5 w-40 animate-pulse rounded bg-gray-100 dark:bg-white/5" />
            <div className="h-3 w-24 animate-pulse rounded bg-gray-100 dark:bg-white/5" />
          </div>
        </div>
        <div className="mt-4 h-2 w-full animate-pulse rounded-full bg-gray-100 dark:bg-white/5" />
      </div>
      <div className="grid grid-cols-8 gap-1 p-3">
        {Array.from({ length: 96 }).map((_, i) => (
          <div key={i} className="h-6 animate-pulse rounded-md bg-gray-100 dark:bg-white/5" />
        ))}
      </div>
    </Card>
  )
}

export function PeakHoursHeatmap({ title, cells, metric, unitLabel, rangeStart, rangeEnd }: PeakHoursHeatmapProps) {
  const [hovered, setHovered] = useState<CellDetail | null>(null)
  const valueByKey = new Map(cells.map((cell) => [`${cell.dayOfWeek}-${cell.hour}`, cell[metric]]))
  const sampleCountByDay = new Map(cells.map((cell) => [cell.dayOfWeek, cell.sampleCount]))
  const maxValue = Math.max(0, ...cells.map((cell) => cell[metric]))
  const hasAnySamples = cells.some((cell) => cell.sampleCount > 0)

  const peak = cells.reduce<CellDetail | null>((best, cell) => {
    const value = cell[metric]
    if (value <= 0) return best
    if (!best || value > best.value) {
      return { day: cell.dayOfWeek, hour: cell.hour, value, sampleCount: cell.sampleCount }
    }
    return best
  }, null)

  return (
    <Card className="transition-shadow duration-300 hover:shadow-md">
      <div className="h-[3px] w-full rounded-t-xl bg-gradient-to-r from-brand-400 via-brand-600 to-brand-800" />

      <div className="border-b border-gray-100 px-5 py-4 dark:border-white/10">
        <div className="flex items-start justify-between gap-3">
          <div className="flex items-start gap-3">
            <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-gradient-to-br from-brand-500 to-brand-700 text-white shadow-md shadow-brand-600/20 dark:shadow-brand-900/40">
              <Flame className="h-4.5 w-4.5" />
            </span>
            <div>
              <h2 className="text-sm font-semibold tracking-tight text-gray-900 dark:text-white">{title}</h2>
              <p className="mt-0.5 text-xs text-gray-400 dark:text-stone-500">
                {rangeStart === rangeEnd ? formatDate(rangeStart) : `${formatDate(rangeStart)} a ${formatDate(rangeEnd)}`}
              </p>
            </div>
          </div>

          {peak && (
            <div className="flex shrink-0 items-center gap-1.5 rounded-full border border-brand-100 bg-brand-50 px-2.5 py-1 shadow-sm dark:border-brand-500/20 dark:bg-brand-500/10">
              <span className="relative flex h-1.5 w-1.5">
                <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-brand-500 opacity-75" />
                <span className="relative inline-flex h-1.5 w-1.5 rounded-full bg-brand-600 dark:bg-brand-400" />
              </span>
              <span className="whitespace-nowrap text-xs font-semibold tabular-nums text-brand-700 dark:text-brand-400">
                Pico {DAY_LABELS[peak.day]}, {peak.hour}h
              </span>
            </div>
          )}
        </div>

        {hasAnySamples && (
          <>
            <p className="mt-3 text-xs text-gray-400 dark:text-stone-500">
              Quanto mais escura a célula, maior a intensidade no período. O ponto marca o horário de pico.
            </p>

            <div className="mt-2.5 flex items-center gap-2.5">
              <span className="text-[11px] font-medium text-gray-400 dark:text-stone-500">Menos</span>
              <div className="flex flex-1 items-center gap-1 rounded-full bg-gray-50 p-1 dark:bg-white/5">
                {LEGEND_STOPS.map((stop) => (
                  <span key={stop} className="h-2 flex-1 rounded-full" style={{ backgroundColor: cellColor(stop) }} />
                ))}
              </div>
              <span className="text-[11px] font-medium text-gray-400 dark:text-stone-500">Mais</span>
            </div>
          </>
        )}
      </div>

      {!hasAnySamples ? (
        <div className="flex flex-col items-center justify-center gap-2 px-6 py-12 text-center">
          <span className="flex h-10 w-10 items-center justify-center rounded-full bg-gray-100 text-gray-400 dark:bg-white/5 dark:text-stone-500">
            <Flame className="h-5 w-5" />
          </span>
          <p className="text-sm font-medium text-gray-600 dark:text-stone-300">Ainda sem dados suficientes</p>
          <p className="max-w-[22rem] text-xs text-gray-400 dark:text-stone-500">
            Nenhum registro de {unitLabel} foi encontrado no período selecionado. Tente escolher um intervalo maior.
          </p>
        </div>
      ) : (
        <div className="p-2 sm:p-3">
          <div className="grid gap-1" style={{ gridTemplateColumns: '2rem repeat(7, minmax(0, 1fr))' }}>
            <div />
            {DAY_ORDER.map((day) => {
              const sampleCount = sampleCountByDay.get(day) ?? 0
              const isDayHovered = hovered?.day === day
              return (
                <div
                  key={day}
                  className={`pb-1.5 text-center text-xs font-semibold transition-colors ${
                    sampleCount === 0
                      ? 'text-gray-300 dark:text-stone-600'
                      : isDayHovered
                        ? 'text-brand-700 dark:text-brand-400'
                        : 'text-gray-600 dark:text-stone-300'
                  }`}
                >
                  {DAY_LABELS[day]}
                  <span className="block text-[10px] font-normal text-gray-400 dark:text-stone-500">
                    {sampleCount === 0 ? 'sem dados' : `${sampleCount}x`}
                  </span>
                </div>
              )
            })}

            {HOURS.map((hour) => {
              const period = PERIODS[hour]
              const isHourHovered = hovered?.hour === hour
              return (
                <div key={hour} className="contents">
                  {period && (
                    <div
                      className={`col-span-8 flex items-center gap-1 px-0.5 pb-1 text-[10px] font-semibold uppercase tracking-wider text-gray-400 dark:text-stone-500 ${
                        hour === 0 ? 'pt-0' : 'pt-3'
                      }`}
                    >
                      <period.icon className="h-3 w-3" />
                      {period.label}
                    </div>
                  )}

                  <div
                    className={`flex items-center justify-end pr-1.5 text-[10px] tabular-nums transition-colors sm:pr-2 sm:text-xs ${
                      isHourHovered
                        ? 'font-semibold text-brand-700 dark:text-brand-400'
                        : 'text-gray-400 dark:text-stone-500'
                    }`}
                  >
                    {hour}h
                  </div>

                  {DAY_ORDER.map((day) => {
                    const value = valueByKey.get(`${day}-${hour}`) ?? 0
                    const sampleCount = sampleCountByDay.get(day) ?? 0
                    const hasSamples = sampleCount > 0
                    const intensity = maxValue > 0 ? value / maxValue : 0
                    const textIsLight = intensity > 0.6
                    const isHovered = hovered?.day === day && hovered?.hour === hour
                    const isPeak = peak?.day === day && peak?.hour === hour
                    const tooltipBelow = hour <= 2

                    return (
                      <button
                        key={day}
                        type="button"
                        aria-label={
                          hasSamples
                            ? `${DAY_FULL_LABELS[day]}, ${hour}h: ${value.toFixed(1)} ${unitLabel}, média de ${sampleLabel(sampleCount)} no período`
                            : `${DAY_FULL_LABELS[day]}, ${hour}h: sem dados coletados no período`
                        }
                        onMouseEnter={() => setHovered({ day, hour, value, sampleCount })}
                        onMouseLeave={() => setHovered(null)}
                        onFocus={() => setHovered({ day, hour, value, sampleCount })}
                        onBlur={() => setHovered(null)}
                        className={
                          hasSamples
                            ? `relative flex h-6 items-center justify-center rounded-md text-[11px] tabular-nums ring-1 ring-inset ring-black/5 transition-all duration-150 ease-out focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 ${
                                textIsLight ? 'text-white' : 'text-gray-700'
                              } ${
                                isHovered
                                  ? 'z-20 scale-110 shadow-md ring-2 ring-brand-500'
                                  : 'hover:z-10 hover:scale-105 hover:shadow-sm'
                              }`
                            : `relative flex h-6 items-center justify-center rounded-md border border-dashed text-[11px] text-gray-300 transition-colors duration-150 ease-out focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 dark:text-stone-600 ${
                                isHovered
                                  ? 'z-20 border-gray-300 bg-gray-50 dark:border-white/20 dark:bg-white/5'
                                  : 'border-gray-100 dark:border-white/10'
                              }`
                        }
                        style={hasSamples ? { backgroundColor: cellColor(intensity) } : undefined}
                      >
                        {hasSamples && value > 0 ? value.toFixed(1) : ''}

                        {isPeak && (
                          <span className="absolute -right-0.5 -top-0.5 flex h-2 w-2">
                            <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-brand-500 opacity-60" />
                            <span className="relative inline-flex h-2 w-2 rounded-full bg-white ring-2 ring-brand-600 dark:ring-brand-400" />
                          </span>
                        )}

                        {isHovered && (
                          <span
                            className={`pointer-events-none absolute left-1/2 z-30 -translate-x-1/2 animate-[tooltip-pop_140ms_ease-out] whitespace-nowrap rounded-lg bg-gray-900 px-2.5 py-1.5 text-[11px] font-medium tabular-nums text-white shadow-xl dark:bg-stone-800 ${
                              tooltipBelow ? 'top-full mt-2' : 'bottom-full mb-2'
                            }`}
                          >
                            <span
                              className={`absolute left-1/2 h-2 w-2 -translate-x-1/2 rotate-45 bg-gray-900 dark:bg-stone-800 ${
                                tooltipBelow ? '-top-1' : '-bottom-1'
                              }`}
                            />
                            {hasSamples ? `${value.toFixed(1)} ${unitLabel}` : 'Sem dados'}
                            <span className="block text-center text-[10px] font-normal text-white/70">
                              {DAY_LABELS[day]}, {hour}h
                            </span>
                          </span>
                        )}
                      </button>
                    )
                  })}
                </div>
              )
            })}
          </div>

          <div className="mt-3 flex items-center gap-2 rounded-lg bg-gray-50 px-3 py-2 dark:bg-white/[0.03]" aria-live="polite">
            {hovered ? (
              <p className="text-xs text-gray-600 dark:text-stone-400">
                <span className="font-semibold text-gray-900 dark:text-white">{DAY_FULL_LABELS[hovered.day]}</span>
                {`, ${hovered.hour}h — `}
                <span className="font-semibold tabular-nums text-brand-700 dark:text-brand-400">
                  {hovered.value.toFixed(1)} {unitLabel}
                </span>
                {hovered.sampleCount > 0 ? ` · média de ${sampleLabel(hovered.sampleCount)} no período` : ' · sem dados coletados'}
              </p>
            ) : (
              <>
                <MousePointerClick className="h-3.5 w-3.5 shrink-0 text-gray-300 dark:text-stone-600" />
                <p className="text-xs text-gray-400 dark:text-stone-500">
                  Passe o mouse ou navegue pelas células para ver o detalhe de cada horário.
                </p>
              </>
            )}
          </div>
        </div>
      )}
    </Card>
  )
}
