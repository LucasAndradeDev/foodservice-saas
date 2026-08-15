import { Check, Plus, Users, X } from 'lucide-react'
import { useState } from 'react'
import type { OrderItem } from '../../api/orders'
import { roundCurrency } from '../../api/tabs'
import { Button } from '../../components/Button'

interface Person {
  id: string
  name: string
}

let personSeq = 0
function nextPersonId() {
  personSeq += 1
  return `person-${personSeq}`
}

const currencyFormatter = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

// Cycles through the app's own status palette (Badge.tsx's free/reserved/attention/critical
// tones, plus brand) so each person gets a stable, distinct color that still belongs to this
// system's identity - not an arbitrary rainbow. Same shape as Badge's TONE_STYLES/DOT_STYLES:
// a soft tint when unselected, the solid -600 when selected/avatar. Tailwind's JIT scanner
// needs full literal class strings (no `bg-${color}-600` interpolation), hence spelled out.
const PERSON_COLORS = [
  { solid: 'bg-brand-600', tint: 'bg-brand-100 text-brand-700 dark:bg-brand-500/20 dark:text-brand-400' },
  { solid: 'bg-teal-600', tint: 'bg-teal-100 text-teal-700 dark:bg-teal-500/20 dark:text-teal-400' },
  { solid: 'bg-sage-600', tint: 'bg-sage-100 text-sage-700 dark:bg-sage-500/20 dark:text-sage-400' },
  { solid: 'bg-wine-600', tint: 'bg-wine-100 text-wine-700 dark:bg-wine-500/20 dark:text-wine-400' },
  { solid: 'bg-gold-500', tint: 'bg-gold-100 text-gold-700 dark:bg-gold-500/20 dark:text-gold-400' },
]

function personColor(index: number) {
  return PERSON_COLORS[index % PERSON_COLORS.length]
}

/** Splits remainingBalance proportionally to each person's assigned-item weight, last
 * weighted person absorbing the rounding remainder so the sum always matches exactly -
 * same rounding idea as CheckoutPage's handleSplitEqually. */
function computeAmounts(weights: number[], remainingBalance: number): number[] {
  const totalWeight = weights.reduce((sum, w) => sum + w, 0)
  if (totalWeight <= 0) return weights.map(() => 0)
  const amounts = weights.map((w) => roundCurrency((remainingBalance * w) / totalWeight))
  const diff = roundCurrency(remainingBalance - amounts.reduce((sum, a) => sum + a, 0))
  const lastWeightedIndex = weights.reduce((last, w, i) => (w > 0 ? i : last), -1)
  if (lastWeightedIndex >= 0) amounts[lastWeightedIndex] = roundCurrency(amounts[lastWeightedIndex] + diff)
  return amounts
}

interface PersonSplitPanelProps {
  items: OrderItem[]
  remainingBalance: number
  onApply: (entries: { name: string; amount: number }[]) => void
  onCancel: () => void
}

export function PersonSplitPanel({ items, remainingBalance, onApply, onCancel }: PersonSplitPanelProps) {
  const [people, setPeople] = useState<Person[]>([
    { id: nextPersonId(), name: '' },
    { id: nextPersonId(), name: '' },
  ])
  // itemId -> personIds sharing that item
  const [assignments, setAssignments] = useState<Record<string, string[]>>({})

  const billableItems = items.filter((item) => item.status !== 'CANCELLED')
  const unassignedCount = billableItems.filter((item) => (assignments[item.id]?.length ?? 0) === 0).length

  function personLabel(person: Person, index: number) {
    return person.name.trim() || `Pessoa ${index + 1}`
  }

  function personInitial(person: Person, index: number) {
    return (person.name.trim()[0] ?? String(index + 1)).toUpperCase()
  }

  function addPerson() {
    setPeople((prev) => [...prev, { id: nextPersonId(), name: '' }])
  }

  function removePerson(id: string) {
    setPeople((prev) => (prev.length <= 2 ? prev : prev.filter((person) => person.id !== id)))
    setAssignments((prev) => {
      const next: Record<string, string[]> = {}
      for (const [itemId, personIds] of Object.entries(prev)) {
        next[itemId] = personIds.filter((personId) => personId !== id)
      }
      return next
    })
  }

  function renamePerson(id: string, name: string) {
    setPeople((prev) => prev.map((person) => (person.id === id ? { ...person, name } : person)))
  }

  // Tapping a chip while the item has 0 or 1 owner is exclusive - it replaces whoever was
  // there (or clears it, tapping the same person twice), so assigning a plain item never takes
  // more than one tap. Once an item has 2+ owners (via "Dividir entre todos" below, or by having
  // been shared in an earlier session), taps become a plain multi-select toggle instead - no
  // separate mode flag needed, the current owner count already says which behavior makes sense.
  function handleChipClick(itemId: string, personId: string) {
    setAssignments((prev) => {
      const current = prev[itemId] ?? []
      if (current.length <= 1) {
        const next = current[0] === personId ? [] : [personId]
        return { ...prev, [itemId]: next }
      }
      const next = current.includes(personId) ? current.filter((id) => id !== personId) : [...current, personId]
      return { ...prev, [itemId]: next }
    })
  }

  function shareWithEveryone(itemId: string) {
    setAssignments((prev) => ({ ...prev, [itemId]: people.map((person) => person.id) }))
  }

  const weights = people.map((person) =>
    billableItems.reduce((sum, item) => {
      const sharedWith = assignments[item.id] ?? []
      if (!sharedWith.includes(person.id)) return sum
      return sum + item.netSubtotal / sharedWith.length
    }, 0),
  )
  const amounts = computeAmounts(weights, remainingBalance)
  const canApply = unassignedCount === 0 && billableItems.length > 0

  return (
    <div className="border-t border-gray-100 pt-4 dark:border-white/10">
      <div className="mb-5">
        <p className="mb-2 text-xs font-semibold tracking-wide text-gray-400 uppercase dark:text-stone-500">Pessoas</p>
        <ul className="space-y-2">
          {people.map((person, index) => {
            const color = personColor(index)
            return (
              <li key={person.id} className="flex items-center gap-2.5">
                <span
                  className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-sm font-bold text-white ${color.solid}`}
                >
                  {personInitial(person, index)}
                </span>
                <input
                  type="text"
                  value={person.name}
                  onChange={(e) => renamePerson(person.id, e.target.value)}
                  placeholder={`Pessoa ${index + 1}`}
                  className="flex-1 rounded-lg border border-gray-300 px-3 py-2.5 text-base focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400 sm:text-sm"
                />
                {people.length > 2 && (
                  <button
                    type="button"
                    onClick={() => removePerson(person.id)}
                    aria-label="Remover pessoa"
                    className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full text-gray-400 transition hover:bg-gray-100 hover:text-gray-600 dark:text-stone-500 dark:hover:bg-white/10 dark:hover:text-stone-300"
                  >
                    <X className="h-5 w-5" />
                  </button>
                )}
              </li>
            )
          })}
        </ul>
        <button
          type="button"
          onClick={addPerson}
          className="mt-1.5 flex items-center gap-1.5 rounded-lg px-2 py-2.5 text-sm font-medium text-brand-600 transition hover:bg-brand-50 hover:text-brand-700 dark:text-brand-400 dark:hover:bg-brand-500/10 dark:hover:text-brand-300"
        >
          <Plus className="h-4 w-4" />
          Adicionar pessoa
        </button>
      </div>

      <div className="mb-5">
        <p className="mb-2 text-xs font-semibold tracking-wide text-gray-400 uppercase dark:text-stone-500">
          De quem é cada item?
        </p>
        <ul className="space-y-2">
          {billableItems.map((item) => {
            const isUnassigned = (assignments[item.id]?.length ?? 0) === 0
            return (
              <li
                key={item.id}
                className={`rounded-xl border border-gray-200 p-3 dark:border-white/10 ${
                  isUnassigned ? 'border-l-[3px] border-l-gold-400 dark:border-l-gold-500' : ''
                }`}
              >
                <div className="mb-2.5 flex items-start justify-between gap-2 text-sm">
                  <span className="text-gray-800 dark:text-white">
                    {item.quantity}x {item.productName}
                  </span>
                  <span className="shrink-0 text-gray-600 dark:text-stone-300">{currencyFormatter.format(item.netSubtotal)}</span>
                </div>
                <div className="flex flex-wrap items-center gap-2">
                  {people.map((person, index) => {
                    const selected = (assignments[item.id] ?? []).includes(person.id)
                    const color = personColor(index)
                    return (
                      <button
                        key={person.id}
                        type="button"
                        onClick={() => handleChipClick(item.id, person.id)}
                        className={`flex min-h-10 items-center gap-1.5 rounded-full px-3.5 py-2 text-sm font-medium transition active:scale-95 ${
                          selected ? `${color.solid} text-white` : `${color.tint} hover:opacity-80`
                        }`}
                      >
                        {selected && <Check className="h-3.5 w-3.5" />}
                        {personLabel(person, index)}
                      </button>
                    )
                  })}
                </div>
                {people.length > 1 && (assignments[item.id]?.length ?? 0) < people.length && (
                  <button
                    type="button"
                    onClick={() => shareWithEveryone(item.id)}
                    className="mt-1.5 flex min-h-10 items-center gap-1.5 rounded-lg px-2 py-2 text-sm font-medium text-gray-500 transition hover:bg-gray-50 hover:text-brand-600 dark:text-stone-400 dark:hover:bg-white/5 dark:hover:text-brand-400"
                  >
                    <Users className="h-3.5 w-3.5" />
                    Dividir entre todos
                  </button>
                )}
              </li>
            )
          })}
        </ul>
        {unassignedCount > 0 && (
          <p className="mt-2 text-xs font-medium text-gold-700 dark:text-gold-400">
            Faltam atribuir {unassignedCount} {unassignedCount === 1 ? 'item' : 'itens'}.
          </p>
        )}
      </div>

      {canApply && (
        <div className="mb-5 divide-y divide-gray-100 rounded-xl border border-gray-200 text-sm dark:divide-white/10 dark:border-white/10">
          {people.map((person, index) => {
            const color = personColor(index)
            return (
              <div key={person.id} className="flex items-center justify-between gap-2 px-3.5 py-2.5">
                <span className="flex items-center gap-2 text-gray-600 dark:text-stone-400">
                  <span className={`h-2 w-2 shrink-0 rounded-full ${color.solid}`} />
                  {personLabel(person, index)}
                </span>
                <span className="font-semibold text-gray-800 dark:text-white">{currencyFormatter.format(amounts[index])}</span>
              </div>
            )
          })}
        </div>
      )}

      <div className="flex gap-2 border-t border-gray-100 pt-4 dark:border-white/10">
        <Button type="button" variant="secondary" onClick={onCancel} className="flex-1">
          Cancelar
        </Button>
        <Button
          type="button"
          onClick={() =>
            onApply(
              people
                .map((person, index) => ({ name: personLabel(person, index), amount: amounts[index] }))
                .filter((entry) => entry.amount > 0),
            )
          }
          disabled={!canApply}
          className="flex-1"
        >
          Aplicar
        </Button>
      </div>
    </div>
  )
}
