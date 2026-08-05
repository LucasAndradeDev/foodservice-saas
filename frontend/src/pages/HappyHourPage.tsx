import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { motion } from 'framer-motion'
import {
  Apple,
  Beef,
  Beer,
  Cake,
  Clock,
  Coffee,
  Cookie,
  CupSoda,
  Donut,
  Drumstick,
  Fish,
  GlassWater,
  Hamburger,
  IceCreamCone,
  Layers,
  type LucideIcon,
  Milk,
  Pencil,
  Percent,
  Pizza,
  Plus,
  Popcorn,
  Power,
  Salad,
  Sandwich,
  Shrimp,
  Store,
  Tag,
  Ticket,
  Trash2,
  Users,
  UtensilsCrossed,
  Vegan,
  Wine,
} from 'lucide-react'
import { useState, type FormEvent } from 'react'
import {
  createHappyHourRule,
  deleteHappyHourRule,
  listHappyHourRules,
  updateHappyHourRule,
  type HappyHourRule,
  type HappyHourRulePayload,
} from '../api/happyHour'
import { listCategories } from '../api/categories'
import type { DayOfWeek } from '../api/productAvailability'
import type { DiscountType } from '../api/orders'
import { Button } from '../components/Button'
import { Dropdown } from '../components/Dropdown'
import { Modal } from '../components/Modal'
import { SectionTabs } from '../components/SectionTabs'
import { TimePicker } from '../components/TimePicker'

const MANAGEMENT_TABS = [
  { to: '/settings', label: 'Geral', icon: Store },
  { to: '/coupons', label: 'Cupons', icon: Ticket },
  { to: '/happy-hour', label: 'Happy Hour', icon: Clock },
  { to: '/staff', label: 'Funcionários', icon: Users },
]

const WEEK_DAYS: { value: DayOfWeek; shortLabel: string }[] = [
  { value: 'MONDAY', shortLabel: 'Seg' },
  { value: 'TUESDAY', shortLabel: 'Ter' },
  { value: 'WEDNESDAY', shortLabel: 'Qua' },
  { value: 'THURSDAY', shortLabel: 'Qui' },
  { value: 'FRIDAY', shortLabel: 'Sex' },
  { value: 'SATURDAY', shortLabel: 'Sáb' },
  { value: 'SUNDAY', shortLabel: 'Dom' },
]

const ALL_WEEK_DAYS: DayOfWeek[] = WEEK_DAYS.map((day) => day.value)
const WEEKDAY_PRESET: DayOfWeek[] = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY']
const WEEKEND_PRESET: DayOfWeek[] = ['SATURDAY', 'SUNDAY']

const currencyFormatter = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })
const PREVIEW_BASE_PRICE = 50

// Category has no icon field in the backend, so this guesses one from the name (PT-BR keywords, first match wins).
const CATEGORY_ICON_RULES: [string[], LucideIcon][] = [
  [['pizza'], Pizza],
  [['salada'], Salad],
  [['combo'], Layers],
  [['sobremesa', 'doce', 'sorvete', 'gelato'], IceCreamCone],
  [['bolo', 'torta'], Cake],
  [['biscoito', 'cookie'], Cookie],
  [['donut', 'rosquinha'], Donut],
  [['hambúrguer', 'hamburguer', 'burger'], Hamburger],
  [['lanche', 'sanduíche', 'sanduiche'], Sandwich],
  [['carne', 'churrasco', 'grelhado', 'espeto'], Beef],
  [['frango', 'ave'], Drumstick],
  [['camarão', 'camarao'], Shrimp],
  [['peixe', 'frutos do mar'], Fish],
  [['cerveja', 'chopp'], Beer],
  [['vinho'], Wine],
  [['café', 'cafe'], Coffee],
  [['água', 'agua'], GlassWater],
  [['leite', 'vitamina', 'milkshake'], Milk],
  [['suco', 'refrigerante', 'bebida', 'drink'], CupSoda],
  [['fruta'], Apple],
  [['pipoca'], Popcorn],
  [['vegano', 'vegetariano', 'vegan'], Vegan],
  [['massa', 'macarrão', 'macarrao', 'lasanha', 'nhoque'], UtensilsCrossed],
]

function categoryIcon(name: string): LucideIcon {
  const lower = name.toLowerCase()
  for (const [keywords, icon] of CATEGORY_ICON_RULES) {
    if (keywords.some((keyword) => lower.includes(keyword))) {
      return icon
    }
  }
  return Tag
}

function dayChipClass(active: boolean): string {
  return `flex h-10 w-10 items-center justify-center rounded-full border text-sm font-medium transition ${
    active
      ? 'border-brand-600 bg-brand-600 text-white shadow-sm'
      : 'border-gray-200 bg-white text-gray-600 shadow-sm hover:border-brand-400 hover:text-brand-700 dark:border-white/10 dark:bg-stone-800 dark:text-stone-400 dark:hover:border-brand-400 dark:hover:text-brand-400'
  }`
}

function presetChipClass(active: boolean): string {
  return `flex h-9 items-center justify-center rounded-full border text-xs font-semibold transition ${
    active
      ? 'border-brand-600 bg-brand-600 text-white shadow-sm'
      : 'border-gray-200 bg-white text-gray-600 shadow-sm hover:border-brand-400 hover:text-brand-700 dark:border-white/10 dark:bg-stone-800 dark:text-stone-400 dark:hover:border-brand-400 dark:hover:text-brand-400'
  }`
}

function sameDaySet(a: DayOfWeek[], b: DayOfWeek[]): boolean {
  return a.length === b.length && new Set(a).size === new Set([...a, ...b]).size
}

function daysLabel(daysOfWeek: DayOfWeek[]): string {
  if (daysOfWeek.length === 0 || daysOfWeek.length === 7) return 'Todo dia'
  if (sameDaySet(daysOfWeek, WEEKDAY_PRESET)) return 'Dias úteis'
  if (sameDaySet(daysOfWeek, WEEKEND_PRESET)) return 'Fim de semana'
  return daysOfWeek
    .slice()
    .sort((a, b) => ALL_WEEK_DAYS.indexOf(a) - ALL_WEEK_DAYS.indexOf(b))
    .map((value) => WEEK_DAYS.find((day) => day.value === value)?.shortLabel)
    .join(', ')
}

function formatTime(time: string): string {
  return time.slice(0, 5)
}

function formatDiscount(type: DiscountType, value: number): string {
  return type === 'PERCENTAGE' ? `${value}%` : `R$ ${value.toFixed(2)}`
}

export function HappyHourPage() {
  const queryClient = useQueryClient()

  const { data: rules, isLoading } = useQuery({
    queryKey: ['happyHourRules'],
    queryFn: listHappyHourRules,
  })

  const { data: categories } = useQuery({
    queryKey: ['categories', { active: true }],
    queryFn: () => listCategories(true),
  })
  const categoryOptions =
    categories?.map((category) => ({ value: category.id, label: category.name, icon: categoryIcon(category.name) })) ?? []

  const [editingRule, setEditingRule] = useState<HappyHourRule | null>(null)
  const [isCreating, setIsCreating] = useState(false)
  const [categoryId, setCategoryId] = useState('')
  const [selectedDays, setSelectedDays] = useState<DayOfWeek[]>(ALL_WEEK_DAYS)
  const [startTime, setStartTime] = useState('17:00')
  const [endTime, setEndTime] = useState('19:00')
  const [discountType, setDiscountType] = useState<DiscountType>('PERCENTAGE')
  const [discountValue, setDiscountValue] = useState('20')
  const [active, setActive] = useState(true)
  const [error, setError] = useState<string | null>(null)

  function invalidate() {
    queryClient.invalidateQueries({ queryKey: ['happyHourRules'] })
  }

  function handleError() {
    setError('Não foi possível salvar. Verifique se o horário de início é antes do fim e se o valor do desconto é válido.')
  }

  const createMutation = useMutation({
    mutationFn: (payload: HappyHourRulePayload) => createHappyHourRule(payload),
    onSuccess: () => {
      invalidate()
      closeForm()
    },
    onError: handleError,
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: HappyHourRulePayload }) => updateHappyHourRule(id, payload),
    onSuccess: () => {
      invalidate()
      closeForm()
    },
    onError: handleError,
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteHappyHourRule(id),
    onSuccess: invalidate,
  })

  function openCreateForm() {
    setCategoryId(categories?.[0]?.id ?? '')
    setSelectedDays(ALL_WEEK_DAYS)
    setStartTime('17:00')
    setEndTime('19:00')
    setDiscountType('PERCENTAGE')
    setDiscountValue('20')
    setActive(true)
    setError(null)
    setIsCreating(true)
  }

  function openEditForm(rule: HappyHourRule) {
    setEditingRule(rule)
    setCategoryId(rule.categoryId)
    setSelectedDays(rule.daysOfWeek.length > 0 ? rule.daysOfWeek : ALL_WEEK_DAYS)
    setStartTime(formatTime(rule.startTime))
    setEndTime(formatTime(rule.endTime))
    setDiscountType(rule.discountType)
    setDiscountValue(String(rule.discountValue))
    setActive(rule.active)
    setError(null)
  }

  function closeForm() {
    setIsCreating(false)
    setEditingRule(null)
  }

  function toggleSelectedDay(day: DayOfWeek) {
    setSelectedDays((prev) => (prev.includes(day) ? prev.filter((d) => d !== day) : [...prev, day]))
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)

    if (startTime >= endTime) {
      setError('O horário de início precisa ser antes do horário de fim.')
      return
    }

    if (selectedDays.length === 0) {
      setError('Selecione ao menos um dia.')
      return
    }

    const payload: HappyHourRulePayload = {
      categoryId,
      daysOfWeek: selectedDays.length === ALL_WEEK_DAYS.length ? null : selectedDays,
      startTime: `${startTime}:00`,
      endTime: `${endTime}:00`,
      discountType,
      discountValue: Number(discountValue),
      active,
    }

    if (editingRule) {
      updateMutation.mutate({ id: editingRule.id, payload })
    } else {
      createMutation.mutate(payload)
    }
  }

  function toggleActive(rule: HappyHourRule) {
    updateMutation.mutate({
      id: rule.id,
      payload: {
        categoryId: rule.categoryId,
        daysOfWeek: rule.daysOfWeek.length === ALL_WEEK_DAYS.length ? null : rule.daysOfWeek,
        startTime: rule.startTime,
        endTime: rule.endTime,
        discountType: rule.discountType,
        discountValue: rule.discountValue,
        active: !rule.active,
      },
    })
  }

  function confirmAndDelete(rule: HappyHourRule) {
    const confirmed = globalThis.confirm(
      `Excluir a regra de happy hour "${rule.categoryName}, ${daysLabel(rule.daysOfWeek)} ${formatTime(rule.startTime)}–${formatTime(rule.endTime)}"?`,
    )
    if (confirmed) {
      deleteMutation.mutate(rule.id)
    }
  }

  const isFormOpen = isCreating || editingRule !== null
  const numericDiscountValue = Number(discountValue)
  const previewPrice =
    discountType === 'PERCENTAGE'
      ? Math.max(PREVIEW_BASE_PRICE * (1 - numericDiscountValue / 100), 0)
      : Math.max(PREVIEW_BASE_PRICE - numericDiscountValue, 0)

  return (
    <div>
      <SectionTabs tabs={MANAGEMENT_TABS} />

      <div className="mb-5 flex flex-wrap items-center justify-between gap-3 rounded-b-2xl border border-gray-200 bg-white p-5 shadow-sm dark:border-white/10 dark:bg-stone-900">
        <div className="flex items-center gap-3">
          <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-brand-600 text-white shadow-sm">
            <Clock className="h-5 w-5" />
          </span>
          <h1 className="text-lg font-bold text-gray-900 dark:text-white">Happy Hour</h1>
        </div>
        <Button
          type="button"
          onClick={openCreateForm}
          disabled={!categories || categories.length === 0}
          className="shrink-0 whitespace-nowrap"
        >
          <Plus className="h-4 w-4" />
          Nova regra
        </Button>
      </div>
      <p className="mb-4 text-sm text-gray-500 dark:text-stone-400">
        Desconto automático por categoria, em dias e horários específicos. Aparece no cardápio digital e é aplicado
        sozinho quando o item é lançado na comanda dentro do horário.
      </p>

      {categories && categories.length === 0 && (
        <div className="mb-4 rounded-xl border border-dashed border-gray-300 bg-gray-50 p-6 text-center dark:border-white/10 dark:bg-white/5">
          <p className="text-sm text-gray-500 dark:text-stone-400">
            Cadastre uma categoria de produtos antes de criar uma regra de happy hour.
          </p>
        </div>
      )}

      {isLoading && <p className="text-sm text-gray-500 dark:text-stone-400">Carregando...</p>}

      {rules && rules.length === 0 && (
        <div className="rounded-xl border border-dashed border-gray-300 bg-gray-50 p-6 text-center dark:border-white/10 dark:bg-white/5">
          <Clock className="mx-auto mb-2 h-8 w-8 text-gray-300 dark:text-stone-600" />
          <p className="mb-1 text-sm font-medium text-gray-700 dark:text-stone-300">Nenhuma regra cadastrada ainda</p>
          <p className="mx-auto max-w-sm text-sm text-gray-500 dark:text-stone-400">
            Exemplo: "Bebidas, sexta a domingo, 17:00–19:00, 20% off". Clique em "Nova regra" pra criar a primeira.
          </p>
        </div>
      )}

      {rules && rules.length > 0 && (
        <div className="space-y-2">
          {rules.map((rule) => (
            <div
              key={rule.id}
              className="flex items-center justify-between gap-3 rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-white/10 dark:bg-stone-900"
            >
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="font-medium text-gray-800 dark:text-white">{rule.categoryName}</span>
                  {!rule.active && (
                    <span className="rounded-full bg-gray-100 px-2 py-0.5 text-xs font-medium text-gray-500 dark:bg-white/10 dark:text-stone-400">
                      Inativa
                    </span>
                  )}
                </div>
                <div className="mt-1.5 flex flex-wrap items-center gap-1.5">
                  <span className="rounded-full bg-gray-100 px-2 py-0.5 text-xs font-medium text-gray-600 dark:bg-white/10 dark:text-stone-400">
                    {daysLabel(rule.daysOfWeek)}
                  </span>
                  <span className="rounded-full bg-gray-100 px-2 py-0.5 text-xs font-medium text-gray-600 dark:bg-white/10 dark:text-stone-400">
                    {formatTime(rule.startTime)}–{formatTime(rule.endTime)}
                  </span>
                  <span className="flex items-center gap-0.5 rounded-full bg-pink-100 px-2 py-0.5 text-xs font-semibold text-pink-700 dark:bg-pink-500/10 dark:text-pink-400">
                    <Percent className="h-3 w-3" />
                    {formatDiscount(rule.discountType, rule.discountValue)} off
                  </span>
                </div>
              </div>
              <div className="flex shrink-0 items-center gap-1">
                <button
                  type="button"
                  onClick={() => toggleActive(rule)}
                  title={rule.active ? 'Desativar' : 'Ativar'}
                  aria-label={rule.active ? 'Desativar' : 'Ativar'}
                  className={`rounded-md p-1.5 hover:bg-gray-100 dark:hover:bg-white/5 ${rule.active ? 'text-gray-500 hover:text-amber-700 dark:text-stone-400 dark:hover:text-amber-400' : 'text-gray-400 hover:text-green-700 dark:text-stone-500 dark:hover:text-green-400'}`}
                >
                  <Power className="h-4 w-4" />
                </button>
                <button
                  type="button"
                  onClick={() => openEditForm(rule)}
                  title="Editar"
                  aria-label="Editar"
                  className="rounded-md p-1.5 text-gray-500 hover:bg-gray-100 hover:text-brand-700 dark:text-stone-400 dark:hover:bg-white/5 dark:hover:text-brand-400"
                >
                  <Pencil className="h-4 w-4" />
                </button>
                <button
                  type="button"
                  onClick={() => confirmAndDelete(rule)}
                  title="Excluir"
                  aria-label="Excluir"
                  className="rounded-md p-1.5 text-gray-500 hover:bg-red-50 hover:text-red-700 dark:text-stone-400 dark:hover:bg-red-500/10 dark:hover:text-red-400"
                >
                  <Trash2 className="h-4 w-4" />
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {isFormOpen && (
        <Modal title={editingRule ? 'Editar regra' : 'Nova regra de happy hour'} onClose={closeForm}>
          <form onSubmit={handleSubmit}>
            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300">Categoria</label>
            <div className="mb-4">
              <Dropdown
                value={categoryId}
                options={categoryOptions}
                onChange={setCategoryId}
                icon={Tag}
                fullWidth
                panelClassName="w-full"
                mobileTitle="Selecionar categoria"
              />
            </div>

            <p className="mb-2 text-xs font-semibold tracking-wide text-gray-400 uppercase dark:text-stone-500">
              Quando
            </p>

            <div className="mb-4">
              <div className="mb-2.5 grid grid-cols-3 gap-1.5">
                <motion.button
                  type="button"
                  whileTap={{ scale: 0.96 }}
                  onClick={() => setSelectedDays(ALL_WEEK_DAYS)}
                  className={presetChipClass(sameDaySet(selectedDays, ALL_WEEK_DAYS))}
                >
                  Todo dia
                </motion.button>
                <motion.button
                  type="button"
                  whileTap={{ scale: 0.96 }}
                  onClick={() => setSelectedDays(WEEKDAY_PRESET)}
                  className={presetChipClass(sameDaySet(selectedDays, WEEKDAY_PRESET))}
                >
                  Dias úteis
                </motion.button>
                <motion.button
                  type="button"
                  whileTap={{ scale: 0.96 }}
                  onClick={() => setSelectedDays(WEEKEND_PRESET)}
                  className={presetChipClass(sameDaySet(selectedDays, WEEKEND_PRESET))}
                >
                  Fim de semana
                </motion.button>
              </div>
              <div className="grid grid-cols-7 place-items-center gap-y-2">
                {WEEK_DAYS.map((day) => (
                  <motion.button
                    key={day.value}
                    type="button"
                    whileTap={{ scale: 0.9 }}
                    onClick={() => toggleSelectedDay(day.value)}
                    className={dayChipClass(selectedDays.includes(day.value))}
                  >
                    {day.shortLabel}
                  </motion.button>
                ))}
              </div>
              <p className="mt-2 text-xs text-gray-400 dark:text-stone-500">
                Toque nos dias pra ajustar manualmente — tudo isso é uma regra só.
              </p>
            </div>

            <div className="mb-4 flex gap-3">
              <div className="flex-1">
                <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="startTime">
                  Início
                </label>
                <TimePicker id="startTime" value={startTime} onChange={setStartTime} />
              </div>
              <div className="flex-1">
                <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="endTime">
                  Fim
                </label>
                <TimePicker id="endTime" value={endTime} onChange={setEndTime} />
              </div>
            </div>

            <p className="mb-2 text-xs font-semibold tracking-wide text-gray-400 uppercase dark:text-stone-500">
              Desconto
            </p>

            <div className="mb-1.5 grid grid-cols-2 gap-3">
              <div>
                <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="discountType">
                  Tipo
                </label>
                <select
                  id="discountType"
                  value={discountType}
                  onChange={(e) => setDiscountType(e.target.value as DiscountType)}
                  className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
                >
                  <option value="PERCENTAGE">Percentual</option>
                  <option value="FIXED">Valor fixo</option>
                </select>
              </div>
              <div>
                <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="discountValue">
                  Valor
                </label>
                <input
                  id="discountValue"
                  type="number"
                  required
                  min="0.01"
                  step="0.01"
                  max={discountType === 'PERCENTAGE' ? 100 : undefined}
                  value={discountValue}
                  onChange={(e) => setDiscountValue(e.target.value)}
                  className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
                />
              </div>
            </div>
            {numericDiscountValue > 0 && (
              <p className="mb-4 text-xs text-gray-400 dark:text-stone-500">
                Ex.: um item de {currencyFormatter.format(PREVIEW_BASE_PRICE)} sai por{' '}
                {currencyFormatter.format(previewPrice)}.
              </p>
            )}

            <div className="mb-4 flex items-center justify-between rounded-lg border border-gray-200 px-3 py-2.5 dark:border-white/10">
              <span className="text-sm font-medium text-gray-700 dark:text-stone-300">Regra ativa</span>
              <label className="relative inline-flex cursor-pointer items-center">
                <input
                  type="checkbox"
                  className="peer sr-only"
                  checked={active}
                  onChange={(e) => setActive(e.target.checked)}
                />
                <div className="h-6 w-11 rounded-full bg-gray-300 transition-colors peer-checked:bg-brand-600 dark:bg-white/20" />
                <div className="absolute top-0.5 left-0.5 h-5 w-5 rounded-full bg-white shadow-sm transition-transform peer-checked:translate-x-5" />
              </label>
            </div>

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
