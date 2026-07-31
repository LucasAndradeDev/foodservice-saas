import moraLogoDark from '../assets/mora-logo-dark.svg'
import moraLogoLight from '../assets/mora-logo.svg'
import { useTheme } from './ThemeContext'

interface LogoProps {
  className?: string
}

export function Logo({ className }: LogoProps) {
  const { theme } = useTheme()
  return <img src={theme === 'dark' ? moraLogoDark : moraLogoLight} alt="Morá" className={className} />
}
