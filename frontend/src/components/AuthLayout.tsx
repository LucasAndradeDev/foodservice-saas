import type { InputHTMLAttributes, ReactNode } from 'react'
import type { LucideIcon } from 'lucide-react'
import { ThemeToggleButton } from '../theme/ThemeToggleButton'

interface AuthLayoutProps {
  children: ReactNode
}

export function AuthLayout({ children }: AuthLayoutProps) {
  return (
    <div className="relative flex min-h-screen bg-white dark:bg-stone-950">
      <div className="relative flex w-full flex-col justify-center px-6 py-12 sm:px-12 lg:w-1/2 lg:px-16 xl:px-24">
        <ThemeToggleButton className="absolute right-4 top-4 z-10" />
        <div className="mx-auto w-full max-w-sm">{children}</div>
      </div>

      <div className="relative hidden overflow-hidden bg-brand-900 lg:block lg:w-1/2">
        <div className="absolute -left-24 -top-24 h-96 w-96 rounded-full bg-brand-500/40" />
        <div className="absolute -right-32 top-1/3 h-[28rem] w-[28rem] rounded-full bg-brand-600/50" />
        <div className="absolute -bottom-40 left-1/4 h-[32rem] w-[32rem] rounded-full bg-brand-700/60" />
        <div className="absolute bottom-24 right-20 h-16 w-16 rounded-full bg-brand-200" />
      </div>
    </div>
  )
}

interface AuthInputProps extends InputHTMLAttributes<HTMLInputElement> {
  icon: LucideIcon
  label: string
  id: string
  rightElement?: ReactNode
}

export function AuthInput({ icon: Icon, label, id, rightElement, className, ...props }: AuthInputProps) {
  return (
    <div className="mb-5">
      <label htmlFor={id} className="mb-1 block text-sm font-medium text-gray-600 dark:text-stone-400">
        {label}
      </label>
      <div className="flex items-center gap-2 border-b border-gray-300 py-2 focus-within:border-brand-500 dark:border-white/10 dark:focus-within:border-brand-400">
        <Icon className="h-4 w-4 shrink-0 text-gray-400 dark:text-stone-500" />
        <input
          id={id}
          {...props}
          className={`w-full border-0 bg-transparent p-0 text-sm text-gray-800 placeholder:text-gray-400 focus:outline-none focus:ring-0 dark:text-white dark:placeholder:text-stone-600 ${className ?? ''}`}
        />
        {rightElement}
      </div>
    </div>
  )
}
