import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AnimatePresence, motion } from 'framer-motion'
import { ShoppingBag } from 'lucide-react'
import { useMemo, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import { getPublicMenu, submitPublicOrder, type PublicMenuProduct } from '../api/publicMenu'
import { createTableRequest, type TableRequestType } from '../api/tableRequests'
import { CartDrawer } from './publicMenu/CartDrawer'
import { CategoryNav } from './publicMenu/CategoryNav'
import { getCategoryIcon } from './publicMenu/categoryIcons'
import { MenuHero } from './publicMenu/MenuHero'
import { ModifierSheet } from './publicMenu/ModifierSheet'
import { ProductCard } from './publicMenu/ProductCard'
import { TableRequestButtons } from './publicMenu/TableRequestButtons'
import { usePublicMenuTheme } from './publicMenu/usePublicMenuTheme'
import { currencyFormatter, modifiersTotal, sameModifiers, type CartItem, type SelectedModifier } from './publicMenu/utils'

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
  const queryClient = useQueryClient()
  const { theme, toggleTheme } = usePublicMenuTheme()
  const [search, setSearch] = useState('')
  const [cart, setCart] = useState<CartItem[]>([])
  const [isCartOpen, setIsCartOpen] = useState(false)
  const [orderError, setOrderError] = useState<string | null>(null)
  const [orderSuccess, setOrderSuccess] = useState(false)
  const [activeModifierProduct, setActiveModifierProduct] = useState<PublicMenuProduct | null>(null)
  const [requestedTypes, setRequestedTypes] = useState<Set<TableRequestType>>(new Set())
  const requestTimeoutsRef = useRef<Partial<Record<TableRequestType, number>>>({})

  const { data: menu, isLoading, isError } = useQuery({
    queryKey: ['publicMenu', slug, tableId],
    queryFn: () => getPublicMenu(slug!, tableId),
    enabled: !!slug,
    retry: false,
    refetchInterval: POLL_INTERVAL_MS,
    refetchIntervalInBackground: true,
  })

  const filteredCategories = useMemo(() => {
    if (!menu) return []
    const term = search.trim().toLowerCase()
    if (!term) return menu.categories

    return menu.categories
      .map((category) => ({
        ...category,
        products: category.products.filter(
          (product) =>
            product.name.toLowerCase().includes(term) ||
            (product.description ?? '').toLowerCase().includes(term),
        ),
      }))
      .filter((category) => category.products.length > 0)
  }, [menu, search])

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
        })),
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
          unitPrice: product.price,
          quantity: 1,
          observation: '',
          selectedModifiers,
        },
      ]
    })
  }

  function handleAddClick(product: PublicMenuProduct) {
    if (product.modifierGroups.length > 0) {
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

  const accentColor = menu.primaryColor || undefined
  const canOrder = !!tableId && !!menu.table
  const canRequestBill = canOrder && !!menu.table?.hasDeliveredItems
  const cartCount = cart.reduce((sum, item) => sum + item.quantity, 0)
  const cartTotal = cart.reduce(
    (sum, item) => sum + (item.unitPrice + modifiersTotal(item.selectedModifiers)) * item.quantity,
    0,
  )

  return (
    <div className={`${themeClass} min-h-screen bg-gray-50 pb-24 dark:bg-stone-950`}>
      <MenuHero
        restaurantName={menu.restaurantName}
        logo={menu.logo}
        tableNumber={menu.table?.number}
        accentColor={accentColor}
        theme={theme}
        onToggleTheme={toggleTheme}
      />

      <CategoryNav
        categories={menu.categories}
        search={search}
        onSearchChange={setSearch}
        accentColor={accentColor}
      />

      <main className="mx-auto max-w-2xl px-4 py-4">
        {filteredCategories.length === 0 && (
          <p className="mt-6 text-center text-sm text-gray-500 dark:text-stone-400">Nenhum produto encontrado.</p>
        )}

        {filteredCategories.map((category) => {
          const CategoryIcon = getCategoryIcon(category.name)
          return (
          <section key={category.id} id={`category-${category.id}`} className="mb-8 scroll-mt-32">
            <h2
              className="mb-3 flex items-center gap-2 text-lg font-semibold text-brand-600 dark:text-brand-400"
              style={{ color: accentColor }}
            >
              <CategoryIcon className="h-5 w-5" />
              {category.name}
            </h2>
            <motion.div variants={containerVariants} initial="hidden" animate="visible" className="space-y-3">
              {category.products.map((product) => (
                <motion.div key={product.id} variants={itemVariants} transition={{ duration: 0.3 }}>
                  <ProductCard
                    product={product}
                    quantity={product.modifierGroups.length > 0 ? 0 : (cartQuantities.get(product.id) ?? 0)}
                    accentColor={accentColor}
                    canOrder={canOrder}
                    onAdd={handleAddClick}
                    onIncrement={(productId) => updateQuantityByProductId(productId, 1)}
                    onDecrement={(productId) => updateQuantityByProductId(productId, -1)}
                  />
                </motion.div>
              ))}
            </motion.div>
          </section>
          )
        })}
      </main>

      <AnimatePresence>
        {orderSuccess && (
          <motion.div
            initial={{ opacity: 0, y: -12 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -12 }}
            transition={{ duration: 0.2 }}
            className="fixed inset-x-0 top-4 z-30 mx-auto w-fit rounded-full bg-green-600 px-4 py-2 text-sm text-white shadow-lg"
          >
            Pedido enviado! Já foi pra cozinha.
          </motion.div>
        )}
      </AnimatePresence>

      {canOrder && (
        <TableRequestButtons
          canRequestBill={canRequestBill}
          requestedTypes={requestedTypes}
          isPending={tableRequestMutation.isPending}
          pendingType={tableRequestMutation.variables}
          accentColor={accentColor}
          cartCount={cartCount}
          isCartOpen={isCartOpen}
          onRequest={(type) => tableRequestMutation.mutate(type)}
        />
      )}

      {canOrder && cartCount > 0 && !isCartOpen && (
        <button
          type="button"
          onClick={() => setIsCartOpen(true)}
          style={{ backgroundColor: accentColor }}
          className="fixed inset-x-4 bottom-4 z-20 flex items-center justify-between gap-2 rounded-2xl bg-brand-600 px-4 py-3 text-sm font-medium text-white shadow-lg dark:shadow-black/50"
        >
          <span className="flex items-center gap-2">
            <ShoppingBag className="h-4 w-4" />
            {cartCount} {cartCount === 1 ? 'item' : 'itens'}
          </span>
          <span>Ver pedido · {currencyFormatter.format(cartTotal)}</span>
        </button>
      )}

      <CartDrawer
        isOpen={isCartOpen}
        cart={cart}
        cartTotal={cartTotal}
        orderError={orderError}
        isSubmitting={submitOrderMutation.isPending}
        accentColor={accentColor}
        onClose={() => setIsCartOpen(false)}
        onUpdateQuantity={updateQuantity}
        onUpdateObservation={updateObservation}
        onRemove={removeFromCart}
        onSubmit={() => submitOrderMutation.mutate()}
      />

      <ModifierSheet
        product={activeModifierProduct}
        accentColor={accentColor}
        onClose={() => setActiveModifierProduct(null)}
        onConfirm={handleConfirmModifiers}
      />
    </div>
  )
}
