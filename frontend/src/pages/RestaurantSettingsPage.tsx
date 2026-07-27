import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AnimatePresence, motion } from 'framer-motion'
import { ChevronDown, MapPin, Palette, QrCode, SlidersHorizontal, Store } from 'lucide-react'
import { useEffect, useState, type ChangeEvent, type FormEvent } from 'react'
import { getMyRestaurant, updateMyRestaurant, uploadRestaurantLogo } from '../api/restaurant'
import { useAuth } from '../auth/AuthContext'
import { QrCodeCard } from '../components/QrCodeCard'
import { publicMenuUrl } from '../utils/publicMenuUrl'

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
  const [isUploadingLogo, setIsUploadingLogo] = useState(false)
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
    return <p className="text-sm text-gray-500">Carregando...</p>
  }

  return (
    <div>
      <h1 className="mb-4 flex items-center gap-2 text-lg font-semibold text-gray-800">
        <Store className="h-5 w-5 text-brand-600" />
        Configurações do restaurante
      </h1>

      <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,28rem)_minmax(0,22rem)]">
        <form onSubmit={handleSubmit} className="rounded-xl border border-gray-200 bg-white p-6 shadow-sm">
          <label className="mb-1 block text-sm font-medium text-gray-700">Nome (razão social)</label>
          <input
            type="text"
            disabled
            value={restaurant.name}
            className="mb-4 w-full rounded-md border border-gray-200 bg-gray-50 px-3 py-2 text-sm text-gray-500"
          />

          <h2 className="mb-3 flex items-center gap-1.5 text-xs font-semibold tracking-wide text-gray-400 uppercase">
            <Palette className="h-3.5 w-3.5" />
            Identidade
          </h2>

          <label className="mb-1 block text-sm font-medium text-gray-700" htmlFor="tradeName">
            Nome fantasia
          </label>
          <input
            id="tradeName"
            type="text"
            disabled={!canManage}
            maxLength={100}
            value={tradeName}
            onChange={(e) => setTradeName(e.target.value)}
            className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none disabled:bg-gray-50 disabled:text-gray-500"
          />

          <label className="mb-1 block text-sm font-medium text-gray-700" htmlFor="logo">
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
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none disabled:bg-gray-50 disabled:text-gray-500"
            />
            {canManage && (
              <label className="flex shrink-0 cursor-pointer items-center justify-center rounded-md border border-gray-300 px-3 py-2 text-sm text-gray-700 hover:bg-gray-100">
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

          <h2 className="mb-3 flex items-center gap-1.5 text-xs font-semibold tracking-wide text-gray-400 uppercase">
            <MapPin className="h-3.5 w-3.5" />
            Contato
          </h2>

          <label className="mb-1 block text-sm font-medium text-gray-700" htmlFor="phone">
            Telefone
          </label>
          <input
            id="phone"
            type="text"
            disabled={!canManage}
            maxLength={20}
            value={phone}
            onChange={(e) => setPhone(e.target.value)}
            className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none disabled:bg-gray-50 disabled:text-gray-500"
          />

          <label className="mb-1 block text-sm font-medium text-gray-700" htmlFor="address">
            Endereço
          </label>
          <input
            id="address"
            type="text"
            disabled={!canManage}
            maxLength={255}
            value={address}
            onChange={(e) => setAddress(e.target.value)}
            className="mb-5 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none disabled:bg-gray-50 disabled:text-gray-500"
          />

          <h2 className="mb-3 flex items-center gap-1.5 text-xs font-semibold tracking-wide text-gray-400 uppercase">
            <QrCode className="h-3.5 w-3.5" />
            Cardápio digital
          </h2>

          <label className="mb-1 block text-sm font-medium text-gray-700" htmlFor="slug">
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
            className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none disabled:bg-gray-50 disabled:text-gray-500"
          />
          <p className="mb-5 mt-1 text-xs text-gray-500">
            Só letras minúsculas, números e hífen. Link do cardápio: {window.location.origin}/menu/{slug || '...'}
          </p>

          <button
            type="button"
            onClick={() => setShowAdvanced((prev) => !prev)}
            className="mb-2 flex w-full items-center justify-between rounded-md border border-gray-200 px-3 py-2 text-sm text-gray-600 hover:bg-gray-50"
          >
            <span className="flex items-center gap-1.5">
              <SlidersHorizontal className="h-4 w-4" />
              Configurações avançadas
            </span>
            <motion.span animate={{ rotate: showAdvanced ? 180 : 0 }} transition={{ duration: 0.2 }}>
              <ChevronDown className="h-4 w-4" />
            </motion.span>
          </button>

          <AnimatePresence initial={false}>
            {showAdvanced && (
              <motion.div
                initial={{ height: 0, opacity: 0 }}
                animate={{ height: 'auto', opacity: 1 }}
                exit={{ height: 0, opacity: 0 }}
                transition={{ duration: 0.2 }}
                className="overflow-hidden"
              >
                <div className="mt-4 space-y-4">
                  <div>
                    <label className="mb-1 block text-sm font-medium text-gray-700" htmlFor="cnpj">
                      CNPJ
                    </label>
                    <input
                      id="cnpj"
                      type="text"
                      disabled={!canManage}
                      maxLength={20}
                      value={cnpj}
                      onChange={(e) => setCnpj(e.target.value)}
                      className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none disabled:bg-gray-50 disabled:text-gray-500"
                    />
                  </div>

                  <label className="flex items-center gap-2 text-sm text-gray-700">
                    <input
                      type="checkbox"
                      disabled={!canManage}
                      checked={autoPrintKitchenTickets}
                      onChange={(e) => setAutoPrintKitchenTickets(e.target.checked)}
                    />
                    Imprimir comandas de cozinha automaticamente ao enviar pedido
                  </label>

                  <div className="rounded-md border border-gray-200 p-3">
                    <p className="mb-1 text-sm font-medium text-gray-700">Alerta de demora na cozinha</p>
                    <p className="mb-3 text-xs text-gray-500">
                      Itens que ficam esperando na cozinha por muito tempo mudam de cor na fila, pra chamar atenção
                      antes que o cliente reclame.
                    </p>
                    <div className="grid grid-cols-2 gap-3">
                      <div>
                        <label className="mb-1 block text-xs font-medium text-gray-600" htmlFor="warningThreshold">
                          Aviso (min)
                        </label>
                        <input
                          id="warningThreshold"
                          type="number"
                          min={1}
                          disabled={!canManage}
                          value={kitchenWarningThresholdMinutes}
                          onChange={(e) => setKitchenWarningThresholdMinutes(e.target.value)}
                          className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none disabled:bg-gray-50 disabled:text-gray-500"
                        />
                      </div>
                      <div>
                        <label className="mb-1 block text-xs font-medium text-gray-600" htmlFor="criticalThreshold">
                          Crítico (min)
                        </label>
                        <input
                          id="criticalThreshold"
                          type="number"
                          min={1}
                          disabled={!canManage}
                          value={kitchenCriticalThresholdMinutes}
                          onChange={(e) => setKitchenCriticalThresholdMinutes(e.target.value)}
                          className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none disabled:bg-gray-50 disabled:text-gray-500"
                        />
                      </div>
                    </div>
                  </div>
                </div>
              </motion.div>
            )}
          </AnimatePresence>

          {error && <p className="mt-4 mb-4 text-sm text-red-600">{error}</p>}
          {success && <p className="mt-4 mb-4 text-sm text-green-600">Configurações salvas.</p>}

          {canManage && (
            <button
              type="submit"
              disabled={updateMutation.isPending}
              className="w-full rounded-md bg-brand-600 px-3 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50"
            >
              Salvar
            </button>
          )}
        </form>

        {restaurant.slug && (
          <QrCodeCard
            title="Cardápio digital"
            url={publicMenuUrl(restaurant.slug)}
            helperText="Use pra divulgar o cardápio nas redes sociais, WhatsApp ou no site. Pra autoatendimento nas mesas, gere o QR de cada mesa em Mesas."
          />
        )}
      </div>
    </div>
  )
}
