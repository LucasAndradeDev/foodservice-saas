import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import { AnimatePresence, motion } from 'framer-motion'
import {
  Ban,
  CalendarClock,
  CheckCircle2,
  Circle,
  Filter,
  Layers,
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
  X,
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
import { ActionMenu, type ActionMenuEntry } from '../components/ActionMenu'
import { Button } from '../components/Button'
import { Dropdown } from '../components/Dropdown'
import { Table, TableHead, TableRow } from '../components/Table'
import { Modal } from '../components/Modal'
import { SectionTabs } from '../components/SectionTabs'

const MENU_TABS = [
  { to: '/products', label: 'Produtos', icon: Package },
  { to: '/categories', label: 'Categorias', icon: Tag },
  { to: '/combos', label: 'Combos', icon: Layers },
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
  const [galleryUrls, setGalleryUrls] = useState<string[]>([])
  const [price, setPrice] = useState('')
  const [costPrice, setCostPrice] = useState('')
  const [categoryId, setCategoryId] = useState('')
  const [featured, setFeatured] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [listError, setListError] = useState<string | null>(null)
  const [showNoCategoryNotice, setShowNoCategoryNotice] = useState(false)
  const [isUploading, setIsUploading] = useState(false)
  const [isUploadingGalleryPhoto, setIsUploadingGalleryPhoto] = useState(false)

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
    setGalleryUrls([])
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
    setGalleryUrls(product.galleryImageUrls)
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
      galleryImageUrls: galleryUrls,
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

  async function handleGalleryFileChange(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) return

    setIsUploadingGalleryPhoto(true)
    setError(null)
    try {
      const url = await uploadProductImage(file)
      setGalleryUrls((prev) => [...prev, url])
    } catch {
      setError('Não foi possível enviar a imagem. Tente novamente.')
    } finally {
      setIsUploadingGalleryPhoto(false)
    }
  }

  function removeGalleryPhoto(index: number) {
    setGalleryUrls((prev) => prev.filter((_, i) => i !== index))
  }

  const isFormOpen = isCreating || editingProduct !== null

  return (
    <div>
      <SectionTabs tabs={MENU_TABS} />

      <div className="mb-4 rounded-b-xl border border-gray-200 bg-white p-4 shadow-xs dark:border-white/10 dark:bg-stone-900">
        <div className="mb-4 flex items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-brand-600 text-white shadow-sm">
              <Package className="h-5 w-5" />
            </span>
            <h1 className="text-lg font-bold text-gray-900 dark:text-white">Produtos</h1>
          </div>
          {canManage && (
            <Button type="button" onClick={openCreateForm}>
              <Plus className="h-4 w-4" />
              <span className="hidden sm:inline">Novo produto</span>
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
                          align="end"
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

            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300">
              Fotos adicionais <span className="font-normal text-gray-400 dark:text-stone-500">(opcional)</span>
            </label>
            <p className="mb-2 text-xs text-gray-500 dark:text-stone-400">
              Com mais de uma foto, o cliente pode passar o dedo pra ver todas no cardápio digital.
            </p>
            <div className="mb-4 flex flex-wrap gap-2">
              {galleryUrls.map((url, index) => (
                <div key={url + index} className="relative h-16 w-16 shrink-0">
                  <img src={url} alt="" className="h-16 w-16 rounded object-cover" />
                  <button
                    type="button"
                    onClick={() => removeGalleryPhoto(index)}
                    aria-label="Remover foto"
                    className="absolute -right-1.5 -top-1.5 flex h-5 w-5 items-center justify-center rounded-full bg-wine-600 text-white shadow-sm hover:bg-wine-700"
                  >
                    <X className="h-3 w-3" />
                  </button>
                </div>
              ))}
              <label className="flex h-16 w-16 shrink-0 cursor-pointer flex-col items-center justify-center gap-1 rounded-md border border-dashed border-gray-300 text-gray-400 hover:bg-gray-50 dark:border-white/20 dark:text-stone-500 dark:hover:bg-white/5">
                <Plus className="h-4 w-4" />
                <span className="text-[10px]">{isUploadingGalleryPhoto ? '...' : 'Adicionar'}</span>
                <input
                  type="file"
                  accept="image/jpeg,image/png,image/webp"
                  className="hidden"
                  onChange={handleGalleryFileChange}
                  disabled={isUploadingGalleryPhoto}
                />
              </label>
            </div>

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
  const triggerRef = useRef<HTMLButtonElement>(null)

  const items: ActionMenuEntry[] = [
    { key: 'modifiers', label: 'Modificadores', icon: ListChecks, to: `/products/${product.id}/modifiers` },
    {
      key: 'availability',
      label: 'Horários de disponibilidade',
      icon: CalendarClock,
      to: `/products/${product.id}/availability`,
    },
    { key: 'active', label: product.active ? 'Desativar' : 'Ativar', icon: Power, onClick: onToggleActive },
    {
      key: 'soldOut',
      label: product.soldOutToday ? 'Remover "esgotado hoje"' : 'Marcar como esgotado hoje',
      icon: Ban,
      onClick: onToggleSoldOut,
    },
    {
      key: 'featured',
      label: product.featured ? 'Remover destaque' : 'Marcar como destaque',
      icon: Star,
      onClick: onToggleFeatured,
    },
    { divider: true },
    { key: 'delete', label: 'Excluir', icon: Trash2, onClick: onDelete, tone: 'danger' },
  ]

  return (
    <div className={`flex items-center gap-1 ${align === 'end' ? 'justify-end' : ''}`}>
      <button
        type="button"
        onClick={onEdit}
        title="Editar"
        aria-label="Editar"
        className="rounded-md p-2 text-gray-500 hover:bg-gray-100 hover:text-brand-700 dark:text-stone-400 dark:hover:bg-white/5 dark:hover:text-brand-400"
      >
        <Pencil className="h-[18px] w-[18px]" />
      </button>

      <button
        ref={triggerRef}
        type="button"
        onClick={() => setIsMenuOpen((open) => !open)}
        title="Mais ações"
        aria-label="Mais ações"
        className="rounded-md p-2 text-gray-500 hover:bg-gray-100 hover:text-gray-700 dark:text-stone-400 dark:hover:bg-white/5 dark:hover:text-stone-200"
      >
        <MoreVertical className="h-[18px] w-[18px]" />
      </button>

      <ActionMenu
        isOpen={isMenuOpen}
        onClose={() => setIsMenuOpen(false)}
        triggerRef={triggerRef}
        items={items}
        align={align}
        mobileTitle={product.name}
        width={256}
      />
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
  const triggerRef = useRef<HTMLButtonElement>(null)

  const items: ActionMenuEntry[] = [
    { key: 'activate', label: 'Ativar selecionados', icon: Power, onClick: onActivate },
    { key: 'deactivate', label: 'Desativar selecionados', icon: Power, onClick: onDeactivate },
    { divider: true },
    { key: 'soldOut', label: 'Marcar como esgotado hoje', icon: Ban, onClick: onMarkSoldOut },
    { key: 'unmarkSoldOut', label: 'Remover "esgotado hoje"', icon: Ban, onClick: onUnmarkSoldOut },
    { divider: true },
    { key: 'featured', label: 'Marcar como destaque', icon: Star, onClick: onMarkFeatured },
    { key: 'unmarkFeatured', label: 'Remover destaque', icon: Star, onClick: onUnmarkFeatured },
    { divider: true },
    { key: 'delete', label: 'Excluir selecionados', icon: Trash2, onClick: onDelete, tone: 'danger' },
  ]

  return (
    <div>
      <button
        ref={triggerRef}
        type="button"
        onClick={() => setIsMenuOpen((open) => !open)}
        disabled={disabled}
        className="flex items-center gap-1.5 rounded-md border border-gray-200 px-2.5 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50 dark:border-white/10 dark:text-stone-300 dark:hover:bg-white/5"
      >
        <MoreVertical className="h-4 w-4" />
        Ações em massa
      </button>

      <ActionMenu
        isOpen={isMenuOpen}
        onClose={() => setIsMenuOpen(false)}
        triggerRef={triggerRef}
        items={items}
        align="end"
        mobileTitle="Ações em massa"
        width={256}
      />
    </div>
  )
}
