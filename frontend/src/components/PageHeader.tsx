import type { ComponentType } from 'react'

interface PageHeaderProps {
  // Broader than LucideIcon so a page can use a custom SVG icon (e.g. DeliveryPage's
  // DeliveryRiderIcon) when lucide has nothing that reads right for the concept.
  icon: ComponentType<{ className?: string }>
  title: string
  className?: string
}

export function PageHeader({ icon: Icon, title, className = '' }: PageHeaderProps) {
  return (
    <div className={`flex min-w-0 items-center gap-3 ${className}`}>
      <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-brand-600 text-white shadow-sm">
        <Icon className="h-5 w-5" />
      </span>
      <h1 className="truncate text-lg font-bold text-gray-900 dark:text-white">{title}</h1>
    </div>
  )
}
