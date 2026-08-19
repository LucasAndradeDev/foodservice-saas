import { UtensilsCrossed } from 'lucide-react'
import { DeliveryRiderIcon } from '../../components/DeliveryRiderIcon'

export type OrderMode = 'DINE_IN' | 'DELIVERY'

interface OrderModeToggleProps {
  mode: OrderMode
  onChange: (mode: OrderMode) => void
}

export function OrderModeToggle({ mode, onChange }: OrderModeToggleProps) {
  return (
    <div className="flex gap-1.5 rounded-2xl bg-gray-100 p-1.5 dark:bg-white/5">
      <button
        type="button"
        onClick={() => onChange('DINE_IN')}
        className={`flex flex-1 items-center justify-center gap-1.5 rounded-xl py-2 text-sm font-semibold transition-colors ${
          mode === 'DINE_IN'
            ? 'bg-white text-brand-600 shadow-sm dark:bg-stone-800 dark:text-brand-400'
            : 'text-gray-500 dark:text-stone-400'
        }`}
      >
        <UtensilsCrossed className="h-4 w-4" />
        Comer no local
      </button>
      <button
        type="button"
        onClick={() => onChange('DELIVERY')}
        className={`flex flex-1 items-center justify-center gap-1.5 rounded-xl py-2 text-sm font-semibold transition-colors ${
          mode === 'DELIVERY'
            ? 'bg-white text-brand-600 shadow-sm dark:bg-stone-800 dark:text-brand-400'
            : 'text-gray-500 dark:text-stone-400'
        }`}
      >
        <DeliveryRiderIcon className="h-4 w-4" />
        Delivery
      </button>
    </div>
  )
}
