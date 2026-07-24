import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
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
  const [logo, setLogo] = useState('')
  const [primaryColor, setPrimaryColor] = useState('')
  const [phone, setPhone] = useState('')
  const [address, setAddress] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState(false)

  useEffect(() => {
    if (!restaurant) return
    setCnpj(restaurant.cnpj ?? '')
    setTradeName(restaurant.tradeName ?? '')
    setLogo(restaurant.logo ?? '')
    setPrimaryColor(restaurant.primaryColor ?? '')
    setPhone(restaurant.phone ?? '')
    setAddress(restaurant.address ?? '')
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
      logo: logo || undefined,
      primaryColor: primaryColor || undefined,
      phone: phone || undefined,
      address: address || undefined,
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
    </div>
  )
}
