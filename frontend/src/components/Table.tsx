import type { HTMLAttributes, TableHTMLAttributes } from 'react'

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

export function TableRow({ className = '', ...props }: HTMLAttributes<HTMLTableRowElement>) {
  return <tr className={`border-t border-gray-100 dark:border-white/10 ${className}`} {...props} />
}
