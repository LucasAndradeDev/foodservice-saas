import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CheckCircle2, CreditCard, Wallet } from 'lucide-react'
import { useState, type KeyboardEvent } from 'react'
import { Link } from 'react-router-dom'
import { getCardIntegrationStatus, saveCardIntegration } from '../api/cardIntegration'

/** Lets the owner/manager connect the restaurant's own Mercado Pago account (see
 * docs/CARD_PAYMENT.md) - the access token and webhook secret are encrypted server-side and never
 * sent back, so this only ever shows a connected/not-connected status, never the stored values. */
export function CardIntegrationCard({ canManage }: { canManage: boolean }) {
  const queryClient = useQueryClient()
  const { data: status } = useQuery({
    queryKey: ['card-integration'],
    queryFn: getCardIntegrationStatus,
    enabled: canManage,
  })

  const [isEditing, setIsEditing] = useState(false)
  const [accessToken, setAccessToken] = useState('')
  const [webhookSecret, setWebhookSecret] = useState('')
  const [error, setError] = useState<string | null>(null)

  const saveMutation = useMutation({
    mutationFn: () => saveCardIntegration(accessToken.trim(), webhookSecret.trim()),
    onSuccess: () => {
      queryClient.setQueryData(['card-integration'], { configured: true })
      setAccessToken('')
      setWebhookSecret('')
      setIsEditing(false)
      setError(null)
    },
    onError: () => setError('Não foi possível salvar. Confira o Access Token e o Webhook Secret e tente novamente.'),
  })

  function handleSave() {
    const hasAccessToken = !!accessToken.trim()
    const hasWebhookSecret = !!webhookSecret.trim()
    // First connection: nothing to fall back to, so both are required. Rotating an existing
    // connection: each field is independent - leaving one blank keeps it as it is server-side,
    // so only block submitting when BOTH are blank (nothing to update at all).
    if (!status?.configured && (!hasAccessToken || !hasWebhookSecret)) return
    if (status?.configured && !hasAccessToken && !hasWebhookSecret) return
    saveMutation.mutate()
  }

  function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'Enter') {
      event.preventDefault()
      handleSave()
    }
  }

  return (
    <div className="rounded-xl border border-gray-200 bg-white p-4 transition-shadow hover:shadow-md dark:border-white/10 dark:bg-stone-900">
      <div className="mb-1 flex items-center gap-2.5">
        <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-brand-50 text-brand-600 dark:bg-brand-500/10 dark:text-brand-400">
          <CreditCard className="h-4 w-4" />
        </span>
        <p className="text-sm font-medium text-gray-700 dark:text-stone-300">Pagamento com cartão (Mercado Pago)</p>
      </div>
      <p className="mb-3 text-xs text-gray-500 dark:text-stone-400">
        Conecte a conta Mercado Pago do próprio restaurante pra cobrar no cartão no Caixa (QR Code) ou direto pelo
        celular no cardápio digital, sem esperar a maquininha do garçom.{' '}
        <Link to="/ajuda/cartao" target="_blank" rel="noopener noreferrer" className="text-brand-600 hover:underline dark:text-brand-400">
          Saiba mais
        </Link>
      </p>

      {!isEditing && status?.configured ? (
        <div className="flex items-center justify-between">
          <span className="flex items-center gap-1.5 text-sm text-sage-700 dark:text-sage-400">
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
        // and a nested <form> is invalid HTML - same gotcha PixIntegrationCard already documents.
        <div className="flex flex-col gap-2">
          <input
            type="text"
            required={!status?.configured}
            placeholder={status?.configured ? 'Access Token de produção (deixe em branco pra manter o atual)' : 'Access Token de produção'}
            value={accessToken}
            onChange={(e) => setAccessToken(e.target.value)}
            onKeyDown={handleKeyDown}
            className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
          />
          <input
            type="text"
            required={!status?.configured}
            placeholder={status?.configured ? 'Webhook Secret (deixe em branco pra manter o atual)' : 'Webhook Secret'}
            value={webhookSecret}
            onChange={(e) => setWebhookSecret(e.target.value)}
            onKeyDown={handleKeyDown}
            className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
          />
          <div className="flex justify-end gap-2">
            {status?.configured && (
              <button
                type="button"
                onClick={() => {
                  setIsEditing(false)
                  setAccessToken('')
                  setWebhookSecret('')
                  setError(null)
                }}
                className="rounded-md border border-gray-300 px-3 py-2 text-sm text-gray-600 hover:bg-gray-100 dark:border-white/10 dark:text-stone-400 dark:hover:bg-white/5"
              >
                Cancelar
              </button>
            )}
            <button
              type="button"
              onClick={handleSave}
              disabled={saveMutation.isPending}
              className="rounded-md bg-brand-600 px-3 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50"
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
