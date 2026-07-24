import {
  BarChart3,
  ChefHat,
  LayoutDashboard,
  LogOut,
  MoreHorizontal,
  Package,
  Settings as SettingsIcon,
  Table2,
  Tag,
  Users,
  Wallet,
  type LucideIcon,
} from 'lucide-react'
import { useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import moraLogo from '../assets/mora-logo.svg'
import type { UserRole } from '../auth/types'
import { useAuth } from '../auth/AuthContext'
import { Modal } from '../components/Modal'

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
}

const PRIMARY_NAV_ITEMS: NavItem[] = [
  { to: '/', label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: '/tables', label: 'Mesas', icon: Table2 },
  { to: '/kitchen', label: 'Cozinha', icon: ChefHat },
  { to: '/checkout', label: 'Caixa', icon: Wallet },
]

const MORE_NAV_ITEMS: NavItem[] = [
  { to: '/categories', label: 'Categorias', icon: Tag },
  { to: '/products', label: 'Produtos', icon: Package },
  { to: '/reports', label: 'Relatórios', icon: BarChart3, roles: ['OWNER', 'MANAGER'] },
  { to: '/settings', label: 'Configurações', icon: SettingsIcon, roles: ['OWNER', 'MANAGER'] },
  { to: '/staff', label: 'Funcionários', icon: Users, roles: ['OWNER', 'MANAGER'] },
]

function sidebarLinkClass({ isActive }: { isActive: boolean }) {
  return `flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition ${
    isActive ? 'bg-brand-600 text-white' : 'text-gray-600 hover:bg-gray-100'
  }`
}

export function AppLayout() {
  const { user, restaurant, logout } = useAuth()
  const navigate = useNavigate()
  const [isMoreOpen, setIsMoreOpen] = useState(false)

  const visibleMoreItems = MORE_NAV_ITEMS.filter((item) => !item.roles || (user && item.roles.includes(user.role)))

  function handleLogout() {
    logout()
    navigate('/login')
  }

  function handleMoreNavigate(to: string) {
    setIsMoreOpen(false)
    navigate(to)
  }

  return (
    <div className="min-h-screen bg-gray-50 sm:flex">
      {/* Desktop sidebar */}
      <aside className="hidden w-64 shrink-0 flex-col border-r border-gray-200 bg-white sm:flex">
        <div className="border-b border-gray-200 p-4">
          <img src={moraLogo} alt="Morá" className="h-8 w-auto" />
        </div>

        <div className="flex-1 overflow-y-auto p-3">
          <p className="mb-2 px-3 text-xs font-semibold tracking-wide text-gray-400 uppercase">Operação</p>
          <nav className="mb-6 flex flex-col gap-1">
            {PRIMARY_NAV_ITEMS.map((item) => (
              <NavLink key={item.to} to={item.to} end={item.end} className={sidebarLinkClass}>
                <item.icon className="h-5 w-5" />
                {item.label}
              </NavLink>
            ))}
          </nav>

          {visibleMoreItems.length > 0 && (
            <>
              <p className="mb-2 px-3 text-xs font-semibold tracking-wide text-gray-400 uppercase">Cadastros</p>
              <nav className="flex flex-col gap-1">
                {visibleMoreItems.map((item) => (
                  <NavLink key={item.to} to={item.to} end={item.end} className={sidebarLinkClass}>
                    <item.icon className="h-5 w-5" />
                    {item.label}
                  </NavLink>
                ))}
              </nav>
            </>
          )}
        </div>

        <div className="border-t border-gray-200 p-4">
          <p className="truncate text-sm font-medium text-gray-800">{restaurant?.tradeName ?? restaurant?.name}</p>
          <p className="mb-3 text-xs text-gray-500">
            {user?.name} · {user ? ROLE_LABELS[user.role] : ''}
          </p>
          <button
            type="button"
            onClick={handleLogout}
            className="flex w-full items-center justify-center gap-2 rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-100"
          >
            <LogOut className="h-4 w-4" />
            Sair
          </button>
        </div>
      </aside>

      <div className="flex-1">
        {/* Mobile top bar */}
        <header className="flex items-center justify-between border-b border-gray-200 bg-white px-4 py-3 sm:hidden">
          <span className="font-semibold text-gray-800">{restaurant?.tradeName ?? restaurant?.name}</span>
          <button
            type="button"
            onClick={handleLogout}
            aria-label="Sair"
            className="rounded-md border border-gray-300 p-2 text-gray-700 hover:bg-gray-100"
          >
            <LogOut className="h-4 w-4" />
          </button>
        </header>

        <main className="p-4 pb-20 sm:p-6 sm:pb-6">
          <Outlet />
        </main>
      </div>

      {/* Mobile bottom nav */}
      <nav className="fixed inset-x-0 bottom-0 z-10 flex border-t border-gray-200 bg-white sm:hidden">
        {PRIMARY_NAV_ITEMS.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.end}
            className={({ isActive }) =>
              `flex flex-1 flex-col items-center gap-0.5 py-2 text-xs ${
                isActive ? 'text-brand-700' : 'text-gray-500'
              }`
            }
          >
            <item.icon className="h-5 w-5" />
            {item.label}
          </NavLink>
        ))}
        {visibleMoreItems.length > 0 && (
          <button
            type="button"
            onClick={() => setIsMoreOpen(true)}
            className="flex flex-1 flex-col items-center gap-0.5 py-2 text-xs text-gray-500"
          >
            <MoreHorizontal className="h-5 w-5" />
            Mais
          </button>
        )}
      </nav>

      {isMoreOpen && (
        <Modal title="Mais" onClose={() => setIsMoreOpen(false)}>
          <div className="flex flex-col divide-y divide-gray-100">
            {visibleMoreItems.map((item) => (
              <button
                key={item.to}
                type="button"
                onClick={() => handleMoreNavigate(item.to)}
                className="flex items-center gap-3 py-3 text-left text-sm text-gray-700"
              >
                <item.icon className="h-5 w-5 text-gray-500" />
                {item.label}
              </button>
            ))}
          </div>
        </Modal>
      )}
    </div>
  )
}
