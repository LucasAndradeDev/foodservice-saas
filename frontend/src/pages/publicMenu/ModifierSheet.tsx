import { AnimatePresence, motion } from 'framer-motion'
import { Check, X } from 'lucide-react'
import { useEffect, useState } from 'react'
import type { PublicMenuProduct } from '../../api/publicMenu'
import { currencyFormatter, type SelectedModifier } from './utils'

interface ModifierSheetProps {
  product: PublicMenuProduct | null
  onClose: () => void
  onConfirm: (selectedModifiers: SelectedModifier[]) => void
}

export function ModifierSheet({ product, onClose, onConfirm }: ModifierSheetProps) {
  const [selections, setSelections] = useState<Record<string, string[]>>({})

  useEffect(() => {
    setSelections({})
  }, [product?.id])

  function toggleOption(groupId: string, optionId: string, isSingle: boolean) {
    setSelections((prev) => {
      const current = prev[groupId] ?? []
      if (isSingle) {
        return { ...prev, [groupId]: current.includes(optionId) ? [] : [optionId] }
      }
      const next = current.includes(optionId) ? current.filter((id) => id !== optionId) : [...current, optionId]
      return { ...prev, [groupId]: next }
    })
  }

  const isValid =
    !product || product.modifierGroups.every((group) => !group.required || (selections[group.id]?.length ?? 0) > 0)

  const selectedModifiers: SelectedModifier[] = product
    ? product.modifierGroups.flatMap((group) =>
        (selections[group.id] ?? []).flatMap((optionId) => {
          const option = group.options.find((o) => o.id === optionId)
          if (!option) return []
          return [
            {
              groupId: group.id,
              groupName: group.name,
              optionId: option.id,
              optionName: option.name,
              priceDelta: option.priceDelta,
            },
          ]
        }),
      )
    : []

  const basePrice = product?.discountedPrice ?? product?.price ?? 0
  const totalPrice = basePrice + selectedModifiers.reduce((sum, m) => sum + m.priceDelta, 0)

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

            <div className="space-y-5">
              {product.modifierGroups.map((group) => (
                <div key={group.id}>
                  <div className="mb-1 flex items-center gap-2">
                    <span className="font-medium text-gray-800 dark:text-white">{group.name}</span>
                    {group.required && (
                      <span className="rounded-full bg-amber-100 px-2 py-0.5 text-xs text-amber-700 dark:bg-amber-500/10 dark:text-amber-400">
                        Obrigatório
                      </span>
                    )}
                  </div>
                  <p className="mb-2 text-xs text-gray-500 dark:text-stone-400">
                    {group.selectionType === 'SINGLE' ? 'Escolha 1 opção' : 'Escolha quantas quiser'}
                  </p>
                  <div className="space-y-1.5">
                    {group.options.map((option) => {
                      const isSelected = (selections[group.id] ?? []).includes(option.id)
                      return (
                        <button
                          key={option.id}
                          type="button"
                          onClick={() => toggleOption(group.id, option.id, group.selectionType === 'SINGLE')}
                          className={`flex w-full items-center justify-between rounded-xl border px-3 py-2 text-left text-sm transition-colors ${
                            isSelected
                              ? 'border-brand-600 bg-brand-50 dark:border-brand-400 dark:bg-brand-400/10'
                              : 'border-gray-200 hover:bg-gray-50 dark:border-white/10 dark:hover:bg-white/5'
                          }`}
                        >
                          <span className="flex items-center gap-2 text-gray-800 dark:text-white">
                            <span
                              className={`flex h-4 w-4 shrink-0 items-center justify-center border ${
                                group.selectionType === 'SINGLE' ? 'rounded-full' : 'rounded'
                              } ${
                                isSelected
                                  ? 'border-brand-600 bg-brand-600 dark:border-brand-400 dark:bg-brand-400'
                                  : 'border-gray-300 dark:border-white/20'
                              }`}
                            >
                              {isSelected && <Check className="h-3 w-3 text-white" />}
                            </span>
                            {option.name}
                          </span>
                          <span className="text-gray-500 dark:text-stone-400">
                            {option.priceDelta > 0 ? `+${currencyFormatter.format(option.priceDelta)}` : 'Grátis'}
                          </span>
                        </button>
                      )
                    })}
                  </div>
                </div>
              ))}
            </div>

            <button
              type="button"
              onClick={() => onConfirm(selectedModifiers)}
              disabled={!isValid}
              className="mt-5 w-full rounded-md bg-brand-600 px-3 py-2 text-sm font-medium text-white hover:opacity-90 disabled:opacity-50"
            >
              Adicionar · {currencyFormatter.format(totalPrice)}
            </button>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  )
}
