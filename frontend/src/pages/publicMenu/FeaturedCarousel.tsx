import { AnimatePresence, motion } from 'framer-motion'
import { ImageOff, Minus, Plus } from 'lucide-react'
import type { PublicMenuProduct } from '../../api/publicMenu'
import { currencyFormatter } from './utils'

interface FeaturedCarouselProps {
  products: PublicMenuProduct[]
  cartQuantities: Map<string, number>
  canOrder: boolean
  onAdd: (product: PublicMenuProduct) => void
  onIncrement: (productId: string) => void
  onDecrement: (productId: string) => void
  onOpenDetail: (product: PublicMenuProduct) => void
}

export function FeaturedCarousel({
  products,
  cartQuantities,
  canOrder,
  onAdd,
  onIncrement,
  onDecrement,
  onOpenDetail,
}: FeaturedCarouselProps) {
  if (products.length === 0) return null

  return (
    <section className="mx-auto max-w-2xl pt-4">
      <h2 className="font-display mb-3 px-4 text-lg font-bold text-brand-600 dark:text-brand-400">Destaques</h2>
      <div className="no-scrollbar flex snap-x snap-mandatory gap-3 overflow-x-auto overscroll-x-contain px-4 pb-1 [-webkit-overflow-scrolling:touch]">
        {products.map((product) => {
          const quantity = product.modifierGroups.length > 0 || product.type === 'COMBO' ? 0 : (cartQuantities.get(product.id) ?? 0)
          const unavailable = product.soldOut || !product.availableNow

          return (
            <div key={product.id} className="w-36 shrink-0 snap-start">
              <div
                onClick={() => onOpenDetail(product)}
                className="relative aspect-[4/5] w-full cursor-pointer overflow-hidden rounded-2xl bg-gray-100 ring-1 ring-black/5 dark:bg-white/5 dark:ring-white/10"
              >
                {product.imageUrl ? (
                  <img src={product.imageUrl} alt={product.name} className="h-full w-full object-cover" />
                ) : (
                  <div className="flex h-full w-full items-center justify-center text-gray-300 dark:text-stone-600">
                    <ImageOff className="h-6 w-6" />
                  </div>
                )}

                {canOrder && !unavailable && (
                  <div className="absolute bottom-2 right-2" onClick={(e) => e.stopPropagation()}>
                    <AnimatePresence mode="wait" initial={false}>
                      {quantity === 0 ? (
                        <motion.button
                          key="add"
                          type="button"
                          initial={{ opacity: 0, scale: 0.8 }}
                          animate={{ opacity: 1, scale: 1 }}
                          exit={{ opacity: 0, scale: 0.8 }}
                          transition={{ duration: 0.15 }}
                          onClick={() => onAdd(product)}
                          aria-label="Adicionar"
                          title="Adicionar"
                          className="flex h-9 w-9 items-center justify-center rounded-full bg-brand-600 text-white shadow-md shadow-brand-900/30 transition hover:bg-brand-700 active:scale-95 dark:bg-brand-500 dark:hover:bg-brand-600"
                        >
                          <Plus className="h-4 w-4" />
                        </motion.button>
                      ) : (
                        <motion.div
                          key="stepper"
                          initial={{ opacity: 0, scale: 0.9 }}
                          animate={{ opacity: 1, scale: 1 }}
                          exit={{ opacity: 0 }}
                          transition={{ duration: 0.15 }}
                          className="flex items-center gap-2 rounded-full bg-brand-600 px-2 py-1 text-white shadow-md shadow-brand-900/30 dark:bg-brand-500"
                        >
                          <button
                            type="button"
                            onClick={() => onDecrement(product.id)}
                            aria-label="Diminuir quantidade"
                            className="flex h-5 w-5 items-center justify-center"
                          >
                            <Minus className="h-3.5 w-3.5" />
                          </button>
                          <span className="min-w-[1ch] text-center text-xs font-semibold">{quantity}</span>
                          <button
                            type="button"
                            onClick={() => onIncrement(product.id)}
                            aria-label="Aumentar quantidade"
                            className="flex h-5 w-5 items-center justify-center"
                          >
                            <Plus className="h-3.5 w-3.5" />
                          </button>
                        </motion.div>
                      )}
                    </AnimatePresence>
                  </div>
                )}
              </div>

              <p className="mt-2 truncate text-sm font-medium text-gray-800 dark:text-white">{product.name}</p>
              <span className="font-display text-sm font-bold text-brand-600 dark:text-brand-400">
                {product.type === 'COMBO' && product.combo
                  ? product.combo.minPrice === product.combo.maxPrice
                    ? currencyFormatter.format(product.combo.minPrice)
                    : `${currencyFormatter.format(product.combo.minPrice)}+`
                  : currencyFormatter.format(product.discountedPrice ?? product.price)}
              </span>
            </div>
          )
        })}
      </div>
    </section>
  )
}
