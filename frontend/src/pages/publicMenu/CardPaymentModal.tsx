import { useMutation } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import { Loader2 } from 'lucide-react'
import { createPublicCardCharge, createPublicDeliveryCardCharge } from '../../api/publicMenu'
import { Modal } from '../../components/Modal'

type CardPaymentModalProps =
  | { slug: string; tableId: string; deliveryToken?: undefined; onClose: () => void }
  | { slug?: undefined; tableId?: undefined; deliveryToken: string; onClose: () => void }

/** Lets the customer generate a card charge and pay straight from the digital menu, without
 * calling the waiter - either for a table's open tab (slug+tableId), or for a delivery order paid
 * immediately at submission (deliveryToken, task 29.1). Unlike Pix's QR code (the customer is
 * already on their own phone here, so there's nothing to scan) - on success this redirects the
 * browser straight to Mercado Pago's hosted checkout page (initPointUrl), which itself redirects
 * back to /pagamento/retorno once the customer finishes. Confirmation is still asynchronous
 * (Mercado Pago's webhook) - the dine-in flow relies on PublicMenuPage's polling of getPublicMenu;
 * the delivery flow relies on DeliveryStatusPage's own polling of the same access token. */
export function CardPaymentModal({ slug, tableId, deliveryToken, onClose }: CardPaymentModalProps) {
  const chargeMutation = useMutation({
    mutationFn: () => (deliveryToken ? createPublicDeliveryCardCharge(deliveryToken) : createPublicCardCharge(slug, tableId)),
    onSuccess: (charge) => {
      if (charge.initPointUrl) {
        window.location.href = charge.initPointUrl
      }
    },
  })

  function errorMessage() {
    const err = chargeMutation.error
    if (isAxiosError(err) && err.response?.status === 400) {
      return deliveryToken ? 'Não foi possível gerar a cobrança pra esse pedido.' : 'Ainda não há nada entregue na mesa pra pagar.'
    }
    return 'Não foi possível gerar a cobrança no cartão. Chame o garçom.'
  }

  return (
    <Modal title="Pagar com cartão" onClose={onClose}>
      {!chargeMutation.isPending && !chargeMutation.isError && !chargeMutation.isSuccess && (
        <div className="flex flex-col items-center gap-4 text-center">
          <p className="text-sm text-gray-600 dark:text-stone-300">
            Você vai ser levado pra uma página segura do Mercado Pago pra pagar a conta agora, direto do celular.
          </p>
          <button
            type="button"
            onClick={() => chargeMutation.mutate()}
            className="w-full rounded-xl bg-brand-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-brand-700"
          >
            Continuar
          </button>
        </div>
      )}

      {(chargeMutation.isPending || chargeMutation.isSuccess) && (
        <div className="flex flex-col items-center gap-3 py-6 text-center">
          <Loader2 className="h-6 w-6 animate-spin text-brand-600 dark:text-brand-400" />
          <p className="text-sm text-gray-500 dark:text-stone-400">Te levando pro pagamento...</p>
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
    </Modal>
  )
}
