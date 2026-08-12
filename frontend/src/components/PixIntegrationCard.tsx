import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CheckCircle2, Wallet } from 'lucide-react'
import { useState, type KeyboardEvent } from 'react'
import { getPixIntegrationStatus, savePixIntegration } from '../api/pixIntegration'

/** Lets the owner/manager connect the restaurant's own Woovi account (see docs/PIX_PAYMENT.md) -
 * the AppID is encrypted server-side and never sent back, so this only ever shows a
 * connected/not-connected status, never the stored value itself. */
export function PixIntegrationCard({ canManage }: { canManage: boolean }) {
  const queryClient = useQueryClient()
  const { data: status } = useQuery({
    queryKey: ['pix-integration'],
    queryFn: getPixIntegrationStatus,
    enabled: canManage,
  })

  const [isEditing, setIsEditing] = useState(false)
  const [appId, setAppId] = useState('')
  const [error, setError] = useState<string | null>(null)

  const saveMutation = useMutation({
    mutationFn: savePixIntegration,
    onSuccess: () => {
      queryClient.setQueryData(['pix-integration'], { configured: true })
      setAppId('')
      setIsEditing(false)
      setError(null)
    },
    onError: () => setError('Não foi possível salvar. Confira o AppID e tente novamente.'),
  })

  function handleSave() {
    if (!appId.trim()) return
    saveMutation.mutate(appId.trim())
  }

  function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'Enter') {
      event.preventDefault()
      handleSave()
    }
  }

  return (
    <div className="rounded-md border border-gray-200 p-3 dark:border-white/10">
      <p className="mb-1 text-sm font-medium text-gray-700 dark:text-stone-300">Pagamento Pix (Woovi)</p>
      <p className="mb-3 text-xs text-gray-500 dark:text-stone-400">
        Conecte a conta Woovi do próprio restaurante pra gerar um QR Code Pix no Caixa e o cliente pagar direto pelo
        celular, sem esperar o garçom confirmar manualmente.
      </p>

      {!isEditing && status?.configured ? (
        <div className="flex items-center justify-between">
          <span className="flex items-center gap-1.5 text-sm text-green-700 dark:text-green-400">
            <CheckCircle2 className="h-4 w-4" />
            Conectado
          </span>
          {canManage && (
            <button
              type="button"
              onClick={() => setIsEditing(true)}
              className="text-sm text-brand-600 hover:underline dark:text-brand-400"
            >
              Trocar
            </button>
          )}
        </div>
      ) : canManage ? (
        // A <div>, not a <form>: this card renders inside RestaurantSettingsPage's own <form>,
        // and a nested <form> is invalid HTML - the browser stops treating the inner submit
        // button as belonging to it and instead does a real native submit of the OUTER form
        // (full page navigation, losing all app state) instead of running this card's own
        // save mutation.
        <div className="flex flex-col gap-2 sm:flex-row">
          <input
            type="text"
            required
            placeholder="AppID da Woovi"
            value={appId}
            onChange={(e) => setAppId(e.target.value)}
            onKeyDown={handleKeyDown}
            className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
          />
          <div className="flex shrink-0 gap-2">
            {status?.configured && (
              <button
                type="button"
                onClick={() => {
                  setIsEditing(false)
                  setAppId('')
                  setError(null)
                }}
                className="flex-1 rounded-md border border-gray-300 px-3 py-2 text-sm text-gray-600 hover:bg-gray-100 sm:flex-none dark:border-white/10 dark:text-stone-400 dark:hover:bg-white/5"
              >
                Cancelar
              </button>
            )}
            <button
              type="button"
              onClick={handleSave}
              disabled={saveMutation.isPending}
              className="flex-1 rounded-md bg-brand-600 px-3 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50 sm:flex-none"
            >
              Salvar
            </button>
          </div>
        </div>
      ) : (
        <span className="flex items-center gap-1.5 text-sm text-gray-500 dark:text-stone-400">
          <Wallet className="h-4 w-4" />
          Não configurado
        </span>
      )}

      {error && <p className="mt-2 text-xs text-wine-600 dark:text-wine-400">{error}</p>}
    </div>
  )
}
