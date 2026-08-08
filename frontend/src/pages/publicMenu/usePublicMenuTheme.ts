import { useEffect } from 'react'
import { useThemeState } from '../../theme/useThemeState'

const STORAGE_KEY = 'restaurant_saas_public_menu_theme'

export function usePublicMenuTheme() {
  const state = useThemeState(STORAGE_KEY)

  // The menu no longer forces a full-viewport-height wrapper (that produced a large empty gap
  // below short/filtered menus), so `<body>` needs to carry this theme's dark class itself —
  // otherwise the page background outside the (now content-sized) wrapper falls back to white.
  useEffect(() => {
    document.body.classList.toggle('dark', state.theme === 'dark')
    return () => {
      document.body.classList.remove('dark')
    }
  }, [state.theme])

  return state
}
