import { AnimatePresence, motion } from 'framer-motion'
import { Star } from 'lucide-react'
import { useState } from 'react'

const MOOD_LABELS: Record<number, string> = {
  1: '😞 Não gostei',
  2: '😕 Podia ser melhor',
  3: '🙂 Foi ok',
  4: '😀 Gostei bastante',
  5: '🤩 Adorei!',
}

interface StarRatingProps {
  value: number
  onChange?: (value: number) => void
  readOnly?: boolean
  size?: 'sm' | 'lg'
}

export function StarRating({ value, onChange, readOnly = false, size = 'sm' }: StarRatingProps) {
  const [hoverValue, setHoverValue] = useState(0)
  const starSize = size === 'lg' ? 'h-9 w-9' : 'h-4 w-4'
  const displayValue = hoverValue || value

  return (
    <div className="flex flex-col items-center gap-2">
      <div
        className="flex items-center gap-1"
        role={readOnly ? undefined : 'radiogroup'}
        aria-label="Nota"
        onMouseLeave={() => setHoverValue(0)}
      >
        {[1, 2, 3, 4, 5].map((star) => {
          const filled = star <= Math.round(readOnly ? value : displayValue)
          if (readOnly) {
            return (
              <Star
                key={star}
                className={`${starSize} ${filled ? 'fill-amber-400 text-amber-400' : 'fill-gray-200 text-gray-200'}`}
              />
            )
          }
          return (
            <motion.button
              key={star}
              type="button"
              role="radio"
              aria-checked={star === value}
              aria-label={`${star} estrela${star > 1 ? 's' : ''}`}
              onClick={() => onChange?.(star)}
              onMouseEnter={() => setHoverValue(star)}
              onFocus={() => setHoverValue(star)}
              whileHover={{ scale: 1.25 }}
              whileTap={{ scale: 0.8, rotate: -10 }}
              transition={{ type: 'spring', stiffness: 500, damping: 15 }}
              className="rounded p-0.5"
            >
              <Star
                className={`${starSize} transition-colors duration-150 ${
                  filled
                    ? 'fill-amber-400 text-amber-400 drop-shadow-[0_0_6px_rgba(251,191,36,0.55)]'
                    : 'fill-gray-200 text-gray-300'
                }`}
              />
            </motion.button>
          )
        })}
      </div>

      {!readOnly && (
        <div className="h-5">
          <AnimatePresence mode="wait">
            {displayValue > 0 && (
              <motion.p
                key={displayValue}
                initial={{ opacity: 0, y: 6 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -6 }}
                transition={{ duration: 0.15 }}
                className="text-sm font-medium text-gray-600 dark:text-stone-300"
              >
                {MOOD_LABELS[displayValue]}
              </motion.p>
            )}
          </AnimatePresence>
        </div>
      )}
    </div>
  )
}
