import { useEffect, useState } from 'react'

const STORAGE_KEY = 'restaurant_saas_public_menu_theme'

type Theme = 'light' | 'dark'

function readStoredTheme(): Theme | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw === 'light' || raw === 'dark' ? raw : null
  } catch {
    return null
  }
}

function writeStoredTheme(theme: Theme) {
  try {
    localStorage.setItem(STORAGE_KEY, theme)
  } catch {
    // Storage may be unavailable in restricted webviews; theme just won't persist.
  }
}

function prefersDark() {
  return window.matchMedia('(prefers-color-scheme: dark)').matches
}

export function usePublicMenuTheme() {
  const [theme, setTheme] = useState<Theme>(() => readStoredTheme() ?? (prefersDark() ? 'dark' : 'light'))

  useEffect(() => {
    if (readStoredTheme()) return

    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
    const handleChange = (e: MediaQueryListEvent) => setTheme(e.matches ? 'dark' : 'light')
    mediaQuery.addEventListener('change', handleChange)
    return () => mediaQuery.removeEventListener('change', handleChange)
  }, [])

  function toggleTheme() {
    setTheme((prev) => {
      const next = prev === 'dark' ? 'light' : 'dark'
      writeStoredTheme(next)
      return next
    })
  }

  return { theme, toggleTheme }
}
