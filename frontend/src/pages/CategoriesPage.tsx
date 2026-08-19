import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AnimatePresence, motion, type Variants } from 'framer-motion'
import { isAxiosError } from 'axios'
import {
  CheckCircle2,
  ChevronDown,
  Circle,
  Image as ImageIcon,
  Layers,
  MoreVertical,
  Package,
  Pencil,
  Plus,
  Power,
  SlidersHorizontal,
  Tag,
  Trash2,
  Wand2,
} from 'lucide-react'
import { useRef, useState, type ChangeEvent, type FormEvent } from 'react'
import { createCategory, deleteCategory, listCategories, updateCategory, uploadCategoryBanner, type Category } from '../api/categories'
import { useAuth } from '../auth/AuthContext'
import { ActionMenu, type ActionMenuEntry } from '../components/ActionMenu'
import { Badge } from '../components/Badge'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { Modal } from '../components/Modal'
import { PageHeader } from '../components/PageHeader'
import { SectionTabs } from '../components/SectionTabs'
import { Table, TableHead, TableRow } from '../components/Table'
import { CATEGORY_ICON_OPTIONS } from './publicMenu/categoryIcons'

const EASE_OUT: [number, number, number, number] = [0.16, 1, 0.3, 1]

const advancedPanelVariants: Variants = {
  collapsed: { height: 0, opacity: 0, transition: { duration: 0.25, ease: EASE_OUT } },
  expanded: { height: 'auto', opacity: 1, transition: { duration: 0.3, ease: EASE_OUT } },
}

const MENU_TABS = [
  { to: '/products', label: 'Produtos', icon: Package },
  { to: '/categories', label: 'Categorias', icon: Tag },
  { to: '/combos', label: 'Combos', icon: Layers },
]

export function CategoriesPage() {
  const { user } = useAuth()
  const canManage = user?.role === 'OWNER' || user?.role === 'MANAGER'
  const queryClient = useQueryClient()

  const { data: categories, isLoading } = useQuery({
    queryKey: ['categories'],
    queryFn: () => listCategories(),
  })

  const [editingCategory, setEditingCategory] = useState<Category | null>(null)
  const [isCreating, setIsCreating] = useState(false)
  const [name, setName] = useState('')
  const [bannerImageUrl, setBannerImageUrl] = useState('')
  const [icon, setIcon] = useState('')
  const [showAdvanced, setShowAdvanced] = useState(false)
  const [isUploadingBanner, setIsUploadingBanner] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [listError, setListError] = useState<string | null>(null)
  const [categoryToDelete, setCategoryToDelete] = useState<Category | null>(null)

  const createMutation = useMutation({
    mutationFn: createCategory,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['categories'] })
      closeForm()
    },
    onError: () => setError('Não foi possível salvar. Verifique se o nome já está em uso.'),
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: Parameters<typeof updateCategory>[1] }) =>
      updateCategory(id, payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['categories'] }),
  })

  const deleteMutation = useMutation({
    mutationFn: deleteCategory,
    onSuccess: () => {
      setListError(null)
      queryClient.invalidateQueries({ queryKey: ['categories'] })
    },
    onError: (err) => {
      if (isAxiosError(err) && err.response?.status === 403) {
        setListError('Não é possível excluir: a categoria ainda tem produtos. Mova ou exclua os produtos primeiro.')
      } else {
        setListError('Não foi possível excluir a categoria.')
      }
    },
  })

  function openCreateForm() {
    setName('')
    setBannerImageUrl('')
    setIcon('')
    setShowAdvanced(false)
    setError(null)
    setIsCreating(true)
  }

  function openEditForm(category: Category) {
    setEditingCategory(category)
    setName(category.name)
    setBannerImageUrl(category.bannerImageUrl ?? '')
    setIcon(category.icon ?? '')
    setShowAdvanced(false)
    setError(null)
  }

  function closeForm() {
    setIsCreating(false)
    setEditingCategory(null)
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    if (editingCategory) {
      updateMutation.mutate(
        { id: editingCategory.id, payload: { name, bannerImageUrl, icon } },
        { onSuccess: closeForm, onError: () => setError('Não foi possível salvar. Verifique se o nome já está em uso.') },
      )
    } else {
      createMutation.mutate({ name, bannerImageUrl, icon })
    }
  }

  async function handleBannerFileChange(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) return

    setIsUploadingBanner(true)
    setError(null)
    try {
      const url = await uploadCategoryBanner(file)
      setBannerImageUrl(url)
    } catch {
      setError('Não foi possível enviar a imagem. Tente novamente.')
    } finally {
      setIsUploadingBanner(false)
    }
  }

  function toggleActive(category: Category) {
    updateMutation.mutate({ id: category.id, payload: { active: !category.active } })
  }

  function handleDelete(category: Category) {
    setCategoryToDelete(category)
  }

  function confirmDelete() {
    if (categoryToDelete) {
      setListError(null)
      deleteMutation.mutate(categoryToDelete.id)
    }
    setCategoryToDelete(null)
  }

  const isFormOpen = isCreating || editingCategory !== null

  return (
    <div>
      <SectionTabs tabs={MENU_TABS} />

      <div className="mb-4 flex items-center justify-between gap-3 rounded-b-xl border border-gray-200 bg-white p-4 shadow-xs dark:border-white/10 dark:bg-stone-900">
        <PageHeader icon={Tag} title="Categorias" />
        {canManage && (
          <Button type="button" onClick={openCreateForm} className="shrink-0 whitespace-nowrap">
            <Plus className="h-4 w-4" />
            <span className="hidden sm:inline">Nova categoria</span>
          </Button>
        )}
      </div>

      {listError && <p className="mb-4 text-sm text-wine-600 dark:text-wine-400">{listError}</p>}

      {isLoading && <p className="text-sm text-gray-500 dark:text-stone-400">Carregando...</p>}

      {categories && categories.length === 0 && (
        <p className="text-sm text-gray-500 dark:text-stone-400">Nenhuma categoria cadastrada.</p>
      )}

      {categories && categories.length > 0 && (
        <>
          {/* Mobile: stacked cards */}
          <div className="space-y-2 sm:hidden">
            {categories.map((category) => (
              <Card key={category.id} className="p-4">
                <div className="mb-2 flex items-center justify-between">
                  <span className="font-medium text-gray-800 dark:text-white">{category.name}</span>
                  <Badge tone={category.active ? 'free' : 'neutral'} className="shrink-0">
                    {category.active ? <CheckCircle2 className="h-3 w-3" /> : <Circle className="h-3 w-3" />}
                    {category.active ? 'Ativa' : 'Inativa'}
                  </Badge>
                </div>
                {canManage && (
                  <CategoryActionButtons
                    category={category}
                    onEdit={() => openEditForm(category)}
                    onToggleActive={() => toggleActive(category)}
                    onDelete={() => handleDelete(category)}
                    align="end"
                  />
                )}
              </Card>
            ))}
          </div>

          {/* Desktop: table */}
          <div className="hidden sm:block">
            <Table>
              <TableHead>
                <tr>
                  <th className="px-4 py-2 font-medium">Nome</th>
                  <th className="px-4 py-2 font-medium">Status</th>
                  {canManage && <th className="px-4 py-2" />}
                </tr>
              </TableHead>
              <tbody>
                {categories.map((category) => (
                  <TableRow key={category.id}>
                    <td className="px-4 py-2 text-gray-800 dark:text-white">{category.name}</td>
                    <td className="px-4 py-2">
                      <Badge tone={category.active ? 'free' : 'neutral'}>
                        {category.active ? <CheckCircle2 className="h-3 w-3" /> : <Circle className="h-3 w-3" />}
                        {category.active ? 'Ativa' : 'Inativa'}
                      </Badge>
                    </td>
                    {canManage && (
                      <td className="px-4 py-2 text-right">
                        <CategoryActionButtons
                          category={category}
                          onEdit={() => openEditForm(category)}
                          onToggleActive={() => toggleActive(category)}
                          onDelete={() => handleDelete(category)}
                          align="end"
                        />
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
        <Modal title={editingCategory ? 'Editar categoria' : 'Nova categoria'} onClose={closeForm}>
          <form onSubmit={handleSubmit}>
            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="categoryName">
              Nome
            </label>
            <input
              id="categoryName"
              type="text"
              required
              maxLength={100}
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
            />

            <div className="mb-4 overflow-hidden rounded-md border border-gray-200 dark:border-white/10">
              <button
                type="button"
                onClick={() => setShowAdvanced((prev) => !prev)}
                className="flex w-full items-center justify-between gap-2 px-3 py-2.5 text-left hover:bg-gray-50 dark:hover:bg-white/5"
              >
                <span className="flex items-center gap-2 text-sm font-medium text-gray-700 dark:text-stone-300">
                  <SlidersHorizontal className="h-4 w-4 text-brand-600 dark:text-brand-400" />
                  Configurações avançadas
                </span>
                <motion.span animate={{ rotate: showAdvanced ? 180 : 0 }} transition={{ duration: 0.25, ease: EASE_OUT }}>
                  <ChevronDown className="h-4 w-4 text-gray-400 dark:text-stone-500" />
                </motion.span>
              </button>

              <AnimatePresence initial={false}>
                {showAdvanced && (
                  <motion.div
                    initial="collapsed"
                    animate="expanded"
                    exit="collapsed"
                    variants={advancedPanelVariants}
                    className="overflow-hidden border-t border-gray-100 dark:border-white/10"
                  >
                    <div className="p-3">
                      <label className="mb-1 flex items-center gap-1.5 text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="categoryBannerUrl">
                        <ImageIcon className="h-3.5 w-3.5" />
                        Banner da categoria <span className="font-normal text-gray-400 dark:text-stone-500">(opcional)</span>
                      </label>
                      <p className="mb-2 text-xs text-gray-500 dark:text-stone-400">
                        Aparece no card da categoria no cardápio digital. Se não for definido, usamos a foto do primeiro
                        produto cadastrado.
                      </p>
                      <div className="mb-2 flex flex-col gap-2 sm:flex-row">
                        <input
                          id="categoryBannerUrl"
                          type="text"
                          placeholder="Cole uma URL..."
                          maxLength={500}
                          value={bannerImageUrl}
                          onChange={(e) => setBannerImageUrl(e.target.value)}
                          className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
                        />
                        <label className="flex shrink-0 cursor-pointer items-center justify-center rounded-md border border-gray-300 px-3 py-2 text-sm text-gray-700 hover:bg-gray-100 dark:border-white/10 dark:text-stone-300 dark:hover:bg-white/5">
                          {isUploadingBanner ? 'Enviando...' : 'Enviar do dispositivo'}
                          <input
                            type="file"
                            accept="image/jpeg,image/png,image/webp"
                            className="hidden"
                            onChange={handleBannerFileChange}
                            disabled={isUploadingBanner}
                          />
                        </label>
                      </div>
                      {bannerImageUrl && (
                        <img src={bannerImageUrl} alt="" className="h-20 w-full rounded-md object-cover" />
                      )}
                    </div>

                    <div className="border-t border-gray-100 p-3 dark:border-white/10">
                      <label className="mb-1 flex items-center gap-1.5 text-sm font-medium text-gray-700 dark:text-stone-300">
                        <Wand2 className="h-3.5 w-3.5" />
                        Ícone da categoria <span className="font-normal text-gray-400 dark:text-stone-500">(opcional)</span>
                      </label>
                      <p className="mb-2 text-xs text-gray-500 dark:text-stone-400">
                        Aparece no cardápio digital. Se não escolher, detectamos automaticamente pelo nome da categoria.
                      </p>
                      <div className="grid grid-cols-6 gap-2">
                        <button
                          type="button"
                          onClick={() => setIcon('')}
                          title="Automático"
                          aria-label="Detectar automaticamente"
                          className={`flex h-10 w-10 items-center justify-center rounded-lg border transition ${
                            icon === ''
                              ? 'border-brand-500 bg-brand-50 text-brand-700 dark:border-brand-400 dark:bg-brand-500/10 dark:text-brand-400'
                              : 'border-gray-200 bg-white text-gray-500 hover:bg-gray-50 dark:border-white/10 dark:bg-stone-800 dark:text-stone-400 dark:hover:bg-white/5'
                          }`}
                        >
                          <Wand2 className="h-4 w-4" />
                        </button>
                        {CATEGORY_ICON_OPTIONS.map((option) => {
                          const isSelected = icon === option.key
                          return (
                            <button
                              key={option.key}
                              type="button"
                              onClick={() => setIcon(option.key)}
                              title={option.label}
                              aria-label={option.label}
                              className={`flex h-10 w-10 items-center justify-center rounded-lg border transition ${
                                isSelected
                                  ? 'border-brand-500 bg-brand-50 dark:border-brand-400 dark:bg-brand-500/10'
                                  : 'border-gray-200 bg-white hover:bg-gray-50 dark:border-white/10 dark:bg-stone-800 dark:hover:bg-white/5'
                              }`}
                            >
                              <img src={option.image} alt="" className="h-6 w-6 object-contain" />
                            </button>
                          )
                        })}
                      </div>
                    </div>
                  </motion.div>
                )}
              </AnimatePresence>
            </div>

            {error && <p className="mb-4 text-sm text-wine-600 dark:text-wine-400">{error}</p>}

            <Button type="submit" disabled={createMutation.isPending || updateMutation.isPending} className="w-full">
              Salvar
            </Button>
          </form>
        </Modal>
      )}

      {categoryToDelete && (
        <ConfirmDialog
          title="Excluir categoria"
          message={`Excluir a categoria "${categoryToDelete.name}"? Essa ação não pode ser desfeita.`}
          confirmLabel="Excluir"
          cancelLabel="Voltar"
          danger
          onConfirm={confirmDelete}
          onCancel={() => setCategoryToDelete(null)}
        />
      )}
    </div>
  )
}

interface CategoryActionButtonsProps {
  category: Category
  onEdit: () => void
  onToggleActive: () => void
  onDelete: () => void
  align?: 'start' | 'end'
}

function CategoryActionButtons({ category, onEdit, onToggleActive, onDelete, align = 'start' }: CategoryActionButtonsProps) {
  const [isMenuOpen, setIsMenuOpen] = useState(false)
  const triggerRef = useRef<HTMLButtonElement>(null)

  const items: ActionMenuEntry[] = [
    { key: 'active', label: category.active ? 'Desativar' : 'Ativar', icon: Power, onClick: onToggleActive },
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
        mobileTitle={category.name}
        width={200}
      />
    </div>
  )
}
