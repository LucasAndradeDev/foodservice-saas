import { useQuery } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { useParams } from 'react-router-dom'
import { getPublicMenu } from '../api/publicMenu'

const currencyFormatter = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

function scrollToCategory(categoryId: string) {
  document.getElementById(`category-${categoryId}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

export function PublicMenuPage() {
  const { slug } = useParams<{ slug: string }>()
  const [search, setSearch] = useState('')

  const { data: menu, isLoading, isError } = useQuery({
    queryKey: ['publicMenu', slug],
    queryFn: () => getPublicMenu(slug!),
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

  return (
    <div className="min-h-screen bg-gray-50 pb-10">
      <header className="border-b border-gray-200 bg-white px-4 py-6 text-center">
        {menu.logo && (
          <img src={menu.logo} alt={menu.restaurantName} className="mx-auto mb-3 h-16 w-16 rounded-full object-cover" />
        )}
        <h1 className="text-xl font-semibold text-gray-800">{menu.restaurantName}</h1>
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
                <div key={product.id} className="flex gap-3 rounded-lg border border-gray-200 bg-white p-3">
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
                  </div>
                </div>
              ))}
            </div>
          </section>
        ))}
      </main>
    </div>
  )
}
