import { useMutation, useQuery } from '@tanstack/react-query'
import { Check } from 'lucide-react'
import { useMemo, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import { getPublicMenu, submitPublicOrder, type PublicMenuProduct } from '../api/publicMenu'
import { Modal } from '../components/Modal'

const currencyFormatter = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

function scrollToCategory(categoryId: string) {
  document.getElementById(`category-${categoryId}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

interface CartItem {
  productId: string
  productName: string
  unitPrice: number
  quantity: number
  observation: string
}

export function PublicMenuPage() {
  const { slug, tableId } = useParams<{ slug: string; tableId?: string }>()
  const [search, setSearch] = useState('')
  const [cart, setCart] = useState<CartItem[]>([])
  const [isCartOpen, setIsCartOpen] = useState(false)
  const [orderError, setOrderError] = useState<string | null>(null)
  const [orderSuccess, setOrderSuccess] = useState(false)
  const [justAddedId, setJustAddedId] = useState<string | null>(null)
  const justAddedTimeoutRef = useRef<number | null>(null)

  const { data: menu, isLoading, isError } = useQuery({
    queryKey: ['publicMenu', slug, tableId],
    queryFn: () => getPublicMenu(slug!, tableId),
    enabled: !!slug,
    retry: false,
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

  const submitOrderMutation = useMutation({
    mutationFn: () =>
      submitPublicOrder(
        slug!,
        tableId!,
        cart.map((item) => ({
          productId: item.productId,
          quantity: item.quantity,
          observation: item.observation.trim() || undefined,
        })),
      ),
    onSuccess: () => {
      setCart([])
      setIsCartOpen(false)
      setOrderError(null)
      setOrderSuccess(true)
      setTimeout(() => setOrderSuccess(false), 4000)
    },
    onError: () => setOrderError('Não foi possível enviar o pedido. Tente novamente.'),
  })

  function addToCart(product: PublicMenuProduct) {
    setOrderError(null)
    setCart((prev) => {
      const existingIndex = prev.findIndex((item) => item.productId === product.id && item.observation === '')
      if (existingIndex !== -1) {
        return prev.map((item, index) =>
          index === existingIndex ? { ...item, quantity: item.quantity + 1 } : item,
        )
      }
      return [
        ...prev,
        { productId: product.id, productName: product.name, unitPrice: product.price, quantity: 1, observation: '' },
      ]
    })

    if (justAddedTimeoutRef.current) window.clearTimeout(justAddedTimeoutRef.current)
    setJustAddedId(product.id)
    justAddedTimeoutRef.current = window.setTimeout(() => setJustAddedId(null), 1200)
  }

  function updateQuantity(index: number, delta: number) {
    setCart((prev) =>
      prev
        .map((item, i) => (i === index ? { ...item, quantity: item.quantity + delta } : item))
        .filter((item) => item.quantity > 0),
    )
  }

  function updateObservation(index: number, observation: string) {
    setCart((prev) => prev.map((item, i) => (i === index ? { ...item, observation } : item)))
  }

  function removeFromCart(index: number) {
    setCart((prev) => prev.filter((_, i) => i !== index))
  }

  if (isLoading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-gray-50 p-4">
        <p className="text-sm text-gray-500">Carregando cardápio...</p>
      </div>
    )
  }

  if (isError || !menu) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-gray-50 p-4">
        <p className="text-sm text-gray-500">Cardápio não encontrado.</p>
      </div>
    )
  }

  const accentColor = menu.primaryColor || undefined
  const canOrder = !!tableId && !!menu.table
  const cartCount = cart.reduce((sum, item) => sum + item.quantity, 0)
  const cartTotal = cart.reduce((sum, item) => sum + item.unitPrice * item.quantity, 0)

  return (
    <div className="min-h-screen bg-gray-50 pb-24">
      <header className="border-b border-gray-200 bg-white px-4 py-6 text-center">
        {menu.logo && (
          <img src={menu.logo} alt={menu.restaurantName} className="mx-auto mb-3 h-16 w-16 rounded-full object-cover" />
        )}
        <h1 className="text-xl font-semibold text-gray-800">{menu.restaurantName}</h1>
        {menu.table && (
          <p className="mt-1 text-sm text-gray-500">Mesa {menu.table.number}</p>
        )}
      </header>

      <div className="sticky top-0 z-10 border-b border-gray-200 bg-white px-4 py-3">
        <input
          type="text"
          placeholder="Buscar no cardápio..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="mb-3 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none"
        />
        {menu.categories.length > 1 && (
          <div className="flex gap-2 overflow-x-auto">
            {menu.categories.map((category) => (
              <button
                key={category.id}
                type="button"
                onClick={() => scrollToCategory(category.id)}
                style={{ borderColor: accentColor, color: accentColor }}
                className="shrink-0 rounded-full border border-gray-300 px-3 py-1 text-sm text-gray-700"
              >
                {category.name}
              </button>
            ))}
          </div>
        )}
      </div>

      <main className="mx-auto max-w-2xl px-4 py-4">
        {filteredCategories.length === 0 && (
          <p className="mt-6 text-center text-sm text-gray-500">Nenhum produto encontrado.</p>
        )}

        {filteredCategories.map((category) => (
          <section key={category.id} id={`category-${category.id}`} className="mb-8 scroll-mt-32">
            <h2 className="mb-3 text-lg font-semibold" style={{ color: accentColor }}>
              {category.name}
            </h2>
            <div className="space-y-3">
              {category.products.map((product) => (
                <div
                  key={product.id}
                  className={`flex gap-3 rounded-lg border border-gray-200 bg-white p-3 ${product.soldOut ? 'opacity-60' : ''}`}
                >
                  {product.imageUrl && (
                    <img
                      src={product.imageUrl}
                      alt={product.name}
                      className="h-20 w-20 shrink-0 rounded-md object-cover"
                    />
                  )}
                  <div className="flex-1">
                    <div className="flex items-start justify-between gap-2">
                      <span className="font-medium text-gray-800">{product.name}</span>
                      <span className="shrink-0 font-semibold" style={{ color: accentColor }}>
                        {currencyFormatter.format(product.price)}
                      </span>
                    </div>
                    {product.description && (
                      <p className="mt-1 text-sm text-gray-500">{product.description}</p>
                    )}
                    {product.soldOut ? (
                      <span className="mt-2 inline-block rounded-md bg-amber-100 px-3 py-1 text-xs font-medium text-amber-700">
                        Esgotado hoje
                      </span>
                    ) : (
                      canOrder && (
                        <button
                          type="button"
                          onClick={() => addToCart(product)}
                          style={justAddedId === product.id ? undefined : { borderColor: accentColor, color: accentColor }}
                          className={`mt-2 flex items-center gap-1 rounded-md border px-3 py-1 text-xs font-medium transition-colors duration-150 ${
                            justAddedId === product.id
                              ? 'border-green-600 bg-green-50 text-green-700'
                              : 'hover:bg-gray-50'
                          }`}
                        >
                          {justAddedId === product.id ? (
                            <>
                              <Check className="h-3.5 w-3.5" />
                              Adicionado!
                            </>
                          ) : (
                            'Adicionar'
                          )}
                        </button>
                      )
                    )}
                  </div>
                </div>
              ))}
            </div>
          </section>
        ))}
      </main>

      {orderSuccess && (
        <div className="fixed inset-x-0 top-4 z-30 mx-auto w-fit rounded-full bg-green-600 px-4 py-2 text-sm text-white shadow-lg">
          Pedido enviado! Já foi pra cozinha.
        </div>
      )}

      {canOrder && cartCount > 0 && !isCartOpen && (
        <button
          type="button"
          onClick={() => setIsCartOpen(true)}
          style={{ backgroundColor: accentColor }}
          className="fixed inset-x-4 bottom-4 z-20 flex items-center justify-between rounded-lg bg-brand-600 px-4 py-3 text-sm font-medium text-white shadow-lg"
        >
          <span>
            {cartCount} {cartCount === 1 ? 'item' : 'itens'}
          </span>
          <span>Ver pedido · {currencyFormatter.format(cartTotal)}</span>
        </button>
      )}

      {isCartOpen && (
        <Modal title="Seu pedido" onClose={() => setIsCartOpen(false)}>
          {cart.length === 0 ? (
            <p className="text-sm text-gray-500">Seu carrinho está vazio.</p>
          ) : (
            <>
              <ul className="mb-4 divide-y divide-gray-100">
                {cart.map((item, index) => (
                  <li key={index} className="py-3">
                    <div className="flex items-start justify-between gap-2">
                      <span className="font-medium text-gray-800">{item.productName}</span>
                      <span className="text-sm text-gray-600">
                        {currencyFormatter.format(item.unitPrice * item.quantity)}
                      </span>
                    </div>
                    <div className="mt-2 flex items-center gap-2">
                      <button
                        type="button"
                        onClick={() => updateQuantity(index, -1)}
                        className="h-7 w-7 rounded-md border border-gray-300 text-gray-600 hover:bg-gray-100"
                      >
                        −
                      </button>
                      <span className="w-6 text-center text-sm">{item.quantity}</span>
                      <button
                        type="button"
                        onClick={() => updateQuantity(index, 1)}
                        className="h-7 w-7 rounded-md border border-gray-300 text-gray-600 hover:bg-gray-100"
                      >
                        +
                      </button>
                      <button
                        type="button"
                        onClick={() => removeFromCart(index)}
                        className="ml-auto text-sm text-red-600 hover:underline"
                      >
                        Remover
                      </button>
                    </div>
                    <input
                      type="text"
                      placeholder="Observação (opcional)"
                      maxLength={255}
                      value={item.observation}
                      onChange={(e) => updateObservation(index, e.target.value)}
                      className="mt-2 w-full rounded-md border border-gray-300 px-2 py-1 text-xs focus:border-brand-500 focus:outline-none"
                    />
                  </li>
                ))}
              </ul>

              <div className="mb-3 flex items-center justify-between text-sm font-semibold text-gray-800">
                <span>Total</span>
                <span>{currencyFormatter.format(cartTotal)}</span>
              </div>

              {orderError && <p className="mb-3 text-sm text-red-600">{orderError}</p>}

              <button
                type="button"
                onClick={() => submitOrderMutation.mutate()}
                disabled={submitOrderMutation.isPending}
                className="w-full rounded-md bg-brand-600 px-3 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50"
              >
                Enviar pedido
              </button>
            </>
          )}
        </Modal>
      )}
    </div>
  )
}
