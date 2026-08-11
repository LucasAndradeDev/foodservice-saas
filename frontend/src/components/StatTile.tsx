import { TrendingDown, TrendingUp, type LucideIcon } from 'lucide-react'

interface StatTileProps {
  icon: LucideIcon
  label: string
  value: string
  changePercentage?: number | null
  previousValueLabel?: string
  previousRangeLabel?: string
}

export function ChangeLine({
  changePercentage,
  previousValueLabel,
  previousRangeLabel,
}: {
  changePercentage: number
  previousValueLabel: string
  previousRangeLabel: string
}) {
  const isPositive = changePercentage >= 0
  const Icon = isPositive ? TrendingUp : TrendingDown
  return (
    <div
      className={`mt-1 flex items-start gap-1 text-xs ${isPositive ? 'text-green-600 dark:text-green-400' : 'text-wine-600 dark:text-wine-400'}`}
    >
      <Icon className="mt-0.5 h-3 w-3 shrink-0" />
      <span>
        {Math.abs(changePercentage)}% vs {previousValueLabel}
        <span className="text-gray-400 dark:text-stone-500"> ({previousRangeLabel})</span>
      </span>
    </div>
  )
}

export function StatTile({ icon: Icon, label, value, changePercentage, previousValueLabel, previousRangeLabel }: StatTileProps) {
  return (
    <div className="rounded-xl border border-gray-200 bg-white dark:border-white/10 dark:bg-stone-900 p-4 shadow-sm transition-all duration-200 hover:-translate-y-0.5 hover:shadow-md">
      <div className="flex items-center gap-1.5 text-sm text-gray-500 dark:text-stone-400">
        <Icon className="h-4 w-4" />
        {label}
      </div>
      <div className="mt-1 text-3xl font-semibold text-brand-700 dark:text-brand-400">{value}</div>
      {changePercentage !== null && changePercentage !== undefined && previousValueLabel && previousRangeLabel && (
        <ChangeLine
          changePercentage={changePercentage}
          previousValueLabel={previousValueLabel}
          previousRangeLabel={previousRangeLabel}
        />
      )}
    </div>
  )
}
