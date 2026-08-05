import { AnimatePresence, motion } from 'framer-motion'
import { Check, X } from 'lucide-react'
import { useEffect, useState } from 'react'
import type { PublicMenuProduct } from '../../api/publicMenu'
import { computeComboUnitPrice, type SelectedComboSlot } from '../../utils/combos'
import { currencyFormatter } from './utils'

interface ComboSheetProps {
  product: PublicMenuProduct | null
  onClose: () => void
  onConfirm: (selections: SelectedComboSlot[], unitPrice: number) => void
}

export function ComboSheet({ product, onClose, onConfirm }: ComboSheetProps) {
  const [slotSelections, setSlotSelections] = useState<Record<string, string>>({})

  useEffect(() => {
    setSlotSelections({})
  }, [product?.id])

  const composition = product?.combo ?? null

  const isValid = !composition || composition.slots.every((slot) => !!slotSelections[slot.id])

  function handleConfirm() {
    if (!composition || !isValid) return
    const selections: SelectedComboSlot[] = composition.slots
      .filter((slot) => !!slotSelections[slot.id])
      .map((slot) => {
        const option = slot.options.find((o) => o.productId === slotSelections[slot.id])!
        return {
          slotId: slot.id,
          slotName: slot.name,
          productId: option.productId,
          productName: option.productName,
          unitPrice: option.unitPrice,
          quantity: option.quantity,
        }
      })
    onConfirm(selections, computeComboUnitPrice(composition, slotSelections))
  }

  return (
    <AnimatePresence>
      {product && (
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
              <h2 className="text-lg font-semibold text-gray-800 dark:text-white">{product.name}</h2>
              <button
                type="button"
                onClick={onClose}
                aria-label="Fechar"
                className="text-gray-400 hover:text-gray-600 dark:text-stone-500 dark:hover:text-stone-300"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            {composition && (
              <div className="space-y-5">
                {composition.fixedItems.length > 0 && (
                  <div>
                    <span className="mb-1 block text-sm font-medium text-gray-800 dark:text-white">Itens inclusos</span>
                    <p className="text-sm text-gray-500 dark:text-stone-400">
                      {composition.fixedItems.map((item) => `${item.quantity}x ${item.productName}`).join(', ')}
                    </p>
                  </div>
                )}

                {composition.slots.map((slot) => (
                  <div key={slot.id}>
                    <div className="mb-1 flex items-center gap-2">
                      <span className="font-medium text-gray-800 dark:text-white">{slot.name}</span>
                      {slot.required && (
                        <span className="rounded-full bg-gold-100 px-2 py-0.5 text-xs text-gold-700 dark:bg-gold-500/10 dark:text-gold-400">
                          Obrigatório
                        </span>
                      )}
                    </div>
                    <p className="mb-2 text-xs text-gray-500 dark:text-stone-400">Escolha 1 opção</p>
                    <div className="space-y-1.5">
                      {slot.options.map((option) => {
                        const isSelected = slotSelections[slot.id] === option.productId
                        return (
                          <button
                            key={option.id}
                            type="button"
                            onClick={() => setSlotSelections((prev) => ({ ...prev, [slot.id]: option.productId }))}
                            className={`flex w-full items-center justify-between rounded-xl border px-3 py-2 text-left text-sm transition-colors ${
                              isSelected
                                ? 'border-brand-600 bg-brand-50 dark:border-brand-400 dark:bg-brand-400/10'
                                : 'border-gray-200 hover:bg-gray-50 dark:border-white/10 dark:hover:bg-white/5'
                            }`}
                          >
                            <span className="flex items-center gap-2 text-gray-800 dark:text-white">
                              <span
                                className={`flex h-4 w-4 shrink-0 items-center justify-center rounded-full border ${
                                  isSelected
                                    ? 'border-brand-600 bg-brand-600 dark:border-brand-400 dark:bg-brand-400'
                                    : 'border-gray-300 dark:border-white/20'
                                }`}
                              >
                                {isSelected && <Check className="h-3 w-3 text-white" />}
                              </span>
                              {option.productName}
                            </span>
                            <span className="text-gray-500 dark:text-stone-400">{currencyFormatter.format(option.unitPrice)}</span>
                          </button>
                        )
                      })}
                    </div>
                  </div>
                ))}
              </div>
            )}

            <button
              type="button"
              onClick={handleConfirm}
              disabled={!composition || !isValid}
              className="mt-5 w-full rounded-md bg-brand-600 px-3 py-2 text-sm font-medium text-white hover:opacity-90 disabled:opacity-50"
            >
              {composition ? `Adicionar · ${currencyFormatter.format(computeComboUnitPrice(composition, slotSelections))}` : 'Adicionar'}
            </button>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  )
}
