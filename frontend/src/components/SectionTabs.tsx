import type { LucideIcon } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { NavLink, useLocation } from 'react-router-dom'

interface SectionTab {
  to: string
  label: string
  icon?: LucideIcon
}

/**
 * Folder-style tabs: the active tab's card matches the background/border of the panel
 * rendered right below it (see each page's header box, which is `rounded-b-*` instead of
 * fully rounded) so the two visually fuse into one surface, like a browser tab.
 *
 * On mobile the row can overflow (long labels, small screens), so it scrolls horizontally
 * with the native scrollbar hidden — edge fades hint there's more to scroll, and whichever
 * tab is active is scrolled into view on load so it's never hidden off-screen by default.
 */
export function SectionTabs({ tabs }: { tabs: SectionTab[] }) {
  const { pathname } = useLocation()
  const scrollerRef = useRef<HTMLDivElement>(null)
  const activeRef = useRef<HTMLAnchorElement>(null)
  const [canScrollLeft, setCanScrollLeft] = useState(false)
  const [canScrollRight, setCanScrollRight] = useState(false)

  useEffect(() => {
    // Runs after the browser has committed and laid out the (just-mounted) tab row, so
    // offsetLeft/scrollWidth are already accurate — no need to defer to a rAF/timeout.
    // Computes the offset manually and uses scrollTo rather than
    // scrollIntoView({ inline: 'center' }), which proved unreliable at centering an element
    // inside a horizontally-scrolling flex row in testing.
    const container = scrollerRef.current
    const target = activeRef.current
    if (!container || !target) return
    const targetCenter = target.offsetLeft + target.offsetWidth / 2
    const desired = targetCenter - container.clientWidth / 2
    const max = container.scrollWidth - container.clientWidth
    const left = Math.max(0, Math.min(desired, max))
    container.scrollTo({ left, behavior: 'instant' })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pathname])

  useEffect(() => {
    const el = scrollerRef.current
    if (!el) return
    function updateFades() {
      if (!el) return
      setCanScrollLeft(el.scrollLeft > 4)
      setCanScrollRight(el.scrollLeft + el.clientWidth < el.scrollWidth - 4)
    }
    updateFades()
    el.addEventListener('scroll', updateFades, { passive: true })
    window.addEventListener('resize', updateFades)
    const raf = requestAnimationFrame(updateFades)
    return () => {
      el.removeEventListener('scroll', updateFades)
      window.removeEventListener('resize', updateFades)
      cancelAnimationFrame(raf)
    }
  }, [tabs, pathname])

  return (
    <div className="relative">
      <div ref={scrollerRef} className="no-scrollbar flex gap-1 overflow-x-auto scroll-smooth sm:justify-center">
        {tabs.map((tab) => {
          const Icon = tab.icon
          return (
            <NavLink
              key={tab.to}
              to={tab.to}
              end
              ref={(el) => {
                if (pathname === tab.to) activeRef.current = el
              }}
              className="relative min-w-0 flex-1 outline-none sm:flex-none sm:shrink-0"
            >
              {({ isActive }) => (
                <span
                  className={`relative -mb-px flex w-full items-center justify-center gap-1.5 rounded-t-xl border border-b-0 px-2 py-2 text-[13px] font-medium transition-colors sm:w-auto sm:px-4 sm:py-2.5 sm:text-sm ${
                    isActive
                      ? 'border-gray-200 bg-white text-brand-700 shadow-[0_-2px_6px_-2px_rgba(0,0,0,0.08)] dark:border-white/10 dark:bg-stone-900 dark:text-brand-400'
                      : 'border-transparent text-gray-500 hover:bg-gray-100/70 hover:text-gray-700 dark:text-stone-400 dark:hover:bg-white/5 dark:hover:text-stone-200'
                  }`}
                >
                  {Icon && (
                    <Icon
                      className={`h-3.5 w-3.5 shrink-0 sm:h-4 sm:w-4 ${
                        isActive ? 'text-brand-600 dark:text-brand-400' : 'text-gray-400 dark:text-stone-500'
                      }`}
                    />
                  )}
                  <span className="min-w-0 truncate">{tab.label}</span>
                </span>
              )}
            </NavLink>
          )
        })}
      </div>

      {canScrollLeft && (
        <div className="pointer-events-none absolute inset-y-0 left-0 w-8 bg-gradient-to-r from-gray-50 to-transparent sm:hidden dark:from-stone-950" />
      )}
      {canScrollRight && (
        <div className="pointer-events-none absolute inset-y-0 right-0 w-8 bg-gradient-to-l from-gray-50 to-transparent sm:hidden dark:from-stone-950" />
      )}
    </div>
  )
}
