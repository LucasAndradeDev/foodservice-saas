import { Boxes, ClipboardList, LogOut, ShoppingCart, Truck, Warehouse } from 'lucide-react'
import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

const NAV_ITEMS = [
  { to: '/ingredients', label: 'Insumos', icon: Boxes },
  { to: '/suppliers', label: 'Fornecedores', icon: Truck },
  { to: '/purchases', label: 'Compras', icon: ShoppingCart },
  { to: '/recipes', label: 'Receitas', icon: ClipboardList },
]

export function Layout() {
  const { restaurantName, logout } = useAuth()

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-stone-950">
      <header className="flex items-center justify-between border-b border-gray-200 bg-white px-4 py-3 dark:border-white/10 dark:bg-stone-900">
        <div className="flex items-center gap-2">
          <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-teal-600 text-white">
            <Warehouse className="h-5 w-5" />
          </span>
          <div>
            <p className="text-sm font-semibold text-gray-800 dark:text-white">Armazém Morá</p>
            {restaurantName && <p className="text-xs text-gray-500 dark:text-stone-400">{restaurantName}</p>}
          </div>
        </div>
        <button
          type="button"
          onClick={logout}
          className="flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-100 dark:text-stone-300 dark:hover:bg-white/5"
        >
          <LogOut className="h-4 w-4" />
          Sair
        </button>
      </header>
      <nav className="flex gap-1 overflow-x-auto border-b border-gray-200 bg-white px-4 dark:border-white/10 dark:bg-stone-900">
        {NAV_ITEMS.map(({ to, label, icon: Icon }) => (
          <NavLink
            key={to}
            to={to}
            className={({ isActive }) =>
              `flex shrink-0 items-center gap-1.5 border-b-2 px-3 py-2.5 text-sm font-medium transition ${
                isActive
                  ? 'border-teal-600 text-teal-700 dark:border-teal-400 dark:text-teal-400'
                  : 'border-transparent text-gray-500 hover:text-gray-700 dark:text-stone-400 dark:hover:text-stone-200'
              }`
            }
          >
            <Icon className="h-4 w-4" />
            {label}
          </NavLink>
        ))}
      </nav>
      <main className="mx-auto max-w-3xl p-4">
        <Outlet />
      </main>
    </div>
  )
}
