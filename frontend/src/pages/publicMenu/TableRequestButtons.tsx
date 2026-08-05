import { Bell, Check, Wallet } from 'lucide-react'
import type { TableRequestType } from '../../api/tableRequests'

interface TableRequestButtonsProps {
  canRequestBill: boolean
  requestedTypes: Set<TableRequestType>
  isPending: boolean
  pendingType: TableRequestType | undefined
  cartCount: number
  isCartOpen: boolean
  onRequest: (type: TableRequestType) => void
}

export function TableRequestButtons({
  canRequestBill,
  requestedTypes,
  isPending,
  pendingType,
  cartCount,
  isCartOpen,
  onRequest,
}: TableRequestButtonsProps) {
  return (
    <div
      className={`fixed right-4 z-20 flex flex-col items-end gap-2 ${
        cartCount > 0 && !isCartOpen ? 'bottom-20' : 'bottom-4'
      }`}
    >
      {canRequestBill && (
        <button
          type="button"
          onClick={() => onRequest('REQUEST_BILL')}
          disabled={(isPending && pendingType === 'REQUEST_BILL') || requestedTypes.has('REQUEST_BILL')}
          className={`flex items-center gap-2 rounded-full border bg-white/90 px-4 py-2 text-sm font-medium shadow-xl backdrop-blur-md transition-colors duration-150 dark:bg-stone-900/90 ${
            requestedTypes.has('REQUEST_BILL')
              ? 'border-sage-600 bg-sage-100 text-sage-700 dark:border-sage-500 dark:bg-sage-500/10 dark:text-sage-400'
              : 'border-brand-600 text-brand-600 hover:bg-gray-50 dark:border-brand-400 dark:text-brand-400 dark:hover:bg-stone-800'
          }`}
        >
          {requestedTypes.has('REQUEST_BILL') ? (
            <>
              <Check className="h-4 w-4" />
              Pedido!
            </>
          ) : (
            <>
              <Wallet className="h-4 w-4" />
              Pedir a conta
            </>
          )}
        </button>
      )}
      <button
        type="button"
        onClick={() => onRequest('CALL_WAITER')}
        disabled={(isPending && pendingType === 'CALL_WAITER') || requestedTypes.has('CALL_WAITER')}
        className={`flex items-center gap-2 rounded-full border bg-white/90 px-4 py-2 text-sm font-medium shadow-xl backdrop-blur-md transition-colors duration-150 dark:bg-stone-900/90 ${
          requestedTypes.has('CALL_WAITER')
            ? 'border-sage-600 bg-sage-100 text-sage-700 dark:border-sage-500 dark:bg-sage-500/10 dark:text-sage-400'
            : 'border-brand-600 text-brand-600 hover:bg-gray-50 dark:border-brand-400 dark:text-brand-400 dark:hover:bg-stone-800'
        }`}
      >
        {requestedTypes.has('CALL_WAITER') ? (
          <>
            <Check className="h-4 w-4" />
            Chamado!
          </>
        ) : (
          <>
            <Bell className="h-4 w-4" />
            Chamar garçom
          </>
        )}
      </button>
    </div>
  )
}
