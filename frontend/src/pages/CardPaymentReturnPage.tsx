import { CheckCircle2, Clock, XCircle } from 'lucide-react'
import { useEffect, useRef } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { verifyCardCharge } from '../api/cardIntegration'

const STATUS_CONTENT = {
  success: { icon: CheckCircle2, message: 'Pagamento em confirmação. Já já a comanda fecha sozinha.', tone: 'text-sage-600 dark:text-sage-400' },
  pending: { icon: Clock, message: 'Pagamento em análise pelo seu banco. Aguarde a confirmação.', tone: 'text-gray-600 dark:text-stone-300' },
  failure: { icon: XCircle, message: 'Pagamento recusado. Volte e tente outro cartão.', tone: 'text-wine-600 dark:text-wine-400' },
} as const

/**
 * Landing page for Mercado Pago's back_urls (success/pending/failure) after a card checkout
 * attempt, for both the Caixa (staff-generated QR, scanned by the customer's own phone) and the
 * digital-menu redirect flows. SECURITY INVARIANT: the query string's own `status` is only ever
 * used to pick which short message to show - never to mutate anything, since a customer could
 * reach this URL with an arbitrary status value just by visiting it directly. The real
 * confirmation always comes from Mercado Pago itself, either via the async webhook
 * (CardChargeService#handleWebhook) or, as a backstop for the sandbox signature flakiness
 * documented in docs/CARD_PAYMENT.md, this page also fires a `verifyCardCharge` call keyed off
 * `ref` (our own externalReference, embedded in the back_url when the charge was created - never
 * anything Mercado Pago appends) - that endpoint re-asks Mercado Pago directly and only acts if
 * the answer matches a real PENDING charge, so it stays just as trustworthy as the webhook, only
 * triggered differently. When menu/table are present (the digital-menu flow), this redirects
 * back into the menu after a few seconds, where the existing polling (already proven for Pix)
 * detects the tab closing; the Caixa flow has nowhere meaningful to redirect back to (this is the
 * customer's own phone, no session), so it just shows a static message.
 */
export function CardPaymentReturnPage() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const verifiedRef = useRef(false)

  const status = searchParams.get('status')
  const menu = searchParams.get('menu')
  const table = searchParams.get('table')
  const ref = searchParams.get('ref')
  const content = STATUS_CONTENT[status as keyof typeof STATUS_CONTENT] ?? STATUS_CONTENT.pending
  const Icon = content.icon

  useEffect(() => {
    if (!ref || verifiedRef.current) return
    verifiedRef.current = true
    verifyCardCharge(ref).catch(() => {
      // Best-effort backstop - the webhook (or a later manual close) still covers this.
    })
  }, [ref])

  useEffect(() => {
    if (!menu || !table) return
    const timeout = setTimeout(() => navigate(`/menu/${menu}/${table}`, { replace: true }), 2500)
    return () => clearTimeout(timeout)
  }, [menu, table, navigate])

  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-4 bg-gray-50 px-4 text-center dark:bg-stone-950">
      <Icon className={`h-10 w-10 ${content.tone}`} />
      <p className="max-w-xs text-sm text-gray-700 dark:text-stone-300">{content.message}</p>
      {!menu && <p className="text-xs text-gray-400 dark:text-stone-500">Pode fechar essa aba.</p>}
    </div>
  )
}
