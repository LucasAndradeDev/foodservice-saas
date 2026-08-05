import type { HTMLAttributes } from 'react'

export function Card({ className = '', ...props }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={`rounded-xl border border-gray-200 bg-white shadow-sm dark:border-white/10 dark:bg-stone-900 ${className}`}
      {...props}
    />
  )
}
