import { useThemeState } from '../../theme/useThemeState'

const STORAGE_KEY = 'restaurant_saas_public_menu_theme'

export function usePublicMenuTheme() {
  return useThemeState(STORAGE_KEY)
}
