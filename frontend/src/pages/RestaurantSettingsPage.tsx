import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AnimatePresence, motion, type Variants } from 'framer-motion'
import {
  Check,
  ChevronDown,
  ChevronRight,
  Copy,
  ExternalLink,
  FileSpreadsheet,
  MapPin,
  Palette,
  QrCode,
  SlidersHorizontal,
  Store,
} from 'lucide-react'
import { QRCodeCanvas } from 'qrcode.react'
import { useEffect, useState, type ChangeEvent, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { getMyRestaurant, updateMyRestaurant, uploadRestaurantLogo } from '../api/restaurant'
import { useAuth } from '../auth/AuthContext'
import { publicMenuUrl } from '../utils/publicMenuUrl'

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

function SettingsCard({
  icon: Icon,
  title,
  children,
}: {
  icon: typeof Store
  title: string
  children: React.ReactNode
}) {
  return (
    <div className="flex h-full flex-col overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm dark:border-white/10 dark:bg-stone-900">
      <div className="flex items-center gap-2 border-b border-gray-100 px-6 py-4 dark:border-white/10">
        <Icon className="h-5 w-5 text-brand-600 dark:text-brand-400" />
        <h2 className="text-sm font-semibold text-gray-800 dark:text-white">{title}</h2>
      </div>
      <div className="flex-1 p-6">{children}</div>
    </div>
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
      <h1 className="mb-4 flex items-center gap-2 rounded-xl border border-gray-200 bg-white p-4 text-lg font-semibold text-gray-800 shadow-xs dark:border-white/10 dark:bg-stone-900 dark:text-white">
        <Store className="h-5 w-5 text-brand-600 dark:text-brand-400" />
        Configurações do restaurante
      </h1>

      <form onSubmit={handleSubmit}>
        <div className="grid grid-cols-1 items-stretch gap-6 lg:grid-cols-2">
          <SettingsCard icon={Palette} title="Identidade e contato">
            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300">Nome (razão social)</label>
            <input
              type="text"
              disabled
              value={restaurant.name}
              className="mb-4 w-full rounded-md border border-gray-200 bg-gray-50 px-3 py-2 text-sm text-gray-500 dark:border-white/10 dark:bg-white/5 dark:text-stone-400"
            />

            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="tradeName">
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

            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="logo">
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
            {logo ? (
              <img src={logo} alt="" className="mb-5 h-16 w-16 rounded object-cover" />
            ) : (
              <div className="mb-5" />
            )}

            <h3 className="mb-3 flex items-center gap-1.5 text-xs font-semibold tracking-wide text-gray-400 uppercase dark:text-stone-500">
              <MapPin className="h-3.5 w-3.5" />
              Contato
            </h3>

            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="phone">
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

            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="address">
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
          </SettingsCard>

          <SettingsCard icon={QrCode} title="Cardápio digital">
            <div className="flex h-full flex-col">
              <div>
                <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="slug">
                  Slug do cardápio
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
                <div className="mt-6 flex flex-1 flex-col items-center justify-center gap-4 border-t border-gray-200 pt-6 sm:flex-row sm:justify-center dark:border-white/10">
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
          </SettingsCard>

          <div className="lg:col-span-2">
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
                    <div className="grid grid-cols-1 gap-4 p-6 md:grid-cols-2">
                      <div className="rounded-md border border-gray-200 p-3 dark:border-white/10">
                        <p className="mb-1 text-sm font-medium text-gray-700 dark:text-stone-300">Dados fiscais e impressão</p>
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
                          className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none disabled:bg-gray-50 disabled:text-gray-500 dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400 dark:disabled:bg-white/5 dark:disabled:text-stone-500"
                        />

                        <label className="mt-4 flex items-center gap-2 text-sm text-gray-700 dark:text-stone-300">
                          <input
                            type="checkbox"
                            disabled={!canManage}
                            checked={autoPrintKitchenTickets}
                            onChange={(e) => setAutoPrintKitchenTickets(e.target.checked)}
                          />
                          Imprimir comandas de cozinha automaticamente ao enviar pedido
                        </label>
                      </div>

                      <div className="rounded-md border border-gray-200 p-3 dark:border-white/10">
                        <p className="mb-1 text-sm font-medium text-gray-700 dark:text-stone-300">Taxa de serviço</p>
                        <label className="mb-3 flex items-center gap-2 text-xs text-gray-500 dark:text-stone-400">
                          <input
                            type="checkbox"
                            disabled={!canManage}
                            checked={serviceChargeEnabled}
                            onChange={(e) => setServiceChargeEnabled(e.target.checked)}
                          />
                          Cobrar por padrão no fechamento da conta (pode ser removida ou ajustada pelo caixa em cada
                          comanda; o cliente pode recusar).
                        </label>
                        <div>
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

                      <div className="rounded-md border border-gray-200 p-3 dark:border-white/10">
                        <p className="mb-1 text-sm font-medium text-gray-700 dark:text-stone-300">Alerta de demora na cozinha</p>
                        <p className="mb-3 text-xs text-gray-500 dark:text-stone-400">
                          Itens que ficam esperando na cozinha por muito tempo mudam de cor na fila, pra chamar atenção
                          antes que o cliente reclame.
                        </p>
                        <div className="grid grid-cols-2 gap-3">
                          <div>
                            <label className="mb-1 block text-xs font-medium text-gray-600 dark:text-stone-400" htmlFor="warningThreshold">
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
                            <label className="mb-1 block text-xs font-medium text-gray-600 dark:text-stone-400" htmlFor="criticalThreshold">
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

                      <div className="rounded-md border border-gray-200 p-3 dark:border-white/10">
                        <p className="mb-1 text-sm font-medium text-gray-700 dark:text-stone-300">Alerta de mesa esquecida</p>
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

                      <Link
                        to="/dining-areas"
                        className="flex items-center justify-between rounded-md border border-gray-200 p-3 text-sm text-gray-700 hover:bg-gray-50 md:col-span-2 dark:border-white/10 dark:text-stone-300 dark:hover:bg-white/5"
                      >
                        <span className="flex items-center gap-2">
                          <MapPin className="h-4 w-4 shrink-0 text-gray-400 dark:text-stone-500" />
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
                  </motion.div>
                )}
              </AnimatePresence>
            </div>
          </div>
        </div>

        {error && <p className="mt-6 text-sm text-red-600 dark:text-red-400">{error}</p>}
        {success && <p className="mt-6 text-sm text-green-600 dark:text-green-400">Configurações salvas.</p>}

        {canManage && (
          <button
            type="submit"
            disabled={updateMutation.isPending}
            className="mt-6 w-full rounded-md bg-brand-600 px-3 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50 lg:w-auto lg:px-8"
          >
            Salvar
          </button>
        )}
      </form>
    </div>
  )
}
