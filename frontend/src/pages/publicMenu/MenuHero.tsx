import { AnimatePresence, motion } from 'framer-motion'
import { Moon, Sun } from 'lucide-react'
import type { CSSProperties } from 'react'

const MENU_ACCENT_COLOR = '#b3421f'

interface MenuHeroProps {
  restaurantName: string
  logo: string | null
  tableNumber?: number
  theme: 'light' | 'dark'
  onToggleTheme: () => void
}

export function MenuHero({ restaurantName, logo, tableNumber, theme, onToggleTheme }: MenuHeroProps) {
  const heroStyle = { '--menu-accent': MENU_ACCENT_COLOR } as CSSProperties

  return (
    <header
      style={heroStyle}
      className="sticky top-0 z-20 border-b border-black/5 bg-gradient-to-b from-brand-50 to-white px-4 py-2.5 backdrop-blur-xl dark:border-white/10 dark:from-brand-900/60 dark:to-stone-950/98"
    >
      <div className="mx-auto flex max-w-2xl items-center gap-3">
        {logo ? (
          <img
            src={logo}
            alt={restaurantName}
            className="h-10 w-10 shrink-0 rounded-2xl object-cover shadow-sm ring-1 ring-black/5 dark:ring-white/10"
          />
        ) : (
          <div
            style={{ backgroundImage: `linear-gradient(135deg, ${MENU_ACCENT_COLOR}, color-mix(in srgb, ${MENU_ACCENT_COLOR} 60%, black))` }}
            className="flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl text-base font-semibold text-white shadow-sm"
          >
            {restaurantName.charAt(0).toUpperCase()}
          </div>
        )}

        <div className="min-w-0 flex-1">
          <h1 className="font-display truncate text-base font-bold leading-tight tracking-tight text-gray-900 dark:text-white">
            {restaurantName}
          </h1>
          {tableNumber !== undefined && (
            <p className="truncate text-xs leading-tight text-gray-500 dark:text-stone-400">Mesa {tableNumber}</p>
          )}
        </div>

        <button
          type="button"
          onClick={onToggleTheme}
          aria-label="Alternar tema"
          className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-gray-500 transition-colors hover:bg-black/5 hover:text-gray-800 dark:text-stone-400 dark:hover:bg-white/10 dark:hover:text-white"
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
      </div>
    </header>
  )
}
