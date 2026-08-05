import {
  Banknote,
  BarChart3,
  CalendarClock,
  ChefHat,
  LayoutDashboard,
  LogOut,
  MessageCircle,
  MoreHorizontal,
  Package,
  Settings as SettingsIcon,
  Table2,
  Wallet,
  type LucideIcon,
} from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { UserRole } from '../auth/types'
import { useAuth } from '../auth/AuthContext'
import { EmailVerificationBanner } from '../components/EmailVerificationBanner'
import { Modal } from '../components/Modal'
import { NotificationToastStack, type ToastItem } from '../components/NotificationToastStack'
import { SupportModal } from '../components/SupportModal'
import { getNavNotificationStatus, markNavSectionSeen, type NavNotificationStatus, type NavSection } from '../api/navNotifications'
import { playAlertTone } from '../utils/alertSound'
import { Logo } from '../theme/Logo'
import { ThemeToggleButton } from '../theme/ThemeToggleButton'

const NAV_STATUS_POLL_MS = 4000
const TOAST_DURATION_MS = 6000
const EMAIL_VERIFICATION_POLL_MS = 10000

const SECTION_TO_STATUS_KEY = {
  KITCHEN: 'kitchen',
  TABLES: 'tables',
  CHECKOUT: 'checkout',
} as const

const SECTION_ALERT_INFO: Record<NavSection, { message: string; to: string }> = {
  KITCHEN: { message: 'Novo pedido na cozinha', to: '/kitchen' },
  TABLES: { message: 'Mesa chamando ou pedindo a conta', to: '/tables' },
  CHECKOUT: { message: 'Comanda pronta pra fechar', to: '/checkout' },
}

const ROLE_LABELS: Record<string, string> = {
  OWNER: 'Proprietário',
  MANAGER: 'Gerente',
  WAITER: 'Garçom',
  KITCHEN: 'Cozinha',
  CASHIER: 'Caixa',
}

interface NavItem {
  to: string
  label: string
  icon: LucideIcon
  end?: boolean
  roles?: UserRole[]
  section?: NavSection
  /** Extra path prefixes that should also highlight this item as active (e.g. pages reachable only via internal tabs). */
  matchPrefixes?: string[]
  /** Shorter label for the mobile bottom nav, where two-word labels wrap onto two lines. Defaults to `label`. */
  mobileLabel?: string
}

const PRIMARY_NAV_ITEMS: NavItem[] = [
  { to: '/', label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: '/tables', label: 'Mesas', icon: Table2, section: 'TABLES' },
  { to: '/kitchen', label: 'Cozinha', icon: ChefHat, section: 'KITCHEN' },
  { to: '/checkout', label: 'Fechar Conta', mobileLabel: 'Conta', icon: Wallet, section: 'CHECKOUT' },
  { to: '/cash-register', label: 'Caixa', icon: Banknote, roles: ['OWNER', 'MANAGER', 'CASHIER'] },
  {
    to: '/reservations',
    label: 'Reservas',
    icon: CalendarClock,
    roles: ['OWNER', 'MANAGER', 'WAITER', 'CASHIER'],
  },
]

const MENU_NAV_ITEMS: NavItem[] = [
  { to: '/products', label: 'Produtos', icon: Package, end: true, matchPrefixes: ['/categories', '/combos'] },
]

const MANAGEMENT_NAV_ITEMS: NavItem[] = [
  { to: '/reports', label: 'Relatórios', icon: BarChart3, roles: ['OWNER', 'MANAGER'] },
  {
    to: '/settings',
    label: 'Configurações',
    icon: SettingsIcon,
    roles: ['OWNER', 'MANAGER'],
    matchPrefixes: ['/coupons', '/happy-hour', '/staff'],
  },
]

function sidebarLinkClass({ isActive }: { isActive: boolean }) {
  return `flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition ${
    isActive
      ? 'bg-brand-600 text-white'
      : 'text-gray-600 hover:bg-gray-100 dark:text-stone-400 dark:hover:bg-white/5'
  }`
}

function isNavItemActive(item: NavItem, pathname: string) {
  const matchesOwnPath = item.end ? pathname === item.to : pathname.startsWith(item.to)
  const matchesExtraPath = item.matchPrefixes?.some((prefix) => pathname.startsWith(prefix)) ?? false
  return matchesOwnPath || matchesExtraPath
}

export function AppLayout() {
  const { user, restaurant, logout, refreshUser } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const queryClient = useQueryClient()
  const [isMoreOpen, setIsMoreOpen] = useState(false)
  const [isSupportOpen, setIsSupportOpen] = useState(false)

  const visiblePrimaryItems = PRIMARY_NAV_ITEMS.filter((item) => !item.roles || (user && item.roles.includes(user.role)))
  const visibleMenuItems = MENU_NAV_ITEMS.filter((item) => !item.roles || (user && item.roles.includes(user.role)))
  const visibleManagementItems = MANAGEMENT_NAV_ITEMS.filter((item) => !item.roles || (user && item.roles.includes(user.role)))
  const visibleMoreItems = [...visibleMenuItems, ...visibleManagementItems]

  // Polls /auth/me while the email isn't verified yet, so confirming it in another
  // tab (e.g. clicking the link from a webmail tab) clears the banner here without
  // the user having to manually reload this tab. Stops itself once verified, since
  // `enabled` turns false as soon as refreshUser() flips user.emailVerified to true.
  useQuery({
    queryKey: ['emailVerificationStatus'],
    queryFn: () => refreshUser(),
    enabled: user?.emailVerified === false,
    refetchInterval: EMAIL_VERIFICATION_POLL_MS,
    refetchIntervalInBackground: true,
  })

  const { data: notificationStatus } = useQuery({
    queryKey: ['navNotifications'],
    queryFn: getNavNotificationStatus,
    refetchInterval: NAV_STATUS_POLL_MS,
    refetchIntervalInBackground: true,
  })

  const markSeenMutation = useMutation({
    mutationFn: markNavSectionSeen,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['navNotifications'] }),
  })

  const [toasts, setToasts] = useState<ToastItem[]>([])
  const previousStatusRef = useRef<NavNotificationStatus | null>(null)

  useEffect(() => {
    if (!notificationStatus) return
    const previousStatus = previousStatusRef.current
    previousStatusRef.current = notificationStatus

    // Skip the very first load — otherwise pre-existing pending items would trigger a beep
    // just from opening the app, instead of only on genuinely new activity.
    if (!previousStatus) return

    ;(Object.keys(SECTION_TO_STATUS_KEY) as NavSection[]).forEach((section) => {
      const statusKey = SECTION_TO_STATUS_KEY[section]
      const justBecamePending = !previousStatus[statusKey] && notificationStatus[statusKey]
      if (!justBecamePending) return

      playAlertTone(section)
      const { message, to } = SECTION_ALERT_INFO[section]
      const id = `${section}-${Date.now()}`
      setToasts((prev) => [...prev, { id, message, to }])
      window.setTimeout(() => {
        setToasts((prev) => prev.filter((toast) => toast.id !== id))
      }, TOAST_DURATION_MS)
    })
  }, [notificationStatus])

  function dismissToast(id: string) {
    setToasts((prev) => prev.filter((toast) => toast.id !== id))
  }

  useEffect(() => {
    const activeItem = PRIMARY_NAV_ITEMS.find((item) =>
      item.end ? location.pathname === item.to : location.pathname.startsWith(item.to),
    )
    if (activeItem?.section) {
      markSeenMutation.mutate(activeItem.section)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [location.pathname])

  function hasNotification(item: NavItem) {
    if (!item.section || !notificationStatus) return false
    return notificationStatus[SECTION_TO_STATUS_KEY[item.section]]
  }

  function handleLogout() {
    logout()
    navigate('/login')
  }

  function handleMoreNavigate(to: string) {
    setIsMoreOpen(false)
    navigate(to)
  }

  function handleMoreSupport() {
    setIsMoreOpen(false)
    setIsSupportOpen(true)
  }

  return (
    <div className="min-h-screen bg-gray-50 sm:flex sm:h-screen sm:overflow-hidden dark:bg-stone-950">
      <NotificationToastStack toasts={toasts} onDismiss={dismissToast} onNavigate={navigate} />

      {/* Desktop sidebar */}
      <aside className="hidden w-64 shrink-0 flex-col border-r border-gray-200 bg-white sm:flex dark:border-white/10 dark:bg-stone-900">
        <div className="flex items-center justify-center border-b border-gray-200 p-4 dark:border-white/10">
          <Logo className="h-9 w-auto" />
        </div>

        <div className="flex-1 overflow-y-auto p-3">
          <p className="mb-2 px-3 text-xs font-semibold tracking-wide text-gray-400 uppercase dark:text-stone-500">
            Operação
          </p>
          <nav className="mb-6 flex flex-col gap-1">
            {visiblePrimaryItems.map((item) => (
              <NavLink key={item.to} to={item.to} end={item.end} className={sidebarLinkClass}>
                <item.icon className="h-5 w-5" />
                {item.label}
                {hasNotification(item) && <span className="ml-auto h-2 w-2 rounded-full bg-brand-600" />}
              </NavLink>
            ))}
          </nav>

          {visibleMenuItems.length > 0 && (
            <>
              <p className="mb-2 px-3 text-xs font-semibold tracking-wide text-gray-400 uppercase dark:text-stone-500">
                Cardápio
              </p>
              <nav className="mb-6 flex flex-col gap-1">
                {visibleMenuItems.map((item) => (
                  <NavLink
                    key={item.to}
                    to={item.to}
                    end={item.end}
                    className={() => sidebarLinkClass({ isActive: isNavItemActive(item, location.pathname) })}
                  >
                    <item.icon className="h-5 w-5" />
                    {item.label}
                  </NavLink>
                ))}
              </nav>
            </>
          )}

          {visibleManagementItems.length > 0 && (
            <>
              <p className="mb-2 px-3 text-xs font-semibold tracking-wide text-gray-400 uppercase dark:text-stone-500">
                Gestão
              </p>
              <nav className="flex flex-col gap-1">
                {visibleManagementItems.map((item) => (
                  <NavLink
                    key={item.to}
                    to={item.to}
                    end={item.end}
                    className={() => sidebarLinkClass({ isActive: isNavItemActive(item, location.pathname) })}
                  >
                    <item.icon className="h-5 w-5" />
                    {item.label}
                  </NavLink>
                ))}
              </nav>
            </>
          )}
        </div>

        <div className="border-t border-gray-200 p-4 dark:border-white/10">
          <div className="mb-3 flex items-center justify-between gap-2">
            <div className="min-w-0">
              <p className="truncate text-sm font-medium text-gray-800 dark:text-white">
                {restaurant?.tradeName ?? restaurant?.name}
              </p>
              <p className="truncate text-xs text-gray-500 dark:text-stone-400">
                {user?.name} · {user ? ROLE_LABELS[user.role] : ''}
              </p>
            </div>
            <ThemeToggleButton />
          </div>
          <button
            type="button"
            onClick={() => setIsSupportOpen(true)}
            className="mb-2 flex w-full items-center justify-center gap-2 rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-100 dark:border-white/10 dark:text-stone-300 dark:hover:bg-white/5"
          >
            <MessageCircle className="h-4 w-4" />
            Fale conosco
          </button>
          <button
            type="button"
            onClick={handleLogout}
            className="flex w-full items-center justify-center gap-2 rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-100 dark:border-white/10 dark:text-stone-300 dark:hover:bg-white/5"
          >
            <LogOut className="h-4 w-4" />
            Sair
          </button>
        </div>
      </aside>

      <div className="flex-1 sm:h-screen sm:overflow-y-auto">
        <EmailVerificationBanner />

        {/* Mobile top bar */}
        <header className="flex items-center justify-between border-b border-gray-200 bg-white px-4 py-3 sm:hidden dark:border-white/10 dark:bg-stone-900">
          <span className="font-semibold text-gray-800 dark:text-white">
            {restaurant?.tradeName ?? restaurant?.name}
          </span>
          <div className="flex items-center gap-2">
            <ThemeToggleButton />
            <button
              type="button"
              onClick={handleLogout}
              aria-label="Sair"
              className="rounded-md border border-gray-300 p-2 text-gray-700 hover:bg-gray-100 dark:border-white/10 dark:text-stone-300 dark:hover:bg-white/5"
            >
              <LogOut className="h-4 w-4" />
            </button>
          </div>
        </header>

        <main className="p-4 pb-20 sm:p-6 sm:pb-6">
          <Outlet />
        </main>
      </div>

      {/* Mobile bottom nav */}
      <nav className="fixed inset-x-0 bottom-0 z-10 flex border-t border-gray-200 bg-white sm:hidden dark:border-white/10 dark:bg-stone-900">
        {visiblePrimaryItems.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.end}
            className={({ isActive }) =>
              `flex flex-1 flex-col items-center gap-0.5 py-2 text-xs ${
                isActive ? 'text-brand-700 dark:text-brand-400' : 'text-gray-500 dark:text-stone-500'
              }`
            }
          >
            <span className="relative">
              <item.icon className="h-5 w-5" />
              {hasNotification(item) && (
                <span className="absolute -top-0.5 -right-0.5 h-2 w-2 rounded-full bg-brand-600" />
              )}
            </span>
            <span className="whitespace-nowrap">{item.mobileLabel ?? item.label}</span>
          </NavLink>
        ))}
        {visibleMoreItems.length > 0 && (
          <button
            type="button"
            onClick={() => setIsMoreOpen(true)}
            className="flex flex-1 flex-col items-center gap-0.5 py-2 text-xs text-gray-500 dark:text-stone-500"
          >
            <MoreHorizontal className="h-5 w-5" />
            Mais
          </button>
        )}
      </nav>

      {isMoreOpen && (
        <Modal title="Mais" onClose={() => setIsMoreOpen(false)}>
          <div className="flex flex-col divide-y divide-gray-100 dark:divide-white/10">
            {visibleMoreItems.map((item) => (
              <button
                key={item.to}
                type="button"
                onClick={() => handleMoreNavigate(item.to)}
                className="flex items-center gap-3 py-3 text-left text-sm text-gray-700 dark:text-stone-300"
              >
                <item.icon className="h-5 w-5 text-gray-500 dark:text-stone-400" />
                {item.label}
              </button>
            ))}
            <button
              type="button"
              onClick={handleMoreSupport}
              className="flex items-center gap-3 py-3 text-left text-sm text-gray-700 dark:text-stone-300"
            >
              <MessageCircle className="h-5 w-5 text-gray-500 dark:text-stone-400" />
              Fale conosco
            </button>
          </div>
        </Modal>
      )}

      {isSupportOpen && <SupportModal onClose={() => setIsSupportOpen(false)} />}
    </div>
  )
}
