import { AnimatePresence, motion } from 'framer-motion'
import { Check, Clock, Flame, ImageOff, Layers, Minus, Percent, Plus, Star, Utensils, X } from 'lucide-react'
import { useEffect, useState } from 'react'
import type { PublicMenuProduct } from '../../api/publicMenu'
import { currencyFormatter } from './utils'

interface ProductDetailModalProps {
  product: PublicMenuProduct | null
  quantity: number
  canOrder: boolean
  onClose: () => void
  onAdd: (product: PublicMenuProduct) => void
  onIncrement: (productId: string) => void
  onDecrement: (productId: string) => void
}

const contentVariants = {
  hidden: {},
  visible: { transition: { staggerChildren: 0.06, delayChildren: 0.1 } },
}

const fadeUpVariants = {
  hidden: { opacity: 0, y: 10 },
  visible: { opacity: 1, y: 0 },
}

export function ProductDetailModal({
  product,
  quantity,
  canOrder,
  onClose,
  onAdd,
  onIncrement,
  onDecrement,
}: ProductDetailModalProps) {
  const [pendingQty, setPendingQty] = useState(1)
  const [justAdded, setJustAdded] = useState(false)

  useEffect(() => {
    setPendingQty(1)
    setJustAdded(false)
  }, [product?.id])

  const unavailable = !!product && (product.soldOut || !product.availableNow)
  const needsSelection = !!product && (product.modifierGroups.length > 0 || product.type === 'COMBO')
  const unitPrice = product ? (product.discountedPrice ?? product.price) : 0
  const savings = product?.discountedPrice !== null && product?.discountedPrice !== undefined ? product.price - product.discountedPrice : 0

  function handleCtaClick() {
    if (!product) return
    if (needsSelection) {
      onAdd(product)
      onClose()
      return
    }
    for (let i = 0; i < pendingQty; i++) onAdd(product)
    setJustAdded(true)
    window.setTimeout(() => setJustAdded(false), 900)
  }

  const displayPrice =
    product?.type === 'COMBO' && product.combo
      ? product.combo.minPrice === product.combo.maxPrice
        ? currencyFormatter.format(product.combo.minPrice)
        : `${currencyFormatter.format(product.combo.minPrice)} – ${currencyFormatter.format(product.combo.maxPrice)}`
      : product
        ? currencyFormatter.format(product.discountedPrice ?? product.price)
        : ''

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
            className="fixed inset-0 z-30 bg-black/50 backdrop-blur-sm"
          />
          <motion.div
            initial={{ y: '100%' }}
            animate={{ y: 0 }}
            exit={{ y: '100%' }}
            transition={{ type: 'spring', stiffness: 320, damping: 32 }}
            className="fixed inset-x-0 bottom-0 z-40 mx-auto flex max-h-[95vh] w-full max-w-2xl flex-col overflow-hidden rounded-t-3xl bg-white shadow-2xl dark:bg-stone-900"
          >
            <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain">
              <motion.div
                layoutId={`product-image-${product.id}`}
                className="relative h-80 w-full shrink-0 overflow-hidden bg-gray-100 sm:h-96 dark:bg-white/5"
              >
                {product.imageUrl ? (
                  <motion.img
                    src={product.imageUrl}
                    alt={product.name}
                    initial={{ scale: 1.15 }}
                    animate={{ scale: 1 }}
                    transition={{ duration: 6, ease: 'easeOut' }}
                    className={`h-full w-full object-cover ${unavailable ? 'grayscale' : ''}`}
                  />
                ) : (
                  <div className="flex h-full w-full items-center justify-center text-gray-300 dark:text-stone-600">
                    <ImageOff className="h-10 w-10" />
                  </div>
                )}
                <div className="pointer-events-none absolute inset-0 bg-gradient-to-t from-black/75 via-black/10 to-black/25" />

                <button
                  type="button"
                  onClick={onClose}
                  aria-label="Fechar"
                  className="absolute right-3 top-3 flex h-10 w-10 items-center justify-center rounded-full bg-black/40 text-white backdrop-blur-sm transition hover:bg-black/60"
                >
                  <X className="h-5 w-5" />
                </button>

                {!unavailable && product.type !== 'COMBO' && (
                  <motion.div
                    initial={{ opacity: 0, scale: 0.6, y: 8 }}
                    animate={{ opacity: 1, scale: 1, y: 0 }}
                    transition={{ type: 'spring', stiffness: 380, damping: 20, delay: 0.35 }}
                    className="absolute bottom-3 right-3 rounded-2xl bg-white/95 px-3.5 py-2 shadow-lg backdrop-blur-sm dark:bg-stone-900/95"
                  >
                    {product.discountedPrice !== null && (
                      <span className="mr-1.5 text-xs text-gray-400 line-through dark:text-stone-500">
                        {currencyFormatter.format(product.price)}
                      </span>
                    )}
                    <span className="font-display text-xl font-bold text-brand-600 dark:text-brand-400">{displayPrice}</span>
                  </motion.div>
                )}
              </motion.div>

              <motion.div variants={contentVariants} initial="hidden" animate="visible" className="px-5 pb-5 pt-5">
                <motion.h2
                  variants={fadeUpVariants}
                  transition={{ duration: 0.3 }}
                  className="font-display text-2xl font-bold leading-tight text-gray-900 dark:text-white"
                >
                  {product.name}
                </motion.h2>

                {(product.type === 'COMBO' || product.bestseller || product.featured || product.discountedPrice !== null) && (
                  <motion.div
                    variants={fadeUpVariants}
                    transition={{ duration: 0.3 }}
                    className="mt-2.5 flex flex-wrap items-center gap-1.5"
                  >
                    {product.type === 'COMBO' && (
                      <span className="flex items-center gap-1 rounded-full bg-teal-600 px-2.5 py-1 text-xs font-semibold text-white">
                        <Layers className="h-3 w-3" />
                        Combo · {displayPrice}
                      </span>
                    )}
                    {product.discountedPrice !== null && (
                      <>
                        <span className="flex items-center gap-1 rounded-full bg-gold-600 px-2.5 py-1 text-xs font-semibold text-white">
                          <Percent className="h-3 w-3" />
                          Happy Hour
                        </span>
                        {savings > 0 && (
                          <span className="flex items-center gap-1 rounded-full bg-sage-600 px-2.5 py-1 text-xs font-semibold text-white">
                            Economize {currencyFormatter.format(savings)}
                          </span>
                        )}
                      </>
                    )}
                    {product.bestseller && (
                      <span className="flex items-center gap-1 rounded-full bg-brand-600 px-2.5 py-1 text-xs font-semibold text-white">
                        <Flame className="h-3 w-3" />
                        Mais pedido
                      </span>
                    )}
                    {product.featured && (
                      <span className="flex items-center gap-1 rounded-full bg-brand-500 px-2.5 py-1 text-xs font-semibold text-white">
                        <Star className="h-3 w-3 fill-current" />
                        Destaque
                      </span>
                    )}
                  </motion.div>
                )}

                {product.description && (
                  <motion.p
                    variants={fadeUpVariants}
                    transition={{ duration: 0.3 }}
                    className="mt-3.5 text-[15px] leading-relaxed text-gray-600 dark:text-stone-300"
                  >
                    {product.description}
                  </motion.p>
                )}

                {!unavailable && product.estimatedWaitMinutes !== null && (
                  <motion.span
                    variants={fadeUpVariants}
                    transition={{ duration: 0.3 }}
                    className="mt-3.5 flex items-center gap-1.5 text-sm text-gray-500 dark:text-stone-400"
                  >
                    <Clock className="h-4 w-4" />
                    Pronto em {product.estimatedWaitMinutes < 1 ? 'menos de 1 min' : `~${product.estimatedWaitMinutes} min`}
                  </motion.span>
                )}

                {unavailable && (
                  <motion.span
                    variants={fadeUpVariants}
                    transition={{ duration: 0.3 }}
                    className="mt-3.5 inline-block rounded-full bg-gold-100 px-3 py-1 text-xs font-medium text-gold-700 dark:bg-gold-500/10 dark:text-gold-400"
                  >
                    {product.soldOut ? 'Esgotado hoje' : 'Fora do horário'}
                  </motion.span>
                )}

                {product.type === 'COMBO' && product.combo && (
                  <motion.div variants={fadeUpVariants} transition={{ duration: 0.3 }} className="mt-5 space-y-3">
                    {product.combo.fixedItems.length > 0 && (
                      <div>
                        <span className="mb-1 block text-sm font-medium text-gray-800 dark:text-white">Itens inclusos</span>
                        <p className="text-sm text-gray-500 dark:text-stone-400">
                          {product.combo.fixedItems.map((item) => `${item.quantity}x ${item.productName}`).join(', ')}
                        </p>
                      </div>
                    )}
                    {product.combo.slots.length > 0 && (
                      <div>
                        <span className="mb-1 block text-sm font-medium text-gray-800 dark:text-white">Você escolhe</span>
                        <p className="text-sm text-gray-500 dark:text-stone-400">
                          {product.combo.slots.map((slot) => slot.name).join(' · ')}
                        </p>
                      </div>
                    )}
                  </motion.div>
                )}

                {product.modifierGroups.length > 0 && (
                  <motion.div variants={fadeUpVariants} transition={{ duration: 0.3 }} className="mt-5">
                    <span className="mb-1 block text-sm font-medium text-gray-800 dark:text-white">Personalize</span>
                    <div className="flex flex-wrap gap-1.5">
                      {product.modifierGroups.map((group) => (
                        <span
                          key={group.id}
                          className="rounded-full border border-gray-200 px-2.5 py-1 text-xs text-gray-600 dark:border-white/10 dark:text-stone-300"
                        >
                          {group.name}
                          {group.required && <span className="ml-1 text-gold-600 dark:text-gold-400">*</span>}
                        </span>
                      ))}
                    </div>
                  </motion.div>
                )}
              </motion.div>
            </div>

            {canOrder && !unavailable && (
              <div className="relative shrink-0 border-t border-gray-100 bg-white px-5 py-4 dark:border-white/10 dark:bg-stone-900">
                <AnimatePresence mode="wait" initial={false}>
                  {justAdded ? (
                    <motion.div
                      key="confirm"
                      initial={{ opacity: 0, scale: 0.9 }}
                      animate={{ opacity: 1, scale: 1 }}
                      exit={{ opacity: 0 }}
                      transition={{ duration: 0.15 }}
                      className="flex w-full items-center justify-center gap-2 rounded-full bg-sage-600 py-3.5 text-sm font-semibold text-white shadow-lg"
                    >
                      <motion.span
                        initial={{ scale: 0, rotate: -90 }}
                        animate={{ scale: 1, rotate: 0 }}
                        transition={{ type: 'spring', stiffness: 500, damping: 18 }}
                      >
                        <Check className="h-4 w-4" />
                      </motion.span>
                      Adicionado ao pedido!
                    </motion.div>
                  ) : quantity === 0 ? (
                    <motion.div key="add" initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }} transition={{ duration: 0.15 }}>
                      {!needsSelection && (
                        <div className="mb-3 flex items-center justify-between">
                          <span className="text-sm font-medium text-gray-600 dark:text-stone-300">Quantidade</span>
                          <div className="flex items-center gap-4 rounded-full bg-gray-100 px-3 py-1.5 dark:bg-white/10">
                            <button
                              type="button"
                              onClick={() => setPendingQty((q) => Math.max(1, q - 1))}
                              aria-label="Diminuir quantidade"
                              disabled={pendingQty <= 1}
                              className="flex h-7 w-7 items-center justify-center rounded-full text-gray-700 disabled:opacity-30 dark:text-white"
                            >
                              <Minus className="h-4 w-4" />
                            </button>
                            <AnimatePresence mode="popLayout" initial={false}>
                              <motion.span
                                key={pendingQty}
                                initial={{ scale: 1.3, opacity: 0 }}
                                animate={{ scale: 1, opacity: 1 }}
                                exit={{ scale: 0.6, opacity: 0 }}
                                transition={{ type: 'spring', stiffness: 500, damping: 20 }}
                                className="min-w-[1.5ch] text-center text-sm font-bold text-gray-900 dark:text-white"
                              >
                                {pendingQty}
                              </motion.span>
                            </AnimatePresence>
                            <button
                              type="button"
                              onClick={() => setPendingQty((q) => q + 1)}
                              aria-label="Aumentar quantidade"
                              className="flex h-7 w-7 items-center justify-center rounded-full text-gray-700 dark:text-white"
                            >
                              <Plus className="h-4 w-4" />
                            </button>
                          </div>
                        </div>
                      )}

                      <div className="relative">
                        <motion.div
                          aria-hidden
                          animate={{ opacity: [0.35, 0.65, 0.35], scale: [1, 1.04, 1] }}
                          transition={{ duration: 2.2, repeat: Infinity, ease: 'easeInOut' }}
                          className="absolute -inset-1 rounded-full bg-brand-500/50 blur-lg"
                        />
                        <motion.button
                          type="button"
                          whileTap={{ scale: 0.97 }}
                          onClick={handleCtaClick}
                          className="relative flex w-full items-center justify-center gap-2 rounded-full bg-gradient-to-r from-brand-600 to-brand-500 py-3.5 text-base font-semibold text-white shadow-lg shadow-brand-900/20"
                        >
                          {needsSelection ? (
                            <>
                              {product.type === 'COMBO' ? 'Montar combo' : 'Escolher opções'}
                              <Layers className="h-4 w-4" />
                            </>
                          ) : (
                            <>
                              Adicionar · {currencyFormatter.format(unitPrice * pendingQty)}
                              <Plus className="h-4 w-4" />
                            </>
                          )}
                        </motion.button>
                      </div>

                      {!needsSelection && product.estimatedWaitMinutes !== null && (
                        <p className="mt-2 flex items-center justify-center gap-1 text-center text-xs text-gray-400 dark:text-stone-500">
                          <Utensils className="h-3 w-3" />
                          Vai direto pra cozinha assim que você confirmar
                        </p>
                      )}
                    </motion.div>
                  ) : (
                    <motion.div
                      key="stepper"
                      initial={{ opacity: 0, y: 6 }}
                      animate={{ opacity: 1, y: 0 }}
                      exit={{ opacity: 0 }}
                      transition={{ duration: 0.15 }}
                      className="flex w-full items-center justify-between rounded-full bg-gradient-to-r from-brand-600 to-brand-500 px-4 py-3 text-white shadow-lg shadow-brand-900/20"
                    >
                      <button
                        type="button"
                        onClick={() => onDecrement(product.id)}
                        aria-label="Diminuir quantidade"
                        className="flex h-9 w-9 items-center justify-center rounded-full bg-white/15"
                      >
                        <Minus className="h-4 w-4" />
                      </button>
                      <span className="flex flex-col items-center leading-tight">
                        <span className="text-sm font-semibold">{quantity} no carrinho</span>
                        <span className="text-xs text-white/80">{currencyFormatter.format(unitPrice * quantity)}</span>
                      </span>
                      <button
                        type="button"
                        onClick={() => onIncrement(product.id)}
                        aria-label="Aumentar quantidade"
                        className="flex h-9 w-9 items-center justify-center rounded-full bg-white/15"
                      >
                        <Plus className="h-4 w-4" />
                      </button>
                    </motion.div>
                  )}
                </AnimatePresence>
              </div>
            )}
          </motion.div>
        </>
      )}
    </AnimatePresence>
  )
}
