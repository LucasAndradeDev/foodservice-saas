import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Check,
  ChevronLeft,
  ChevronRight,
  Image as ImageIcon,
  Layers,
  ListChecks,
  ListPlus,
  Minus,
  Package,
  Plus,
  Wallet,
  type LucideIcon,
} from 'lucide-react'
import { useEffect, useState, type ChangeEvent, type FormEvent, type ReactNode } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { createCategory, listCategories, type Category } from '../api/categories'
import {
  getComboComposition,
  updateComboComposition,
  type ComboItemInput,
  type ComboSlotInput,
  type UpdateComboCompositionPayload,
} from '../api/combos'
import { createProduct, getProduct, listProducts, updateProduct, uploadProductImage, type Product } from '../api/products'
import { GroupedSelect } from '../components/GroupedSelect'
import { IconSelect } from '../components/IconSelect'
import { getCategoryIcon } from './publicMenu/categoryIcons'

const currencyFormatter = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

const PLACEHOLDER_PRICE = 0.01
const COMBOS_CATEGORY_NAME = 'Combos'

interface FixedItemDraft {
  productId: string
  quantity: string
}

interface SlotOptionDraft {
  productId: string
  quantity: string
}

interface SlotDraft {
  categoryId: string
  name: string
  options: SlotOptionDraft[]
}

function groupProductsByCategory(products: Product[], categories: Category[]) {
  return categories
    .map((category) => ({
      category,
      products: products.filter((product) => product.categoryId === category.id),
    }))
    .filter((group) => group.products.length > 0)
}

function round2(n: number) {
  return Math.round((n + Number.EPSILON) * 100) / 100
}

export function ComboFormPage() {
  const { productId } = useParams<{ productId: string }>()
  const isEditing = !!productId
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const { data: categories } = useQuery({
    queryKey: ['categories'],
    queryFn: () => listCategories(),
  })

  const { data: product } = useQuery({
    queryKey: ['products', productId],
    queryFn: () => getProduct(productId!),
    enabled: isEditing,
  })

  const { data: composition, isLoading: isCompositionLoading } = useQuery({
    queryKey: ['products', productId, 'combo'],
    queryFn: () => getComboComposition(productId!),
    enabled: isEditing && product?.type === 'COMBO',
  })

  const { data: candidateProducts } = useQuery({
    queryKey: ['products', 'combo-candidates'],
    queryFn: () => listProducts({ active: true, type: 'SIMPLE' }),
  })

  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [photoUrl, setPhotoUrl] = useState('')
  const [discountPercentage, setDiscountPercentage] = useState('0')
  const [fixedItems, setFixedItems] = useState<FixedItemDraft[]>([])
  const [slots, setSlots] = useState<SlotDraft[]>([])
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState(false)
  const [isUploading, setIsUploading] = useState(false)
  const [step, setStep] = useState(1)

  useEffect(() => {
    if (!product) return
    setName(product.name)
    setDescription(product.description ?? '')
    setPhotoUrl(product.imageUrl ?? '')
  }, [product])

  useEffect(() => {
    if (!composition || !categories) return
    setDiscountPercentage(String(composition.discountPercentage ?? 0))
    setFixedItems(composition.fixedItems.map((item) => ({ productId: item.productId, quantity: String(item.quantity) })))
    setSlots(
      composition.slots.map((slot) => {
        const matchedCategory = categories.find((category) => category.name.trim().toLowerCase() === slot.name.trim().toLowerCase())
        return {
          categoryId: matchedCategory?.id ?? '',
          name: slot.name,
          options: slot.options.map((option) => ({ productId: option.productId, quantity: String(option.quantity) })),
        }
      }),
    )
  }, [composition, categories])

  const otherProducts = (candidateProducts ?? []).filter((p) => p.id !== productId)
  const productsByCategory = groupProductsByCategory(otherProducts, categories ?? [])
  const productSelectGroups = productsByCategory.map((group) => ({
    label: group.category.name,
    options: group.products.map((p) => ({ value: p.id, label: `${p.name} · ${currencyFormatter.format(p.price)}` })),
  }))
  // A combo can't contain another combo, so its own category never makes sense as a choice-group source.
  const slotCategoryOptions = (categories ?? []).filter(
    (category) => category.name.trim().toLowerCase() !== COMBOS_CATEGORY_NAME.toLowerCase(),
  )
  const slotCategorySelectOptions = slotCategoryOptions.map((category) => ({
    value: category.id,
    label: category.name,
    icon: getCategoryIcon(category.name),
  }))

  const priceOf = (productIdValue: string) => otherProducts.find((p) => p.id === productIdValue)?.price
  const nameOf = (productIdValue: string) => otherProducts.find((p) => p.id === productIdValue)?.name ?? '—'
  const hasAnyItem = fixedItems.some((i) => i.productId) || slots.some((s) => s.options.some((o) => o.productId))
  let priceRange: { min: number; max: number } | null = null
  if (hasAnyItem) {
    let fixedTotal = 0
    for (const item of fixedItems) {
      const price = priceOf(item.productId)
      if (price == null) continue
      fixedTotal += price * (Number(item.quantity) || 1)
    }
    let slotsMin = 0
    let slotsMax = 0
    for (const slot of slots) {
      const prices = slot.options
        .map((option) => {
          const price = priceOf(option.productId)
          return price == null ? null : price * (Number(option.quantity) || 1)
        })
        .filter((v): v is number => v != null)
      if (prices.length === 0) continue
      slotsMin += Math.min(...prices)
      slotsMax += Math.max(...prices)
    }
    const discountFactor = (100 - (Number(discountPercentage) || 0)) / 100
    priceRange = { min: round2((fixedTotal + slotsMin) * discountFactor), max: round2((fixedTotal + slotsMax) * discountFactor) }
  }

  function addFixedItemRow() {
    setFixedItems((prev) => [...prev, { productId: '', quantity: '1' }])
  }

  function updateFixedItemRow(index: number, patch: Partial<FixedItemDraft>) {
    setFixedItems((prev) => prev.map((item, i) => (i === index ? { ...item, ...patch } : item)))
  }

  function removeFixedItemRow(index: number) {
    setFixedItems((prev) => prev.filter((_, i) => i !== index))
  }

  function addSlot() {
    setSlots((prev) => [...prev, { categoryId: '', name: '', options: [] }])
  }

  function updateSlotCategory(index: number, categoryId: string) {
    const category = categories?.find((c) => c.id === categoryId)
    const optionsFromCategory: SlotOptionDraft[] = otherProducts
      .filter((p) => p.categoryId === categoryId)
      .map((p) => ({ productId: p.id, quantity: '1' }))
    setSlots((prev) =>
      prev.map((slot, i) =>
        i === index ? { ...slot, categoryId, name: category?.name ?? '', options: optionsFromCategory } : slot,
      ),
    )
  }

  function removeSlot(index: number) {
    setSlots((prev) => prev.filter((_, i) => i !== index))
  }

  function addSlotOption(slotIndex: number) {
    setSlots((prev) =>
      prev.map((slot, i) => (i === slotIndex ? { ...slot, options: [...slot.options, { productId: '', quantity: '1' }] } : slot)),
    )
  }

  function updateSlotOption(slotIndex: number, optionIndex: number, patch: Partial<SlotOptionDraft>) {
    setSlots((prev) =>
      prev.map((slot, i) =>
        i === slotIndex
          ? { ...slot, options: slot.options.map((option, j) => (j === optionIndex ? { ...option, ...patch } : option)) }
          : slot,
      ),
    )
  }

  function removeSlotOption(slotIndex: number, optionIndex: number) {
    setSlots((prev) =>
      prev.map((slot, i) => (i === slotIndex ? { ...slot, options: slot.options.filter((_, j) => j !== optionIndex) } : slot)),
    )
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

  async function resolveCombosCategoryId(): Promise<string> {
    const existing = (categories ?? []).find((category) => category.name.trim().toLowerCase() === COMBOS_CATEGORY_NAME.toLowerCase())
    if (existing) return existing.id
    const created = await createCategory({ name: COMBOS_CATEGORY_NAME })
    queryClient.invalidateQueries({ queryKey: ['categories'] })
    return created.id
  }

  const saveMutation = useMutation({
    mutationFn: async ({
      compositionPayload,
    }: {
      compositionPayload: UpdateComboCompositionPayload
    }) => {
      const categoryId = await resolveCombosCategoryId()
      if (isEditing) {
        await updateProduct(productId!, { name, description, imageUrl: photoUrl, categoryId })
        await updateComboComposition(productId!, compositionPayload)
        return productId!
      }
      const created = await createProduct({
        name,
        description,
        imageUrl: photoUrl,
        categoryId,
        price: PLACEHOLDER_PRICE,
        type: 'COMBO',
      })
      await updateComboComposition(created.id, compositionPayload)
      return created.id
    },
    onSuccess: (newProductId) => {
      queryClient.invalidateQueries({ queryKey: ['products'] })
      if (isEditing) {
        queryClient.invalidateQueries({ queryKey: ['products', productId, 'combo'] })
        setSuccess(true)
        setTimeout(() => setSuccess(false), 3000)
      } else {
        navigate(`/combos/${newProductId}`)
      }
    },
    onError: () => setError('Não foi possível salvar. Verifique os dados e tente novamente.'),
  })

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)

    if (!name.trim()) {
      setError('Informe um nome para o combo.')
      setStep(1)
      return
    }

    const cleanedFixedItems: ComboItemInput[] = fixedItems
      .filter((item) => item.productId !== '')
      .map((item) => ({ productId: item.productId, quantity: Number(item.quantity || 1) }))

    const cleanedSlots: ComboSlotInput[] = slots
      .filter((slot) => slot.name.trim() !== '')
      .map((slot) => ({
        name: slot.name.trim(),
        options: slot.options
          .filter((option) => option.productId !== '')
          .map((option) => ({ productId: option.productId, quantity: Number(option.quantity || 1) })),
      }))

    if (cleanedFixedItems.length === 0 && cleanedSlots.length === 0) {
      setError('Adicione pelo menos um item fixo ou um grupo de escolha.')
      return
    }
    if (cleanedSlots.some((slot) => slot.options.length === 0)) {
      setError('Todo grupo de escolha precisa de pelo menos uma opção.')
      return
    }

    saveMutation.mutate({
      compositionPayload: {
        discountPercentage: Number(discountPercentage || 0),
        fixedItems: cleanedFixedItems,
        slots: cleanedSlots,
      },
    })
  }

  const notCombo = isEditing && product && product.type !== 'COMBO'
  const isLoading = isEditing && (!product || isCompositionLoading)

  return (
    <div className="mx-auto max-w-6xl">
      <Link
        to="/combos"
        className="mb-2 inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 dark:text-stone-400 dark:hover:text-stone-200"
      >
        <ChevronLeft className="h-4 w-4" />
        Combos
      </Link>

      <h1 className="mb-1 flex items-center gap-2 text-xl font-semibold text-gray-800 dark:text-white">
        <Layers className="h-5 w-5 text-brand-600 dark:text-brand-400" />
        {isEditing ? `Editar combo${product ? ` · ${product.name}` : ''}` : 'Novo combo'}
      </h1>
      <p className="mb-6 max-w-2xl text-sm text-gray-500 dark:text-stone-400">
        Monte o combo em 4 passos: informações básicas, itens que sempre entram, grupos em que o cliente escolhe 1
        produto, e o desconto final.
      </p>

      {notCombo && (
        <div className="rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800 dark:border-amber-500/20 dark:bg-amber-500/10 dark:text-amber-300">
          Este produto não é mais um combo.
        </div>
      )}

      {!notCombo && (
        <>
          {isLoading && <p className="text-sm text-gray-500 dark:text-stone-400">Carregando...</p>}

          {!isLoading && (
            <form onSubmit={handleSubmit} className="grid min-w-0 grid-cols-1 gap-6 lg:grid-cols-[1fr_320px] lg:items-start">
              <div className="min-w-0 space-y-6">
                <Stepper current={step} onStepClick={setStep} />

                {step === 1 && (
                  <Card>
                    <SectionHeader icon={ImageIcon} title="1. Informações básicas" />
                    <div className="flex flex-col gap-5 sm:flex-row">
                      {/* sm:mt-7 nudges the photo down to align with the Nome input, not its label above it. */}
                      <div className="shrink-0 sm:mt-7 sm:w-56">
                        {photoUrl ? (
                          <div className="flex aspect-[4/3] w-full items-center justify-center overflow-hidden rounded-lg bg-gray-100 dark:bg-white/5">
                            {/* object-contain (not cover) so the whole photo shows, uncropped — this
                                is just an upload preview, not the customer-facing menu card. */}
                            <img src={photoUrl} alt="" className="h-full w-full object-contain" />
                          </div>
                        ) : (
                          <div className="flex aspect-[4/3] w-full items-center justify-center rounded-lg bg-gray-100 text-gray-300 dark:bg-white/5 dark:text-stone-700">
                            <ImageIcon className="h-8 w-8" />
                          </div>
                        )}
                        <label className="mt-2 flex w-full cursor-pointer items-center justify-center rounded-md border border-gray-300 px-2 py-1.5 text-center text-xs text-gray-700 hover:bg-gray-100 dark:border-white/10 dark:text-stone-300 dark:hover:bg-white/5">
                          {isUploading ? 'Enviando...' : 'Trocar foto'}
                          <input type="file" accept="image/jpeg,image/png,image/webp" className="hidden" onChange={handleFileChange} disabled={isUploading} />
                        </label>
                      </div>

                      <div className="min-w-0 flex-1 space-y-3">
                        <div>
                          <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="comboName">
                            Nome
                          </label>
                          <input
                            id="comboName"
                            type="text"
                            required
                            maxLength={100}
                            value={name}
                            onChange={(e) => setName(e.target.value)}
                            placeholder="Ex: Combo Casal, Combo Família..."
                            className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
                          />
                        </div>
                        <div>
                          <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="comboDescription">
                            Descrição <span className="font-normal text-gray-400 dark:text-stone-500">(opcional)</span>
                          </label>
                          <textarea
                            id="comboDescription"
                            maxLength={255}
                            value={description}
                            onChange={(e) => setDescription(e.target.value)}
                            className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
                            rows={2}
                          />
                        </div>
                        <div>
                          <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="comboPhotoUrl">
                            URL da foto <span className="font-normal text-gray-400 dark:text-stone-500">(opcional)</span>
                          </label>
                          <input
                            id="comboPhotoUrl"
                            type="text"
                            placeholder="Cole uma URL..."
                            maxLength={500}
                            value={photoUrl}
                            onChange={(e) => setPhotoUrl(e.target.value)}
                            className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
                          />
                        </div>
                      </div>
                    </div>
                    <StepNav onNext={() => setStep(2)} nextDisabled={!name.trim()} />
                  </Card>
                )}

                {step === 2 && (
                  <Card>
                    <SectionHeader icon={Package} title="2. Itens fixos" subtitle="Sempre entram no combo, sem escolha do cliente." />
                    <div className="space-y-2">
                      {fixedItems.map((item, index) => (
                        <ItemRow key={index}>
                          <GroupedSelect
                            value={item.productId}
                            onChange={(val) => updateFixedItemRow(index, { productId: val })}
                            groups={productSelectGroups}
                            placeholder="Selecione um produto"
                          />
                          <div className="flex items-center justify-between gap-2 md:justify-start">
                            <QuantityStepper value={item.quantity} onChange={(val) => updateFixedItemRow(index, { quantity: val })} />
                            <RemoveButton onClick={() => removeFixedItemRow(index)} label="Remover" />
                          </div>
                        </ItemRow>
                      ))}
                      {fixedItems.length === 0 && (
                        <p className="text-xs text-gray-400 dark:text-stone-500">
                          Nenhum item fixo ainda. Opcional — pule para o próximo passo se este combo não tiver itens fixos.
                        </p>
                      )}
                    </div>
                    <AddRowButton onClick={addFixedItemRow} label="Adicionar item fixo" />
                    <StepNav onBack={() => setStep(1)} onNext={() => setStep(3)} />
                  </Card>
                )}

                {step === 3 && (
                  <Card>
                    <SectionHeader
                      icon={ListChecks}
                      title="3. Grupos de escolha"
                      subtitle="Escolha a categoria da qual o cliente vai poder escolher 1 produto. Todos os produtos ativos da categoria entram como opção — remova os que não fizerem sentido pro combo."
                    />
                    <div className="space-y-4">
                      {slots.map((slot, slotIndex) => (
                        <div
                          key={slotIndex}
                          className="rounded-lg border border-gray-200 bg-gray-50/60 dark:border-white/10 dark:bg-white/[0.03]"
                        >
                          <div className="flex items-center gap-2 p-2.5 pb-2">
                            <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-amber-100 text-xs font-semibold text-amber-700 dark:bg-amber-500/15 dark:text-amber-400">
                              {slotIndex + 1}
                            </span>
                            <span className="text-xs font-semibold tracking-wide text-gray-500 uppercase dark:text-stone-400">
                              Grupo {slotIndex + 1} · Escolhe 1
                            </span>
                            <div className="ml-auto">
                              <RemoveButton onClick={() => removeSlot(slotIndex)} label="Remover grupo" />
                            </div>
                          </div>
                          <div className="px-2.5 pb-2.5">
                            <IconSelect
                              value={slot.categoryId}
                              onChange={(categoryId) => updateSlotCategory(slotIndex, categoryId)}
                              options={slotCategorySelectOptions}
                              placeholder="Selecione uma categoria"
                            />
                          </div>
                          <div className="space-y-2 rounded-lg border-t border-gray-200 bg-white p-2.5 dark:border-white/10 dark:bg-stone-900">
                            {slot.options.map((option, optionIndex) => (
                              <ItemRow key={optionIndex}>
                                <GroupedSelect
                                  value={option.productId}
                                  onChange={(val) => updateSlotOption(slotIndex, optionIndex, { productId: val })}
                                  groups={productSelectGroups}
                                  placeholder="Selecione um produto"
                                />
                                <div className="flex items-center justify-between gap-2 md:justify-start">
                                  <QuantityStepper
                                    value={option.quantity}
                                    onChange={(val) => updateSlotOption(slotIndex, optionIndex, { quantity: val })}
                                  />
                                  <RemoveButton onClick={() => removeSlotOption(slotIndex, optionIndex)} label="Remover opção" />
                                </div>
                              </ItemRow>
                            ))}
                            {slot.categoryId !== '' && slot.options.length === 0 && (
                              <p className="text-xs text-gray-400 dark:text-stone-500">
                                Nenhum produto ativo nessa categoria. Adicione uma opção manualmente ou escolha outra categoria.
                              </p>
                            )}
                            <AddOptionLink onClick={() => addSlotOption(slotIndex)} label="Adicionar opção a este grupo" />
                          </div>
                        </div>
                      ))}
                      {slots.length === 0 && (
                        <p className="text-xs text-gray-400 dark:text-stone-500">
                          Nenhum grupo de escolha ainda. Opcional — pule para o próximo passo se este combo não tiver grupos de escolha.
                        </p>
                      )}
                    </div>
                    <AddGroupButton onClick={addSlot} label="Adicionar novo grupo de escolha" />
                    <StepNav onBack={() => setStep(2)} onNext={() => setStep(4)} />
                  </Card>
                )}

                {step === 4 && (
                  <Card>
                    <SectionHeader icon={Wallet} title="4. Desconto e revisão" subtitle="Confira o combo e defina o desconto sobre a soma dos itens." />

                    <div className="mb-4">
                      <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="discountPercentage">
                        Desconto (%)
                      </label>
                      <input
                        id="discountPercentage"
                        type="number"
                        min="0"
                        max="100"
                        step="0.01"
                        value={discountPercentage}
                        onChange={(e) => setDiscountPercentage(e.target.value)}
                        className="w-full max-w-40 rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
                      />
                      <p className="mt-1 text-xs text-gray-400 dark:text-stone-500">
                        Aplicado sobre a soma dos itens fixos + a opção escolhida em cada grupo, pelo preço atual de cada produto.
                      </p>
                    </div>

                    <div className="space-y-3 rounded-lg border border-gray-200 p-3 dark:border-white/10">
                      <div>
                        <p className="mb-1 text-xs font-semibold tracking-wide text-gray-500 uppercase dark:text-stone-400">
                          Itens fixos ({fixedItems.filter((i) => i.productId).length})
                        </p>
                        {fixedItems.some((i) => i.productId) ? (
                          <ul className="space-y-0.5 text-sm text-gray-700 dark:text-stone-300">
                            {fixedItems
                              .filter((i) => i.productId)
                              .map((item, i) => (
                                <li key={i}>
                                  {item.quantity}x {nameOf(item.productId)}
                                </li>
                              ))}
                          </ul>
                        ) : (
                          <p className="text-sm text-gray-400 dark:text-stone-500">Nenhum.</p>
                        )}
                      </div>
                      <div>
                        <p className="mb-1 text-xs font-semibold tracking-wide text-gray-500 uppercase dark:text-stone-400">
                          Grupos de escolha ({slots.filter((s) => s.categoryId).length})
                        </p>
                        {slots.some((s) => s.categoryId) ? (
                          <ul className="space-y-1 text-sm text-gray-700 dark:text-stone-300">
                            {slots
                              .filter((s) => s.categoryId)
                              .map((slot, i) => (
                                <li key={i}>
                                  <span className="font-medium">{slot.name}</span> — escolhe 1 de {slot.options.length}{' '}
                                  {slot.options.length === 1 ? 'opção' : 'opções'}
                                </li>
                              ))}
                          </ul>
                        ) : (
                          <p className="text-sm text-gray-400 dark:text-stone-500">Nenhum.</p>
                        )}
                      </div>
                    </div>

                    {error && <p className="mt-3 text-sm text-wine-600 dark:text-wine-400">{error}</p>}
                    {success && <p className="mt-3 text-sm text-green-600 dark:text-green-400">Combo salvo com sucesso.</p>}

                    <div className="mt-4 flex items-center justify-between border-t border-gray-100 pt-4 dark:border-white/10">
                      <button
                        type="button"
                        onClick={() => setStep(3)}
                        className="flex items-center gap-1 rounded-md px-3 py-2 text-sm font-medium text-gray-600 hover:bg-gray-100 dark:text-stone-300 dark:hover:bg-white/5"
                      >
                        <ChevronLeft className="h-4 w-4" /> Voltar
                      </button>
                      <button
                        type="submit"
                        disabled={saveMutation.isPending}
                        className="rounded-md bg-brand-600 px-5 py-2.5 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50"
                      >
                        {saveMutation.isPending ? 'Salvando...' : 'Salvar combo'}
                      </button>
                    </div>
                  </Card>
                )}
              </div>

              <aside className="min-w-0 lg:sticky lg:top-6">
                <div className="space-y-4 rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-white/10 dark:bg-stone-900">
                  <div>
                    <h2 className="truncate text-base font-semibold text-gray-800 dark:text-white">{name || 'Novo combo'}</h2>
                    {description && <p className="mt-0.5 line-clamp-2 text-xs text-gray-500 dark:text-stone-400">{description}</p>}
                  </div>

                  <div className="rounded-lg bg-brand-50 p-3 dark:bg-brand-500/10">
                    <p className="flex items-center gap-1.5 text-xs font-medium text-brand-700 dark:text-brand-400">
                      <Wallet className="h-3.5 w-3.5" />
                      Preço estimado
                    </p>
                    <p className="mt-0.5 text-xl font-semibold text-brand-800 dark:text-brand-300">
                      {priceRange
                        ? priceRange.min === priceRange.max
                          ? currencyFormatter.format(priceRange.min)
                          : `${currencyFormatter.format(priceRange.min)} – ${currencyFormatter.format(priceRange.max)}`
                        : '—'}
                    </p>
                  </div>

                  <dl className="space-y-1.5 text-sm">
                    <div className="flex items-center justify-between">
                      <dt className="text-gray-500 dark:text-stone-400">Itens fixos</dt>
                      <dd className="font-medium text-gray-800 dark:text-white">{fixedItems.filter((i) => i.productId).length}</dd>
                    </div>
                    <div className="flex items-center justify-between">
                      <dt className="text-gray-500 dark:text-stone-400">Grupos de escolha</dt>
                      <dd className="font-medium text-gray-800 dark:text-white">{slots.filter((s) => s.categoryId).length}</dd>
                    </div>
                  </dl>

                  {step < 4 && (
                    <button
                      type="button"
                      onClick={() => setStep(4)}
                      className="w-full rounded-md border border-dashed border-gray-300 px-3 py-2 text-xs font-medium text-gray-500 hover:border-brand-400 hover:text-brand-600 dark:border-white/15 dark:text-stone-400 dark:hover:text-brand-400"
                    >
                      Ir direto para desconto e salvar
                    </button>
                  )}
                </div>
              </aside>
            </form>
          )}
        </>
      )}
    </div>
  )
}

function Card({ children }: { children: ReactNode }) {
  return (
    <div className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-white/10 dark:bg-stone-900">{children}</div>
  )
}

function SectionHeader({ icon: Icon, title, subtitle }: { icon: LucideIcon; title: string; subtitle?: string }) {
  return (
    <div className="mb-4 flex items-start gap-3">
      <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-brand-50 text-brand-600 dark:bg-brand-500/10 dark:text-brand-400">
        <Icon className="h-4 w-4" />
      </div>
      <div className="min-w-0 flex-1">
        <h2 className="text-sm font-semibold text-gray-800 dark:text-white">{title}</h2>
        {subtitle && <p className="mt-0.5 text-xs text-gray-500 dark:text-stone-400">{subtitle}</p>}
      </div>
    </div>
  )
}

const WIZARD_STEPS = [
  { id: 1, label: 'Informações' },
  { id: 2, label: 'Itens fixos' },
  { id: 3, label: 'Grupos de escolha' },
  { id: 4, label: 'Desconto' },
]

function Stepper({ current, onStepClick }: { current: number; onStepClick: (step: number) => void }) {
  return (
    <div className="flex items-center" aria-label="Passos do formulário">
      {WIZARD_STEPS.map((s, i) => {
        const isActive = s.id === current
        const isDone = s.id < current
        return (
          <div key={s.id} className={`flex items-center ${i < WIZARD_STEPS.length - 1 ? 'flex-1' : ''}`}>
            <button
              type="button"
              onClick={() => onStepClick(s.id)}
              className="flex shrink-0 items-center gap-2"
              aria-current={isActive ? 'step' : undefined}
            >
              <span
                className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-xs font-semibold transition-colors ${
                  isActive
                    ? 'bg-brand-600 text-white'
                    : isDone
                      ? 'bg-brand-100 text-brand-700 dark:bg-brand-500/20 dark:text-brand-400'
                      : 'bg-gray-100 text-gray-400 dark:bg-white/5 dark:text-stone-500'
                }`}
              >
                {isDone ? <Check className="h-3.5 w-3.5" /> : s.id}
              </span>
              <span
                className={`hidden text-sm font-medium whitespace-nowrap sm:inline ${
                  isActive ? 'text-gray-800 dark:text-white' : 'text-gray-400 dark:text-stone-500'
                }`}
              >
                {s.label}
              </span>
            </button>
            {i < WIZARD_STEPS.length - 1 && (
              <div className={`mx-2 h-0.5 flex-1 ${isDone ? 'bg-brand-300 dark:bg-brand-500/40' : 'bg-gray-200 dark:bg-white/10'}`} />
            )}
          </div>
        )
      })}
    </div>
  )
}

function StepNav({
  onBack,
  onNext,
  nextDisabled,
}: {
  onBack?: () => void
  onNext?: () => void
  nextDisabled?: boolean
}) {
  return (
    <div className="mt-4 flex items-center justify-between border-t border-gray-100 pt-4 dark:border-white/10">
      {onBack ? (
        <button
          type="button"
          onClick={onBack}
          className="flex items-center gap-1 rounded-md px-3 py-2 text-sm font-medium text-gray-600 hover:bg-gray-100 dark:text-stone-300 dark:hover:bg-white/5"
        >
          <ChevronLeft className="h-4 w-4" /> Voltar
        </button>
      ) : (
        <span />
      )}
      {onNext && (
        <button
          type="button"
          onClick={onNext}
          disabled={nextDisabled}
          title={nextDisabled ? 'Informe um nome para continuar' : undefined}
          className="flex items-center gap-1 rounded-md bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:cursor-not-allowed disabled:opacity-40"
        >
          Próximo <ChevronRight className="h-4 w-4" />
        </button>
      )}
    </div>
  )
}

function ItemRow({ children }: { children: ReactNode }) {
  return (
    <div className="flex flex-col gap-2 rounded-lg border border-gray-100 bg-gray-50/60 p-2 md:flex-row md:items-center dark:border-white/5 dark:bg-white/[0.03]">
      {children}
    </div>
  )
}

function QuantityStepper({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  const numeric = Number(value) || 1
  return (
    <div className="flex shrink-0 items-center gap-0.5 rounded-md border border-gray-300 dark:border-white/10">
      <button
        type="button"
        onClick={() => onChange(String(Math.max(1, numeric - 1)))}
        aria-label="Diminuir quantidade"
        className="flex h-9 w-7 items-center justify-center text-gray-500 hover:bg-gray-100 dark:text-stone-400 dark:hover:bg-white/5"
      >
        <Minus className="h-3.5 w-3.5" />
      </button>
      <span className="w-5 text-center text-sm font-medium text-gray-800 dark:text-white">{numeric}</span>
      <button
        type="button"
        onClick={() => onChange(String(numeric + 1))}
        aria-label="Aumentar quantidade"
        className="flex h-9 w-7 items-center justify-center text-gray-500 hover:bg-gray-100 dark:text-stone-400 dark:hover:bg-white/5"
      >
        <Plus className="h-3.5 w-3.5" />
      </button>
    </div>
  )
}

function RemoveButton({ onClick, label }: { onClick: () => void; label: string }) {
  return (
    <button
      type="button"
      onClick={onClick}
      title={label}
      aria-label={label}
      className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md text-gray-400 hover:bg-red-50 hover:text-red-600 dark:text-stone-500 dark:hover:bg-red-500/10 dark:hover:text-red-400"
    >
      <Minus className="h-4 w-4" />
    </button>
  )
}

function AddRowButton({ onClick, label }: { onClick: () => void; label: string }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="mt-3 flex w-full items-center justify-center gap-1.5 rounded-lg border border-dashed border-gray-300 py-2 text-sm font-medium text-brand-600 hover:border-brand-400 hover:bg-brand-50/50 dark:border-white/15 dark:text-brand-400 dark:hover:bg-brand-500/5"
    >
      <Plus className="h-4 w-4" />
      {label}
    </button>
  )
}

/** Adds a new top-level choice group. Deliberately bolder than {@link AddOptionLink} —
 * a solid, filled button instead of a text link — so it doesn't get confused with the
 * action that adds an option *inside* a group. */
function AddGroupButton({ onClick, label }: { onClick: () => void; label: string }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="mt-4 flex w-full items-center justify-center gap-2 rounded-lg border-2 border-brand-500 bg-brand-50 py-3 text-sm font-semibold text-brand-700 hover:bg-brand-100 dark:border-brand-400/60 dark:bg-brand-500/10 dark:text-brand-400 dark:hover:bg-brand-500/15"
    >
      <ListPlus className="h-4 w-4" />
      {label}
    </button>
  )
}

/** Adds an option inside an existing group. A plain text link, not a button, so it
 * reads as a minor action nested inside the group card rather than competing with
 * {@link AddGroupButton}. */
function AddOptionLink({ onClick, label }: { onClick: () => void; label: string }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="flex items-center gap-1 py-1 pl-0.5 text-xs font-medium text-brand-600 hover:text-brand-700 hover:underline dark:text-brand-400 dark:hover:text-brand-300"
    >
      <Plus className="h-3.5 w-3.5" />
      {label}
    </button>
  )
}
