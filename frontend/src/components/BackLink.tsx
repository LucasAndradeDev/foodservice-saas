import { ChevronLeft } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'

interface BackLinkProps {
  children: ReactNode
  to?: string
  onClick?: () => void
  className?: string
}

const baseClassName =
  'inline-flex items-center gap-1.5 rounded-lg border border-gray-200 bg-white py-1.5 pl-2.5 pr-3.5 text-sm font-semibold text-gray-700 shadow-sm transition-colors hover:border-gray-300 hover:bg-gray-50 hover:text-gray-900 dark:border-white/10 dark:bg-white/[0.03] dark:text-stone-300 dark:hover:border-white/20 dark:hover:bg-white/[0.06] dark:hover:text-white'

export function BackLink({ children, to, onClick, className = '' }: BackLinkProps) {
  const content = (
    <>
      <ChevronLeft className="h-4 w-4 shrink-0" />
      {children}
    </>
  )

  if (to) {
    return (
      <Link to={to} className={`${baseClassName} ${className}`}>
        {content}
      </Link>
    )
  }

  return (
    <button type="button" onClick={onClick} className={`${baseClassName} ${className}`}>
      {content}
    </button>
  )
}
