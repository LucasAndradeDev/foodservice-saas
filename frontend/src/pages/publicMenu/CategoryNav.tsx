import { motion } from 'framer-motion'
import { Search } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import type { PublicMenuCategory } from '../../api/publicMenu'
import { getCategoryIcon } from './categoryIcons'
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
    <div className="sticky top-[61px] z-10 bg-white/90 px-4 py-3 backdrop-blur-md dark:bg-stone-950/90">
      <div className="relative mb-3">
        <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400 dark:text-stone-500" />
        <input
          type="text"
          placeholder="Buscar no cardápio..."
          value={search}
          onChange={(e) => onSearchChange(e.target.value)}
          className="w-full rounded-full border border-gray-200 bg-gray-50 py-2 pl-9 pr-3 text-sm text-gray-800 focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-white/5 dark:text-white dark:placeholder:text-stone-500"
        />
      </div>
      {categories.length > 1 && (
        <div className="relative -mx-4">
          <div className="no-scrollbar flex snap-x snap-proximity gap-2 overflow-x-auto overscroll-x-contain scroll-smooth px-4 pb-1 [-webkit-overflow-scrolling:touch]">
            {categories.map((category) => {
              const isActive = !isSearching && category.id === activeCategoryId
              const Icon = getCategoryIcon(category.name)
              return (
                <button
                  key={category.id}
                  ref={(el) => {
                    pillRefs.current[category.id] = el
                  }}
                  type="button"
                  onClick={() => handlePillClick(category.id)}
                  className={`relative flex shrink-0 snap-center items-center gap-1.5 rounded-full border px-4 py-1.5 text-sm font-medium active:scale-95 ${
                    isActive
                      ? 'border-transparent text-white'
                      : 'border-brand-600 text-brand-600 transition-colors dark:border-brand-400 dark:text-brand-400'
                  }`}
                >
                  {isActive && (
                    <motion.span
                      layoutId="category-pill-active-bg"
                      transition={{ type: 'spring', stiffness: 500, damping: 40 }}
                      className="absolute inset-0 rounded-full bg-brand-600 shadow-md dark:bg-brand-400"
                    />
                  )}
                  <Icon className="relative z-10 h-3.5 w-3.5" />
                  <span className="relative z-10">{category.name}</span>
                </button>
              )
            })}
          </div>
          <div className="pointer-events-none absolute inset-y-0 left-0 w-6 bg-gradient-to-r from-white/90 to-transparent dark:from-stone-950/90" />
          <div className="pointer-events-none absolute inset-y-0 right-0 w-6 bg-gradient-to-l from-white/90 to-transparent dark:from-stone-950/90" />
        </div>
      )}
    </div>
  )
}
