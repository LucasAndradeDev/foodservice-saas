import { AnimatePresence, motion } from 'framer-motion'
import { Clock, Flame, ImageOff, Minus, Plus, Sparkles, Star } from 'lucide-react'
import { useEffect, useRef } from 'react'
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

const EASE_OUT: [number, number, number, number] = [0.16, 1, 0.3, 1]
const AUTOPLAY_INTERVAL_MS = 2800
const AUTOPLAY_RESUME_DELAY_MS = 3500

const rowVariants = {
  hidden: {},
  visible: { transition: { staggerChildren: 0.09, delayChildren: 0.05 } },
}

const cardVariants = {
  hidden: { opacity: 0, y: 16, scale: 0.94 },
  visible: { opacity: 1, y: 0, scale: 1, transition: { duration: 0.45, ease: EASE_OUT } },
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
  const scrollRef = useRef<HTMLDivElement | null>(null)
  const cardRefs = useRef<Record<number, HTMLDivElement | null>>({})
  const autoplayIndexRef = useRef(0)
  const isPausedRef = useRef(false)
  const resumeTimeoutRef = useRef<number | null>(null)

  useEffect(() => {
    autoplayIndexRef.current = 0
    if (products.length <= 1) return
    const interval = window.setInterval(() => {
      if (isPausedRef.current) return
      const container = scrollRef.current
      const nextIndex = (autoplayIndexRef.current + 1) % products.length
      const card = cardRefs.current[nextIndex]
      autoplayIndexRef.current = nextIndex
      if (!container || !card) return
      // Scroll this row only, targeting the card's own layout position (offsetLeft) rather than
      // a delta off the row's *current* scroll position. offsetLeft never depends on whether a
      // previous smooth-scroll has finished animating, so consecutive ticks can't compound onto
      // a stale read. It also never touches window/page scroll, unlike scrollIntoView(), which
      // would drag the whole page up to reveal the target if the row wasn't fully in view.
      const scrollPaddingLeft = parseFloat(getComputedStyle(container).scrollPaddingLeft || '0')
      container.scrollTo({ left: card.offsetLeft - scrollPaddingLeft, behavior: 'smooth' })
    }, AUTOPLAY_INTERVAL_MS)
    return () => window.clearInterval(interval)
  }, [products.length])

  function pauseAutoplay() {
    isPausedRef.current = true
    if (resumeTimeoutRef.current) window.clearTimeout(resumeTimeoutRef.current)
  }

  function scheduleAutoplayResume() {
    if (resumeTimeoutRef.current) window.clearTimeout(resumeTimeoutRef.current)
    resumeTimeoutRef.current = window.setTimeout(() => {
      isPausedRef.current = false
    }, AUTOPLAY_RESUME_DELAY_MS)
  }

  if (products.length === 0) return null

  return (
    <section className="mx-auto max-w-2xl pb-2 pt-4">
      <div className="mb-3.5 flex items-end justify-between px-4">
        <div className="flex items-center gap-2.5">
          <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-gold-400 via-brand-500 to-brand-700 text-white shadow-md shadow-brand-900/30">
            <Sparkles className="h-4 w-4" />
          </span>
          <div>
            <h2 className="font-display text-xl font-bold leading-tight tracking-tight text-gray-900 dark:text-white">Destaques</h2>
            <p className="text-[11px] font-medium text-gray-400 dark:text-stone-500">Selecionados especialmente pra você</p>
          </div>
        </div>
      </div>
      <motion.div
        ref={scrollRef}
        variants={rowVariants}
        initial="hidden"
        animate="visible"
        onPointerDown={pauseAutoplay}
        onPointerUp={scheduleAutoplayResume}
        onPointerCancel={scheduleAutoplayResume}
        onTouchStart={pauseAutoplay}
        onTouchEnd={scheduleAutoplayResume}
        className="no-scrollbar relative flex snap-x snap-mandatory gap-4 overflow-x-auto overscroll-x-contain scroll-pl-4 scroll-pr-4 px-4 pb-5 [-webkit-overflow-scrolling:touch]"
      >
        {products.map((product, index) => {
          const quantity = product.modifierGroups.length > 0 || product.type === 'COMBO' ? 0 : (cartQuantities.get(product.id) ?? 0)
          const unavailable = product.soldOut || !product.availableNow
          const hasDiscount = product.discountedPrice !== null
          const discountPct = hasDiscount ? Math.round((1 - product.discountedPrice! / product.price) * 100) : 0
          const price =
            product.type === 'COMBO' && product.combo
              ? product.combo.minPrice === product.combo.maxPrice
                ? currencyFormatter.format(product.combo.minPrice)
                : `${currencyFormatter.format(product.combo.minPrice)}+`
              : currencyFormatter.format(product.discountedPrice ?? product.price)

          return (
            <motion.div
              key={product.id}
              ref={(el) => {
                cardRefs.current[index] = el
              }}
              variants={cardVariants}
              whileTap={{ scale: 0.96 }}
              onClick={() => onOpenDetail(product)}
              className="group relative w-[calc((100%-1rem)/2)] shrink-0 snap-start cursor-pointer rounded-[28px] bg-gradient-to-br from-gold-400/90 via-brand-400/70 to-brand-600/90 p-[2px] shadow-lg shadow-black/10 dark:shadow-black/30"
            >
              <div className="relative aspect-[3/4] w-full overflow-hidden rounded-[26px] bg-gray-100 dark:bg-stone-900">
                {product.imageUrl ? (
                  <motion.img
                    src={product.imageUrl}
                    alt={product.name}
                    initial={{ scale: 1.15 }}
                    animate={{ scale: 1 }}
                    whileHover={{ scale: 1.06, transition: { duration: 0.3, ease: 'easeOut' } }}
                    transition={{ duration: 5, ease: 'easeOut' }}
                    className={`h-full w-full object-cover ${unavailable ? 'grayscale' : ''}`}
                  />
                ) : (
                  <div className="flex h-full w-full items-center justify-center text-gray-300 dark:text-stone-600">
                    <ImageOff className="h-6 w-6" />
                  </div>
                )}

                <div className="pointer-events-none absolute inset-0 bg-gradient-to-t from-black/90 via-black/20 to-black/5" />

                <div className="absolute inset-x-2.5 top-2.5 flex items-start justify-between gap-1.5">
                  {product.bestseller || product.featured ? (
                    <span
                      className={`flex items-center gap-1 rounded-full px-2.5 py-1 text-[10px] font-bold uppercase tracking-wide text-white shadow-sm ${
                        product.bestseller ? 'bg-gradient-to-r from-brand-600 to-brand-500' : 'bg-gradient-to-r from-gold-600 to-gold-500'
                      }`}
                    >
                      {product.bestseller ? <Flame className="h-2.5 w-2.5" /> : <Star className="h-2.5 w-2.5 fill-current" />}
                      {product.bestseller ? 'Mais pedido' : 'Destaque'}
                    </span>
                  ) : (
                    <span />
                  )}
                  {hasDiscount && discountPct > 0 && (
                    <span className="flex items-center rounded-full bg-sage-600 px-2 py-1 text-[10px] font-bold text-white shadow-sm">
                      -{discountPct}%
                    </span>
                  )}
                </div>

                <div className="absolute inset-x-0 bottom-0 p-3.5">
                  {!unavailable && product.estimatedWaitMinutes !== null && (
                    <span className="mb-1.5 inline-flex items-center gap-1 rounded-full bg-white/15 px-2 py-0.5 text-[10px] font-medium text-white/90 backdrop-blur-sm">
                      <Clock className="h-2.5 w-2.5" />
                      {product.estimatedWaitMinutes < 1 ? '<1 min' : `~${product.estimatedWaitMinutes} min`}
                    </span>
                  )}
                  <p className="truncate text-sm font-semibold leading-tight text-white drop-shadow-sm">{product.name}</p>
                  <div className="mt-0.5 flex items-baseline gap-1.5">
                    {hasDiscount && (
                      <span className="text-xs text-white/60 line-through">{currencyFormatter.format(product.price)}</span>
                    )}
                    <span className="font-display text-lg font-bold text-white">{price}</span>
                  </div>
                </div>

                {canOrder && !unavailable && (
                  <div className="absolute bottom-3 right-3" onClick={(e) => e.stopPropagation()}>
                    <AnimatePresence mode="wait" initial={false}>
                      {quantity === 0 ? (
                        <motion.button
                          key="add"
                          type="button"
                          initial={{ opacity: 0, scale: 0.8 }}
                          animate={{ opacity: 1, scale: 1 }}
                          exit={{ opacity: 0, scale: 0.8 }}
                          whileTap={{ scale: 0.9 }}
                          transition={{ duration: 0.15 }}
                          onClick={() => onAdd(product)}
                          aria-label="Adicionar"
                          title="Adicionar"
                          className="relative flex h-10 w-10 items-center justify-center rounded-full bg-white text-brand-600 shadow-lg ring-1 ring-black/5 transition hover:bg-brand-50 active:scale-95 dark:bg-stone-900 dark:text-brand-400 dark:ring-white/10"
                        >
                          <motion.span
                            aria-hidden
                            animate={{ opacity: [0.5, 0.9, 0.5], scale: [1, 1.15, 1] }}
                            transition={{ duration: 1.8, repeat: Infinity, ease: 'easeInOut' }}
                            className="absolute -inset-1 -z-10 rounded-full bg-brand-400/60 blur-md"
                          />
                          <Plus className="h-4 w-4" />
                        </motion.button>
                      ) : (
                        <motion.div
                          key="stepper"
                          initial={{ opacity: 0, scale: 0.9 }}
                          animate={{ opacity: 1, scale: 1 }}
                          exit={{ opacity: 0 }}
                          transition={{ duration: 0.15 }}
                          className="flex items-center gap-2 rounded-full bg-white px-2 py-1.5 text-brand-600 shadow-lg ring-1 ring-black/5 dark:bg-stone-900 dark:text-brand-400 dark:ring-white/10"
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
            </motion.div>
          )
        })}
      </motion.div>
    </section>
  )
}
