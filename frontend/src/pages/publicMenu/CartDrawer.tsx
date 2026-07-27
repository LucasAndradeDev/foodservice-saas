import { AnimatePresence, motion } from 'framer-motion'
import { X } from 'lucide-react'
import type { CartItem } from './utils'
import { currencyFormatter, modifiersTotal } from './utils'

interface CartDrawerProps {
  isOpen: boolean
  cart: CartItem[]
  cartTotal: number
  orderError: string | null
  isSubmitting: boolean
  onClose: () => void
  onUpdateQuantity: (index: number, delta: number) => void
  onUpdateObservation: (index: number, observation: string) => void
  onRemove: (index: number) => void
  onSubmit: () => void
}

export function CartDrawer({
  isOpen,
  cart,
  cartTotal,
  orderError,
  isSubmitting,
  onClose,
  onUpdateQuantity,
  onUpdateObservation,
  onRemove,
  onSubmit,
}: CartDrawerProps) {
  return (
    <AnimatePresence>
      {isOpen && (
        <>
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.2 }}
            onClick={onClose}
            className="fixed inset-0 z-30 bg-black/40"
          />
          <motion.div
            initial={{ y: '100%' }}
            animate={{ y: 0 }}
            exit={{ y: '100%' }}
            transition={{ duration: 0.25, ease: 'easeOut' }}
            className="fixed inset-x-0 bottom-0 z-40 max-h-[85vh] overflow-y-auto rounded-t-3xl bg-white p-4 shadow-2xl dark:bg-stone-900"
          >
            <div className="mx-auto mb-3 h-1 w-10 rounded-full bg-gray-300 dark:bg-stone-700" />

            <div className="mb-4 flex items-center justify-between">
              <h2 className="text-lg font-semibold text-gray-800 dark:text-white">Seu pedido</h2>
              <button
                type="button"
                onClick={onClose}
                aria-label="Fechar"
                className="text-gray-400 hover:text-gray-600 dark:text-stone-500 dark:hover:text-stone-300"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            {cart.length === 0 ? (
              <p className="pb-4 text-sm text-gray-500 dark:text-stone-400">Seu carrinho está vazio.</p>
            ) : (
              <>
                <ul className="mb-4 divide-y divide-gray-100 dark:divide-white/10">
                  {cart.map((item, index) => (
                    <li key={index} className="py-3">
                      <div className="flex items-start justify-between gap-2">
                        <span className="font-medium text-gray-800 dark:text-white">{item.productName}</span>
                        <span className="text-sm text-gray-600 dark:text-stone-400">
                          {currencyFormatter.format((item.unitPrice + modifiersTotal(item.selectedModifiers)) * item.quantity)}
                        </span>
                      </div>
                      {item.selectedModifiers.length > 0 && (
                        <div className="mt-1 flex flex-wrap gap-1">
                          {item.selectedModifiers.map((modifier) => (
                            <span
                              key={modifier.optionId}
                              className="rounded-full bg-gray-100 px-2 py-0.5 text-xs text-gray-600 dark:bg-white/10 dark:text-stone-300"
                            >
                              {modifier.optionName}
                            </span>
                          ))}
                        </div>
                      )}
                      <div className="mt-2 flex items-center gap-2">
                        <button
                          type="button"
                          onClick={() => onUpdateQuantity(index, -1)}
                          className="h-7 w-7 rounded-md border border-gray-300 text-gray-600 hover:bg-gray-100 dark:border-white/10 dark:text-stone-300 dark:hover:bg-white/5"
                        >
                          −
                        </button>
                        <span className="w-6 text-center text-sm dark:text-white">{item.quantity}</span>
                        <button
                          type="button"
                          onClick={() => onUpdateQuantity(index, 1)}
                          className="h-7 w-7 rounded-md border border-gray-300 text-gray-600 hover:bg-gray-100 dark:border-white/10 dark:text-stone-300 dark:hover:bg-white/5"
                        >
                          +
                        </button>
                        <button
                          type="button"
                          onClick={() => onRemove(index)}
                          className="ml-auto text-sm text-red-600 hover:underline dark:text-red-400"
                        >
                          Remover
                        </button>
                      </div>
                      <input
                        type="text"
                        placeholder="Observação (opcional)"
                        maxLength={255}
                        value={item.observation}
                        onChange={(e) => onUpdateObservation(index, e.target.value)}
                        className="mt-2 w-full rounded-md border border-gray-300 px-2 py-1 text-xs focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-white/5 dark:text-white dark:placeholder:text-stone-500"
                      />
                    </li>
                  ))}
                </ul>

                <div className="mb-3 flex items-center justify-between text-sm font-semibold text-gray-800 dark:text-white">
                  <span>Total</span>
                  <span>{currencyFormatter.format(cartTotal)}</span>
                </div>

                {orderError && <p className="mb-3 text-sm text-red-600 dark:text-red-400">{orderError}</p>}

                <button
                  type="button"
                  onClick={onSubmit}
                  disabled={isSubmitting}
                  className="w-full rounded-md bg-brand-600 px-3 py-2 text-sm font-medium text-white hover:opacity-90 disabled:opacity-50"
                >
                  Enviar pedido
                </button>
              </>
            )}
          </motion.div>
        </>
      )}
    </AnimatePresence>
  )
}
