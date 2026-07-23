import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

const ROLE_LABELS: Record<string, string> = {
  OWNER: 'Proprietário',
  MANAGER: 'Gerente',
  WAITER: 'Garçom',
  KITCHEN: 'Cozinha',
  CASHIER: 'Caixa',
}

const NAV_ITEMS = [
  { to: '/tables', label: 'Mesas' },
  { to: '/kitchen', label: 'Cozinha' },
  { to: '/categories', label: 'Categorias' },
  { to: '/products', label: 'Produtos' },
  { to: '/settings', label: 'Configurações' },
]

export function AppLayout() {
  const { user, restaurant, logout } = useAuth()
  const navigate = useNavigate()

  function handleLogout() {
    logout()
    navigate('/login')
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <header className="flex items-center justify-between border-b border-gray-200 bg-white px-6 py-3">
        <div className="flex items-center gap-6">
          <span className="font-semibold text-gray-800">
            {restaurant?.tradeName ?? restaurant?.name}
          </span>
          <nav className="flex gap-4">
            {NAV_ITEMS.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                className={({ isActive }) =>
                  `text-sm ${isActive ? 'font-medium text-gray-900' : 'text-gray-500 hover:text-gray-800'}`
                }
              >
                {item.label}
              </NavLink>
            ))}
          </nav>
        </div>
        <div className="flex items-center gap-4">
          <span className="text-sm text-gray-600">
            {user?.name} · {user ? ROLE_LABELS[user.role] : ''}
          </span>
          <button
            type="button"
            onClick={handleLogout}
            className="rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-100"
          >
            Sair
          </button>
        </div>
      </header>
      <main className="p-6">
        <Outlet />
      </main>
    </div>
  )
}
