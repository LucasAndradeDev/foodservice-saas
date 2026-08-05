import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowRightLeft, CheckCircle2, Circle, Layers, MoreVertical, Package, Pencil, Plus, Power, Tag, Trash2 } from 'lucide-react'
import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { deleteProduct, updateProduct, listProducts, type Product } from '../api/products'
import { useAuth } from '../auth/AuthContext'
import { ActionMenu, type ActionMenuEntry } from '../components/ActionMenu'
import { Card } from '../components/Card'
import { SectionTabs } from '../components/SectionTabs'
import { Table, TableHead, TableRow } from '../components/Table'

const MENU_TABS = [
  { to: '/products', label: 'Produtos', icon: Package },
  { to: '/categories', label: 'Categorias', icon: Tag },
  { to: '/combos', label: 'Combos', icon: Layers },
]

export function CombosPage() {
  const { user } = useAuth()
  const canManage = user?.role === 'OWNER' || user?.role === 'MANAGER'
  const queryClient = useQueryClient()

  const { data: combos, isLoading } = useQuery({
    queryKey: ['products', 'combos'],
    queryFn: () => listProducts({ type: 'COMBO' }),
  })

  const [listError, setListError] = useState<string | null>(null)

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
    onError: () => setListError('Não foi possível excluir: já foi usado em pedidos, ou é usado como item de outro combo. Desative-o.'),
  })

  function toggleActive(combo: Product) {
    updateMutation.mutate({ id: combo.id, payload: { active: !combo.active } })
  }

  function convertToSimple(combo: Product) {
    const confirmed = window.confirm(
      `Transformar "${combo.name}" em produto simples? A composição do combo (itens fixos e grupos de escolha) será apagada e essa ação não pode ser desfeita.`,
    )
    if (confirmed) {
      updateMutation.mutate({ id: combo.id, payload: { type: 'SIMPLE' } })
    }
  }

  function handleDelete(combo: Product) {
    const confirmed = window.confirm(`Excluir o combo "${combo.name}"? Essa ação não pode ser desfeita.`)
    if (confirmed) {
      setListError(null)
      deleteMutation.mutate(combo.id)
    }
  }

  return (
    <div>
      <SectionTabs tabs={MENU_TABS} />

      <div className="mb-4 flex items-center justify-between gap-3 rounded-b-xl border border-gray-200 bg-white p-4 shadow-xs dark:border-white/10 dark:bg-stone-900">
        <div className="flex items-center gap-3">
          <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-brand-600 text-white shadow-sm">
            <Layers className="h-5 w-5" />
          </span>
          <h1 className="text-lg font-bold text-gray-900 dark:text-white">Combos</h1>
        </div>
        {canManage && (
          <Link
            to="/combos/new"
            className="flex items-center gap-1.5 rounded-lg bg-brand-600 px-3.5 py-2 text-sm font-medium text-white hover:bg-brand-700"
          >
            <Plus className="h-4 w-4" />
            <span className="hidden sm:inline">Novo combo</span>
          </Link>
        )}
      </div>

      {listError && <p className="mb-4 text-sm text-wine-600 dark:text-wine-400">{listError}</p>}

      {isLoading && <p className="text-sm text-gray-500 dark:text-stone-400">Carregando...</p>}

      {combos && combos.length === 0 && (
        <div className="rounded-xl border border-dashed border-gray-300 bg-gray-50 p-6 text-center dark:border-white/10 dark:bg-white/5">
          <Layers className="mx-auto mb-2 h-8 w-8 text-gray-300 dark:text-stone-600" />
          <p className="mb-1 text-sm font-medium text-gray-700 dark:text-stone-300">Nenhum combo cadastrado ainda</p>
          <p className="mx-auto max-w-sm text-sm text-gray-500 dark:text-stone-400">
            Um combo é um conjunto de produtos vendido junto, com desconto — ex: hambúrguer + fritas + refrigerante.
          </p>
        </div>
      )}

      {combos && combos.length > 0 && (
        <>
          {/* Mobile: stacked cards */}
          <div className="space-y-2 sm:hidden">
            {combos.map((combo) => (
              <Card key={combo.id} className="p-4">
                <div className="mb-2 flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <Link
                      to={`/combos/${combo.id}`}
                      className="block truncate font-medium text-gray-800 hover:underline dark:text-white"
                    >
                      {combo.name}
                    </Link>
                    <span className="block text-sm text-gray-500 dark:text-stone-400">
                      {combo.discountPercentage != null ? `${combo.discountPercentage}% de desconto` : 'Sem desconto'}
                    </span>
                  </div>
                  <span
                    className={`flex shrink-0 items-center gap-1 rounded-full px-2 py-0.5 text-xs ${
                      combo.active
                        ? 'bg-sage-100 text-sage-700 dark:bg-sage-500/10 dark:text-sage-400'
                        : 'bg-gray-100 text-gray-500 dark:bg-white/10 dark:text-stone-400'
                    }`}
                  >
                    {combo.active ? <CheckCircle2 className="h-3 w-3" /> : <Circle className="h-3 w-3" />}
                    {combo.active ? 'Ativo' : 'Inativo'}
                  </span>
                </div>
                {canManage && (
                  <ComboActionButtons
                    combo={combo}
                    onToggleActive={() => toggleActive(combo)}
                    onConvertToSimple={() => convertToSimple(combo)}
                    onDelete={() => handleDelete(combo)}
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
                  <th className="px-4 py-2 font-medium">Desconto</th>
                  <th className="px-4 py-2 font-medium">Status</th>
                  {canManage && <th className="w-16 px-4 py-2" />}
                </tr>
              </TableHead>
              <tbody>
                {combos.map((combo) => (
                  <TableRow key={combo.id}>
                    <td className="px-4 py-2 text-gray-800 dark:text-white">
                      <Link to={`/combos/${combo.id}`} className="hover:underline">
                        {combo.name}
                      </Link>
                    </td>
                    <td className="px-4 py-2 text-gray-600 dark:text-stone-400">
                      {combo.discountPercentage != null ? `${combo.discountPercentage}%` : '—'}
                    </td>
                    <td className="px-4 py-2">
                      <span
                        className={`flex w-fit items-center gap-1 rounded-full px-2 py-0.5 text-xs ${
                          combo.active
                            ? 'bg-sage-100 text-sage-700 dark:bg-sage-500/10 dark:text-sage-400'
                            : 'bg-gray-100 text-gray-500 dark:bg-white/10 dark:text-stone-400'
                        }`}
                      >
                        {combo.active ? <CheckCircle2 className="h-3 w-3" /> : <Circle className="h-3 w-3" />}
                        {combo.active ? 'Ativo' : 'Inativo'}
                      </span>
                    </td>
                    {canManage && (
                      <td className="px-4 py-2 text-right">
                        <ComboActionButtons
                          combo={combo}
                          onToggleActive={() => toggleActive(combo)}
                          onConvertToSimple={() => convertToSimple(combo)}
                          onDelete={() => handleDelete(combo)}
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
    </div>
  )
}

interface ComboActionButtonsProps {
  combo: Product
  onToggleActive: () => void
  onConvertToSimple: () => void
  onDelete: () => void
}

function ComboActionButtons({ combo, onToggleActive, onConvertToSimple, onDelete }: ComboActionButtonsProps) {
  const [isMenuOpen, setIsMenuOpen] = useState(false)
  const triggerRef = useRef<HTMLButtonElement>(null)

  const items: ActionMenuEntry[] = [
    { key: 'active', label: combo.active ? 'Desativar' : 'Ativar', icon: Power, onClick: onToggleActive },
    {
      key: 'convert',
      label: 'Transformar em produto simples',
      icon: ArrowRightLeft,
      onClick: onConvertToSimple,
      tone: 'warning',
    },
    { divider: true },
    { key: 'delete', label: 'Excluir', icon: Trash2, onClick: onDelete, tone: 'danger' },
  ]

  return (
    <div className="flex items-center justify-end gap-1">
      <Link
        to={`/combos/${combo.id}`}
        title="Editar"
        aria-label="Editar"
        className="rounded-md p-2 text-gray-500 hover:bg-gray-100 hover:text-brand-700 dark:text-stone-400 dark:hover:bg-white/5 dark:hover:text-brand-400"
      >
        <Pencil className="h-[18px] w-[18px]" />
      </Link>

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
        align="end"
        mobileTitle={combo.name}
        width={224}
      />
    </div>
  )
}
