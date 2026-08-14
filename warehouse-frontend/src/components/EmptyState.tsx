import type { LucideIcon } from 'lucide-react'

interface EmptyStateProps {
  icon: LucideIcon
  message: string
}

export function EmptyState({ icon: Icon, message }: EmptyStateProps) {
  return (
    <div className="flex flex-col items-center gap-3 rounded-xl border border-dashed border-gray-200 py-12 text-center dark:border-white/10">
      <div className="rounded-full bg-gray-50 p-4 text-gray-300 dark:bg-white/5 dark:text-stone-600">
        <Icon className="h-8 w-8" />
      </div>
      <p className="text-sm text-gray-500 dark:text-stone-400">{message}</p>
    </div>
  )
}
