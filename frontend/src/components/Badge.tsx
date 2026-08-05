import type { ReactNode } from 'react'

export type BadgeTone = 'free' | 'occupied' | 'reserved' | 'attention' | 'critical' | 'neutral'

const TONE_STYLES: Record<BadgeTone, string> = {
  free: 'bg-sage-100 text-sage-700 dark:bg-sage-500/20 dark:text-sage-400',
  occupied: 'bg-brand-100 text-brand-700 dark:bg-brand-500/20 dark:text-brand-400',
  reserved: 'bg-teal-100 text-teal-700 dark:bg-teal-500/20 dark:text-teal-400',
  attention: 'bg-gold-100 text-gold-700 dark:bg-gold-500/20 dark:text-gold-400',
  critical: 'bg-wine-100 text-wine-700 dark:bg-wine-500/20 dark:text-wine-400',
  neutral: 'bg-gray-100 text-gray-600 dark:bg-white/10 dark:text-stone-400',
}

const DOT_STYLES: Record<BadgeTone, string> = {
  free: 'bg-sage-600',
  occupied: 'bg-brand-600',
  reserved: 'bg-teal-600',
  attention: 'bg-gold-500',
  critical: 'bg-wine-600',
  neutral: 'bg-gray-400',
}

interface BadgeProps {
  tone: BadgeTone
  children: ReactNode
  dot?: boolean
  className?: string
}

export function Badge({ tone, children, dot = false, className = '' }: BadgeProps) {
  return (
    <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium ${TONE_STYLES[tone]} ${className}`}>
      {dot && <span className={`h-1.5 w-1.5 shrink-0 rounded-full ${DOT_STYLES[tone]}`} />}
      {children}
    </span>
  )
}
