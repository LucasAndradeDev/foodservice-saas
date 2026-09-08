import { AnimatePresence, motion } from 'framer-motion'
import { Clock, Moon, ShoppingBag, Sun } from 'lucide-react'
import { useEffect, useRef, useState, type CSSProperties } from 'react'
import { Link } from 'react-router-dom'
import { DELIVERY_STATUS_LABELS, DELIVERY_STATUS_STYLES, type DeliveryStatus } from '../../api/deliveries'
import { currencyFormatter } from './utils'

const MENU_ACCENT_COLOR = '#b3421f'

interface ActiveOrderInfo {
  token: string
  status: DeliveryStatus
  paid: boolean
  items: { productName: string; quantity: number; unitPrice: number }[]
  deliveryFee: number
  billTotal: number | null
  etaMinutes: number | null
}

interface MenuHeroProps {
  restaurantName: string
  logo: string | null
  tableNumber?: number
  theme: 'light' | 'dark'
  onToggleTheme: () => void
  activeOrder?: ActiveOrderInfo | null
}

export function MenuHero({ restaurantName, logo, tableNumber, theme, onToggleTheme, activeOrder }: MenuHeroProps) {
  const heroStyle = { '--menu-accent': MENU_ACCENT_COLOR } as CSSProperties
  const [isOrderPreviewOpen, setIsOrderPreviewOpen] = useState(false)
  const orderPreviewRef = useRef<HTMLDivElement>(null)

  // Closing on outside click (not just the toggle button) is what makes this feel like a real
  // dropdown instead of a panel the customer has to hunt for a way to dismiss.
  useEffect(() => {
    if (!isOrderPreviewOpen) return
    function handlePointerDown(event: MouseEvent) {
      if (orderPreviewRef.current && !orderPreviewRef.current.contains(event.target as Node)) {
        setIsOrderPreviewOpen(false)
      }
    }
    document.addEventListener('mousedown', handlePointerDown)
    return () => document.removeEventListener('mousedown', handlePointerDown)
  }, [isOrderPreviewOpen])

  // Collapses again whenever the order itself disappears (e.g. delivered), so a stale preview
  // never lingers open pointing at a badge that's no longer rendered.
  useEffect(() => {
    if (!activeOrder) setIsOrderPreviewOpen(false)
  }, [activeOrder])

  const itemsTotal = activeOrder?.items.reduce((sum, item) => sum + item.unitPrice * item.quantity, 0) ?? 0
  const orderTotal = activeOrder ? activeOrder.billTotal ?? itemsTotal + activeOrder.deliveryFee : 0

  return (
    <header
      style={heroStyle}
      className="relative z-10 bg-white/95 px-4 py-2.5 backdrop-blur-xl dark:bg-stone-950/95"
    >
      <div className="mx-auto flex max-w-2xl items-center gap-3">
        {logo ? (
          <img
            src={logo}
            alt={restaurantName}
            className="h-10 w-10 shrink-0 rounded-2xl object-cover shadow-sm ring-1 ring-black/5 dark:ring-white/10"
          />
        ) : (
          <div
            style={{ backgroundImage: `linear-gradient(135deg, ${MENU_ACCENT_COLOR}, color-mix(in srgb, ${MENU_ACCENT_COLOR} 60%, black))` }}
            className="flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl text-base font-semibold text-white shadow-sm"
          >
            {restaurantName.charAt(0).toUpperCase()}
          </div>
        )}

        <div className="min-w-0 flex-1">
          <h1 className="font-display truncate text-base font-bold leading-tight tracking-tight text-gray-900 dark:text-white">
            {restaurantName}
          </h1>
          {tableNumber !== undefined && (
            <p className="truncate text-xs leading-tight text-gray-500 dark:text-stone-400">Mesa {tableNumber}</p>
          )}
        </div>

        {activeOrder && (
          <div className="relative" ref={orderPreviewRef}>
            <button
              type="button"
              onClick={() => setIsOrderPreviewOpen((open) => !open)}
              aria-label={`Pedido em andamento: ${activeOrder.paid ? DELIVERY_STATUS_LABELS[activeOrder.status] : 'Aguardando pagamento'}`}
              aria-expanded={isOrderPreviewOpen}
              className="relative flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-gray-500 transition-colors hover:bg-black/5 hover:text-gray-800 dark:text-stone-400 dark:hover:bg-white/10 dark:hover:text-white"
            >
              <ShoppingBag className="h-4 w-4" />
              <span className="absolute top-0.5 right-0.5 flex h-2 w-2">
                <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-brand-500 opacity-75" />
                <span className="relative inline-flex h-2 w-2 rounded-full bg-brand-500" />
              </span>
            </button>

            <AnimatePresence>
              {isOrderPreviewOpen && (
                <motion.div
                  initial={{ opacity: 0, y: -4, scale: 0.97 }}
                  animate={{ opacity: 1, y: 0, scale: 1 }}
                  exit={{ opacity: 0, y: -4, scale: 0.97 }}
                  transition={{ duration: 0.15 }}
                  className="absolute top-full right-0 z-30 mt-2 w-72 overflow-hidden rounded-2xl border border-gray-100 bg-white shadow-lg dark:border-white/10 dark:bg-stone-900"
                >
                  <div className="flex items-center justify-between gap-2 border-b border-gray-100 px-4 py-3 dark:border-white/10">
                    <span className="text-sm font-semibold text-gray-800 dark:text-white">Seu pedido</span>
                    <span
                      className={`inline-flex rounded-full px-2 py-0.5 text-[11px] font-medium ${DELIVERY_STATUS_STYLES[activeOrder.status]}`}
                    >
                      {activeOrder.paid ? DELIVERY_STATUS_LABELS[activeOrder.status] : 'Aguardando pagamento'}
                    </span>
                  </div>

                  {activeOrder.paid && activeOrder.etaMinutes != null && (
                    <div className="flex items-center gap-1.5 px-4 pt-2.5 text-xs font-medium text-gray-500 dark:text-stone-400">
                      <Clock className="h-3.5 w-3.5" />
                      Chegada estimada: ~{activeOrder.etaMinutes} min
                    </div>
                  )}

                  <div className="max-h-40 space-y-1.5 overflow-y-auto px-4 py-3 text-sm">
                    {activeOrder.items.map((item, index) => (
                      <div key={index} className="flex items-center justify-between gap-3 text-gray-600 dark:text-stone-300">
                        <span>
                          <span className="font-medium text-gray-800 dark:text-white">{item.quantity}x</span> {item.productName}
                        </span>
                        <span className="shrink-0 text-gray-500 dark:text-stone-400">
                          {currencyFormatter.format(item.unitPrice * item.quantity)}
                        </span>
                      </div>
                    ))}
                  </div>

                  <div className="flex items-center justify-between border-t border-gray-100 px-4 py-2.5 text-sm font-semibold text-gray-900 dark:border-white/10 dark:text-white">
                    <span>Total</span>
                    <span>{currencyFormatter.format(orderTotal)}</span>
                  </div>

                  <Link
                    to={`/delivery/status/${activeOrder.token}`}
                    onClick={() => setIsOrderPreviewOpen(false)}
                    className="flex items-center justify-center border-t border-gray-100 px-4 py-2.5 text-sm font-semibold text-brand-600 hover:bg-brand-50 dark:border-white/10 dark:text-brand-400 dark:hover:bg-white/5"
                  >
                    Ver pedido completo
                  </Link>
                </motion.div>
              )}
            </AnimatePresence>
          </div>
        )}

        <button
          type="button"
          onClick={onToggleTheme}
          aria-label="Alternar tema"
          className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-gray-500 transition-colors hover:bg-black/5 hover:text-gray-800 dark:text-stone-400 dark:hover:bg-white/10 dark:hover:text-white"
        >
          <AnimatePresence mode="wait" initial={false}>
            <motion.span
              key={theme}
              initial={{ opacity: 0, rotate: -90 }}
              animate={{ opacity: 1, rotate: 0 }}
              exit={{ opacity: 0, rotate: 90 }}
              transition={{ duration: 0.2 }}
              className="flex items-center justify-center"
            >
              {theme === 'dark' ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
            </motion.span>
          </AnimatePresence>
        </button>
      </div>
    </header>
  )
}
