import { AnimatePresence, motion } from 'framer-motion'
import { ArrowRight, MessageCircle, Minus, Plus, Ticket, Trash2, X } from 'lucide-react'
import type { CartItem, DeliveryAddressForm } from './utils'
import { currencyFormatter, isDeliveryAddressComplete, modifiersTotal } from './utils'

interface CartDrawerProps {
  isOpen: boolean
  cart: CartItem[]
  cartSubtotal: number
  cartDiscountAmount: number
  cartTotal: number
  orderError: string | null
  isSubmitting: boolean
  onClose: () => void
  onUpdateQuantity: (index: number, delta: number) => void
  onUpdateObservation: (index: number, observation: string) => void
  onRemove: (index: number) => void
  onSubmit: () => void
  discountAppliedLabel: string | null
  couponCode: string
  onCouponCodeChange: (value: string) => void
  onApplyCoupon: () => void
  isApplyingCoupon: boolean
  onRemoveCoupon: () => void
  isRemovingCoupon: boolean
  couponError: string | null
  showPhoneField: boolean
  customerPhone: string
  onCustomerPhoneChange: (value: string) => void
  showDeliveryFields: boolean
  deliveryAddress: DeliveryAddressForm
  onDeliveryAddressChange: (patch: Partial<DeliveryAddressForm>) => void
  deliveryFeeQuote?: { available: boolean; fee: number | null }
}

export function CartDrawer({
  isOpen,
  cart,
  cartSubtotal,
  cartDiscountAmount,
  cartTotal,
  orderError,
  isSubmitting,
  onClose,
  onUpdateQuantity,
  onUpdateObservation,
  onRemove,
  onSubmit,
  discountAppliedLabel,
  couponCode,
  onCouponCodeChange,
  onApplyCoupon,
  isApplyingCoupon,
  onRemoveCoupon,
  isRemovingCoupon,
  couponError,
  showPhoneField,
  customerPhone,
  onCustomerPhoneChange,
  showDeliveryFields,
  deliveryAddress,
  onDeliveryAddressChange,
  deliveryFeeQuote,
}: CartDrawerProps) {
  const deliveryFieldClass =
    'w-full rounded-xl border border-gray-200 px-3 py-2.5 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-white/5 dark:text-white dark:placeholder:text-stone-500'
  const deliveryComplete = isDeliveryAddressComplete(deliveryAddress)
  return (
    <AnimatePresence>
      {isOpen && (
        <>
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.2 }}
            onClick={onClose}
            className="fixed inset-0 z-30 bg-black/40"
          />
          <motion.div
            initial={{ y: '100%' }}
            animate={{ y: 0 }}
            exit={{ y: '100%' }}
            transition={{ duration: 0.25, ease: 'easeOut' }}
            className="fixed inset-x-0 bottom-0 z-40 mx-auto max-h-[85vh] w-full max-w-2xl overflow-y-auto rounded-t-3xl bg-white p-4 shadow-2xl dark:bg-stone-900"
          >
            <div className="mx-auto mb-3 h-1 w-10 rounded-full bg-gray-300 dark:bg-stone-700" />

            <div className="mb-4 flex items-center justify-between">
              <h2 className="text-lg font-semibold text-gray-800 dark:text-white">Seu pedido</h2>
              <button
                type="button"
                onClick={onClose}
                aria-label="Fechar"
                className="text-gray-400 hover:text-gray-600 dark:text-stone-500 dark:hover:text-stone-300"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            {discountAppliedLabel ? (
              <div className="mb-4 flex items-center gap-2 rounded-xl bg-sage-100 px-3 py-2 text-sm font-medium text-sage-700 dark:bg-sage-500/10 dark:text-sage-400">
                <Ticket className="h-4 w-4 shrink-0" />
                <span className="flex-1">{discountAppliedLabel}</span>
                <button
                  type="button"
                  onClick={onRemoveCoupon}
                  disabled={isRemovingCoupon}
                  className="shrink-0 text-xs font-semibold text-sage-700 underline hover:text-sage-600 disabled:opacity-50 dark:text-sage-300 dark:hover:text-sage-200"
                >
                  Remover
                </button>
              </div>
            ) : (
              <div className="mb-4">
                <div className="flex gap-2">
                  <input
                    type="text"
                    value={couponCode}
                    onChange={(e) => onCouponCodeChange(e.target.value.toUpperCase())}
                    placeholder="Tenho um cupom"
                    className="flex-1 rounded-xl border border-gray-200 px-3 py-2.5 text-sm uppercase focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-white/5 dark:text-white dark:placeholder:text-stone-500"
                  />
                  <button
                    type="button"
                    onClick={onApplyCoupon}
                    disabled={!couponCode.trim() || isApplyingCoupon}
                    className="shrink-0 rounded-xl border border-brand-600 px-4 py-2.5 text-sm font-semibold text-brand-600 hover:bg-brand-50 disabled:opacity-50 dark:border-brand-400 dark:text-brand-400 dark:hover:bg-white/5"
                  >
                    Aplicar
                  </button>
                </div>
                {couponError && <p className="mt-1.5 text-xs text-wine-600 dark:text-wine-400">{couponError}</p>}
              </div>
            )}

            {cart.length === 0 ? (
              <p className="pb-4 text-sm text-gray-500 dark:text-stone-400">Seu carrinho está vazio.</p>
            ) : (
              <>
                <ul className="mb-4 flex flex-col gap-3">
                  {cart.map((item, index) => (
                    <li key={index} className="rounded-2xl border border-gray-100 bg-gray-50/70 p-3 dark:border-white/10 dark:bg-white/5">
                      <div className="flex items-start justify-between gap-2">
                        <span className="font-medium text-gray-800 dark:text-white">{item.productName}</span>
                        <span className="text-sm font-semibold text-gray-800 dark:text-white">
                          {currencyFormatter.format((item.unitPrice + modifiersTotal(item.selectedModifiers)) * item.quantity)}
                        </span>
                      </div>
                      {item.selectedModifiers.length > 0 && (
                        <div className="mt-1.5 flex flex-wrap gap-1">
                          {item.selectedModifiers.map((modifier) => (
                            <span
                              key={modifier.optionId}
                              className="rounded-full bg-white px-2 py-0.5 text-xs text-gray-600 ring-1 ring-gray-200 dark:bg-white/10 dark:text-stone-300 dark:ring-white/10"
                            >
                              {modifier.optionName}
                            </span>
                          ))}
                        </div>
                      )}
                      {item.comboSelections && item.comboSelections.length > 0 && (
                        <div className="mt-1.5 flex flex-wrap gap-1">
                          {item.comboSelections.map((selection) => (
                            <span
                              key={selection.slotId}
                              className="rounded-full bg-white px-2 py-0.5 text-xs text-gray-600 ring-1 ring-gray-200 dark:bg-white/10 dark:text-stone-300 dark:ring-white/10"
                            >
                              {selection.productName}
                            </span>
                          ))}
                        </div>
                      )}
                      <div className="mt-3 flex items-center gap-2">
                        <div className="flex items-center gap-1 rounded-full border border-gray-200 bg-white p-1 dark:border-white/10 dark:bg-stone-900">
                          <button
                            type="button"
                            onClick={() => onUpdateQuantity(index, -1)}
                            aria-label="Diminuir quantidade"
                            className="flex h-7 w-7 items-center justify-center rounded-full text-gray-600 hover:bg-gray-100 dark:text-stone-300 dark:hover:bg-white/10"
                          >
                            <Minus className="h-3.5 w-3.5" />
                          </button>
                          <span className="w-6 text-center text-sm font-medium dark:text-white">{item.quantity}</span>
                          <button
                            type="button"
                            onClick={() => onUpdateQuantity(index, 1)}
                            aria-label="Aumentar quantidade"
                            className="flex h-7 w-7 items-center justify-center rounded-full text-gray-600 hover:bg-gray-100 dark:text-stone-300 dark:hover:bg-white/10"
                          >
                            <Plus className="h-3.5 w-3.5" />
                          </button>
                        </div>
                        <button
                          type="button"
                          onClick={() => onRemove(index)}
                          aria-label="Remover item"
                          className="ml-auto flex h-8 w-8 items-center justify-center rounded-full text-wine-500 hover:bg-wine-100 dark:text-wine-400 dark:hover:bg-wine-500/10"
                        >
                          <Trash2 className="h-4 w-4" />
                        </button>
                      </div>
                      <input
                        type="text"
                        placeholder="Observação (opcional)"
                        maxLength={255}
                        value={item.observation}
                        onChange={(e) => onUpdateObservation(index, e.target.value)}
                        className="mt-2 w-full rounded-lg border border-gray-200 bg-white px-2.5 py-1.5 text-xs focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-900 dark:text-white dark:placeholder:text-stone-500"
                      />
                    </li>
                  ))}
                </ul>

                <div className="-mx-4 border-t border-gray-100 px-4 pt-3 dark:border-white/10">
                  {cartDiscountAmount > 0 && (
                    <>
                      <div className="mb-1 flex items-center justify-between text-sm text-gray-500 dark:text-stone-400">
                        <span>Subtotal</span>
                        <span>{currencyFormatter.format(cartSubtotal)}</span>
                      </div>
                      <div className="mb-1 flex items-center justify-between text-sm text-sage-700 dark:text-sage-400">
                        <span>Desconto</span>
                        <span>-{currencyFormatter.format(cartDiscountAmount)}</span>
                      </div>
                    </>
                  )}
                  <div className="mb-3 flex items-center justify-between">
                    <span className="text-sm text-gray-500 dark:text-stone-400">Total</span>
                    <span className="text-xl font-bold text-gray-900 dark:text-white">{currencyFormatter.format(cartTotal)}</span>
                  </div>

                  {showPhoneField && (
                    <div className="mb-3">
                      <label className="mb-1 flex items-center gap-1.5 text-xs font-medium text-gray-500 dark:text-stone-400">
                        <MessageCircle className="h-3.5 w-3.5" />
                        WhatsApp (opcional) — avisamos quando o pedido estiver pronto
                      </label>
                      <input
                        type="tel"
                        value={customerPhone}
                        onChange={(e) => onCustomerPhoneChange(e.target.value)}
                        placeholder="(11) 91234-5678"
                        className="w-full rounded-xl border border-gray-200 px-3 py-2.5 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-white/5 dark:text-white dark:placeholder:text-stone-500"
                      />
                    </div>
                  )}

                  {showDeliveryFields && (
                    <div className="mb-3 space-y-2">
                      <p className="text-xs font-medium text-gray-500 dark:text-stone-400">Endereço de entrega</p>
                      <input
                        type="text"
                        value={deliveryAddress.customerName}
                        onChange={(e) => onDeliveryAddressChange({ customerName: e.target.value })}
                        placeholder="Seu nome"
                        className={deliveryFieldClass}
                      />
                      <input
                        type="tel"
                        value={deliveryAddress.customerPhone}
                        onChange={(e) => onDeliveryAddressChange({ customerPhone: e.target.value })}
                        placeholder="WhatsApp — (11) 91234-5678"
                        className={deliveryFieldClass}
                      />
                      <div className="flex gap-2">
                        <input
                          type="text"
                          value={deliveryAddress.street}
                          onChange={(e) => onDeliveryAddressChange({ street: e.target.value })}
                          placeholder="Rua"
                          className={`${deliveryFieldClass} flex-[3]`}
                        />
                        <input
                          type="text"
                          value={deliveryAddress.number}
                          onChange={(e) => onDeliveryAddressChange({ number: e.target.value })}
                          placeholder="Número"
                          className={`${deliveryFieldClass} flex-1`}
                        />
                      </div>
                      <input
                        type="text"
                        value={deliveryAddress.complement}
                        onChange={(e) => onDeliveryAddressChange({ complement: e.target.value })}
                        placeholder="Complemento (opcional)"
                        className={deliveryFieldClass}
                      />
                      <div className="flex gap-2">
                        <input
                          type="text"
                          value={deliveryAddress.neighborhood}
                          onChange={(e) => onDeliveryAddressChange({ neighborhood: e.target.value })}
                          placeholder="Bairro"
                          className={`${deliveryFieldClass} flex-1`}
                        />
                        <input
                          type="text"
                          value={deliveryAddress.city}
                          onChange={(e) => onDeliveryAddressChange({ city: e.target.value })}
                          placeholder="Cidade"
                          className={`${deliveryFieldClass} flex-1`}
                        />
                      </div>
                      {deliveryAddress.neighborhood.trim().length > 0 && deliveryFeeQuote && (
                        <p
                          className={`text-xs font-medium ${
                            deliveryFeeQuote.available
                              ? 'text-sage-600 dark:text-sage-400'
                              : 'text-wine-600 dark:text-wine-400'
                          }`}
                        >
                          {deliveryFeeQuote.available
                            ? `Taxa de entrega pra ${deliveryAddress.neighborhood}: ${currencyFormatter.format(deliveryFeeQuote.fee ?? 0)}`
                            : `Ainda não entregamos em "${deliveryAddress.neighborhood}".`}
                        </p>
                      )}
                      <div className="flex gap-2">
                        <input
                          type="text"
                          value={deliveryAddress.zipCode}
                          onChange={(e) => onDeliveryAddressChange({ zipCode: e.target.value })}
                          placeholder="CEP (opcional)"
                          className={`${deliveryFieldClass} flex-1`}
                        />
                        <input
                          type="text"
                          value={deliveryAddress.referencePoint}
                          onChange={(e) => onDeliveryAddressChange({ referencePoint: e.target.value })}
                          placeholder="Ponto de referência (opcional)"
                          className={`${deliveryFieldClass} flex-[2]`}
                        />
                      </div>
                    </div>
                  )}

                  {orderError && <p className="mb-3 text-sm text-wine-600 dark:text-wine-400">{orderError}</p>}

                  <button
                    type="button"
                    onClick={onSubmit}
                    disabled={isSubmitting || (showDeliveryFields && !deliveryComplete)}
                    className="flex w-full items-center justify-center gap-2 rounded-full bg-gradient-to-r from-brand-600 to-brand-500 px-4 py-3.5 text-sm font-semibold text-white shadow-lg shadow-brand-900/20 transition active:scale-[0.98] disabled:opacity-50 disabled:active:scale-100"
                  >
                    {isSubmitting ? 'Enviando...' : 'Enviar pedido'}
                    {!isSubmitting && <ArrowRight className="h-4 w-4" />}
                  </button>
                  {showDeliveryFields && !deliveryComplete && (
                    <p className="mt-2 text-center text-xs text-gray-400 dark:text-stone-500">
                      Preencha nome, telefone, rua, número, bairro e cidade.
                    </p>
                  )}
                </div>
              </>
            )}
          </motion.div>
        </>
      )}
    </AnimatePresence>
  )
}
