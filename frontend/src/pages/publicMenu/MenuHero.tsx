import { AnimatePresence, motion } from 'framer-motion'
import { Moon, Sun } from 'lucide-react'
import type { CSSProperties } from 'react'

interface MenuHeroProps {
  restaurantName: string
  logo: string | null
  tableNumber?: number
  accentColor?: string
  theme: 'light' | 'dark'
  onToggleTheme: () => void
}

export function MenuHero({ restaurantName, logo, tableNumber, accentColor, theme, onToggleTheme }: MenuHeroProps) {
  const accent = accentColor ?? '#b3421f'
  const heroStyle = { '--menu-accent': accent } as CSSProperties

  return (
    <header
      style={heroStyle}
      className="relative overflow-hidden px-4 py-10 text-center sm:py-14 bg-[radial-gradient(120%_100%_at_50%_0%,color-mix(in_srgb,var(--menu-accent)_12%,white)_0%,white_65%)] dark:bg-[radial-gradient(120%_100%_at_50%_0%,color-mix(in_srgb,var(--menu-accent)_35%,black)_0%,#0b0b0c_65%)]"
    >
      <button
        type="button"
        onClick={onToggleTheme}
        aria-label="Alternar tema"
        className="absolute right-4 top-4 flex h-9 w-9 items-center justify-center rounded-full bg-black/5 text-gray-700 backdrop-blur-md transition-colors hover:bg-black/10 dark:bg-white/10 dark:text-white dark:hover:bg-white/20"
      >
        <AnimatePresence mode="wait" initial={false}>
          <motion.span
            key={theme}
            initial={{ opacity: 0, rotate: -90 }}
            animate={{ opacity: 1, rotate: 0 }}
            exit={{ opacity: 0, rotate: 90 }}
            transition={{ duration: 0.2 }}
            className="flex items-center justify-center"
          >
            {theme === 'dark' ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
          </motion.span>
        </AnimatePresence>
      </button>

      {logo ? (
        <img
          src={logo}
          alt={restaurantName}
          style={{ '--tw-ring-color': accent } as CSSProperties}
          className="mx-auto mb-3 h-16 w-16 rounded-full object-cover ring-2"
        />
      ) : (
        <div
          style={{ backgroundColor: accent }}
          className="mx-auto mb-3 flex h-16 w-16 items-center justify-center rounded-full text-xl font-semibold text-white"
        >
          {restaurantName.charAt(0).toUpperCase()}
        </div>
      )}

      <h1 className="text-xl font-semibold tracking-tight text-gray-800 dark:text-white sm:text-2xl">
        {restaurantName}
      </h1>

      {tableNumber !== undefined && (
        <p className="mt-2 inline-block rounded-full bg-black/5 px-3 py-1 text-xs font-medium text-gray-600 backdrop-blur-md dark:bg-white/10 dark:text-white/80">
          Mesa {tableNumber}
        </p>
      )}
    </header>
  )
}
