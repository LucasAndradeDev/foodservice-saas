import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import {
  Ban,
  CheckCircle2,
  ChevronDown,
  Circle,
  ListChecks,
  MoreVertical,
  Package,
  Pencil,
  Plus,
  Power,
  Search,
  Star,
  Trash2,
} from 'lucide-react'
import { useEffect, useRef, useState, type ChangeEvent, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { listCategories } from '../api/categories'
import {
  createProduct,
  deleteProduct,
  listProducts,
  updateProduct,
  uploadProductImage,
  type Product,
} from '../api/products'
import { useAuth } from '../auth/AuthContext'
import { Modal } from '../components/Modal'

const currencyFormatter = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

export function ProductsPage() {
  const { user } = useAuth()
  const canManage = user?.role === 'OWNER' || user?.role === 'MANAGER'
  const queryClient = useQueryClient()

  const { data: categories } = useQuery({
    queryKey: ['categories'],
    queryFn: () => listCategories(),
  })

  const [searchInput, setSearchInput] = useState('')
  const [search, setSearch] = useState('')
  const [categoryFilter, setCategoryFilter] = useState('')
  const [statusFilter, setStatusFilter] = useState<'active' | 'inactive' | 'all'>('active')

  useEffect(() => {
    const timeout = setTimeout(() => setSearch(searchInput), 300)
    return () => clearTimeout(timeout)
  }, [searchInput])

  const { data: products, isLoading } = useQuery({
    queryKey: ['products', categoryFilter, search, statusFilter],
    queryFn: () =>
      listProducts({
        categoryId: categoryFilter || undefined,
        search: search || undefined,
        active: statusFilter === 'all' ? undefined : statusFilter === 'active',
      }),
  })

  const [editingProduct, setEditingProduct] = useState<Product | null>(null)
  const [isCreating, setIsCreating] = useState(false)
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [photoUrl, setPhotoUrl] = useState('')
  const [price, setPrice] = useState('')
  const [costPrice, setCostPrice] = useState('')
  const [categoryId, setCategoryId] = useState('')
  const [featured, setFeatured] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [listError, setListError] = useState<string | null>(null)
  const [showNoCategoryNotice, setShowNoCategoryNotice] = useState(false)
  const [isUploading, setIsUploading] = useState(false)

  const createMutation = useMutation({
    mutationFn: createProduct,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['products'] })
      closeForm()
    },
    onError: () => setError('Não foi possível salvar. Verifique os dados e tente novamente.'),
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: Parameters<typeof updateProduct>[1] }) =>
      updateProduct(id, payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['products'] }),
  })

  const deleteMutation = useMutation({
    mutationFn: deleteProduct,
    onSuccess: () => {
      setListError(null)
      queryClient.invalidateQueries({ queryKey: ['products'] })
    },
    onError: (err) => {
      if (isAxiosError(err) && err.response?.status === 403) {
        setListError('Não é possível excluir: já foi usado em pedidos. Desative-o.')
      } else {
        setListError('Não foi possível excluir o produto.')
      }
    },
  })

  function categoryName(id: string) {
    return categories?.find((category) => category.id === id)?.name ?? '—'
  }

  function openCreateForm() {
    if (!categories || categories.length === 0) {
      setShowNoCategoryNotice(true)
      return
    }
    setName('')
    setDescription('')
    setPhotoUrl('')
    setPrice('')
    setCostPrice('')
    setCategoryId(categories[0].id)
    setFeatured(false)
    setError(null)
    setIsCreating(true)
  }

  function openEditForm(product: Product) {
    setEditingProduct(product)
    setName(product.name)
    setDescription(product.description ?? '')
    setPhotoUrl(product.imageUrl ?? '')
    setPrice(String(product.price))
    setCostPrice(product.costPrice !== null ? String(product.costPrice) : '')
    setCategoryId(product.categoryId)
    setFeatured(product.featured)
    setError(null)
  }

  function closeForm() {
    setIsCreating(false)
    setEditingProduct(null)
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    const payload = {
      name,
      description,
      imageUrl: photoUrl,
      price: Number(price),
      costPrice: costPrice ? Number(costPrice) : undefined,
      categoryId,
      featured,
    }
    if (editingProduct) {
      updateMutation.mutate(
        { id: editingProduct.id, payload },
        { onSuccess: closeForm, onError: () => setError('Não foi possível salvar. Verifique os dados e tente novamente.') },
      )
    } else {
      createMutation.mutate(payload)
    }
  }

  function toggleActive(product: Product) {
    updateMutation.mutate({ id: product.id, payload: { active: !product.active } })
  }

  function toggleSoldOut(product: Product) {
    updateMutation.mutate({ id: product.id, payload: { soldOut: !product.soldOutToday } })
  }

  function toggleFeatured(product: Product) {
    updateMutation.mutate({ id: product.id, payload: { featured: !product.featured } })
  }

  function handleDelete(product: Product) {
    const confirmed = window.confirm(`Excluir o produto "${product.name}"? Essa ação não pode ser desfeita.`)
    if (confirmed) {
      setListError(null)
      deleteMutation.mutate(product.id)
    }
  }

  async function handleFileChange(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) return

    setIsUploading(true)
    setError(null)
    try {
      const url = await uploadProductImage(file)
      setPhotoUrl(url)
    } catch {
      setError('Não foi possível enviar a imagem. Tente novamente.')
    } finally {
      setIsUploading(false)
    }
  }

  const isFormOpen = isCreating || editingProduct !== null

  return (
    <div>
      <div className="mb-4 rounded-xl border border-gray-200 bg-white p-4 shadow-xs">
        <div className="mb-4 flex items-center justify-between gap-3">
          <h1 className="flex items-center gap-2 text-lg font-semibold text-gray-800">
            <Package className="h-5 w-5 text-brand-600" />
            Produtos
          </h1>
          {canManage && (
            <button
              type="button"
              onClick={openCreateForm}
              className="flex items-center gap-1.5 rounded-lg bg-brand-600 px-3.5 py-2 text-sm font-medium text-white hover:bg-brand-700"
            >
              <Plus className="h-4 w-4" />
              Novo produto
            </button>
          )}
        </div>

        <div className="flex flex-col gap-3 sm:flex-row">
          <div className="relative flex-1 sm:max-w-xs">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" />
            <input
              type="text"
              placeholder="Buscar por nome..."
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              className="w-full rounded-lg border border-gray-200 bg-gray-50 py-2 pl-9 pr-3 text-sm focus:border-brand-500 focus:bg-white focus:outline-none"
            />
          </div>
          <div className="relative w-full sm:w-auto">
            <select
              value={categoryFilter}
              onChange={(e) => setCategoryFilter(e.target.value)}
              className="w-full appearance-none rounded-lg border border-gray-200 bg-gray-50 py-2 pl-3 pr-8 text-sm focus:border-brand-500 focus:bg-white focus:outline-none sm:w-auto"
            >
              <option value="">Todas as categorias</option>
              {categories?.map((category) => (
                <option key={category.id} value={category.id}>
                  {category.name}
                </option>
              ))}
            </select>
            <ChevronDown className="pointer-events-none absolute right-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" />
          </div>
          <div className="relative w-full sm:w-auto">
            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value as 'active' | 'inactive' | 'all')}
              className="w-full appearance-none rounded-lg border border-gray-200 bg-gray-50 py-2 pl-3 pr-8 text-sm focus:border-brand-500 focus:bg-white focus:outline-none sm:w-auto"
            >
              <option value="active">Ativos</option>
              <option value="inactive">Inativos</option>
              <option value="all">Todos</option>
            </select>
            <ChevronDown className="pointer-events-none absolute right-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" />
          </div>
        </div>
      </div>

      {showNoCategoryNotice && categories?.length === 0 && (
        <div className="mb-4 flex flex-col gap-2 rounded-md border border-amber-200 bg-amber-50 px-4 py-2 text-sm text-amber-800 sm:flex-row sm:items-center sm:justify-between">
          <span>Crie uma categoria antes de cadastrar produtos.</span>
          <Link to="/categories" className="font-medium underline">
            Ir para Categorias
          </Link>
        </div>
      )}

      {listError && <p className="mb-4 text-sm text-red-600">{listError}</p>}

      {isLoading && <p className="text-sm text-gray-500">Carregando...</p>}

      {products && products.length === 0 && <p className="text-sm text-gray-500">Nenhum produto encontrado.</p>}

      {products && products.length > 0 && (
        <>
          {/* Mobile: stacked cards */}
          <div className="space-y-2 sm:hidden">
            {products.map((product) => (
              <div key={product.id} className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm">
                <div className="mb-2 flex items-start justify-between gap-2">
                  <span className="flex items-center gap-3">
                    {product.imageUrl ? (
                      <img src={product.imageUrl} alt="" className="h-14 w-14 shrink-0 rounded-lg object-cover" />
                    ) : (
                      <span className="h-14 w-14 shrink-0 rounded-lg bg-gray-100" />
                    )}
                    <span>
                      <span className="block font-medium text-gray-800">{product.name}</span>
                      <span className="block text-sm text-gray-500">
                        {categoryName(product.categoryId)} · {currencyFormatter.format(product.price)}
                      </span>
                    </span>
                  </span>
                  <span className="flex shrink-0 flex-col items-end gap-1">
                    <span
                      className={`flex items-center gap-1 rounded-full px-2 py-0.5 text-xs ${
                        product.active ? 'bg-green-100 text-green-700' : 'bg-gray-100 text-gray-500'
                      }`}
                    >
                      {product.active ? <CheckCircle2 className="h-3 w-3" /> : <Circle className="h-3 w-3" />}
                      {product.active ? 'Ativo' : 'Inativo'}
                    </span>
                    {product.soldOutToday && (
                      <span className="flex items-center gap-1 rounded-full bg-amber-100 px-2 py-0.5 text-xs text-amber-700">
                        <Ban className="h-3 w-3" />
                        Esgotado hoje
                      </span>
                    )}
                    {product.featured && (
                      <span className="flex items-center gap-1 rounded-full bg-yellow-100 px-2 py-0.5 text-xs text-yellow-700">
                        <Star className="h-3 w-3" />
                        Destaque
                      </span>
                    )}
                  </span>
                </div>
                {canManage && (
                  <ProductActionButtons
                    product={product}
                    onEdit={() => openEditForm(product)}
                    onToggleActive={() => toggleActive(product)}
                    onToggleSoldOut={() => toggleSoldOut(product)}
                    onToggleFeatured={() => toggleFeatured(product)}
                    onDelete={() => handleDelete(product)}
                  />
                )}
              </div>
            ))}
          </div>

          {/* Desktop: table */}
          <div className="hidden overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm sm:block">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 text-left text-gray-500">
                <tr>
                  <th className="px-4 py-2 font-medium">Nome</th>
                  <th className="px-4 py-2 font-medium">Categoria</th>
                  <th className="px-4 py-2 font-medium">Preço</th>
                  <th className="px-4 py-2 font-medium">Status</th>
                  {canManage && <th className="px-4 py-2" />}
                </tr>
              </thead>
              <tbody>
                {products.map((product) => (
                  <tr key={product.id} className="border-t border-gray-100">
                    <td className="px-4 py-2 text-gray-800">
                      <span className="flex items-center gap-3">
                        {product.imageUrl ? (
                          <img src={product.imageUrl} alt="" className="h-14 w-14 shrink-0 rounded-lg object-cover" />
                        ) : (
                          <span className="h-14 w-14 shrink-0 rounded-lg bg-gray-100" />
                        )}
                        {product.name}
                      </span>
                    </td>
                    <td className="px-4 py-2 text-gray-600">{categoryName(product.categoryId)}</td>
                    <td className="px-4 py-2 text-gray-600">{currencyFormatter.format(product.price)}</td>
                    <td className="px-4 py-2">
                      <span className="flex w-fit flex-col items-start gap-1">
                        <span
                          className={`flex items-center gap-1 rounded-full px-2 py-0.5 text-xs ${
                            product.active ? 'bg-green-100 text-green-700' : 'bg-gray-100 text-gray-500'
                          }`}
                        >
                          {product.active ? <CheckCircle2 className="h-3 w-3" /> : <Circle className="h-3 w-3" />}
                          {product.active ? 'Ativo' : 'Inativo'}
                        </span>
                        {product.soldOutToday && (
                          <span className="flex items-center gap-1 rounded-full bg-amber-100 px-2 py-0.5 text-xs text-amber-700">
                            <Ban className="h-3 w-3" />
                            Esgotado hoje
                          </span>
                        )}
                        {product.featured && (
                          <span className="flex items-center gap-1 rounded-full bg-yellow-100 px-2 py-0.5 text-xs text-yellow-700">
                            <Star className="h-3 w-3" />
                            Destaque
                          </span>
                        )}
                      </span>
                    </td>
                    {canManage && (
                      <td className="px-4 py-2 text-right">
                        <ProductActionButtons
                          product={product}
                          onEdit={() => openEditForm(product)}
                          onToggleActive={() => toggleActive(product)}
                          onToggleSoldOut={() => toggleSoldOut(product)}
                          onToggleFeatured={() => toggleFeatured(product)}
                          onDelete={() => handleDelete(product)}
                          align="end"
                        />
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}

      {isFormOpen && (
        <Modal title={editingProduct ? 'Editar produto' : 'Novo produto'} onClose={closeForm}>
          <form onSubmit={handleSubmit}>
            <label className="mb-1 block text-sm font-medium text-gray-700" htmlFor="productName">
              Nome
            </label>
            <input
              id="productName"
              type="text"
              required
              maxLength={100}
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none"
            />

            <label className="mb-1 block text-sm font-medium text-gray-700" htmlFor="productDescription">
              Descrição
            </label>
            <textarea
              id="productDescription"
              maxLength={255}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none"
              rows={2}
            />

            <label className="mb-1 block text-sm font-medium text-gray-700" htmlFor="productPhotoUrl">
              Foto do produto <span className="font-normal text-gray-400">(opcional)</span>
            </label>
            <div className="mb-1 flex flex-col gap-2 sm:flex-row">
              <input
                id="productPhotoUrl"
                type="text"
                placeholder="Cole uma URL..."
                maxLength={500}
                value={photoUrl}
                onChange={(e) => setPhotoUrl(e.target.value)}
                className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none"
              />
              <label className="flex shrink-0 cursor-pointer items-center justify-center rounded-md border border-gray-300 px-3 py-2 text-sm text-gray-700 hover:bg-gray-100">
                {isUploading ? 'Enviando...' : 'Enviar do dispositivo'}
                <input type="file" accept="image/jpeg,image/png,image/webp" className="hidden" onChange={handleFileChange} disabled={isUploading} />
              </label>
            </div>
            {photoUrl && (
              <img src={photoUrl} alt="" className="mb-4 h-16 w-16 rounded object-cover" />
            )}
            {!photoUrl && <div className="mb-4" />}

            <label className="mb-1 block text-sm font-medium text-gray-700" htmlFor="productPrice">
              Preço
            </label>
            <input
              id="productPrice"
              type="number"
              required
              min="0.01"
              step="0.01"
              value={price}
              onChange={(e) => setPrice(e.target.value)}
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none"
            />

            <label className="mb-1 block text-sm font-medium text-gray-700" htmlFor="productCostPrice">
              Preço de custo <span className="font-normal text-gray-400">(opcional)</span>
            </label>
            <input
              id="productCostPrice"
              type="number"
              min="0"
              step="0.01"
              value={costPrice}
              onChange={(e) => setCostPrice(e.target.value)}
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none"
            />

            <label className="mb-1 block text-sm font-medium text-gray-700" htmlFor="productCategory">
              Categoria
            </label>
            <select
              id="productCategory"
              required
              value={categoryId}
              onChange={(e) => setCategoryId(e.target.value)}
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none"
            >
              <option value="" disabled>
                Selecione uma categoria
              </option>
              {categories?.map((category) => (
                <option key={category.id} value={category.id}>
                  {category.name}
                </option>
              ))}
            </select>

            <label className="mb-4 flex items-center gap-2 text-sm text-gray-700">
              <input
                type="checkbox"
                checked={featured}
                onChange={(e) => setFeatured(e.target.checked)}
                className="rounded border-gray-300 text-brand-600 focus:ring-brand-500"
              />
              Marcar como destaque no cardápio digital
            </label>

            {error && <p className="mb-4 text-sm text-red-600">{error}</p>}

            <button
              type="submit"
              disabled={createMutation.isPending || updateMutation.isPending}
              className="w-full rounded-md bg-brand-600 px-3 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50"
            >
              Salvar
            </button>
          </form>
        </Modal>
      )}
    </div>
  )
}

interface ProductActionButtonsProps {
  product: Product
  onEdit: () => void
  onToggleActive: () => void
  onToggleSoldOut: () => void
  onToggleFeatured: () => void
  onDelete: () => void
  align?: 'start' | 'end'
}

function ProductActionButtons({
  product,
  onEdit,
  onToggleActive,
  onToggleSoldOut,
  onToggleFeatured,
  onDelete,
  align = 'start',
}: ProductActionButtonsProps) {
  const [isMenuOpen, setIsMenuOpen] = useState(false)
  const menuRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!isMenuOpen) return
    function handleClickOutside(event: MouseEvent) {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
        setIsMenuOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [isMenuOpen])

  function runAndClose(action: () => void) {
    action()
    setIsMenuOpen(false)
  }

  return (
    <div className={`flex items-center gap-1 ${align === 'end' ? 'justify-end' : ''}`}>
      <button
        type="button"
        onClick={onEdit}
        title="Editar"
        aria-label="Editar"
        className="rounded-md p-1.5 text-gray-500 hover:bg-gray-100 hover:text-brand-700"
      >
        <Pencil className="h-4 w-4" />
      </button>

      <div className="relative" ref={menuRef}>
        <button
          type="button"
          onClick={() => setIsMenuOpen((open) => !open)}
          title="Mais ações"
          aria-label="Mais ações"
          className="rounded-md p-1.5 text-gray-500 hover:bg-gray-100 hover:text-gray-700"
        >
          <MoreVertical className="h-4 w-4" />
        </button>

        {isMenuOpen && (
          <div
            className={`absolute z-10 mt-1 w-52 overflow-hidden rounded-md border border-gray-200 bg-white py-1 text-sm shadow-lg ${
              align === 'end' ? 'right-0' : 'left-0'
            }`}
          >
            <Link
              to={`/products/${product.id}/modifiers`}
              onClick={() => setIsMenuOpen(false)}
              className="flex items-center gap-2 px-3 py-2 text-gray-700 hover:bg-gray-50"
            >
              <ListChecks className="h-4 w-4 text-gray-400" />
              Modificadores
            </Link>
            <button
              type="button"
              onClick={() => runAndClose(onToggleActive)}
              className="flex w-full items-center gap-2 px-3 py-2 text-left text-gray-700 hover:bg-gray-50"
            >
              <Power className="h-4 w-4 text-gray-400" />
              {product.active ? 'Desativar' : 'Ativar'}
            </button>
            <button
              type="button"
              onClick={() => runAndClose(onToggleSoldOut)}
              className="flex w-full items-center gap-2 px-3 py-2 text-left text-gray-700 hover:bg-gray-50"
            >
              <Ban className="h-4 w-4 text-gray-400" />
              {product.soldOutToday ? 'Remover "esgotado hoje"' : 'Marcar como esgotado hoje'}
            </button>
            <button
              type="button"
              onClick={() => runAndClose(onToggleFeatured)}
              className="flex w-full items-center gap-2 px-3 py-2 text-left text-gray-700 hover:bg-gray-50"
            >
              <Star className="h-4 w-4 text-gray-400" />
              {product.featured ? 'Remover destaque' : 'Marcar como destaque'}
            </button>
            <div className="my-1 border-t border-gray-100" />
            <button
              type="button"
              onClick={() => runAndClose(onDelete)}
              className="flex w-full items-center gap-2 px-3 py-2 text-left text-red-600 hover:bg-red-50"
            >
              <Trash2 className="h-4 w-4" />
              Excluir
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
