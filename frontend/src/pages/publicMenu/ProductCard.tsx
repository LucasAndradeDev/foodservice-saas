import { AnimatePresence, motion } from 'framer-motion'
import { Clock, Flame, ImageOff, Layers, Minus, Percent, Plus, Star } from 'lucide-react'
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
  const unavailable = product.soldOut || !product.availableNow

  return (
    <motion.div
      whileTap={canOrder && !unavailable ? { scale: 0.97 } : undefined}
      className={`flex gap-3 rounded-2xl border border-gray-100 bg-white p-3 shadow-sm dark:border-white/10 dark:bg-stone-900 ${
        unavailable ? 'opacity-60' : ''
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
          <span className="shrink-0 text-right">
            {product.type === 'COMBO' && product.combo ? (
              <span className="text-base font-bold text-brand-600 dark:text-brand-400">
                {product.combo.minPrice === product.combo.maxPrice
                  ? currencyFormatter.format(product.combo.minPrice)
                  : `${currencyFormatter.format(product.combo.minPrice)} – ${currencyFormatter.format(product.combo.maxPrice)}`}
              </span>
            ) : (
              <>
                {product.discountedPrice !== null && (
                  <span className="mr-1 text-xs text-gray-400 line-through dark:text-stone-500">
                    {currencyFormatter.format(product.price)}
                  </span>
                )}
                <span className="text-base font-bold text-brand-600 dark:text-brand-400">
                  {currencyFormatter.format(product.discountedPrice ?? product.price)}
                </span>
              </>
            )}
          </span>
        </div>
        {!unavailable && (product.type === 'COMBO' || product.bestseller || product.featured || product.discountedPrice !== null) && (
          <div className="mt-1 flex flex-wrap items-center gap-1">
            {product.type === 'COMBO' && (
              <span className="flex items-center gap-0.5 rounded-full bg-purple-100 px-1.5 py-0.5 text-[10px] font-semibold text-purple-700 dark:bg-purple-500/10 dark:text-purple-400">
                <Layers className="h-2.5 w-2.5" />
                Combo
              </span>
            )}
            {product.discountedPrice !== null && (
              <span className="flex items-center gap-0.5 rounded-full bg-pink-100 px-1.5 py-0.5 text-[10px] font-semibold text-pink-700 dark:bg-pink-500/10 dark:text-pink-400">
                <Percent className="h-2.5 w-2.5" />
                Happy Hour
              </span>
            )}
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
        {!unavailable && product.estimatedWaitMinutes !== null && (
          <span className="mt-1 flex items-center gap-1 text-xs text-gray-400 dark:text-stone-500">
            <Clock className="h-3 w-3" />
            Pronto em {product.estimatedWaitMinutes < 1 ? 'menos de 1 min' : `~${product.estimatedWaitMinutes} min`}
          </span>
        )}
        {product.soldOut ? (
          <span className="mt-2 inline-block rounded-full bg-gold-100 px-3 py-1 text-xs font-medium text-gold-700 dark:bg-gold-500/10 dark:text-gold-400">
            Esgotado hoje
          </span>
        ) : !product.availableNow ? (
          <span className="mt-2 inline-block rounded-full bg-gold-100 px-3 py-1 text-xs font-medium text-gold-700 dark:bg-gold-500/10 dark:text-gold-400">
            Fora do horário
          </span>
        ) : (
          canOrder && (
            <div className="mt-2 flex justify-end">
              <AnimatePresence mode="wait" initial={false}>
                {quantity === 0 ? (
                  <motion.button
                    key="add"
                    type="button"
                    layout
                    initial={{ opacity: 0, scale: 0.8 }}
                    animate={{ opacity: 1, scale: 1 }}
                    exit={{ opacity: 0, scale: 0.8 }}
                    transition={{ duration: 0.15 }}
                    onClick={() => onAdd(product)}
                    aria-label="Adicionar"
                    title="Adicionar"
                    className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-brand-600 text-white shadow-sm transition hover:bg-brand-700 active:scale-95 dark:bg-brand-500 dark:hover:bg-brand-600"
                  >
                    <Plus className="h-5 w-5" />
                  </motion.button>
                ) : (
                  <motion.div
                    key="stepper"
                    layout
                    initial={{ opacity: 0, scale: 0.9 }}
                    animate={{ opacity: 1, scale: 1 }}
                    exit={{ opacity: 0 }}
                    transition={{ duration: 0.15 }}
                    className="flex w-fit items-center gap-3 rounded-full bg-brand-600 px-2.5 py-1.5 text-white shadow-sm dark:bg-brand-500"
                  >
                    <button
                      type="button"
                      onClick={() => onDecrement(product.id)}
                      aria-label="Diminuir quantidade"
                      className="flex h-6 w-6 items-center justify-center"
                    >
                      <Minus className="h-4 w-4" />
                    </button>
                    <span className="min-w-[1ch] text-center text-sm font-semibold">{quantity}</span>
                    <button
                      type="button"
                      onClick={() => onIncrement(product.id)}
                      aria-label="Aumentar quantidade"
                      className="flex h-6 w-6 items-center justify-center"
                    >
                      <Plus className="h-4 w-4" />
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
