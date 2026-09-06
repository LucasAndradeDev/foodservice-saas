import type { ChangeEvent } from 'react'

const formatter = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

interface CurrencyInputProps {
  id?: string
  value: number | null
  onChange: (value: number | null) => void
  placeholder?: string
  className?: string
  disabled?: boolean
}

// Digits-as-cents input (type "500" -> "R$ 5,00", same idiom as most BR banking apps) - the
// displayed string is always fully derived from `value`, never held as separate local state, so
// there's nothing to get out of sync.
export function CurrencyInput({ id, value, onChange, placeholder = 'R$ 0,00', className = '', disabled }: CurrencyInputProps) {
  const display = value != null ? formatter.format(value) : ''

  function handleChange(event: ChangeEvent<HTMLInputElement>) {
    const digitsOnly = event.target.value.replace(/\D/g, '')
    onChange(digitsOnly === '' ? null : Number(digitsOnly) / 100)
  }

  return (
    <input
      id={id}
      type="text"
      inputMode="decimal"
      value={display}
      onChange={handleChange}
      placeholder={placeholder}
      disabled={disabled}
      className={className}
    />
  )
}
