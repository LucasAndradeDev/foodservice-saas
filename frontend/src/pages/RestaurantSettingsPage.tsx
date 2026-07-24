import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Check, Copy, ExternalLink, QrCode } from 'lucide-react'
import { QRCodeCanvas } from 'qrcode.react'
import { useEffect, useState, type FormEvent } from 'react'
import { getMyRestaurant, updateMyRestaurant } from '../api/restaurant'
import { useAuth } from '../auth/AuthContext'

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
  const [linkCopied, setLinkCopied] = useState(false)
  const [logo, setLogo] = useState('')
  const [primaryColor, setPrimaryColor] = useState('')
  const [phone, setPhone] = useState('')
  const [address, setAddress] = useState('')
  const [autoPrintKitchenTickets, setAutoPrintKitchenTickets] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState(false)

  useEffect(() => {
    if (!restaurant) return
    setCnpj(restaurant.cnpj ?? '')
    setTradeName(restaurant.tradeName ?? '')
    setSlug(restaurant.slug ?? '')
    setLogo(restaurant.logo ?? '')
    setPrimaryColor(restaurant.primaryColor ?? '')
    setPhone(restaurant.phone ?? '')
    setAddress(restaurant.address ?? '')
    setAutoPrintKitchenTickets(restaurant.autoPrintKitchenTickets)
  }, [restaurant])

  const updateMutation = useMutation({
    mutationFn: updateMyRestaurant,
    onSuccess: (updated) => {
      queryClient.setQueryData(['restaurant'], updated)
      updateRestaurant({ tradeName: updated.tradeName, logo: updated.logo, primaryColor: updated.primaryColor })
      setSuccess(true)
      setTimeout(() => setSuccess(false), 3000)
    },
    onError: () => setError('Não foi possível salvar. Verifique os dados e tente novamente.'),
  })

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    updateMutation.mutate({
      cnpj: cnpj || undefined,
      tradeName: tradeName || undefined,
      slug: slug || undefined,
      logo: logo || undefined,
      primaryColor: primaryColor || undefined,
      phone: phone || undefined,
      address: address || undefined,
      autoPrintKitchenTickets,
    })
  }

  if (isLoading || !restaurant) {
    return <p className="text-sm text-gray-500">Carregando...</p>
  }

  return (
    <div>
      <h1 className="mb-4 text-lg font-semibold text-gray-800">Configurações do restaurante</h1>

      <form onSubmit={handleSubmit} className="max-w-lg rounded-lg border border-gray-200 bg-white p-6">
        <label className="mb-1 block text-sm font-medium text-gray-700">Nome (razão social)</label>
        <input
          type="text"
          disabled
          value={restaurant.name}
          className="mb-4 w-full rounded-md border border-gray-200 bg-gray-50 px-3 py-2 text-sm text-gray-500"
        />

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
          className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none disabled:bg-gray-50 disabled:text-gray-500"
        />

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
        <p className="mb-4 mt-1 text-xs text-gray-500">
          Só letras minúsculas, números e hífen. Link do cardápio: {window.location.origin}/menu/{slug || '...'}
        </p>

        <label className="mb-1 block text-sm font-medium text-gray-700" htmlFor="logo">
          Logo (URL)
        </label>
        <input
          id="logo"
          type="text"
          disabled={!canManage}
          maxLength={255}
          value={logo}
          onChange={(e) => setLogo(e.target.value)}
          className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none disabled:bg-gray-50 disabled:text-gray-500"
        />

        <label className="mb-1 block text-sm font-medium text-gray-700" htmlFor="primaryColor">
          Cor principal
        </label>
        <input
          id="primaryColor"
          type="text"
          disabled={!canManage}
          maxLength={20}
          placeholder="#000000"
          value={primaryColor}
          onChange={(e) => setPrimaryColor(e.target.value)}
          className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none disabled:bg-gray-50 disabled:text-gray-500"
        />

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
          className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none disabled:bg-gray-50 disabled:text-gray-500"
        />

        <label className="mb-4 flex items-center gap-2 text-sm text-gray-700">
          <input
            type="checkbox"
            disabled={!canManage}
            checked={autoPrintKitchenTickets}
            onChange={(e) => setAutoPrintKitchenTickets(e.target.checked)}
          />
          Imprimir comandas de cozinha automaticamente ao enviar pedido
        </label>

        {error && <p className="mb-4 text-sm text-red-600">{error}</p>}
        {success && <p className="mb-4 text-sm text-green-600">Configurações salvas.</p>}

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
        <div className="mt-6 max-w-3xl overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm">
          <div className="flex items-center gap-2 border-b border-gray-100 bg-gradient-to-r from-brand-50 to-white px-6 py-4">
            <QrCode className="h-5 w-5 text-brand-600" />
            <h2 className="text-sm font-semibold text-gray-800">Cardápio digital</h2>
          </div>

          <div className="grid gap-6 p-6 sm:grid-cols-[auto_1fr]">
            <div className="flex flex-col items-center gap-2">
              <div className="rounded-xl border border-gray-200 bg-white p-3 shadow-md">
                <QRCodeCanvas value={publicMenuUrl(restaurant.slug)} size={168} />
              </div>
              <p className="max-w-[180px] text-center text-xs text-gray-400">
                Clique com o botão direito pra salvar e imprimir nas mesas
              </p>
            </div>

            <div className="flex flex-col gap-4">
              <div>
                <p className="mb-1 text-xs font-semibold tracking-wide text-gray-400 uppercase">Link público</p>
                <p className="truncate rounded-md bg-gray-50 px-3 py-2 text-sm text-gray-700">
                  {publicMenuUrl(restaurant.slug)}
                </p>
              </div>

              <div className="flex gap-2">
                <a
                  href={publicMenuUrl(restaurant.slug)}
                  target="_blank"
                  rel="noreferrer"
                  className="flex items-center gap-1.5 rounded-md bg-brand-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-brand-700"
                >
                  <ExternalLink className="h-4 w-4" />
                  Abrir cardápio
                </a>
                <button
                  type="button"
                  onClick={() => {
                    navigator.clipboard.writeText(publicMenuUrl(restaurant.slug!))
                    setLinkCopied(true)
                    setTimeout(() => setLinkCopied(false), 2000)
                  }}
                  className="flex items-center gap-1.5 rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-100"
                >
                  {linkCopied ? <Check className="h-4 w-4 text-green-600" /> : <Copy className="h-4 w-4" />}
                  {linkCopied ? 'Copiado!' : 'Copiar link'}
                </button>
              </div>

              <div>
                <p className="mb-2 text-xs font-semibold tracking-wide text-gray-400 uppercase">
                  Pré-visualização ao vivo
                </p>
                <div className="mx-auto w-[280px] overflow-hidden rounded-[1.75rem] border-[6px] border-gray-800 bg-gray-800 shadow-lg">
                  <div className="flex h-4 items-center justify-center bg-gray-800">
                    <div className="h-1 w-10 rounded-full bg-gray-600" />
                  </div>
                  <iframe
                    src={publicMenuUrl(restaurant.slug)}
                    title="Pré-visualização do cardápio"
                    className="h-[420px] w-full border-0 bg-white"
                  />
                </div>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function publicMenuUrl(slug: string) {
  return `${window.location.origin}/menu/${slug}`
}
