import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AnimatePresence, motion, type Variants } from 'framer-motion'
import {
  AlertTriangle,
  BellRing,
  Bike,
  Check,
  ChevronDown,
  ChevronRight,
  Clock,
  Copy,
  ExternalLink,
  FileSpreadsheet,
  Flame,
  MapPin,
  Palette,
  Percent,
  Puzzle,
  QrCode,
  Receipt,
  SlidersHorizontal,
  Store,
  Ticket,
  Users,
  type LucideIcon,
} from 'lucide-react'
import { QRCodeCanvas } from 'qrcode.react'
import { useEffect, useState, type ChangeEvent, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { getMyRestaurant, updateMyRestaurant, uploadRestaurantLogo } from '../api/restaurant'
import { useAuth } from '../auth/AuthContext'
import { Button } from '../components/Button'
import { PageHeader } from '../components/PageHeader'
import { PixIntegrationCard } from '../components/PixIntegrationCard'
import { SectionTabs } from '../components/SectionTabs'
import { Toggle } from '../components/Toggle'
import { publicMenuUrl } from '../utils/publicMenuUrl'

const MANAGEMENT_TABS = [
  { to: '/settings', label: 'Geral', icon: Store },
  { to: '/coupons', label: 'Cupons', icon: Ticket },
  { to: '/happy-hour', label: 'Happy Hour', icon: Clock },
  { to: '/delivery-zones', label: 'Entrega', icon: Bike },
  { to: '/staff', label: 'Funcionários', icon: Users },
]

const EASE_OUT: [number, number, number, number] = [0.16, 1, 0.3, 1]

const advancedPanelVariants: Variants = {
  collapsed: {
    height: 0,
    opacity: 0,
    transition: { duration: 0.25, ease: EASE_OUT },
  },
  expanded: {
    height: 'auto',
    opacity: 1,
    transition: { duration: 0.3, ease: EASE_OUT },
  },
}

type Tint = 'gold' | 'wine' | 'sage' | 'teal' | 'brand'

const TINT_STYLES: Record<Tint, { badge: string; pill: string }> = {
  gold: {
    badge: 'bg-gold-100 text-gold-700 dark:bg-gold-500/15 dark:text-gold-400',
    pill: 'bg-gold-100 text-gold-700 dark:bg-gold-500/15 dark:text-gold-400',
  },
  wine: {
    badge: 'bg-wine-100 text-wine-700 dark:bg-wine-500/15 dark:text-wine-400',
    pill: 'bg-wine-100 text-wine-700 dark:bg-wine-500/15 dark:text-wine-400',
  },
  sage: {
    badge: 'bg-sage-100 text-sage-700 dark:bg-sage-500/15 dark:text-sage-400',
    pill: 'bg-sage-100 text-sage-700 dark:bg-sage-500/15 dark:text-sage-400',
  },
  teal: {
    badge: 'bg-teal-100 text-teal-700 dark:bg-teal-500/15 dark:text-teal-400',
    pill: 'bg-teal-100 text-teal-700 dark:bg-teal-500/15 dark:text-teal-400',
  },
  brand: {
    badge: 'bg-brand-100 text-brand-700 dark:bg-brand-500/15 dark:text-brand-400',
    pill: 'bg-brand-100 text-brand-700 dark:bg-brand-500/15 dark:text-brand-400',
  },
}

function IconBadge({ icon: Icon, tint }: { icon: LucideIcon; tint: Tint }) {
  return (
    <span className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-lg ${TINT_STYLES[tint].badge}`}>
      <Icon className="h-4 w-4" />
    </span>
  )
}

function GroupLabel({ icon: Icon, tint, children }: { icon: LucideIcon; tint: Tint; children: React.ReactNode }) {
  return (
    <span
      className={`mb-3 inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold tracking-wide uppercase ${TINT_STYLES[tint].pill}`}
    >
      <Icon className="h-3 w-3" />
      {children}
    </span>
  )
}

export function RestaurantSettingsPage() {
  const { user, updateRestaurant } = useAuth()
  const canManage = user?.role === 'OWNER' || user?.role === 'MANAGER'
  const queryClient = useQueryClient()

  const { data: restaurant, isLoading } = useQuery({
    queryKey: ['restaurant'],
    queryFn: getMyRestaurant,
  })

  const [cnpj, setCnpj] = useState('')
  const [tradeName, setTradeName] = useState('')
  const [slug, setSlug] = useState('')
  const [logo, setLogo] = useState('')
  const [phone, setPhone] = useState('')
  const [address, setAddress] = useState('')
  const [autoPrintKitchenTickets, setAutoPrintKitchenTickets] = useState(false)
  const [kitchenWarningThresholdMinutes, setKitchenWarningThresholdMinutes] = useState('10')
  const [kitchenCriticalThresholdMinutes, setKitchenCriticalThresholdMinutes] = useState('20')
  const [tableForgottenWarningThresholdMinutes, setTableForgottenWarningThresholdMinutes] = useState('30')
  const [tableForgottenCriticalThresholdMinutes, setTableForgottenCriticalThresholdMinutes] = useState('60')
  const [serviceChargeEnabled, setServiceChargeEnabled] = useState(true)
  const [serviceChargePercentage, setServiceChargePercentage] = useState('10')
  const [isUploadingLogo, setIsUploadingLogo] = useState(false)
  const [linkCopied, setLinkCopied] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState(false)
  const [showAdvanced, setShowAdvanced] = useState(false)

  useEffect(() => {
    if (!restaurant) return
    setCnpj(restaurant.cnpj ?? '')
    setTradeName(restaurant.tradeName ?? '')
    setSlug(restaurant.slug ?? '')
    setLogo(restaurant.logo ?? '')
    setPhone(restaurant.phone ?? '')
    setAddress(restaurant.address ?? '')
    setAutoPrintKitchenTickets(restaurant.autoPrintKitchenTickets)
    setKitchenWarningThresholdMinutes(String(restaurant.kitchenWarningThresholdMinutes))
    setKitchenCriticalThresholdMinutes(String(restaurant.kitchenCriticalThresholdMinutes))
    setTableForgottenWarningThresholdMinutes(String(restaurant.tableForgottenWarningThresholdMinutes))
    setTableForgottenCriticalThresholdMinutes(String(restaurant.tableForgottenCriticalThresholdMinutes))
    setServiceChargeEnabled(restaurant.serviceChargeEnabled)
    setServiceChargePercentage(String(restaurant.serviceChargePercentage))
  }, [restaurant])

  const updateMutation = useMutation({
    mutationFn: updateMyRestaurant,
    onSuccess: (updated) => {
      queryClient.setQueryData(['restaurant'], updated)
      updateRestaurant({ tradeName: updated.tradeName, logo: updated.logo })
      setSuccess(true)
      setTimeout(() => setSuccess(false), 3000)
    },
    onError: () => setError('Não foi possível salvar. Verifique os dados e tente novamente.'),
  })

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)

    const warningMinutes = Number(kitchenWarningThresholdMinutes)
    const criticalMinutes = Number(kitchenCriticalThresholdMinutes)
    if (criticalMinutes <= warningMinutes) {
      setError('O tempo crítico precisa ser maior que o tempo de aviso.')
      return
    }

    const tableForgottenWarningMinutes = Number(tableForgottenWarningThresholdMinutes)
    const tableForgottenCriticalMinutes = Number(tableForgottenCriticalThresholdMinutes)
    if (tableForgottenCriticalMinutes <= tableForgottenWarningMinutes) {
      setError('O tempo crítico do alerta de mesa esquecida precisa ser maior que o tempo de aviso.')
      return
    }

    updateMutation.mutate({
      cnpj: cnpj || undefined,
      tradeName: tradeName || undefined,
      slug: slug || undefined,
      logo: logo || undefined,
      phone: phone || undefined,
      address: address || undefined,
      autoPrintKitchenTickets,
      kitchenWarningThresholdMinutes: warningMinutes,
      kitchenCriticalThresholdMinutes: criticalMinutes,
      tableForgottenWarningThresholdMinutes: tableForgottenWarningMinutes,
      tableForgottenCriticalThresholdMinutes: tableForgottenCriticalMinutes,
      serviceChargeEnabled,
      serviceChargePercentage: Number(serviceChargePercentage),
    })
  }

  async function handleLogoFileChange(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) return

    setIsUploadingLogo(true)
    setError(null)
    try {
      const url = await uploadRestaurantLogo(file)
      setLogo(url)
    } catch {
      setError('Não foi possível enviar a imagem. Tente novamente.')
    } finally {
      setIsUploadingLogo(false)
    }
  }

  if (isLoading || !restaurant) {
    return <p className="text-sm text-gray-500 dark:text-stone-400">Carregando...</p>
  }

  const menuUrl = restaurant.slug ? publicMenuUrl(restaurant.slug) : null

  return (
    <div>
      <SectionTabs tabs={MANAGEMENT_TABS} />

      <div className="mb-5 rounded-b-2xl border border-gray-200 bg-white p-5 shadow-sm dark:border-white/10 dark:bg-stone-900">
        <PageHeader icon={Store} title="Configurações do restaurante" />
      </div>

      <form onSubmit={handleSubmit}>
        <div className="flex flex-col gap-6">
          <div>
            <GroupLabel icon={Palette} tint="brand">
              Identidade e contato
            </GroupLabel>
            <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
              <div className="rounded-lg border border-gray-200 p-4 transition-shadow hover:shadow-md dark:border-white/10">
                <div className="mb-3 flex items-center gap-2.5">
                  <IconBadge icon={Store} tint="brand" />
                  <p className="text-sm font-medium text-gray-700 dark:text-stone-300">Dados do restaurante</p>
                </div>

                <label className="mb-1 block text-xs font-medium text-gray-600 dark:text-stone-400">
                  Nome (razão social)
                </label>
                <input
                  type="text"
                  disabled
                  value={restaurant.name}
                  className="mb-4 w-full rounded-md border border-gray-200 bg-gray-50 px-3 py-2 text-sm text-gray-500 dark:border-white/10 dark:bg-white/5 dark:text-stone-400"
                />

                <label className="mb-1 block text-xs font-medium text-gray-600 dark:text-stone-400" htmlFor="tradeName">
                  Nome fantasia
                </label>
                <input
                  id="tradeName"
                  type="text"
                  disabled={!canManage}
                  maxLength={100}
                  value={tradeName}
                  onChange={(e) => setTradeName(e.target.value)}
                  className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none disabled:bg-gray-50 disabled:text-gray-500 dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400 dark:disabled:bg-white/5 dark:disabled:text-stone-500"
                />

                <label className="mb-1 block text-xs font-medium text-gray-600 dark:text-stone-400" htmlFor="logo">
                  Logo
                </label>
                <div className="mb-1 flex flex-col gap-2 sm:flex-row">
                  <input
                    id="logo"
                    type="text"
                    placeholder="Cole uma URL..."
                    disabled={!canManage}
                    maxLength={255}
                    value={logo}
                    onChange={(e) => setLogo(e.target.value)}
                    className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none disabled:bg-gray-50 disabled:text-gray-500 dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400 dark:disabled:bg-white/5 dark:disabled:text-stone-500"
                  />
                  {canManage && (
                    <label className="flex shrink-0 cursor-pointer items-center justify-center rounded-md border border-gray-300 px-3 py-2 text-sm text-gray-700 hover:bg-gray-100 dark:border-white/10 dark:text-stone-300 dark:hover:bg-white/5">
                      {isUploadingLogo ? 'Enviando...' : 'Enviar do dispositivo'}
                      <input
                        type="file"
                        accept="image/jpeg,image/png,image/webp"
                        className="hidden"
                        onChange={handleLogoFileChange}
                        disabled={isUploadingLogo}
                      />
                    </label>
                  )}
                </div>
                {logo && <img src={logo} alt="" className="h-16 w-16 rounded object-cover" />}
              </div>

              <div className="rounded-lg border border-gray-200 p-4 transition-shadow hover:shadow-md dark:border-white/10">
                <div className="mb-3 flex items-center gap-2.5">
                  <IconBadge icon={MapPin} tint="brand" />
                  <p className="text-sm font-medium text-gray-700 dark:text-stone-300">Contato</p>
                </div>

                <label className="mb-1 block text-xs font-medium text-gray-600 dark:text-stone-400" htmlFor="phone">
                  Telefone
                </label>
                <input
                  id="phone"
                  type="text"
                  disabled={!canManage}
                  maxLength={20}
                  value={phone}
                  onChange={(e) => setPhone(e.target.value)}
                  className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none disabled:bg-gray-50 disabled:text-gray-500 dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400 dark:disabled:bg-white/5 dark:disabled:text-stone-500"
                />

                <label className="mb-1 block text-xs font-medium text-gray-600 dark:text-stone-400" htmlFor="address">
                  Endereço
                </label>
                <input
                  id="address"
                  type="text"
                  disabled={!canManage}
                  maxLength={255}
                  value={address}
                  onChange={(e) => setAddress(e.target.value)}
                  className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none disabled:bg-gray-50 disabled:text-gray-500 dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400 dark:disabled:bg-white/5 dark:disabled:text-stone-500"
                />
              </div>
            </div>
          </div>

          <div>
            <GroupLabel icon={QrCode} tint="teal">
              Cardápio digital
            </GroupLabel>
            <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
              <div className="rounded-lg border border-gray-200 p-4 transition-shadow hover:shadow-md dark:border-white/10">
                <div className="mb-3 flex items-center gap-2.5">
                  <IconBadge icon={QrCode} tint="teal" />
                  <p className="text-sm font-medium text-gray-700 dark:text-stone-300">Slug do cardápio</p>
                </div>

                <label className="mb-1 block text-xs font-medium text-gray-600 dark:text-stone-400" htmlFor="slug">
                  Slug
                </label>
                <input
                  id="slug"
                  type="text"
                  disabled={!canManage}
                  maxLength={150}
                  placeholder="meu-restaurante"
                  value={slug}
                  onChange={(e) => setSlug(e.target.value.toLowerCase())}
                  className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none disabled:bg-gray-50 disabled:text-gray-500 dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400 dark:disabled:bg-white/5 dark:disabled:text-stone-500"
                />
                <p className="mt-1 text-xs text-gray-500 dark:text-stone-400">
                  Só letras minúsculas, números e hífen. Link do cardápio: {window.location.origin}/menu/
                  {slug || '...'}
                </p>

                {canManage && (
                  <Link
                    to="/products/import"
                    className="mt-5 flex items-center justify-between rounded-md border border-gray-200 p-3 text-sm text-gray-700 hover:bg-gray-50 dark:border-white/10 dark:text-stone-300 dark:hover:bg-white/5"
                  >
                    <span className="flex items-center gap-2">
                      <FileSpreadsheet className="h-4 w-4 shrink-0 text-gray-400 dark:text-stone-500" />
                      <span>
                        <span className="block font-medium">Importar cardápio</span>
                        <span className="block text-xs text-gray-500 dark:text-stone-400">
                          Cadastre categorias e produtos em lote a partir de uma planilha Excel
                        </span>
                      </span>
                    </span>
                    <ChevronRight className="h-4 w-4 shrink-0 text-gray-400 dark:text-stone-500" />
                  </Link>
                )}
              </div>

              {menuUrl && (
                <div className="flex flex-col items-center justify-center gap-4 rounded-lg border border-gray-200 p-4 transition-shadow hover:shadow-md sm:flex-row dark:border-white/10">
                  <div className="rounded-xl border border-gray-200 bg-white p-3 shadow-md">
                    <QRCodeCanvas value={menuUrl} size={140} />
                  </div>
                  <div className="flex max-w-xs flex-col items-center gap-3">
                    <p className="text-center text-xs text-gray-400 dark:text-stone-500">
                      Use pra divulgar o cardápio nas redes sociais, WhatsApp ou no site. Pra autoatendimento nas mesas,
                      gere o QR de cada mesa em Mesas.
                    </p>
                    <div className="flex w-full gap-2">
                      <a
                        href={menuUrl}
                        target="_blank"
                        rel="noreferrer"
                        className="flex flex-1 items-center justify-center gap-1.5 rounded-md bg-brand-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-brand-700"
                      >
                        <ExternalLink className="h-4 w-4" />
                        Abrir
                      </a>
                      <button
                        type="button"
                        onClick={() => {
                          navigator.clipboard.writeText(menuUrl)
                          setLinkCopied(true)
                          setTimeout(() => setLinkCopied(false), 2000)
                        }}
                        className="flex flex-1 items-center justify-center gap-1.5 rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-100 dark:border-white/10 dark:text-stone-300 dark:hover:bg-white/5"
                      >
                        {linkCopied ? <Check className="h-4 w-4 text-green-600 dark:text-green-400" /> : <Copy className="h-4 w-4" />}
                        {linkCopied ? 'Copiado!' : 'Copiar'}
                      </button>
                    </div>
                  </div>
                </div>
              )}
            </div>
          </div>

          <div>
            <div className="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm dark:border-white/10 dark:bg-stone-900">
              <button
                type="button"
                onClick={() => setShowAdvanced((prev) => !prev)}
                className="flex w-full items-center justify-between gap-2 px-6 py-4 text-left hover:bg-gray-50 dark:hover:bg-white/5"
              >
                <span className="flex items-center gap-2">
                  <SlidersHorizontal className="h-5 w-5 text-brand-600 dark:text-brand-400" />
                  <h2 className="text-sm font-semibold text-gray-800 dark:text-white">Configurações avançadas</h2>
                </span>
                <motion.span
                  animate={{ rotate: showAdvanced ? 180 : 0 }}
                  transition={{ duration: 0.35, ease: EASE_OUT }}
                >
                  <ChevronDown className="h-4 w-4 text-gray-400 dark:text-stone-500" />
                </motion.span>
              </button>

              <AnimatePresence initial={false}>
                {showAdvanced && (
                  <motion.div
                    initial="collapsed"
                    animate="expanded"
                    exit="collapsed"
                    variants={advancedPanelVariants}
                    className="overflow-hidden border-t border-gray-100 dark:border-white/10"
                  >
                    <div className="flex flex-col gap-6 p-6">
                      <div>
                        <GroupLabel icon={Receipt} tint="gold">
                          Fiscal e cobrança
                        </GroupLabel>
                        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                          <div className="rounded-lg border border-gray-200 p-4 transition-shadow hover:shadow-md dark:border-white/10">
                            <div className="mb-1 flex items-center gap-2.5">
                              <IconBadge icon={Receipt} tint="gold" />
                              <p className="text-sm font-medium text-gray-700 dark:text-stone-300">Dados fiscais e impressão</p>
                            </div>
                            <p className="mb-3 text-xs text-gray-500 dark:text-stone-400">
                              Usados na nota do cliente e no fluxo de envio de pedidos pra cozinha.
                            </p>

                            <label className="mb-1 block text-xs font-medium text-gray-600 dark:text-stone-400" htmlFor="cnpj">
                              CNPJ
                            </label>
                            <input
                              id="cnpj"
                              type="text"
                              disabled={!canManage}
                              maxLength={20}
                              value={cnpj}
                              onChange={(e) => setCnpj(e.target.value)}
                              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none disabled:bg-gray-50 disabled:text-gray-500 dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400 dark:disabled:bg-white/5 dark:disabled:text-stone-500"
                            />

                            <div className="flex items-center justify-between gap-3 rounded-lg border border-gray-200 px-3 py-2.5 dark:border-white/10">
                              <span className="text-sm font-medium text-gray-700 dark:text-stone-300">Impressão automática</span>
                              <Toggle
                                checked={autoPrintKitchenTickets}
                                onChange={setAutoPrintKitchenTickets}
                                disabled={!canManage}
                              />
                            </div>
                            <p className="mt-1.5 text-xs text-gray-500 dark:text-stone-400">
                              Imprimir comandas de cozinha automaticamente ao enviar pedido.
                            </p>
                          </div>

                          <div className="rounded-lg border border-gray-200 p-4 transition-shadow hover:shadow-md dark:border-white/10">
                            <div className="mb-3 flex items-center gap-2.5">
                              <IconBadge icon={Percent} tint="gold" />
                              <p className="text-sm font-medium text-gray-700 dark:text-stone-300">Taxa de serviço</p>
                            </div>

                            <div className="flex items-center justify-between gap-3 rounded-lg border border-gray-200 px-3 py-2.5 dark:border-white/10">
                              <span className="text-sm font-medium text-gray-700 dark:text-stone-300">Cobrar por padrão</span>
                              <Toggle
                                checked={serviceChargeEnabled}
                                onChange={setServiceChargeEnabled}
                                disabled={!canManage}
                              />
                            </div>
                            <p className="mt-1.5 mb-3 text-xs text-gray-500 dark:text-stone-400">
                              No fechamento da conta (pode ser removida ou ajustada pelo caixa em cada comanda; o cliente
                              pode recusar).
                            </p>

                            <label
                              className="mb-1 block text-xs font-medium text-gray-600 dark:text-stone-400"
                              htmlFor="serviceChargePercentage"
                            >
                              Percentual (%)
                            </label>
                            <input
                              id="serviceChargePercentage"
                              type="number"
                              min={0}
                              max={100}
                              step="0.01"
                              disabled={!canManage}
                              value={serviceChargePercentage}
                              onChange={(e) => setServiceChargePercentage(e.target.value)}
                              className="w-full max-w-[140px] rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none disabled:bg-gray-50 disabled:text-gray-500 dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400 dark:disabled:bg-white/5 dark:disabled:text-stone-500"
                            />
                          </div>
                        </div>
                      </div>

                      <div>
                        <GroupLabel icon={AlertTriangle} tint="wine">
                          Alertas operacionais
                        </GroupLabel>
                        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                          <div className="rounded-lg border border-gray-200 p-4 transition-shadow hover:shadow-md dark:border-white/10">
                            <div className="mb-1 flex items-center gap-2.5">
                              <IconBadge icon={Flame} tint="wine" />
                              <p className="text-sm font-medium text-gray-700 dark:text-stone-300">Alerta de demora na cozinha</p>
                            </div>
                            <p className="mb-3 text-xs text-gray-500 dark:text-stone-400">
                              Itens que ficam esperando na cozinha por muito tempo mudam de cor na fila, pra chamar
                              atenção antes que o cliente reclame.
                            </p>
                            <div className="grid grid-cols-2 gap-3">
                              <div>
                                <label
                                  className="mb-1 block text-xs font-medium text-gray-600 dark:text-stone-400"
                                  htmlFor="warningThreshold"
                                >
                                  Aviso (min)
                                </label>
                                <input
                                  id="warningThreshold"
                                  type="number"
                                  min={1}
                                  disabled={!canManage}
                                  value={kitchenWarningThresholdMinutes}
                                  onChange={(e) => setKitchenWarningThresholdMinutes(e.target.value)}
                                  className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none disabled:bg-gray-50 disabled:text-gray-500 dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400 dark:disabled:bg-white/5 dark:disabled:text-stone-500"
                                />
                              </div>
                              <div>
                                <label
                                  className="mb-1 block text-xs font-medium text-gray-600 dark:text-stone-400"
                                  htmlFor="criticalThreshold"
                                >
                                  Crítico (min)
                                </label>
                                <input
                                  id="criticalThreshold"
                                  type="number"
                                  min={1}
                                  disabled={!canManage}
                                  value={kitchenCriticalThresholdMinutes}
                                  onChange={(e) => setKitchenCriticalThresholdMinutes(e.target.value)}
                                  className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none disabled:bg-gray-50 disabled:text-gray-500 dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400 dark:disabled:bg-white/5 dark:disabled:text-stone-500"
                                />
                              </div>
                            </div>
                          </div>

                          <div className="rounded-lg border border-gray-200 p-4 transition-shadow hover:shadow-md dark:border-white/10">
                            <div className="mb-1 flex items-center gap-2.5">
                              <IconBadge icon={BellRing} tint="wine" />
                              <p className="text-sm font-medium text-gray-700 dark:text-stone-300">Alerta de mesa esquecida</p>
                            </div>
                            <p className="mb-3 text-xs text-gray-500 dark:text-stone-400">
                              Mesa ocupada há muito tempo sem pedido novo avisa o garçom, pra ninguém ficar esquecido.
                            </p>
                            <div className="grid grid-cols-2 gap-3">
                              <div>
                                <label
                                  className="mb-1 block text-xs font-medium text-gray-600 dark:text-stone-400"
                                  htmlFor="tableForgottenWarningThreshold"
                                >
                                  Aviso (min)
                                </label>
                                <input
                                  id="tableForgottenWarningThreshold"
                                  type="number"
                                  min={1}
                                  disabled={!canManage}
                                  value={tableForgottenWarningThresholdMinutes}
                                  onChange={(e) => setTableForgottenWarningThresholdMinutes(e.target.value)}
                                  className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none disabled:bg-gray-50 disabled:text-gray-500 dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400 dark:disabled:bg-white/5 dark:disabled:text-stone-500"
                                />
                              </div>
                              <div>
                                <label
                                  className="mb-1 block text-xs font-medium text-gray-600 dark:text-stone-400"
                                  htmlFor="tableForgottenCriticalThreshold"
                                >
                                  Crítico (min)
                                </label>
                                <input
                                  id="tableForgottenCriticalThreshold"
                                  type="number"
                                  min={1}
                                  disabled={!canManage}
                                  value={tableForgottenCriticalThresholdMinutes}
                                  onChange={(e) => setTableForgottenCriticalThresholdMinutes(e.target.value)}
                                  className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none disabled:bg-gray-50 disabled:text-gray-500 dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400 dark:disabled:bg-white/5 dark:disabled:text-stone-500"
                                />
                              </div>
                            </div>
                          </div>
                        </div>
                      </div>

                      <div>
                        <GroupLabel icon={Puzzle} tint="sage">
                          Integrações e organização
                        </GroupLabel>
                        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                          <PixIntegrationCard canManage={canManage} />

                          <Link
                            to="/dining-areas"
                            className="flex items-center justify-between rounded-lg border border-gray-200 p-4 text-sm text-gray-700 transition-shadow hover:bg-gray-50 hover:shadow-md dark:border-white/10 dark:text-stone-300 dark:hover:bg-white/5"
                          >
                            <span className="flex items-center gap-2.5">
                              <IconBadge icon={MapPin} tint="sage" />
                              <span>
                                <span className="block font-medium">Áreas do salão</span>
                                <span className="block text-xs text-gray-500 dark:text-stone-400">
                                  Agrupe as mesas por ambiente (salão, varanda, deck...)
                                </span>
                              </span>
                            </span>
                            <ChevronRight className="h-4 w-4 shrink-0 text-gray-400 dark:text-stone-500" />
                          </Link>
                        </div>
                      </div>
                    </div>
                  </motion.div>
                )}
              </AnimatePresence>
            </div>
          </div>
        </div>

        {error && <p className="mt-6 text-sm text-wine-600 dark:text-wine-400">{error}</p>}
        {success && <p className="mt-6 text-sm text-green-600 dark:text-green-400">Configurações salvas.</p>}

        {canManage && (
          <Button type="submit" disabled={updateMutation.isPending} className="mt-6 w-full lg:w-auto lg:px-8">
            Salvar
          </Button>
        )}
      </form>
    </div>
  )
}
