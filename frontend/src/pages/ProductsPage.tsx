import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import { AnimatePresence, motion } from 'framer-motion'
import {
  Ban,
  CalendarClock,
  CheckCircle2,
  Circle,
  Filter,
  ListChecks,
  MoreVertical,
  Package,
  Pencil,
  Plus,
  Power,
  Search,
  Square,
  SquareCheck,
  SquareMinus,
  Star,
  Tag,
  Trash2,
} from 'lucide-react'
import { useEffect, useLayoutEffect, useRef, useState, type ChangeEvent, type FormEvent } from 'react'
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
import { Button } from '../components/Button'
import { Dropdown } from '../components/Dropdown'
import { Table, TableHead, TableRow } from '../components/Table'
import { Modal } from '../components/Modal'
import { SectionTabs } from '../components/SectionTabs'

const MENU_TABS = [
  { to: '/products', label: 'Produtos' },
  { to: '/categories', label: 'Categorias' },
  { to: '/combos', label: 'Combos' },
]

const currencyFormatter = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

const STATUS_FILTER_OPTIONS: { value: 'active' | 'inactive' | 'all'; label: string }[] = [
  { value: 'active', label: 'Ativos' },
  { value: 'inactive', label: 'Inativos' },
  { value: 'all', label: 'Todos' },
]

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
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())
  const [isBulkProcessing, setIsBulkProcessing] = useState(false)
  const [bulkError, setBulkError] = useState<string | null>(null)

  const categoryFilterOptions = [
    { value: '', label: 'Todas as categorias' },
    ...(categories ?? []).map((category) => ({ value: category.id, label: category.name })),
  ]

  useEffect(() => {
    const timeout = setTimeout(() => setSearch(searchInput), 300)
    return () => clearTimeout(timeout)
  }, [searchInput])

  useEffect(() => {
    setSelectedIds(new Set())
  }, [categoryFilter, search, statusFilter])

  const { data: products, isLoading } = useQuery({
    queryKey: ['products', categoryFilter, search, statusFilter],
    queryFn: () =>
      listProducts({
        categoryId: categoryFilter || undefined,
        search: search || undefined,
        active: statusFilter === 'all' ? undefined : statusFilter === 'active',
        type: 'SIMPLE',
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

  function toggleSelect(id: string) {
    setSelectedIds((prev) => {
      const next = new Set(prev)
      if (next.has(id)) {
        next.delete(id)
      } else {
        next.add(id)
      }
      return next
    })
  }

  function toggleSelectAll() {
    if (!products) return
    setSelectedIds((prev) => (prev.size === products.length ? new Set() : new Set(products.map((product) => product.id))))
  }

  async function runBulkUpdate(payload: Parameters<typeof updateProduct>[1]) {
    const ids = Array.from(selectedIds)
    setBulkError(null)
    setIsBulkProcessing(true)
    const results = await Promise.allSettled(ids.map((id) => updateProduct(id, payload)))
    setIsBulkProcessing(false)
    setSelectedIds(new Set())
    queryClient.invalidateQueries({ queryKey: ['products'] })
    const failed = results.filter((result) => result.status === 'rejected').length
    if (failed > 0) {
      setBulkError(`${failed} de ${ids.length} produtos não puderam ser atualizados.`)
    }
  }

  async function handleBulkDelete() {
    const ids = Array.from(selectedIds)
    const confirmed = window.confirm(`Excluir ${ids.length} produtos selecionados? Essa ação não pode ser desfeita.`)
    if (!confirmed) return

    setBulkError(null)
    setIsBulkProcessing(true)
    const results = await Promise.allSettled(ids.map((id) => deleteProduct(id)))
    setIsBulkProcessing(false)
    setSelectedIds(new Set())
    queryClient.invalidateQueries({ queryKey: ['products'] })
    const failed = results.filter((result) => result.status === 'rejected').length
    if (failed > 0) {
      setBulkError(`${failed} de ${ids.length} produtos não puderam ser excluídos (provavelmente já usados em pedidos).`)
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
      <SectionTabs tabs={MENU_TABS} />

      <div className="mb-4 rounded-xl border border-gray-200 bg-white p-4 shadow-xs dark:border-white/10 dark:bg-stone-900">
        <div className="mb-4 flex items-center justify-between gap-3">
          <h1 className="flex items-center gap-2 text-lg font-semibold text-gray-800 dark:text-white">
            <Package className="h-5 w-5 text-brand-600 dark:text-brand-400" />
            Produtos
          </h1>
          {canManage && (
            <Button type="button" onClick={openCreateForm}>
              <Plus className="h-4 w-4" />
              Novo produto
            </Button>
          )}
        </div>

        <div className="flex flex-col gap-3 sm:flex-row">
          <div className="relative flex-1 sm:max-w-xs">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400 dark:text-stone-500" />
            <input
              type="text"
              placeholder="Buscar por nome..."
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              className="w-full rounded-lg border border-gray-200 bg-gray-50 py-2 pl-9 pr-3 text-sm focus:border-brand-500 focus:bg-white focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400 dark:focus:bg-stone-800"
            />
          </div>
          <Dropdown
            value={categoryFilter}
            options={categoryFilterOptions}
            onChange={setCategoryFilter}
            icon={Tag}
            panelClassName="w-56"
            mobileTitle="Filtrar por categoria"
          />
          <Dropdown
            value={statusFilter}
            options={STATUS_FILTER_OPTIONS}
            onChange={setStatusFilter}
            icon={Filter}
            panelClassName="w-40"
            mobileTitle="Filtrar por status"
          />
        </div>
      </div>

      {showNoCategoryNotice && categories?.length === 0 && (
        <div className="mb-4 flex flex-col gap-2 rounded-md border border-amber-200 bg-amber-50 px-4 py-2 text-sm text-amber-800 sm:flex-row sm:items-center sm:justify-between dark:border-amber-500/30 dark:bg-amber-500/10 dark:text-amber-400">
          <span>Crie uma categoria antes de cadastrar produtos.</span>
          <Link to="/categories" className="font-medium underline">
            Ir para Categorias
          </Link>
        </div>
      )}

      {listError && <p className="mb-4 text-sm text-wine-600 dark:text-wine-400">{listError}</p>}
      {bulkError && <p className="mb-4 text-sm text-wine-600 dark:text-wine-400">{bulkError}</p>}

      {isLoading && <p className="text-sm text-gray-500 dark:text-stone-400">Carregando...</p>}

      {products && products.length === 0 && (
        <p className="text-sm text-gray-500 dark:text-stone-400">Nenhum produto encontrado.</p>
      )}

      {canManage && products && products.length > 0 && (
        <div className="mb-2 flex min-h-[52px] flex-wrap items-center justify-between gap-2 rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm dark:border-white/10 dark:bg-stone-900">
          <button
            type="button"
            onClick={toggleSelectAll}
            className="flex items-center gap-2 text-gray-600 hover:text-gray-800 dark:text-stone-400 dark:hover:text-stone-200"
          >
            <SelectionCheckbox
              state={selectedIds.size === 0 ? 'unchecked' : selectedIds.size === products.length ? 'checked' : 'indeterminate'}
            />
            {selectedIds.size > 0 ? `${selectedIds.size} selecionado(s)` : 'Selecionar todos'}
          </button>
          {selectedIds.size > 0 && (
            <div className="flex items-center gap-3">
              <button
                type="button"
                onClick={() => setSelectedIds(new Set())}
                className="text-gray-500 hover:underline dark:text-stone-400"
              >
                Limpar seleção
              </button>
              <BulkActionsMenu
                disabled={isBulkProcessing}
                onActivate={() => runBulkUpdate({ active: true })}
                onDeactivate={() => runBulkUpdate({ active: false })}
                onMarkSoldOut={() => runBulkUpdate({ soldOut: true })}
                onUnmarkSoldOut={() => runBulkUpdate({ soldOut: false })}
                onMarkFeatured={() => runBulkUpdate({ featured: true })}
                onUnmarkFeatured={() => runBulkUpdate({ featured: false })}
                onDelete={handleBulkDelete}
              />
            </div>
          )}
        </div>
      )}

      {products && products.length > 0 && (
        <>
          {/* Mobile: stacked cards */}
          <div className="space-y-2 sm:hidden">
            {products.map((product) => (
              <div
                key={product.id}
                className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-white/10 dark:bg-stone-900"
              >
                <div className="mb-2 flex items-start justify-between gap-2">
                  <span className="flex items-center gap-3">
                    {canManage && (
                      <button type="button" onClick={() => toggleSelect(product.id)} className="shrink-0">
                        <SelectionCheckbox state={selectedIds.has(product.id) ? 'checked' : 'unchecked'} />
                      </button>
                    )}
                    {product.imageUrl ? (
                      <img src={product.imageUrl} alt="" className="h-14 w-14 shrink-0 rounded-lg object-cover" />
                    ) : (
                      <span className="h-14 w-14 shrink-0 rounded-lg bg-gray-100 dark:bg-white/10" />
                    )}
                    <span>
                      <span className="block font-medium text-gray-800 dark:text-white">{product.name}</span>
                      <span className="block text-sm text-gray-500 dark:text-stone-400">
                        {categoryName(product.categoryId)} · {currencyFormatter.format(product.price)}
                      </span>
                    </span>
                  </span>
                  <span className="flex shrink-0 flex-col items-end gap-1">
                    <span
                      className={`flex items-center gap-1 rounded-full px-2 py-0.5 text-xs ${
                        product.active
                          ? 'bg-sage-100 text-sage-700 dark:bg-sage-500/10 dark:text-sage-400'
                          : 'bg-gray-100 text-gray-500 dark:bg-white/10 dark:text-stone-400'
                      }`}
                    >
                      {product.active ? <CheckCircle2 className="h-3 w-3" /> : <Circle className="h-3 w-3" />}
                      {product.active ? 'Ativo' : 'Inativo'}
                    </span>
                    {product.soldOutToday && (
                      <span className="flex items-center gap-1 rounded-full bg-gold-100 px-2 py-0.5 text-xs text-gold-700 dark:bg-gold-500/10 dark:text-gold-400">
                        <Ban className="h-3 w-3" />
                        Esgotado hoje
                      </span>
                    )}
                    {!product.soldOutToday && !product.availableNow && (
                      <span className="flex items-center gap-1 rounded-full bg-gold-100 px-2 py-0.5 text-xs text-gold-700 dark:bg-gold-500/10 dark:text-gold-400">
                        <CalendarClock className="h-3 w-3" />
                        Fora do horário
                      </span>
                    )}
                    {product.featured && (
                      <span className="flex items-center gap-1 rounded-full bg-yellow-100 px-2 py-0.5 text-xs text-yellow-700 dark:bg-yellow-500/10 dark:text-yellow-400">
                        <Star className="h-3 w-3" />
                        Destaque
                      </span>
                    )}
                  </span>
                </div>
                {canManage && (
                  <AnimatePresence>
                    {selectedIds.size < 2 && (
                      <motion.div
                        initial={{ opacity: 0, scale: 0.9 }}
                        animate={{ opacity: 1, scale: 1 }}
                        exit={{ opacity: 0, scale: 0.9 }}
                        transition={{ duration: 0.15 }}
                      >
                        <ProductActionButtons
                          product={product}
                          onEdit={() => openEditForm(product)}
                          onToggleActive={() => toggleActive(product)}
                          onToggleSoldOut={() => toggleSoldOut(product)}
                          onToggleFeatured={() => toggleFeatured(product)}
                          onDelete={() => handleDelete(product)}
                        />
                      </motion.div>
                    )}
                  </AnimatePresence>
                )}
              </div>
            ))}
          </div>

          {/* Desktop: table */}
          <div className="hidden sm:block">
            <Table>
              <TableHead>
                <tr>
                  {canManage && <th className="w-10 px-4 py-2" />}
                  <th className="px-4 py-2 font-medium">Nome</th>
                  <th className="px-4 py-2 font-medium">Categoria</th>
                  <th className="px-4 py-2 font-medium">Preço</th>
                  <th className="px-4 py-2 font-medium">Status</th>
                  {canManage && <th className="w-24 px-4 py-2" />}
                </tr>
              </TableHead>
              <tbody>
                {products.map((product) => (
                  <TableRow key={product.id}>
                    {canManage && (
                      <td className="px-4 py-2">
                        <button type="button" onClick={() => toggleSelect(product.id)}>
                          <SelectionCheckbox state={selectedIds.has(product.id) ? 'checked' : 'unchecked'} />
                        </button>
                      </td>
                    )}
                    <td className="px-4 py-2 text-gray-800 dark:text-white">
                      <span className="flex items-center gap-3">
                        {product.imageUrl ? (
                          <img src={product.imageUrl} alt="" className="h-14 w-14 shrink-0 rounded-lg object-cover" />
                        ) : (
                          <span className="h-14 w-14 shrink-0 rounded-lg bg-gray-100 dark:bg-white/10" />
                        )}
                        {product.name}
                      </span>
                    </td>
                    <td className="px-4 py-2 text-gray-600 dark:text-stone-400">{categoryName(product.categoryId)}</td>
                    <td className="px-4 py-2 text-gray-600 dark:text-stone-400">{currencyFormatter.format(product.price)}</td>
                    <td className="px-4 py-2">
                      <span className="flex w-fit flex-col items-start gap-1">
                        <span
                          className={`flex items-center gap-1 rounded-full px-2 py-0.5 text-xs ${
                            product.active
                              ? 'bg-sage-100 text-sage-700 dark:bg-sage-500/10 dark:text-sage-400'
                              : 'bg-gray-100 text-gray-500 dark:bg-white/10 dark:text-stone-400'
                          }`}
                        >
                          {product.active ? <CheckCircle2 className="h-3 w-3" /> : <Circle className="h-3 w-3" />}
                          {product.active ? 'Ativo' : 'Inativo'}
                        </span>
                        {product.soldOutToday && (
                          <span className="flex items-center gap-1 rounded-full bg-gold-100 px-2 py-0.5 text-xs text-gold-700 dark:bg-gold-500/10 dark:text-gold-400">
                            <Ban className="h-3 w-3" />
                            Esgotado hoje
                          </span>
                        )}
                        {!product.soldOutToday && !product.availableNow && (
                          <span className="flex items-center gap-1 rounded-full bg-gold-100 px-2 py-0.5 text-xs text-gold-700 dark:bg-gold-500/10 dark:text-gold-400">
                            <CalendarClock className="h-3 w-3" />
                            Fora do horário
                          </span>
                        )}
                        {product.featured && (
                          <span className="flex items-center gap-1 rounded-full bg-yellow-100 px-2 py-0.5 text-xs text-yellow-700 dark:bg-yellow-500/10 dark:text-yellow-400">
                            <Star className="h-3 w-3" />
                            Destaque
                          </span>
                        )}
                      </span>
                    </td>
                    {canManage && (
                      <td className="w-24 px-4 py-2 text-right">
                        <AnimatePresence>
                          {selectedIds.size < 2 && (
                            <motion.div
                              initial={{ opacity: 0, scale: 0.9 }}
                              animate={{ opacity: 1, scale: 1 }}
                              exit={{ opacity: 0, scale: 0.9 }}
                              transition={{ duration: 0.15 }}
                            >
                              <ProductActionButtons
                                product={product}
                                onEdit={() => openEditForm(product)}
                                onToggleActive={() => toggleActive(product)}
                                onToggleSoldOut={() => toggleSoldOut(product)}
                                onToggleFeatured={() => toggleFeatured(product)}
                                onDelete={() => handleDelete(product)}
                                align="end"
                              />
                            </motion.div>
                          )}
                        </AnimatePresence>
                      </td>
                    )}
                  </TableRow>
                ))}
              </tbody>
            </Table>
          </div>
        </>
      )}

      {isFormOpen && (
        <Modal title={editingProduct ? 'Editar produto' : 'Novo produto'} onClose={closeForm}>
          <form onSubmit={handleSubmit}>
            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="productName">
              Nome
            </label>
            <input
              id="productName"
              type="text"
              required
              maxLength={100}
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
            />

            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="productDescription">
              Descrição
            </label>
            <textarea
              id="productDescription"
              maxLength={255}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
              rows={2}
            />

            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="productPhotoUrl">
              Foto do produto <span className="font-normal text-gray-400 dark:text-stone-500">(opcional)</span>
            </label>
            <div className="mb-1 flex flex-col gap-2 sm:flex-row">
              <input
                id="productPhotoUrl"
                type="text"
                placeholder="Cole uma URL..."
                maxLength={500}
                value={photoUrl}
                onChange={(e) => setPhotoUrl(e.target.value)}
                className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
              />
              <label className="flex shrink-0 cursor-pointer items-center justify-center rounded-md border border-gray-300 px-3 py-2 text-sm text-gray-700 hover:bg-gray-100 dark:border-white/10 dark:text-stone-300 dark:hover:bg-white/5">
                {isUploading ? 'Enviando...' : 'Enviar do dispositivo'}
                <input type="file" accept="image/jpeg,image/png,image/webp" className="hidden" onChange={handleFileChange} disabled={isUploading} />
              </label>
            </div>
            {photoUrl && (
              <img src={photoUrl} alt="" className="mb-4 h-16 w-16 rounded object-cover" />
            )}
            {!photoUrl && <div className="mb-4" />}

            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="productPrice">
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
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
            />

            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="productCostPrice">
              Preço de custo <span className="font-normal text-gray-400 dark:text-stone-500">(opcional)</span>
            </label>
            <input
              id="productCostPrice"
              type="number"
              min="0"
              step="0.01"
              value={costPrice}
              onChange={(e) => setCostPrice(e.target.value)}
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
            />

            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="productCategory">
              Categoria
            </label>
            <select
              id="productCategory"
              required
              value={categoryId}
              onChange={(e) => setCategoryId(e.target.value)}
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
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

            <label className="mb-4 flex items-center gap-2 text-sm text-gray-700 dark:text-stone-300">
              <input
                type="checkbox"
                checked={featured}
                onChange={(e) => setFeatured(e.target.checked)}
                className="rounded border-gray-300 text-brand-600 focus:ring-brand-500 dark:border-white/20 dark:bg-stone-800"
              />
              Marcar como destaque no cardápio digital
            </label>

            {error && <p className="mb-4 text-sm text-wine-600 dark:text-wine-400">{error}</p>}

            <Button type="submit" disabled={createMutation.isPending || updateMutation.isPending} className="w-full">
              Salvar
            </Button>
          </form>
        </Modal>
      )}
    </div>
  )
}

function SelectionCheckbox({ state }: { state: 'checked' | 'unchecked' | 'indeterminate' }) {
  const Icon = state === 'checked' ? SquareCheck : state === 'indeterminate' ? SquareMinus : Square
  return (
    <Icon
      className={`h-[18px] w-[18px] ${
        state === 'unchecked'
          ? 'text-gray-300 dark:text-stone-600'
          : 'text-brand-600 dark:text-brand-400'
      }`}
    />
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
  const [openUpward, setOpenUpward] = useState(false)
  const menuRef = useRef<HTMLDivElement>(null)
  const triggerRef = useRef<HTMLButtonElement>(null)

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

  useLayoutEffect(() => {
    if (!isMenuOpen || !triggerRef.current) return
    const estimatedMenuHeight = 340
    const triggerBottom = triggerRef.current.getBoundingClientRect().bottom
    // The desktop table wrapper clips overflow to keep its rounded corners, so a dropdown
    // opening downward on the last row gets cut off by that boundary before it ever reaches
    // the edge of the viewport. Whichever boundary is closer is the one that actually clips it.
    const clippingAncestor = triggerRef.current.closest('.overflow-hidden') as HTMLElement | null
    const spaceBelowContainer = clippingAncestor
      ? clippingAncestor.getBoundingClientRect().bottom - triggerBottom
      : Infinity
    const spaceBelowViewport = window.innerHeight - triggerBottom
    setOpenUpward(Math.min(spaceBelowContainer, spaceBelowViewport) < estimatedMenuHeight)
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
        className="rounded-md p-1.5 text-gray-500 hover:bg-gray-100 hover:text-brand-700 dark:text-stone-400 dark:hover:bg-white/5 dark:hover:text-brand-400"
      >
        <Pencil className="h-4 w-4" />
      </button>

      <div className="relative" ref={menuRef}>
        <button
          ref={triggerRef}
          type="button"
          onClick={() => setIsMenuOpen((open) => !open)}
          title="Mais ações"
          aria-label="Mais ações"
          className="rounded-md p-1.5 text-gray-500 hover:bg-gray-100 hover:text-gray-700 dark:text-stone-400 dark:hover:bg-white/5 dark:hover:text-stone-200"
        >
          <MoreVertical className="h-4 w-4" />
        </button>

        {isMenuOpen && (
          <div
            className={`absolute z-10 w-64 overflow-hidden rounded-md border border-gray-200 bg-white py-1 text-sm shadow-lg dark:border-white/10 dark:bg-stone-800 ${
              openUpward ? 'bottom-full mb-1' : 'mt-1'
            } ${align === 'end' ? 'right-0' : 'left-0'}`}
          >
            <Link
              to={`/products/${product.id}/modifiers`}
              onClick={() => setIsMenuOpen(false)}
              className="flex items-center gap-2 whitespace-nowrap px-3 py-2 text-gray-700 hover:bg-gray-50 dark:text-stone-300 dark:hover:bg-white/5"
            >
              <ListChecks className="h-4 w-4 text-gray-400 dark:text-stone-500" />
              Modificadores
            </Link>
            <Link
              to={`/products/${product.id}/availability`}
              onClick={() => setIsMenuOpen(false)}
              className="flex items-center gap-2 whitespace-nowrap px-3 py-2 text-gray-700 hover:bg-gray-50 dark:text-stone-300 dark:hover:bg-white/5"
            >
              <CalendarClock className="h-4 w-4 text-gray-400 dark:text-stone-500" />
              Horários de disponibilidade
            </Link>
            <button
              type="button"
              onClick={() => runAndClose(onToggleActive)}
              className="flex w-full items-center gap-2 whitespace-nowrap px-3 py-2 text-left text-gray-700 hover:bg-gray-50 dark:text-stone-300 dark:hover:bg-white/5"
            >
              <Power className="h-4 w-4 text-gray-400 dark:text-stone-500" />
              {product.active ? 'Desativar' : 'Ativar'}
            </button>
            <button
              type="button"
              onClick={() => runAndClose(onToggleSoldOut)}
              className="flex w-full items-center gap-2 whitespace-nowrap px-3 py-2 text-left text-gray-700 hover:bg-gray-50 dark:text-stone-300 dark:hover:bg-white/5"
            >
              <Ban className="h-4 w-4 text-gray-400 dark:text-stone-500" />
              {product.soldOutToday ? 'Remover "esgotado hoje"' : 'Marcar como esgotado hoje'}
            </button>
            <button
              type="button"
              onClick={() => runAndClose(onToggleFeatured)}
              className="flex w-full items-center gap-2 whitespace-nowrap px-3 py-2 text-left text-gray-700 hover:bg-gray-50 dark:text-stone-300 dark:hover:bg-white/5"
            >
              <Star className="h-4 w-4 text-gray-400 dark:text-stone-500" />
              {product.featured ? 'Remover destaque' : 'Marcar como destaque'}
            </button>
            <div className="my-1 border-t border-gray-100 dark:border-white/10" />
            <button
              type="button"
              onClick={() => runAndClose(onDelete)}
              className="flex w-full items-center gap-2 px-3 py-2 text-left text-red-600 hover:bg-red-50 dark:text-red-400 dark:hover:bg-red-500/10"
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

interface BulkActionsMenuProps {
  disabled: boolean
  onActivate: () => void
  onDeactivate: () => void
  onMarkSoldOut: () => void
  onUnmarkSoldOut: () => void
  onMarkFeatured: () => void
  onUnmarkFeatured: () => void
  onDelete: () => void
}

function BulkActionsMenu({
  disabled,
  onActivate,
  onDeactivate,
  onMarkSoldOut,
  onUnmarkSoldOut,
  onMarkFeatured,
  onUnmarkFeatured,
  onDelete,
}: BulkActionsMenuProps) {
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
    <div className="relative" ref={menuRef}>
      <button
        type="button"
        onClick={() => setIsMenuOpen((open) => !open)}
        disabled={disabled}
        className="flex items-center gap-1.5 rounded-md border border-gray-200 px-2.5 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50 dark:border-white/10 dark:text-stone-300 dark:hover:bg-white/5"
      >
        <MoreVertical className="h-4 w-4" />
        Ações em massa
      </button>

      {isMenuOpen && (
        <div className="absolute right-0 z-10 mt-1 w-64 overflow-hidden rounded-md border border-gray-200 bg-white py-1 text-sm shadow-lg dark:border-white/10 dark:bg-stone-800">
          <button
            type="button"
            onClick={() => runAndClose(onActivate)}
            className="flex w-full items-center gap-2 whitespace-nowrap px-3 py-2 text-left text-gray-700 hover:bg-gray-50 dark:text-stone-300 dark:hover:bg-white/5"
          >
            <Power className="h-4 w-4 text-gray-400 dark:text-stone-500" />
            Ativar selecionados
          </button>
          <button
            type="button"
            onClick={() => runAndClose(onDeactivate)}
            className="flex w-full items-center gap-2 whitespace-nowrap px-3 py-2 text-left text-gray-700 hover:bg-gray-50 dark:text-stone-300 dark:hover:bg-white/5"
          >
            <Power className="h-4 w-4 text-gray-400 dark:text-stone-500" />
            Desativar selecionados
          </button>
          <div className="my-1 border-t border-gray-100 dark:border-white/10" />
          <button
            type="button"
            onClick={() => runAndClose(onMarkSoldOut)}
            className="flex w-full items-center gap-2 whitespace-nowrap px-3 py-2 text-left text-gray-700 hover:bg-gray-50 dark:text-stone-300 dark:hover:bg-white/5"
          >
            <Ban className="h-4 w-4 text-gray-400 dark:text-stone-500" />
            Marcar como esgotado hoje
          </button>
          <button
            type="button"
            onClick={() => runAndClose(onUnmarkSoldOut)}
            className="flex w-full items-center gap-2 whitespace-nowrap px-3 py-2 text-left text-gray-700 hover:bg-gray-50 dark:text-stone-300 dark:hover:bg-white/5"
          >
            <Ban className="h-4 w-4 text-gray-400 dark:text-stone-500" />
            Remover "esgotado hoje"
          </button>
          <div className="my-1 border-t border-gray-100 dark:border-white/10" />
          <button
            type="button"
            onClick={() => runAndClose(onMarkFeatured)}
            className="flex w-full items-center gap-2 whitespace-nowrap px-3 py-2 text-left text-gray-700 hover:bg-gray-50 dark:text-stone-300 dark:hover:bg-white/5"
          >
            <Star className="h-4 w-4 text-gray-400 dark:text-stone-500" />
            Marcar como destaque
          </button>
          <button
            type="button"
            onClick={() => runAndClose(onUnmarkFeatured)}
            className="flex w-full items-center gap-2 whitespace-nowrap px-3 py-2 text-left text-gray-700 hover:bg-gray-50 dark:text-stone-300 dark:hover:bg-white/5"
          >
            <Star className="h-4 w-4 text-gray-400 dark:text-stone-500" />
            Remover destaque
          </button>
          <div className="my-1 border-t border-gray-100 dark:border-white/10" />
          <button
            type="button"
            onClick={() => runAndClose(onDelete)}
            className="flex w-full items-center gap-2 px-3 py-2 text-left text-red-600 hover:bg-red-50 dark:text-red-400 dark:hover:bg-red-500/10"
          >
            <Trash2 className="h-4 w-4" />
            Excluir selecionados
          </button>
        </div>
      )}
    </div>
  )
}
