import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { Logo } from '../theme/Logo'
import { ThemeToggleButton } from '../theme/ThemeToggleButton'

const inputClass =
  'mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400'
const labelClass = 'mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300'

export function RegisterPage() {
  const { registerRestaurant, isAuthenticated } = useAuth()
  const navigate = useNavigate()
  const [restaurantName, setRestaurantName] = useState('')
  const [cnpj, setCnpj] = useState('')
  const [phone, setPhone] = useState('')
  const [address, setAddress] = useState('')
  const [ownerName, setOwnerName] = useState('')
  const [ownerEmail, setOwnerEmail] = useState('')
  const [confirmOwnerEmail, setConfirmOwnerEmail] = useState('')
  const [ownerPassword, setOwnerPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  if (isAuthenticated) {
    return <Navigate to="/" replace />
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)

    if (ownerEmail.trim().toLowerCase() !== confirmOwnerEmail.trim().toLowerCase()) {
      setError('Os emails não coincidem.')
      return
    }

    setIsSubmitting(true)
    try {
      await registerRestaurant({
        restaurantName,
        cnpj: cnpj || undefined,
        phone: phone || undefined,
        address: address || undefined,
        ownerName,
        ownerEmail,
        ownerPassword,
      })
      navigate('/')
    } catch {
      setError('Não foi possível concluir o cadastro. Verifique os dados e tente novamente.')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="relative flex min-h-screen items-center justify-center bg-gray-50 p-4 dark:bg-stone-950">
      <ThemeToggleButton className="absolute right-4 top-4" />
      <form
        onSubmit={handleSubmit}
        className="w-full max-w-sm rounded-lg bg-white p-6 shadow-sm sm:p-8 dark:bg-stone-900"
      >
        <Logo className="mx-auto mb-4 h-12 w-auto" />
        <h1 className="mb-6 text-center text-sm font-medium text-gray-500 dark:text-stone-400">
          Cadastrar restaurante
        </h1>

        <label className={labelClass} htmlFor="restaurantName">
          Nome do restaurante
        </label>
        <input
          id="restaurantName"
          type="text"
          required
          value={restaurantName}
          onChange={(e) => setRestaurantName(e.target.value)}
          className={inputClass}
        />

        <label className={labelClass} htmlFor="cnpj">
          CNPJ <span className="font-normal text-gray-400 dark:text-stone-500">(opcional)</span>
        </label>
        <input id="cnpj" type="text" value={cnpj} onChange={(e) => setCnpj(e.target.value)} className={inputClass} />

        <label className={labelClass} htmlFor="phone">
          Telefone <span className="font-normal text-gray-400 dark:text-stone-500">(opcional)</span>
        </label>
        <input
          id="phone"
          type="text"
          value={phone}
          onChange={(e) => setPhone(e.target.value)}
          className={inputClass}
        />

        <label className={labelClass} htmlFor="address">
          Endereço <span className="font-normal text-gray-400 dark:text-stone-500">(opcional)</span>
        </label>
        <input
          id="address"
          type="text"
          value={address}
          onChange={(e) => setAddress(e.target.value)}
          className={inputClass}
        />

        <label className={labelClass} htmlFor="ownerName">
          Seu nome
        </label>
        <input
          id="ownerName"
          type="text"
          required
          value={ownerName}
          onChange={(e) => setOwnerName(e.target.value)}
          className={inputClass}
        />

        <label className={labelClass} htmlFor="ownerEmail">
          Email
        </label>
        <input
          id="ownerEmail"
          type="email"
          required
          value={ownerEmail}
          onChange={(e) => setOwnerEmail(e.target.value)}
          className={inputClass}
        />

        <label className={labelClass} htmlFor="confirmOwnerEmail">
          Confirmar email
        </label>
        <input
          id="confirmOwnerEmail"
          type="email"
          required
          value={confirmOwnerEmail}
          onChange={(e) => setConfirmOwnerEmail(e.target.value)}
          onPaste={(e) => e.preventDefault()}
          className={inputClass}
        />

        <label className={labelClass} htmlFor="ownerPassword">
          Senha
        </label>
        <input
          id="ownerPassword"
          type="password"
          required
          minLength={6}
          value={ownerPassword}
          onChange={(e) => setOwnerPassword(e.target.value)}
          className={inputClass}
        />

        {error && <p className="mb-4 text-sm text-red-600 dark:text-red-400">{error}</p>}

        <button
          type="submit"
          disabled={isSubmitting}
          className="w-full rounded-md bg-brand-600 px-3 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50"
        >
          {isSubmitting ? 'Cadastrando...' : 'Cadastrar'}
        </button>

        <p className="mt-4 text-center text-sm text-gray-600 dark:text-stone-400">
          Já tem uma conta?{' '}
          <Link to="/login" className="font-medium text-brand-700 hover:underline dark:text-brand-400">
            Entrar
          </Link>
        </p>
      </form>
    </div>
  )
}
