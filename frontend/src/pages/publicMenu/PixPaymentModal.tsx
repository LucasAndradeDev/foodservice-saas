import { useMutation } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import { Check, Copy, Loader2 } from 'lucide-react'
import { useState } from 'react'
import { QRCodeCanvas } from 'qrcode.react'
import { createPublicPixCharge } from '../../api/publicMenu'
import { Modal } from '../../components/Modal'

interface PixPaymentModalProps {
  slug: string
  tableId: string
  onClose: () => void
}

/** Lets the customer generate and pay a Pix charge for the table's open tab straight from the
 * digital menu, without calling the waiter. Confirmation is asynchronous (Woovi's webhook) - this
 * modal doesn't poll on its own, since PublicMenuPage already polls getPublicMenu and redirects to
 * the feedback page the moment the tab closes, which unmounts this modal along with the rest of
 * the page. */
export function PixPaymentModal({ slug, tableId, onClose }: PixPaymentModalProps) {
  const [copied, setCopied] = useState(false)

  const chargeMutation = useMutation({
    mutationFn: () => createPublicPixCharge(slug, tableId),
  })

  const charge = chargeMutation.data

  function handleCopy() {
    if (!charge) return
    navigator.clipboard.writeText(charge.brCode)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  function errorMessage() {
    const err = chargeMutation.error
    if (isAxiosError(err) && err.response?.status === 400) {
      return 'Ainda não há nada entregue na mesa pra pagar.'
    }
    return 'Não foi possível gerar a cobrança Pix. Chame o garçom.'
  }

  return (
    <Modal title="Pagar com Pix" onClose={onClose}>
      {!charge && !chargeMutation.isPending && !chargeMutation.isError && (
        <div className="flex flex-col items-center gap-4 text-center">
          <p className="text-sm text-gray-600 dark:text-stone-300">
            Gera um QR Code Pix pra você pagar a conta agora, direto do celular.
          </p>
          <button
            type="button"
            onClick={() => chargeMutation.mutate()}
            className="w-full rounded-xl bg-brand-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-brand-700"
          >
            Gerar QR Code
          </button>
        </div>
      )}

      {chargeMutation.isPending && (
        <div className="flex flex-col items-center gap-3 py-6 text-center">
          <Loader2 className="h-6 w-6 animate-spin text-brand-600 dark:text-brand-400" />
          <p className="text-sm text-gray-500 dark:text-stone-400">Gerando cobrança...</p>
        </div>
      )}

      {chargeMutation.isError && (
        <div className="flex flex-col items-center gap-3 text-center">
          <p className="text-sm text-wine-600 dark:text-wine-400">{errorMessage()}</p>
          <button
            type="button"
            onClick={() => chargeMutation.mutate()}
            className="w-full rounded-xl border border-gray-300 px-4 py-2.5 text-sm font-medium text-gray-700 hover:bg-gray-50 dark:border-white/10 dark:text-stone-300 dark:hover:bg-white/5"
          >
            Tentar de novo
          </button>
        </div>
      )}

      {charge && (
        <div className="flex flex-col items-center gap-3">
          <div className="rounded-xl border border-gray-200 bg-white p-3 shadow-md">
            <QRCodeCanvas value={charge.brCode} size={200} />
          </div>
          <p className="flex items-center gap-1.5 text-sm text-gray-600 dark:text-stone-400">
            <Loader2 className="h-4 w-4 animate-spin" />
            Aguardando o pagamento...
          </p>
          <div className="w-full">
            <p className="mb-1 text-xs font-semibold tracking-wide text-gray-400 uppercase dark:text-stone-500">
              Pix copia e cola
            </p>
            <p className="truncate rounded-md bg-gray-50 px-3 py-2 text-xs text-gray-700 dark:bg-stone-800 dark:text-stone-300">
              {charge.brCode}
            </p>
          </div>
          <button
            type="button"
            onClick={handleCopy}
            className="flex w-full items-center justify-center gap-1.5 rounded-xl bg-brand-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-brand-700"
          >
            {copied ? <Check className="h-4 w-4" /> : <Copy className="h-4 w-4" />}
            {copied ? 'Copiado!' : 'Copiar código'}
          </button>
        </div>
      )}
    </Modal>
  )
}
