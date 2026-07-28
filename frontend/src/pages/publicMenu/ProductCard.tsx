import { AnimatePresence, motion } from 'framer-motion'
import { Clock, Flame, ImageOff, Minus, Plus, Star } from 'lucide-react'
import type { PublicMenuProduct } from '../../api/publicMenu'
import { currencyFormatter } from './utils'

interface ProductCardProps {
  product: PublicMenuProduct
  quantity: number
  canOrder: boolean
  onAdd: (product: PublicMenuProduct) => void
  onIncrement: (productId: string) => void
  onDecrement: (productId: string) => void
}

export function ProductCard({ product, quantity, canOrder, onAdd, onIncrement, onDecrement }: ProductCardProps) {
  return (
    <motion.div
      whileTap={canOrder && !product.soldOut ? { scale: 0.97 } : undefined}
      className={`flex gap-3 rounded-2xl border border-gray-100 bg-white p-3 shadow-sm dark:border-white/10 dark:bg-stone-900 ${
        product.soldOut ? 'opacity-60' : ''
      }`}
    >
      {product.imageUrl ? (
        <img
          src={product.imageUrl}
          alt={product.name}
          className="h-24 w-24 shrink-0 rounded-xl object-cover ring-1 ring-black/5 dark:ring-white/10 sm:h-28 sm:w-28"
        />
      ) : (
        <div className="flex h-24 w-24 shrink-0 items-center justify-center rounded-xl bg-gray-100 text-gray-300 dark:bg-white/5 dark:text-stone-600 sm:h-28 sm:w-28">
          <ImageOff className="h-6 w-6" />
        </div>
      )}
      <div className="flex-1">
        <div className="flex items-start justify-between gap-2">
          <span className="font-medium text-gray-800 dark:text-white">{product.name}</span>
          <span className="shrink-0 text-base font-bold text-brand-600 dark:text-brand-400">
            {currencyFormatter.format(product.price)}
          </span>
        </div>
        {!product.soldOut && (product.bestseller || product.featured) && (
          <div className="mt-1 flex flex-wrap items-center gap-1">
            {product.bestseller && (
              <span className="flex items-center gap-0.5 rounded-full bg-orange-100 px-1.5 py-0.5 text-[10px] font-semibold text-orange-700 dark:bg-orange-500/10 dark:text-orange-400">
                <Flame className="h-2.5 w-2.5" />
                Mais pedido
              </span>
            )}
            {product.featured && (
              <span className="flex items-center gap-0.5 rounded-full bg-yellow-100 px-1.5 py-0.5 text-[10px] font-semibold text-yellow-700 dark:bg-yellow-500/10 dark:text-yellow-400">
                <Star className="h-2.5 w-2.5 fill-current" />
                Destaque
              </span>
            )}
          </div>
        )}
        {product.description && (
          <p className="mt-1 text-sm italic text-gray-500 dark:text-stone-400">{product.description}</p>
        )}
        {!product.soldOut && product.estimatedWaitMinutes !== null && (
          <span className="mt-1 flex items-center gap-1 text-xs text-gray-400 dark:text-stone-500">
            <Clock className="h-3 w-3" />
            Pronto em {product.estimatedWaitMinutes < 1 ? 'menos de 1 min' : `~${product.estimatedWaitMinutes} min`}
          </span>
        )}
        {product.soldOut ? (
          <span className="mt-2 inline-block rounded-full bg-amber-100 px-3 py-1 text-xs font-medium text-amber-700 dark:bg-amber-500/10 dark:text-amber-400">
            Esgotado hoje
          </span>
        ) : (
          canOrder && (
            <div className="mt-2">
              <AnimatePresence mode="wait" initial={false}>
                {quantity === 0 ? (
                  <motion.button
                    key="add"
                    type="button"
                    layout
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    exit={{ opacity: 0 }}
                    transition={{ duration: 0.15 }}
                    onClick={() => onAdd(product)}
                    className="rounded-full border border-brand-600 px-3 py-1 text-xs font-medium text-brand-600 hover:bg-gray-50 dark:border-brand-400 dark:text-brand-400 dark:hover:bg-white/5"
                  >
                    Adicionar
                  </motion.button>
                ) : (
                  <motion.div
                    key="stepper"
                    layout
                    initial={{ opacity: 0, scale: 0.9 }}
                    animate={{ opacity: 1, scale: 1 }}
                    exit={{ opacity: 0 }}
                    transition={{ duration: 0.15 }}
                    className="flex w-fit items-center gap-3 rounded-full border border-brand-600 px-2 py-1 dark:border-brand-400"
                  >
                    <button
                      type="button"
                      onClick={() => onDecrement(product.id)}
                      className="flex h-5 w-5 items-center justify-center text-brand-600 dark:text-brand-400"
                    >
                      <Minus className="h-3.5 w-3.5" />
                    </button>
                    <span className="min-w-[1ch] text-center text-xs font-semibold text-gray-800 dark:text-white">
                      {quantity}
                    </span>
                    <button
                      type="button"
                      onClick={() => onIncrement(product.id)}
                      className="flex h-5 w-5 items-center justify-center text-brand-600 dark:text-brand-400"
                    >
                      <Plus className="h-3.5 w-3.5" />
                    </button>
                  </motion.div>
                )}
              </AnimatePresence>
            </div>
          )
        )}
      </div>
    </motion.div>
  )
}
