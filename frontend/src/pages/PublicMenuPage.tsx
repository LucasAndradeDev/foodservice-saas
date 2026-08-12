import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AnimatePresence, motion } from 'framer-motion'
import { CalendarClock, ShoppingBag, Ticket } from 'lucide-react'
import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { getPublicMenu, redeemCoupon, removeCoupon, submitPublicOrder, type PublicMenuProduct } from '../api/publicMenu'
import { createTableRequest, type TableRequestType } from '../api/tableRequests'
import { sameComboSelections, type SelectedComboSlot } from '../utils/combos'
import { CartDrawer } from './publicMenu/CartDrawer'
import { CategoryBanner } from './publicMenu/CategoryBanner'
import { CategoryNav } from './publicMenu/CategoryNav'
import { AmbientBackground } from './publicMenu/AmbientBackground'
import { ComboSheet } from './publicMenu/ComboSheet'
import { FeaturedCarousel } from './publicMenu/FeaturedCarousel'
import { MenuHero } from './publicMenu/MenuHero'
import { ModifierSheet } from './publicMenu/ModifierSheet'
import { OrderStatusPanel } from './publicMenu/OrderStatusPanel'
import { PixPaymentModal } from './publicMenu/PixPaymentModal'
import { ProductCard } from './publicMenu/ProductCard'
import { ProductDetailModal } from './publicMenu/ProductDetailModal'
import { ReservationFormModal } from './publicMenu/ReservationFormModal'
import { TableRequestButtons } from './publicMenu/TableRequestButtons'
import { usePublicMenuTheme } from './publicMenu/usePublicMenuTheme'
import {
  computeDiscountAmount,
  currencyFormatter,
  modifiersTotal,
  roundCurrency,
  sameModifiers,
  type CartItem,
  type SelectedModifier,
} from './publicMenu/utils'

const TABLE_REQUEST_COOLDOWN_MS = 60000
const POLL_INTERVAL_MS = 4000

const containerVariants = {
  hidden: {},
  visible: { transition: { staggerChildren: 0.04 } },
}

const itemVariants = {
  hidden: { opacity: 0, y: 8 },
  visible: { opacity: 1, y: 0 },
}

export function PublicMenuPage() {
  const { slug, tableId } = useParams<{ slug: string; tableId?: string }>()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { theme, toggleTheme } = usePublicMenuTheme()
  const [search, setSearch] = useState('')
  const [cart, setCart] = useState<CartItem[]>([])
  const [isCartOpen, setIsCartOpen] = useState(false)
  const [orderError, setOrderError] = useState<string | null>(null)
  const [orderSuccess, setOrderSuccess] = useState(false)
  const [reorderNotice, setReorderNotice] = useState<string | null>(null)
  const [activeModifierProduct, setActiveModifierProduct] = useState<PublicMenuProduct | null>(null)
  const [activeComboProduct, setActiveComboProduct] = useState<PublicMenuProduct | null>(null)
  const [detailProduct, setDetailProduct] = useState<PublicMenuProduct | null>(null)
  const [requestedTypes, setRequestedTypes] = useState<Set<TableRequestType>>(new Set())
  const requestTimeoutsRef = useRef<Partial<Record<TableRequestType, number>>>({})
  const [couponCode, setCouponCode] = useState('')
  const [couponError, setCouponError] = useState<string | null>(null)
  const [isReservationModalOpen, setIsReservationModalOpen] = useState(false)
  const [isPixModalOpen, setIsPixModalOpen] = useState(false)
  const [customerPhone, setCustomerPhone] = useState('')
  const [collapsedCategories, setCollapsedCategories] = useState<Set<string>>(new Set())

  function toggleCategory(categoryId: string) {
    setCollapsedCategories((prev) => {
      const next = new Set(prev)
      if (next.has(categoryId)) next.delete(categoryId)
      else next.add(categoryId)
      return next
    })
  }

  const { data: menu, isLoading, isError } = useQuery({
    queryKey: ['publicMenu', slug, tableId],
    queryFn: () => getPublicMenu(slug!, tableId),
    enabled: !!slug,
    retry: false,
    refetchInterval: POLL_INTERVAL_MS,
    refetchIntervalInBackground: true,
  })

  // Only redirects when a tab we've watched be open during this very session just closed —
  // never on a fresh page load. That's what keeps a new customer scanning the table's QR from
  // ever landing on the previous customer's feedback prompt instead of the ordering menu.
  const currentTabId = menu?.table?.currentTabId ?? null
  const seenTabIdRef = useRef<string | null>(null)
  useEffect(() => {
    if (slug && seenTabIdRef.current && !currentTabId) {
      navigate(`/feedback/${slug}/${seenTabIdRef.current}`, { replace: true })
    }
    seenTabIdRef.current = currentTabId
  }, [slug, currentTabId, navigate])

  // Combos is a promotional category, so it's pinned first regardless of the restaurant's
  // configured category order — everything else keeps its relative order.
  const orderedCategories = useMemo(() => {
    if (!menu) return []
    return [...menu.categories].sort((a, b) => {
      const aIsCombos = a.name.trim().toLowerCase() === 'combos'
      const bIsCombos = b.name.trim().toLowerCase() === 'combos'
      if (aIsCombos === bIsCombos) return 0
      return aIsCombos ? -1 : 1
    })
  }, [menu])

  const filteredCategories = useMemo(() => {
    if (!menu) return []
    const term = search.trim().toLowerCase()
    if (!term) return orderedCategories

    return orderedCategories
      .map((category) => ({
        ...category,
        products: category.products.filter(
          (product) =>
            product.name.toLowerCase().includes(term) ||
            (product.description ?? '').toLowerCase().includes(term),
        ),
      }))
      .filter((category) => category.products.length > 0)
  }, [menu, orderedCategories, search])

  const isSearching = search.trim().length > 0

  const highlightedProducts = useMemo(() => {
    if (!menu) return []
    const seen = new Set<string>()
    const result: PublicMenuProduct[] = []
    menu.categories.forEach((category) => {
      category.products.forEach((product) => {
        if (seen.has(product.id)) return
        if (!(product.featured || product.bestseller)) return
        if (product.soldOut || !product.availableNow) return
        seen.add(product.id)
        result.push(product)
      })
    })
    return result
  }, [menu])

  const cartQuantities = useMemo(() => {
    const map = new Map<string, number>()
    cart.forEach((item) => {
      if (item.observation === '') {
        map.set(item.productId, (map.get(item.productId) ?? 0) + item.quantity)
      }
    })
    return map
  }, [cart])

  const submitOrderMutation = useMutation({
    mutationFn: () =>
      submitPublicOrder(
        slug!,
        tableId!,
        cart.map((item) => ({
          productId: item.productId,
          quantity: item.quantity,
          observation: item.observation.trim() || undefined,
          selectedOptionIds: item.selectedModifiers.map((modifier) => modifier.optionId),
          slotSelections: item.comboSelections?.map((selection) => ({
            slotId: selection.slotId,
            selectedProductId: selection.productId,
          })),
        })),
        !currentTabId ? customerPhone.trim() : undefined,
      ),
    onSuccess: () => {
      setCart([])
      setIsCartOpen(false)
      setOrderError(null)
      setOrderSuccess(true)
      setTimeout(() => setOrderSuccess(false), 4000)
      queryClient.invalidateQueries({ queryKey: ['publicMenu', slug, tableId] })
    },
    onError: () => setOrderError('Não foi possível enviar o pedido. Tente novamente.'),
  })

  const applyCouponMutation = useMutation({
    mutationFn: () => redeemCoupon(slug!, tableId!, couponCode.trim()),
    onSuccess: () => {
      setCouponError(null)
      setCouponCode('')
      queryClient.invalidateQueries({ queryKey: ['publicMenu', slug, tableId] })
    },
    onError: () => setCouponError('Cupom inválido, expirado ou esgotado.'),
  })

  const removeCouponMutation = useMutation({
    mutationFn: () => removeCoupon(slug!, tableId!),
    onSuccess: () => {
      setCouponError(null)
      queryClient.invalidateQueries({ queryKey: ['publicMenu', slug, tableId] })
    },
    onError: () => setCouponError('Não foi possível remover o cupom. Tente novamente.'),
  })

  const tableRequestMutation = useMutation({
    mutationFn: (type: TableRequestType) => createTableRequest(slug!, tableId!, type),
    onSuccess: (_, type) => {
      setRequestedTypes((prev) => new Set(prev).add(type))
      if (requestTimeoutsRef.current[type]) window.clearTimeout(requestTimeoutsRef.current[type])
      requestTimeoutsRef.current[type] = window.setTimeout(() => {
        setRequestedTypes((prev) => {
          const next = new Set(prev)
          next.delete(type)
          return next
        })
      }, TABLE_REQUEST_COOLDOWN_MS)
    },
  })

  function addToCart(product: PublicMenuProduct, selectedModifiers: SelectedModifier[] = []) {
    setOrderError(null)
    setCart((prev) => {
      const existingIndex = prev.findIndex(
        (item) =>
          item.productId === product.id &&
          item.observation === '' &&
          sameModifiers(item.selectedModifiers, selectedModifiers),
      )
      if (existingIndex !== -1) {
        return prev.map((item, index) =>
          index === existingIndex ? { ...item, quantity: item.quantity + 1 } : item,
        )
      }
      return [
        ...prev,
        {
          productId: product.id,
          productName: product.name,
          unitPrice: product.discountedPrice ?? product.price,
          quantity: 1,
          observation: '',
          selectedModifiers,
        },
      ]
    })
  }

  function handleAddClick(product: PublicMenuProduct) {
    if (product.type === 'COMBO') {
      setActiveComboProduct(product)
    } else if (product.modifierGroups.length > 0) {
      setActiveModifierProduct(product)
    } else {
      addToCart(product)
    }
  }

  function handleConfirmModifiers(selectedModifiers: SelectedModifier[]) {
    if (activeModifierProduct) {
      addToCart(activeModifierProduct, selectedModifiers)
    }
    setActiveModifierProduct(null)
  }

  function handleConfirmCombo(selections: SelectedComboSlot[], unitPrice: number) {
    const product = activeComboProduct
    if (!product) return
    setOrderError(null)
    setCart((prev) => {
      const existingIndex = prev.findIndex(
        (item) => item.productId === product.id && item.observation === '' && sameComboSelections(item.comboSelections ?? [], selections),
      )
      if (existingIndex !== -1) {
        return prev.map((item, index) => (index === existingIndex ? { ...item, quantity: item.quantity + 1 } : item))
      }
      return [
        ...prev,
        {
          productId: product.id,
          productName: product.name,
          unitPrice,
          quantity: 1,
          observation: '',
          selectedModifiers: [],
          comboSelections: selections,
        },
      ]
    })
    setActiveComboProduct(null)
  }

  function updateQuantity(index: number, delta: number) {
    setCart((prev) =>
      prev
        .map((item, i) => (i === index ? { ...item, quantity: item.quantity + delta } : item))
        .filter((item) => item.quantity > 0),
    )
  }

  function updateQuantityByProductId(productId: string, delta: number) {
    const index = cart.findIndex((item) => item.productId === productId && item.observation === '')
    if (index !== -1) updateQuantity(index, delta)
  }

  function updateObservation(index: number, observation: string) {
    setCart((prev) => prev.map((item, i) => (i === index ? { ...item, observation } : item)))
  }

  function removeFromCart(index: number) {
    setCart((prev) => prev.filter((_, i) => i !== index))
  }

  function handleReorder() {
    if (!menu?.table) return

    const productsById = new Map(menu.categories.flatMap((category) => category.products).map((product) => [product.id, product]))
    const skippedNames: string[] = []

    const items: CartItem[] = []
    menu.table.lastOrderItems.forEach((historyItem) => {
      const product = productsById.get(historyItem.productId)
      if (!product || product.soldOut || !product.availableNow || product.type === 'COMBO') {
        skippedNames.push(historyItem.productName)
        return
      }

      const selectedModifiers: SelectedModifier[] = []
      historyItem.modifiers.forEach((historyModifier) => {
        const group = product.modifierGroups.find((g) => g.name === historyModifier.groupName)
        const option = group?.options.find((o) => o.name === historyModifier.optionName)
        if (group && option) {
          selectedModifiers.push({
            groupId: group.id,
            groupName: group.name,
            optionId: option.id,
            optionName: option.name,
            priceDelta: option.priceDelta,
          })
        }
      })

      items.push({
        productId: product.id,
        productName: product.name,
        unitPrice: product.discountedPrice ?? product.price,
        quantity: historyItem.quantity,
        observation: historyItem.observation ?? '',
        selectedModifiers,
      })
    })

    setCart(items)
    setIsCartOpen(true)
    setOrderError(null)
    if (skippedNames.length > 0) {
      const names = skippedNames.join(', ')
      setReorderNotice(
        skippedNames.length === 1
          ? `${names} não está mais disponível e ficou de fora do pedido.`
          : `${names} não estão mais disponíveis e ficaram de fora do pedido.`,
      )
      setTimeout(() => setReorderNotice(null), 6000)
    }
  }

  const themeClass = theme === 'dark' ? 'dark' : ''

  if (isLoading) {
    return (
      <div className={`${themeClass} flex min-h-screen items-center justify-center bg-gray-50 p-4 dark:bg-stone-950`}>
        <p className="text-sm text-gray-500 dark:text-stone-400">Carregando cardápio...</p>
      </div>
    )
  }

  if (isError || !menu) {
    return (
      <div className={`${themeClass} flex min-h-screen items-center justify-center bg-gray-50 p-4 dark:bg-stone-950`}>
        <p className="text-sm text-gray-500 dark:text-stone-400">Cardápio não encontrado.</p>
      </div>
    )
  }

  const canOrder = !!tableId && !!menu.table
  const canRequestBill = canOrder && !!menu.table?.hasDeliveredItems
  const canPayWithPix = canRequestBill && !!menu.table?.pixConfigured
  const cartCount = cart.reduce((sum, item) => sum + item.quantity, 0)
  const cartSubtotal = cart.reduce(
    (sum, item) => sum + (item.unitPrice + modifiersTotal(item.selectedModifiers)) * item.quantity,
    0,
  )
  const cartDiscountAmount = computeDiscountAmount(menu.table?.discountType ?? null, menu.table?.discountValue ?? null, cartSubtotal)
  const cartTotal = roundCurrency(cartSubtotal - cartDiscountAmount)

  return (
    <div className={`${themeClass} isolate bg-gray-50 dark:bg-stone-950`}>
      <AmbientBackground theme={theme} />
      <div className="sticky top-0 z-20">
        <MenuHero
          restaurantName={menu.restaurantName}
          logo={menu.logo}
          tableNumber={menu.table?.number}
          theme={theme}
          onToggleTheme={toggleTheme}
        />

        <CategoryNav categories={orderedCategories} search={search} onSearchChange={setSearch} />
      </div>

      {!tableId && (
        <div className="mx-auto max-w-2xl px-4 pt-3">
          <button
            type="button"
            onClick={() => setIsReservationModalOpen(true)}
            className="flex w-full items-center justify-center gap-2 rounded-xl border border-dashed border-brand-300 px-3 py-2.5 text-sm font-medium text-brand-600 hover:bg-brand-50 dark:border-brand-500/30 dark:text-brand-400 dark:hover:bg-brand-500/10"
          >
            <CalendarClock className="h-4 w-4" />
            Reservar mesa
          </button>
        </div>
      )}

      {menu.table?.discountAppliedLabel && (
        <div className="mx-auto max-w-2xl px-4 pt-3">
          <div className="flex items-center gap-2 rounded-xl bg-sage-100 px-3 py-2 text-sm font-medium text-sage-700 dark:bg-sage-500/10 dark:text-sage-400">
            <Ticket className="h-4 w-4 shrink-0" />
            {menu.table.discountAppliedLabel}
          </div>
        </div>
      )}

      {!isSearching && highlightedProducts.length > 0 && (
        <FeaturedCarousel
          products={highlightedProducts}
          cartQuantities={cartQuantities}
          canOrder={canOrder}
          onAdd={handleAddClick}
          onIncrement={(productId) => updateQuantityByProductId(productId, 1)}
          onDecrement={(productId) => updateQuantityByProductId(productId, -1)}
          onOpenDetail={setDetailProduct}
        />
      )}

      <main className="mx-auto max-w-2xl px-4 pt-6 pb-1">
        {filteredCategories.length === 0 && (
          <p className="mt-6 text-center text-sm text-gray-500 dark:text-stone-400">Nenhum produto encontrado.</p>
        )}

        {filteredCategories.map((category) => {
          const isOpen = isSearching || !collapsedCategories.has(category.id)
          return (
          <section key={category.id} id={`category-${category.id}`} className="mb-8 scroll-mt-[220px]">
            <CategoryBanner category={category} isOpen={isOpen} onToggle={() => toggleCategory(category.id)} />
            <AnimatePresence initial={false}>
              {isOpen && (
                <motion.div
                  initial={{ height: 0, opacity: 0 }}
                  animate={{ height: 'auto', opacity: 1 }}
                  exit={{ height: 0, opacity: 0 }}
                  transition={{ duration: 0.25, ease: 'easeInOut' }}
                  className="overflow-hidden"
                >
                  <motion.div variants={containerVariants} initial="hidden" animate="visible" className="space-y-3">
                    {category.products.map((product) => (
                      <motion.div key={product.id} variants={itemVariants} transition={{ duration: 0.3 }}>
                        <ProductCard
                          product={product}
                          quantity={product.modifierGroups.length > 0 || product.type === 'COMBO' ? 0 : (cartQuantities.get(product.id) ?? 0)}
                          canOrder={canOrder}
                          onAdd={handleAddClick}
                          onIncrement={(productId) => updateQuantityByProductId(productId, 1)}
                          onDecrement={(productId) => updateQuantityByProductId(productId, -1)}
                          onOpenDetail={setDetailProduct}
                        />
                      </motion.div>
                    ))}
                  </motion.div>
                </motion.div>
              )}
            </AnimatePresence>
          </section>
          )
        })}
      </main>

      <footer className="mx-auto max-w-2xl px-4 pt-1 pb-24 text-center text-[11px] text-gray-300 dark:text-stone-700">
        <Link to="/terms" target="_blank" className="hover:underline">
          Termos de Uso
        </Link>
        {' · '}
        <Link to="/privacy" target="_blank" className="hover:underline">
          Política de Privacidade
        </Link>
      </footer>

      <AnimatePresence>
        {orderSuccess && (
          <motion.div
            initial={{ opacity: 0, y: -12 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -12 }}
            transition={{ duration: 0.2 }}
            className="fixed inset-x-0 top-4 z-30 mx-auto w-fit rounded-full bg-sage-600 px-4 py-2 text-sm text-white shadow-lg"
          >
            Pedido enviado! Já foi pra cozinha.
          </motion.div>
        )}
      </AnimatePresence>

      <AnimatePresence>
        {reorderNotice && (
          <motion.div
            initial={{ opacity: 0, y: -12 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -12 }}
            transition={{ duration: 0.2 }}
            className="fixed inset-x-4 top-4 z-30 mx-auto w-fit max-w-md rounded-2xl bg-gold-500 px-4 py-2 text-center text-sm text-white shadow-lg"
          >
            {reorderNotice}
          </motion.div>
        )}
      </AnimatePresence>

      {canOrder && (
        <TableRequestButtons
          canRequestBill={canRequestBill}
          canPayWithPix={canPayWithPix}
          requestedTypes={requestedTypes}
          isPending={tableRequestMutation.isPending}
          pendingType={tableRequestMutation.variables}
          cartCount={cartCount}
          isCartOpen={isCartOpen}
          onRequest={(type) => tableRequestMutation.mutate(type)}
          onPayWithPix={() => setIsPixModalOpen(true)}
        />
      )}

      {canOrder && menu.table && (
        <OrderStatusPanel
          items={menu.table.orderItems}
          lifted={cartCount > 0 && !isCartOpen}
          hasLastOrder={menu.table.lastOrderItems.length > 0}
          onReorder={handleReorder}
        />
      )}

      <AnimatePresence>
        {canOrder && cartCount > 0 && !isCartOpen && (
          <motion.button
            type="button"
            onClick={() => setIsCartOpen(true)}
            initial={{ opacity: 0, y: 24 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: 24 }}
            transition={{ duration: 0.2 }}
            whileTap={{ scale: 0.97 }}
            className="fixed inset-x-4 bottom-4 z-20 mx-auto flex max-w-md items-center gap-3 rounded-full bg-gradient-to-r from-brand-600 to-brand-500 py-2.5 pl-2.5 pr-4 text-white shadow-xl shadow-brand-900/30 ring-1 ring-white/10 dark:shadow-black/50"
          >
            <span className="relative flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-white/15">
              <ShoppingBag className="h-4 w-4" />
              <AnimatePresence mode="popLayout" initial={false}>
                <motion.span
                  key={cartCount}
                  initial={{ scale: 1.4, opacity: 0 }}
                  animate={{ scale: 1, opacity: 1 }}
                  exit={{ scale: 0.6, opacity: 0 }}
                  transition={{ type: 'spring', stiffness: 500, damping: 20 }}
                  className="absolute -right-1 -top-1 flex h-5 min-w-5 items-center justify-center rounded-full bg-white px-1 text-[11px] font-bold text-brand-600"
                >
                  {cartCount}
                </motion.span>
              </AnimatePresence>
            </span>
            <span className="flex flex-1 flex-col items-start leading-tight">
              <span className="text-[11px] font-medium uppercase tracking-wide text-white/75">Ver pedido</span>
              <span className="text-sm font-semibold">{cartCount === 1 ? '1 item' : `${cartCount} itens`}</span>
            </span>
            <span className="text-base font-bold">{currencyFormatter.format(cartTotal)}</span>
          </motion.button>
        )}
      </AnimatePresence>

      <CartDrawer
        isOpen={isCartOpen}
        cart={cart}
        cartSubtotal={cartSubtotal}
        cartDiscountAmount={cartDiscountAmount}
        cartTotal={cartTotal}
        orderError={orderError}
        isSubmitting={submitOrderMutation.isPending}
        onClose={() => setIsCartOpen(false)}
        onUpdateQuantity={updateQuantity}
        onUpdateObservation={updateObservation}
        onRemove={removeFromCart}
        onSubmit={() => submitOrderMutation.mutate()}
        discountAppliedLabel={menu.table?.discountAppliedLabel ?? null}
        couponCode={couponCode}
        onCouponCodeChange={setCouponCode}
        onApplyCoupon={() => applyCouponMutation.mutate()}
        isApplyingCoupon={applyCouponMutation.isPending}
        onRemoveCoupon={() => removeCouponMutation.mutate()}
        isRemovingCoupon={removeCouponMutation.isPending}
        couponError={couponError}
        showPhoneField={!currentTabId}
        customerPhone={customerPhone}
        onCustomerPhoneChange={setCustomerPhone}
      />

      <ModifierSheet
        product={activeModifierProduct}
        onClose={() => setActiveModifierProduct(null)}
        onConfirm={handleConfirmModifiers}
      />

      <ComboSheet
        product={activeComboProduct}
        onClose={() => setActiveComboProduct(null)}
        onConfirm={handleConfirmCombo}
      />

      <ProductDetailModal
        product={detailProduct}
        quantity={
          detailProduct && (detailProduct.modifierGroups.length > 0 || detailProduct.type === 'COMBO')
            ? 0
            : (cartQuantities.get(detailProduct?.id ?? '') ?? 0)
        }
        canOrder={canOrder}
        onClose={() => setDetailProduct(null)}
        onAdd={handleAddClick}
        onIncrement={(productId) => updateQuantityByProductId(productId, 1)}
        onDecrement={(productId) => updateQuantityByProductId(productId, -1)}
      />

      {isReservationModalOpen && slug && (
        <ReservationFormModal slug={slug} onClose={() => setIsReservationModalOpen(false)} />
      )}

      {isPixModalOpen && slug && tableId && (
        <PixPaymentModal slug={slug} tableId={tableId} onClose={() => setIsPixModalOpen(false)} />
      )}
    </div>
  )
}
