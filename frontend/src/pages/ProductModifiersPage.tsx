import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ListChecks, Minus, Pencil, Plus, Trash2 } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { useParams } from 'react-router-dom'
import {
  createModifierGroup,
  deleteModifierGroup,
  listModifierGroups,
  updateModifierGroup,
  type ModifierGroup,
  type ModifierGroupPayload,
  type ModifierOptionInput,
  type SelectionType,
} from '../api/productModifiers'
import { getProduct } from '../api/products'
import { BackLink } from '../components/BackLink'
import { Button } from '../components/Button'
import { Modal } from '../components/Modal'

const currencyFormatter = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

interface OptionDraft {
  name: string
  priceDelta: string
}

export function ProductModifiersPage() {
  const { productId } = useParams<{ productId: string }>()
  const queryClient = useQueryClient()

  const { data: product } = useQuery({
    queryKey: ['products', productId],
    queryFn: () => getProduct(productId!),
    enabled: !!productId,
  })

  const { data: groups, isLoading } = useQuery({
    queryKey: ['products', productId, 'modifierGroups'],
    queryFn: () => listModifierGroups(productId!),
    enabled: !!productId,
  })

  const [editingGroup, setEditingGroup] = useState<ModifierGroup | null>(null)
  const [isCreating, setIsCreating] = useState(false)
  const [name, setName] = useState('')
  const [selectionType, setSelectionType] = useState<SelectionType>('SINGLE')
  const [required, setRequired] = useState(false)
  const [options, setOptions] = useState<OptionDraft[]>([{ name: '', priceDelta: '0' }])
  const [error, setError] = useState<string | null>(null)

  function invalidate() {
    queryClient.invalidateQueries({ queryKey: ['products', productId, 'modifierGroups'] })
  }

  const createMutation = useMutation({
    mutationFn: (payload: ModifierGroupPayload) => createModifierGroup(productId!, payload),
    onSuccess: () => {
      invalidate()
      closeForm()
    },
    onError: () => setError('Não foi possível salvar. Verifique os dados e tente novamente.'),
  })

  const updateMutation = useMutation({
    mutationFn: ({ groupId, payload }: { groupId: string; payload: ModifierGroupPayload }) =>
      updateModifierGroup(productId!, groupId, payload),
    onSuccess: () => {
      invalidate()
      closeForm()
    },
    onError: () => setError('Não foi possível salvar. Verifique os dados e tente novamente.'),
  })

  const deleteMutation = useMutation({
    mutationFn: (groupId: string) => deleteModifierGroup(productId!, groupId),
    onSuccess: invalidate,
  })

  function openCreateForm() {
    setName('')
    setSelectionType('SINGLE')
    setRequired(false)
    setOptions([{ name: '', priceDelta: '0' }])
    setError(null)
    setIsCreating(true)
  }

  function openEditForm(group: ModifierGroup) {
    setEditingGroup(group)
    setName(group.name)
    setSelectionType(group.selectionType)
    setRequired(group.required)
    setOptions(group.options.map((option) => ({ name: option.name, priceDelta: String(option.priceDelta) })))
    setError(null)
  }

  function closeForm() {
    setIsCreating(false)
    setEditingGroup(null)
  }

  function addOptionRow() {
    setOptions((prev) => [...prev, { name: '', priceDelta: '0' }])
  }

  function updateOptionRow(index: number, patch: Partial<OptionDraft>) {
    setOptions((prev) => prev.map((option, i) => (i === index ? { ...option, ...patch } : option)))
  }

  function removeOptionRow(index: number) {
    setOptions((prev) => prev.filter((_, i) => i !== index))
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)

    const cleanedOptions: ModifierOptionInput[] = options
      .filter((option) => option.name.trim() !== '')
      .map((option) => ({ name: option.name.trim(), priceDelta: Number(option.priceDelta || 0) }))

    if (cleanedOptions.length === 0) {
      setError('Adicione pelo menos uma opção.')
      return
    }

    const payload: ModifierGroupPayload = { name, selectionType, required, options: cleanedOptions }

    if (editingGroup) {
      updateMutation.mutate({ groupId: editingGroup.id, payload })
    } else {
      createMutation.mutate(payload)
    }
  }

  function handleDelete(group: ModifierGroup) {
    const confirmed = window.confirm(`Excluir o grupo "${group.name}"? Essa ação não pode ser desfeita.`)
    if (confirmed) {
      deleteMutation.mutate(group.id)
    }
  }

  const isFormOpen = isCreating || editingGroup !== null

  return (
    <div>
      <BackLink to="/products" className="mb-2">
        Produtos
      </BackLink>

      <div className="mb-1 flex items-center justify-between">
        <h1 className="flex items-center gap-2 text-lg font-semibold text-gray-800 dark:text-white">
          <ListChecks className="h-5 w-5 text-brand-600 dark:text-brand-400" />
          Modificadores {product && `· ${product.name}`}
        </h1>
        <button
          type="button"
          onClick={openCreateForm}
          className="rounded-md bg-brand-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-brand-700"
        >
          Adicionar grupo
        </button>
      </div>
      <p className="mb-4 text-sm text-gray-500 dark:text-stone-400">
        São as opções que o cliente escolhe ao pedir esse produto — como tamanho, ponto da carne ou ingredientes
        extras. Cada grupo aparece pro cliente exatamente como você configurar aqui.
      </p>

      {isLoading && <p className="text-sm text-gray-500 dark:text-stone-400">Carregando...</p>}

      {groups && groups.length === 0 && (
        <div className="rounded-xl border border-dashed border-gray-300 bg-gray-50 p-6 text-center dark:border-white/10 dark:bg-white/5">
          <ListChecks className="mx-auto mb-2 h-8 w-8 text-gray-300 dark:text-stone-600" />
          <p className="mb-1 text-sm font-medium text-gray-700 dark:text-stone-300">Nenhum modificador cadastrado ainda</p>
          <p className="mx-auto max-w-sm text-sm text-gray-500 dark:text-stone-400">
            Exemplo: um grupo "Tamanho" com as opções P, M e G, onde o G custa R$ 15,00 a mais. Clique em "Adicionar
            grupo" pra criar o primeiro.
          </p>
        </div>
      )}

      {groups && groups.length > 0 && (
        <div className="space-y-3">
          {groups.map((group) => (
            <div
              key={group.id}
              className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-white/10 dark:bg-stone-900"
            >
              <div className="flex items-start justify-between gap-2">
                <div>
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-medium text-gray-800 dark:text-white">{group.name}</span>
                    <span className="rounded-full bg-gray-100 px-2 py-0.5 text-xs text-gray-600 dark:bg-white/10 dark:text-stone-400">
                      {group.selectionType === 'SINGLE' ? 'Cliente escolhe 1' : 'Cliente escolhe vários'}
                    </span>
                    <span
                      className={`rounded-full px-2 py-0.5 text-xs ${
                        group.required
                          ? 'bg-amber-100 text-amber-700 dark:bg-amber-500/10 dark:text-amber-400'
                          : 'bg-gray-100 text-gray-500 dark:bg-white/10 dark:text-stone-400'
                      }`}
                    >
                      {group.required ? 'Obrigatório' : 'Opcional'}
                    </span>
                  </div>
                  <ul className="mt-2 space-y-1 text-sm text-gray-600 dark:text-stone-400">
                    {group.options.map((option) => (
                      <li key={option.id} className="flex items-center gap-2">
                        <span>{option.name}</span>
                        <span className="text-gray-400 dark:text-stone-500">
                          {option.priceDelta > 0 ? `+${currencyFormatter.format(option.priceDelta)}` : '—'}
                        </span>
                      </li>
                    ))}
                  </ul>
                </div>
                <div className="flex shrink-0 items-center gap-1">
                  <button
                    type="button"
                    onClick={() => openEditForm(group)}
                    title="Editar"
                    aria-label="Editar"
                    className="rounded-md p-1.5 text-gray-500 hover:bg-gray-100 hover:text-brand-700 dark:text-stone-400 dark:hover:bg-white/5 dark:hover:text-brand-400"
                  >
                    <Pencil className="h-4 w-4" />
                  </button>
                  <button
                    type="button"
                    onClick={() => handleDelete(group)}
                    title="Excluir"
                    aria-label="Excluir"
                    className="rounded-md p-1.5 text-gray-500 hover:bg-red-50 hover:text-red-700 dark:text-stone-400 dark:hover:bg-red-500/10 dark:hover:text-red-400"
                  >
                    <Trash2 className="h-4 w-4" />
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {isFormOpen && (
        <Modal title={editingGroup ? 'Editar grupo' : 'Novo grupo de modificador'} onClose={closeForm}>
          <form onSubmit={handleSubmit}>
            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="groupName">
              Nome
            </label>
            <input
              id="groupName"
              type="text"
              required
              maxLength={100}
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Ex: Tamanho, Ponto da carne..."
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
            />

            <span className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300">Como o cliente escolhe?</span>
            <div className="mb-4 space-y-2">
              <button
                type="button"
                onClick={() => setSelectionType('SINGLE')}
                className={`w-full rounded-md border px-3 py-2 text-left text-sm transition-colors ${
                  selectionType === 'SINGLE'
                    ? 'border-brand-600 bg-brand-50 dark:border-brand-400 dark:bg-brand-500/10'
                    : 'border-gray-200 hover:bg-gray-50 dark:border-white/10 dark:hover:bg-white/5'
                }`}
              >
                <span className="block font-medium text-gray-800 dark:text-white">Apenas 1 opção</span>
                <span className="block text-xs text-gray-500 dark:text-stone-400">Ex: tamanho — o cliente marca só P, M ou G</span>
              </button>
              <button
                type="button"
                onClick={() => setSelectionType('MULTIPLE')}
                className={`w-full rounded-md border px-3 py-2 text-left text-sm transition-colors ${
                  selectionType === 'MULTIPLE'
                    ? 'border-brand-600 bg-brand-50 dark:border-brand-400 dark:bg-brand-500/10'
                    : 'border-gray-200 hover:bg-gray-50 dark:border-white/10 dark:hover:bg-white/5'
                }`}
              >
                <span className="block font-medium text-gray-800 dark:text-white">Uma ou mais opções</span>
                <span className="block text-xs text-gray-500 dark:text-stone-400">Ex: ingredientes extras — o cliente pode marcar vários</span>
              </button>
            </div>

            <div className="mb-4 rounded-md border border-gray-200 bg-gray-50 px-3 py-2 dark:border-white/10 dark:bg-white/5">
              <label className="flex items-start gap-2 text-sm text-gray-700 dark:text-stone-300">
                <input
                  type="checkbox"
                  className="mt-0.5"
                  checked={required}
                  onChange={(e) => setRequired(e.target.checked)}
                />
                <span>
                  <span className="block font-medium text-gray-800 dark:text-white">Obrigatório</span>
                  <span className="block text-xs text-gray-500 dark:text-stone-400">
                    O cliente precisa escolher uma opção antes de adicionar ao carrinho. Marque pra tamanho; deixe
                    desmarcado pra extras opcionais.
                  </span>
                </span>
              </label>
            </div>

            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300">Opções</label>
            <p className="mb-2 text-xs text-gray-500 dark:text-stone-400">
              Deixe o preço em R$ 0,00 se a opção não mudar o valor do produto.
            </p>
            <div className="mb-2 space-y-2">
              {options.map((option, index) => (
                <div key={index} className="flex items-center gap-2">
                  <input
                    type="text"
                    placeholder="Nome (ex: G)"
                    maxLength={100}
                    value={option.name}
                    onChange={(e) => updateOptionRow(index, { name: e.target.value })}
                    className="flex-1 rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
                  />
                  <input
                    type="number"
                    step="0.01"
                    min="0"
                    placeholder="+R$"
                    value={option.priceDelta}
                    onChange={(e) => updateOptionRow(index, { priceDelta: e.target.value })}
                    className="w-24 rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
                  />
                  <button
                    type="button"
                    onClick={() => removeOptionRow(index)}
                    disabled={options.length === 1}
                    title="Remover"
                    aria-label="Remover"
                    className="rounded-md p-1.5 text-gray-400 hover:bg-red-50 hover:text-red-700 disabled:opacity-30 dark:text-stone-500 dark:hover:bg-red-500/10 dark:hover:text-red-400"
                  >
                    <Minus className="h-4 w-4" />
                  </button>
                </div>
              ))}
            </div>
            <button
              type="button"
              onClick={addOptionRow}
              className="mb-4 flex items-center gap-1 text-sm text-brand-600 hover:underline"
            >
              <Plus className="h-3.5 w-3.5" />
              Adicionar opção
            </button>

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
