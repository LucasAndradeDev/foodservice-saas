import { LogOut } from 'lucide-react'
import { Outlet } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { Logo } from '../theme/Logo'
import { ThemeToggleButton } from '../theme/ThemeToggleButton'

// A courier's whole app experience, deliberately not a stripped-down AppLayout: AppLayout carries
// kitchen/table/checkout notification polling, the warehouse SSO handoff, offline/payment-due
// banners - none of it applicable to a courier, whose only job here is seeing their own
// deliveries and marking them delivered. Just a header (logo, theme, logout) and the page.
export function CourierLayout() {
  const { logout } = useAuth()

  return (
    <div className="flex min-h-screen flex-col bg-gray-50 dark:bg-stone-950">
      <header className="flex shrink-0 items-center justify-between border-b border-gray-200 bg-white px-4 py-3 dark:border-white/10 dark:bg-stone-900">
        <Logo className="h-8 w-auto" />
        <div className="flex items-center gap-1">
          <ThemeToggleButton />
          <button
            type="button"
            onClick={logout}
            title="Sair"
            aria-label="Sair"
            className="rounded-md p-2 text-gray-500 hover:bg-gray-100 hover:text-wine-700 dark:text-stone-400 dark:hover:bg-white/5 dark:hover:text-wine-400"
          >
            <LogOut className="h-5 w-5" />
          </button>
        </div>
      </header>
      <main className="flex flex-1 flex-col">
        <Outlet />
      </main>
    </div>
  )
}
