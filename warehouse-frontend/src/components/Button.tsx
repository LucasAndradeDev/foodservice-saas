import { forwardRef, type ButtonHTMLAttributes } from 'react'

type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger'
type ButtonSize = 'sm' | 'md'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant
  size?: ButtonSize
}

const VARIANT_STYLES: Record<ButtonVariant, string> = {
  primary: 'bg-teal-600 text-white shadow-sm hover:bg-teal-700 active:scale-[0.98]',
  secondary:
    'border border-gray-300 bg-white text-gray-700 hover:bg-gray-50 dark:border-white/10 dark:bg-stone-900 dark:text-stone-200 dark:hover:bg-white/5',
  ghost: 'text-teal-700 hover:bg-teal-50 dark:text-teal-400 dark:hover:bg-teal-500/10',
  danger: 'bg-wine-600 text-white shadow-sm hover:bg-wine-700 active:scale-[0.98]',
}

const SIZE_STYLES: Record<ButtonSize, string> = {
  sm: 'gap-1.5 rounded-lg px-3 py-1.5 text-xs',
  md: 'gap-1.5 rounded-lg px-3.5 py-2 text-sm',
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { variant = 'primary', size = 'md', className = '', ...props },
  ref,
) {
  return (
    <button
      ref={ref}
      className={`inline-flex items-center justify-center font-medium transition disabled:cursor-default disabled:opacity-50 ${SIZE_STYLES[size]} ${VARIANT_STYLES[variant]} ${className}`}
      {...props}
    />
  )
})
