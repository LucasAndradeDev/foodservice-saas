import { isAxiosError } from 'axios'
import { Eye, EyeOff, IdCard, Lock, Mail, MapPin, Phone, Store, User } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { AuthInput } from '../components/AuthLayout'
import { Logo } from '../theme/Logo'
import { formatBrazilianPhone } from '../utils/phone'

// The backend already distinguishes these two cases (AuthService) from any other failure -
// showing the same generic "verifique os dados" for a duplicate email hid the one thing a
// returning owner most needs to hear: they already have an account (2026-09-09 onboarding audit,
// finding #3). Matched by exact message rather than status code alone, since 400 also covers
// unrelated validation errors that should keep the generic text.
type RegisterErrorReason = 'duplicate-email' | 'duplicate-cnpj' | 'generic'

function registerErrorReason(error: unknown): RegisterErrorReason {
  const message = isAxiosError(error) ? (error.response?.data as { message?: string } | undefined)?.message : undefined
  if (message === 'Email already registered.') return 'duplicate-email'
  if (message === 'CNPJ already registered.') return 'duplicate-cnpj'
  return 'generic'
}

const REGISTER_ERROR_MESSAGES: Record<RegisterErrorReason, string> = {
  'duplicate-email': 'Esse e-mail já tem uma conta.',
  'duplicate-cnpj': 'Esse CNPJ já está cadastrado em outra conta.',
  generic: 'Não foi possível concluir o cadastro. Verifique os dados e tente novamente.',
}

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
  const [isDuplicateEmail, setIsDuplicateEmail] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)

  if (isAuthenticated) {
    return <Navigate to="/dashboard" replace />
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setIsDuplicateEmail(false)

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
      navigate('/dashboard')
    } catch (err) {
      const reason = registerErrorReason(err)
      setError(REGISTER_ERROR_MESSAGES[reason])
      setIsDuplicateEmail(reason === 'duplicate-email')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <>
      <Logo className="mx-auto mb-6 block h-16 w-auto" />
      <h1 className="mb-8 text-2xl font-bold text-gray-800 dark:text-white">Cadastre seu restaurante</h1>

      <form onSubmit={handleSubmit}>
        <p className="mb-3 text-xs font-semibold uppercase tracking-wide text-gray-400 dark:text-stone-500">
          Dados do restaurante
        </p>
        <AuthInput
          id="restaurantName"
          type="text"
          label="Nome do restaurante"
          icon={Store}
          required
          autoComplete="organization"
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
          type="tel"
          label="Telefone (opcional)"
          icon={Phone}
          autoComplete="tel"
          maxLength={16}
          placeholder="Digite o telefone"
          value={phone}
          onChange={(e) => setPhone(formatBrazilianPhone(e.target.value))}
        />

        <AuthInput
          id="address"
          type="text"
          label="Endereço (opcional)"
          icon={MapPin}
          autoComplete="street-address"
          placeholder="Digite o endereço"
          value={address}
          onChange={(e) => setAddress(e.target.value)}
        />

        <p className="mt-2 mb-3 border-t border-gray-100 pt-5 text-xs font-semibold uppercase tracking-wide text-gray-400 dark:border-white/10 dark:text-stone-500">
          Seus dados
        </p>
        <AuthInput
          id="ownerName"
          type="text"
          label="Seu nome"
          icon={User}
          required
          autoComplete="name"
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
          autoComplete="email"
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
          autoComplete="off"
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
          minLength={8}
          autoComplete="new-password"
          placeholder="Digite a senha"
          value={ownerPassword}
          onChange={(e) => setOwnerPassword(e.target.value)}
          rightElement={
            <button
              type="button"
              onClick={() => setShowPassword((show) => !show)}
              aria-label={showPassword ? 'Ocultar senha' : 'Mostrar senha'}
              className="-m-2 p-2 text-gray-400 hover:text-gray-600 dark:text-stone-500 dark:hover:text-stone-300"
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

        {error && (
          <p role="alert" className="mb-4 text-sm text-wine-600 dark:text-wine-400">
            {error}
            {isDuplicateEmail && (
              <>
                {' '}
                <Link to="/login" className="font-medium underline">
                  Fazer login
                </Link>
                ?
              </>
            )}
          </p>
        )}

        <button
          type="submit"
          disabled={isSubmitting}
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
