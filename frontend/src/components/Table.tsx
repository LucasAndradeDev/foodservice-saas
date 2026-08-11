import { forwardRef, type HTMLAttributes, type TableHTMLAttributes } from 'react'

export function Table({ className = '', ...props }: TableHTMLAttributes<HTMLTableElement>) {
  return (
    <div className="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm dark:border-white/10 dark:bg-stone-900">
      <table className={`w-full text-sm ${className}`} {...props} />
    </div>
  )
}

export function TableHead({ className = '', ...props }: HTMLAttributes<HTMLTableSectionElement>) {
  return <thead className={`bg-gray-50 text-left text-gray-500 dark:bg-white/5 dark:text-stone-400 ${className}`} {...props} />
}

export const TableRow = forwardRef<HTMLTableRowElement, HTMLAttributes<HTMLTableRowElement>>(function TableRow(
  { className = '', ...props },
  ref,
) {
  return <tr ref={ref} className={`border-t border-gray-100 dark:border-white/10 ${className}`} {...props} />
})
