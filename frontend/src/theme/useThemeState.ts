import { useEffect, useState } from 'react'

export type Theme = 'light' | 'dark'

function readStoredTheme(storageKey: string): Theme | null {
  try {
    const raw = localStorage.getItem(storageKey)
    return raw === 'light' || raw === 'dark' ? raw : null
  } catch {
    return null
  }
}

function writeStoredTheme(storageKey: string, theme: Theme) {
  try {
    localStorage.setItem(storageKey, theme)
  } catch {
    // Storage may be unavailable in restricted webviews; theme just won't persist.
  }
}

function prefersDark() {
  return window.matchMedia('(prefers-color-scheme: dark)').matches
}

export function useThemeState(storageKey: string) {
  const [theme, setTheme] = useState<Theme>(() => readStoredTheme(storageKey) ?? (prefersDark() ? 'dark' : 'light'))

  useEffect(() => {
    if (readStoredTheme(storageKey)) return

    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
    const handleChange = (e: MediaQueryListEvent) => setTheme(e.matches ? 'dark' : 'light')
    mediaQuery.addEventListener('change', handleChange)
    return () => mediaQuery.removeEventListener('change', handleChange)
  }, [storageKey])

  function toggleTheme() {
    setTheme((prev) => {
      const next = prev === 'dark' ? 'light' : 'dark'
      writeStoredTheme(storageKey, next)
      return next
    })
  }

  return { theme, toggleTheme }
}
