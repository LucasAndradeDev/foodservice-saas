import { AnimatePresence, motion } from 'framer-motion'
import { Search, X } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import type { PublicMenuCategory } from '../../api/publicMenu'
import { getCategoryEmoji } from './categoryIcons'
import { scrollToCategory } from './utils'

const SPY_RESUME_FALLBACK_MS = 1000

interface CategoryNavProps {
  categories: PublicMenuCategory[]
  search: string
  onSearchChange: (value: string) => void
}

export function CategoryNav({ categories, search, onSearchChange }: CategoryNavProps) {
  const [activeCategoryId, setActiveCategoryId] = useState<string | null>(null)
  const pillRefs = useRef<Record<string, HTMLButtonElement | null>>({})
  const suppressSpyRef = useRef(false)
  const resumeTimeoutRef = useRef<number | null>(null)
  const isSearching = search.trim().length > 0

  useEffect(() => {
    if (isSearching || categories.length === 0) return

    const sections = categories
      .map((category) => document.getElementById(`category-${category.id}`))
      .filter((el): el is HTMLElement => el !== null)

    if (sections.length === 0) return

    const observer = new IntersectionObserver(
      (entries) => {
        if (suppressSpyRef.current) return
        const visible = entries.filter((entry) => entry.isIntersecting)
        if (visible.length === 0) return
        const topMost = visible.reduce((a, b) => (a.intersectionRatio > b.intersectionRatio ? a : b))
        setActiveCategoryId(topMost.target.id.replace('category-', ''))
      },
      { rootMargin: '-45% 0px -50% 0px' },
    )

    sections.forEach((section) => observer.observe(section))
    return () => observer.disconnect()
  }, [categories, isSearching])

  useEffect(() => {
    if (!activeCategoryId) return
    pillRefs.current[activeCategoryId]?.scrollIntoView({ behavior: 'smooth', inline: 'center', block: 'nearest' })
  }, [activeCategoryId])

  useEffect(() => {
    function resumeSpy() {
      suppressSpyRef.current = false
    }
    window.addEventListener('scrollend', resumeSpy)
    return () => window.removeEventListener('scrollend', resumeSpy)
  }, [])

  function handlePillClick(categoryId: string) {
    suppressSpyRef.current = true
    if (resumeTimeoutRef.current) window.clearTimeout(resumeTimeoutRef.current)
    resumeTimeoutRef.current = window.setTimeout(() => {
      suppressSpyRef.current = false
    }, SPY_RESUME_FALLBACK_MS)

    setActiveCategoryId(categoryId)
    scrollToCategory(categoryId)
  }

  return (
    <div className="bg-white/95 py-3 backdrop-blur-md dark:bg-stone-950/95">
      <div className="mx-auto max-w-2xl px-4">
        <div className="group relative mb-3">
          <Search className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400 transition-colors group-focus-within:text-brand-500 dark:text-stone-500 dark:group-focus-within:text-brand-400" />
          <input
            type="text"
            placeholder="Buscar no cardápio..."
            value={search}
            onChange={(e) => onSearchChange(e.target.value)}
            className="w-full rounded-2xl border border-gray-200 bg-white py-2.5 pl-10 pr-9 text-sm text-gray-800 shadow-sm transition-all placeholder:text-gray-400 focus:border-brand-400 focus:shadow-md focus:outline-none focus:ring-4 focus:ring-brand-500/10 dark:border-white/10 dark:bg-stone-900 dark:text-white dark:placeholder:text-stone-500 dark:focus:ring-brand-400/10"
          />
          <AnimatePresence>
            {search.length > 0 && (
              <motion.button
                type="button"
                initial={{ opacity: 0, scale: 0.7 }}
                animate={{ opacity: 1, scale: 1 }}
                exit={{ opacity: 0, scale: 0.7 }}
                transition={{ duration: 0.15 }}
                onClick={() => onSearchChange('')}
                aria-label="Limpar busca"
                className="absolute right-3 top-1/2 flex h-5 w-5 -translate-y-1/2 items-center justify-center rounded-full bg-gray-200 text-gray-500 transition-colors hover:bg-gray-300 dark:bg-white/10 dark:text-stone-400 dark:hover:bg-white/20"
              >
                <X className="h-3 w-3" />
              </motion.button>
            )}
          </AnimatePresence>
        </div>
        {categories.length > 1 && (
          <div className="relative -mx-4">
            <div className="no-scrollbar flex snap-x snap-proximity gap-3 overflow-x-auto overscroll-x-contain scroll-smooth px-4 py-1.5 sm:justify-center [-webkit-overflow-scrolling:touch]">
              {categories.map((category) => {
                const isActive = !isSearching && category.id === activeCategoryId
                const emoji = getCategoryEmoji(category.name)
                return (
                  <button
                    key={category.id}
                    ref={(el) => {
                      pillRefs.current[category.id] = el
                    }}
                    type="button"
                    onClick={() => handlePillClick(category.id)}
                    className="flex w-16 shrink-0 snap-center flex-col items-center gap-1.5 active:scale-95"
                  >
                    <span className="relative flex h-14 w-14 items-center justify-center rounded-2xl">
                      {isActive && (
                        <motion.span
                          layoutId="category-tile-active-bg"
                          transition={{ type: 'spring', stiffness: 500, damping: 40 }}
                          className="absolute inset-0 rounded-2xl bg-brand-50 ring-2 ring-brand-500 dark:bg-brand-500/15 dark:ring-brand-400"
                        />
                      )}
                      {!isActive && <span className="absolute inset-0 rounded-2xl bg-gray-100 dark:bg-white/5" />}
                      <span className="relative z-10 text-2xl">{emoji}</span>
                    </span>
                    <span
                      className={`font-display line-clamp-2 text-center text-[11px] font-semibold leading-tight transition-colors ${
                        isActive ? 'text-brand-600 dark:text-brand-400' : 'text-gray-600 dark:text-stone-400'
                      }`}
                    >
                      {category.name}
                    </span>
                  </button>
                )
              })}
            </div>
            <div className="pointer-events-none absolute inset-y-0 left-0 w-6 bg-gradient-to-r from-white/90 to-transparent dark:from-stone-950/90" />
            <div className="pointer-events-none absolute inset-y-0 right-0 w-6 bg-gradient-to-l from-white/90 to-transparent dark:from-stone-950/90" />
          </div>
        )}
      </div>
    </div>
  )
}
