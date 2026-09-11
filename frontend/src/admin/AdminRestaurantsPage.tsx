import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { LogOut, ShieldAlert, ShieldCheck, UserCheck } from 'lucide-react'
import { useState } from 'react'
import { approveRestaurant, listRestaurants, updateRestaurantStatus, type AdminRestaurant } from '../api/admin'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { Logo } from '../theme/Logo'
import { useAdminAuth } from './AdminAuthContext'

export function AdminRestaurantsPage() {
  const { logout } = useAdminAuth()
  const queryClient = useQueryClient()
  const [pendingBlock, setPendingBlock] = useState<AdminRestaurant | null>(null)

  const { data: restaurants, isLoading } = useQuery({
    queryKey: ['adminRestaurants'],
    queryFn: listRestaurants,
  })

  // Pending-approval signups need attention first - surfaced at the top regardless of when
  // they were created.
  const sortedRestaurants = restaurants
    ? [...restaurants].sort((a, b) => Number(a.approved) - Number(b.approved))
    : undefined

  const statusMutation = useMutation({
    mutationFn: ({ id, active, paymentDueDate }: { id: string; active: boolean; paymentDueDate: string | null }) =>
      updateRestaurantStatus(id, active, paymentDueDate),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['adminRestaurants'] })
      setPendingBlock(null)
    },
  })

  const approveMutation = useMutation({
    mutationFn: approveRestaurant,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['adminRestaurants'] })
    },
  })

  function handleDueDateChange(restaurant: AdminRestaurant, value: string) {
    statusMutation.mutate({ id: restaurant.id, active: restaurant.active, paymentDueDate: value || null })
  }

  function confirmBlockToggle() {
    if (!pendingBlock) return
    statusMutation.mutate({
      id: pendingBlock.id,
      active: !pendingBlock.active,
      paymentDueDate: pendingBlock.paymentDueDate,
    })
  }

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-stone-950">
      <header className="flex items-center justify-between border-b border-gray-200 bg-white px-4 py-3 dark:border-white/10 dark:bg-stone-900">
        <div className="flex items-center gap-3">
          <Logo className="h-8 w-auto" />
          <span className="font-semibold text-gray-800 dark:text-white">Painel admin</span>
        </div>
        <button
          type="button"
          onClick={logout}
          className="flex items-center gap-2 rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-100 dark:border-white/10 dark:text-stone-300 dark:hover:bg-white/5"
        >
          <LogOut className="h-4 w-4" />
          Sair
        </button>
      </header>

      <main className="mx-auto max-w-5xl px-4 py-8">
        <h1 className="mb-6 text-xl font-bold text-gray-800 dark:text-white">Restaurantes</h1>

        {isLoading && <p className="text-sm text-gray-500 dark:text-stone-400">Carregando...</p>}

        {sortedRestaurants && (
          <div className="overflow-x-auto rounded-lg border border-gray-200 bg-white dark:border-white/10 dark:bg-stone-900">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="border-b border-gray-200 text-xs uppercase text-gray-500 dark:border-white/10 dark:text-stone-400">
                  <th className="px-4 py-3 font-semibold">Restaurante</th>
                  <th className="px-4 py-3 font-semibold">Contato</th>
                  <th className="px-4 py-3 font-semibold">Status</th>
                  <th className="px-4 py-3 font-semibold">Vencimento</th>
                  <th className="px-4 py-3 font-semibold">Ação</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100 dark:divide-white/10">
                {sortedRestaurants.map((restaurant) => (
                  <tr key={restaurant.id}>
                    <td className="px-4 py-3 text-gray-800 dark:text-white">
                      {restaurant.tradeName || restaurant.name}
                    </td>
                    <td className="px-4 py-3 text-gray-500 dark:text-stone-400">
                      {restaurant.phone || '—'}
                      {restaurant.cnpj && <div className="text-xs">{restaurant.cnpj}</div>}
                    </td>
                    <td className="px-4 py-3">
                      <span
                        className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium ${
                          !restaurant.approved
                            ? 'bg-amber-100 text-amber-700 dark:bg-amber-500/15 dark:text-amber-400'
                            : restaurant.active
                              ? 'bg-sage-100 text-sage-700 dark:bg-sage-500/15 dark:text-sage-400'
                              : 'bg-wine-100 text-wine-700 dark:bg-wine-500/15 dark:text-wine-400'
                        }`}
                      >
                        {!restaurant.approved ? 'Pendente' : restaurant.active ? 'Ativo' : 'Bloqueado'}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      <input
                        type="date"
                        value={restaurant.paymentDueDate ?? ''}
                        onChange={(e) => handleDueDateChange(restaurant, e.target.value)}
                        className="rounded-md border border-gray-300 px-2 py-1 text-sm text-gray-800 dark:border-white/10 dark:bg-stone-800 dark:text-white"
                      />
                    </td>
                    <td className="px-4 py-3">
                      {!restaurant.approved ? (
                        <button
                          type="button"
                          onClick={() => approveMutation.mutate(restaurant.id)}
                          disabled={approveMutation.isPending}
                          className="inline-flex items-center gap-1.5 rounded-md border border-brand-300 px-3 py-1.5 text-sm font-medium text-brand-700 hover:bg-brand-50 disabled:opacity-50 dark:border-brand-700 dark:text-brand-400 dark:hover:bg-brand-500/10"
                        >
                          <UserCheck className="h-4 w-4" /> Aprovar
                        </button>
                      ) : (
                        <button
                          type="button"
                          onClick={() => setPendingBlock(restaurant)}
                          className={`inline-flex items-center gap-1.5 rounded-md px-3 py-1.5 text-sm font-medium ${
                            restaurant.active
                              ? 'border border-wine-300 text-wine-700 hover:bg-wine-50 dark:border-wine-700 dark:text-wine-400 dark:hover:bg-wine-500/10'
                              : 'border border-sage-300 text-sage-700 hover:bg-sage-50 dark:border-sage-700 dark:text-sage-400 dark:hover:bg-sage-500/10'
                          }`}
                        >
                          {restaurant.active ? (
                            <>
                              <ShieldAlert className="h-4 w-4" /> Bloquear
                            </>
                          ) : (
                            <>
                              <ShieldCheck className="h-4 w-4" /> Desbloquear
                            </>
                          )}
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </main>

      {pendingBlock && (
        <ConfirmDialog
          title={pendingBlock.active ? 'Bloquear restaurante?' : 'Desbloquear restaurante?'}
          message={
            pendingBlock.active
              ? `${pendingBlock.tradeName || pendingBlock.name} vai perder o acesso ao sistema imediatamente.`
              : `${pendingBlock.tradeName || pendingBlock.name} volta a ter acesso normal ao sistema.`
          }
          confirmLabel={pendingBlock.active ? 'Bloquear' : 'Desbloquear'}
          danger={pendingBlock.active}
          isLoading={statusMutation.isPending}
          onConfirm={confirmBlockToggle}
          onCancel={() => setPendingBlock(null)}
        />
      )}
    </div>
  )
}
