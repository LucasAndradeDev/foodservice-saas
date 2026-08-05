import { Eye, EyeOff, IdCard, Lock, Mail, MapPin, Phone, Store, User } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { AuthInput } from '../components/AuthLayout'
import { Logo } from '../theme/Logo'

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
  const [showPassword, setShowPassword] = useState(false)
  const [termsAccepted, setTermsAccepted] = useState(false)
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

    if (!termsAccepted) {
      setError('Você precisa aceitar os Termos de Uso e a Política de Privacidade para continuar.')
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
        termsAccepted,
      })
      navigate('/')
    } catch {
      setError('Não foi possível concluir o cadastro. Verifique os dados e tente novamente.')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <>
      <Logo className="mx-auto mb-6 block h-16 w-auto" />
      <h1 className="mb-8 text-2xl font-bold text-gray-800 dark:text-white">Cadastre seu restaurante</h1>

      <form onSubmit={handleSubmit}>
        <AuthInput
          id="restaurantName"
          type="text"
          label="Nome do restaurante"
          icon={Store}
          required
          placeholder="Digite o nome do restaurante"
          value={restaurantName}
          onChange={(e) => setRestaurantName(e.target.value)}
        />

        <AuthInput
          id="cnpj"
          type="text"
          label="CNPJ (opcional)"
          icon={IdCard}
          placeholder="Digite o CNPJ"
          value={cnpj}
          onChange={(e) => setCnpj(e.target.value)}
        />

        <AuthInput
          id="phone"
          type="text"
          label="Telefone (opcional)"
          icon={Phone}
          placeholder="Digite o telefone"
          value={phone}
          onChange={(e) => setPhone(e.target.value)}
        />

        <AuthInput
          id="address"
          type="text"
          label="Endereço (opcional)"
          icon={MapPin}
          placeholder="Digite o endereço"
          value={address}
          onChange={(e) => setAddress(e.target.value)}
        />

        <AuthInput
          id="ownerName"
          type="text"
          label="Seu nome"
          icon={User}
          required
          placeholder="Digite seu nome"
          value={ownerName}
          onChange={(e) => setOwnerName(e.target.value)}
        />

        <AuthInput
          id="ownerEmail"
          type="email"
          label="Email"
          icon={Mail}
          required
          placeholder="Digite o e-mail"
          value={ownerEmail}
          onChange={(e) => setOwnerEmail(e.target.value)}
        />

        <AuthInput
          id="confirmOwnerEmail"
          type="email"
          label="Confirmar email"
          icon={Mail}
          required
          placeholder="Confirme o e-mail"
          value={confirmOwnerEmail}
          onChange={(e) => setConfirmOwnerEmail(e.target.value)}
          onPaste={(e) => e.preventDefault()}
        />

        <AuthInput
          id="ownerPassword"
          type={showPassword ? 'text' : 'password'}
          label="Senha"
          icon={Lock}
          required
          minLength={6}
          placeholder="Digite a senha"
          value={ownerPassword}
          onChange={(e) => setOwnerPassword(e.target.value)}
          rightElement={
            <button
              type="button"
              onClick={() => setShowPassword((show) => !show)}
              aria-label={showPassword ? 'Ocultar senha' : 'Mostrar senha'}
              className="text-gray-400 hover:text-gray-600 dark:text-stone-500 dark:hover:text-stone-300"
            >
              {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
            </button>
          }
        />

        <label className="mb-4 flex items-start gap-2 text-sm text-gray-600 dark:text-stone-400">
          <input
            type="checkbox"
            checked={termsAccepted}
            onChange={(e) => setTermsAccepted(e.target.checked)}
            className="mt-0.5 h-4 w-4 shrink-0 rounded border-gray-300 text-brand-600 focus:ring-brand-500 dark:border-stone-600 dark:bg-stone-800"
          />
          <span>
            Li e aceito os{' '}
            <Link to="/terms" target="_blank" className="font-medium text-brand-700 hover:underline dark:text-brand-400">
              Termos de Uso
            </Link>{' '}
            e a{' '}
            <Link to="/privacy" target="_blank" className="font-medium text-brand-700 hover:underline dark:text-brand-400">
              Política de Privacidade
            </Link>
          </span>
        </label>

        {error && <p className="mb-4 text-sm text-wine-600 dark:text-wine-400">{error}</p>}

        <button
          type="submit"
          disabled={isSubmitting || !termsAccepted}
          className="mt-2 w-full rounded-lg bg-brand-600 px-3 py-2.5 text-sm font-semibold text-white hover:bg-brand-700 disabled:opacity-50"
        >
          {isSubmitting ? 'Cadastrando...' : 'Cadastrar'}
        </button>
      </form>

      <p className="mt-6 text-center text-sm text-gray-600 dark:text-stone-400">
        Já tem uma conta?{' '}
        <Link to="/login" className="font-medium text-brand-700 hover:underline dark:text-brand-400">
          Entrar
        </Link>
      </p>
    </>
  )
}
