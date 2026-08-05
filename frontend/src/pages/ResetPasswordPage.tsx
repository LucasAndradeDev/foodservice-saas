import { isAxiosError } from 'axios'
import { Eye, EyeOff, Lock } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { AuthInput } from '../components/AuthLayout'
import { Logo } from '../theme/Logo'

export function ResetPasswordPage() {
  const { resetPassword, isAuthenticated } = useAuth()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  if (isAuthenticated) {
    return <Navigate to="/" replace />
  }

  if (!token) {
    return (
      <>
        <Logo className="mx-auto mb-8 block h-16 w-auto" />
        <h1 className="mb-4 text-2xl font-bold text-gray-800 dark:text-white">Link inválido</h1>
        <p className="mb-8 text-sm text-gray-600 dark:text-stone-400">
          Esse link de recuperação de senha está incompleto. Solicite um novo.
        </p>
        <Link
          to="/forgot-password"
          className="block w-full rounded-lg bg-brand-600 px-3 py-2.5 text-center text-sm font-semibold text-white hover:bg-brand-700"
        >
          Solicitar novo link
        </Link>
      </>
    )
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)

    if (newPassword !== confirmPassword) {
      setError('As senhas não coincidem.')
      return
    }

    setIsSubmitting(true)
    try {
      await resetPassword(token!, newPassword)
      navigate('/')
    } catch (err) {
      if (isAxiosError(err) && err.response?.status === 429) {
        setError('Muitas tentativas. Aguarde alguns minutos e tente novamente.')
      } else if (isAxiosError(err) && err.response?.status === 400) {
        setError('Esse link de recuperação é inválido ou já expirou. Solicite um novo.')
      } else {
        setError('Não foi possível redefinir a senha. Tente novamente.')
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <>
      <Logo className="mx-auto mb-8 block h-16 w-auto" />
      <h1 className="mb-8 text-2xl font-bold text-gray-800 dark:text-white">Crie uma nova senha</h1>

      <form onSubmit={handleSubmit}>
        <AuthInput
          id="newPassword"
          type={showPassword ? 'text' : 'password'}
          label="Nova senha"
          icon={Lock}
          required
          minLength={6}
          placeholder="Digite a nova senha"
          value={newPassword}
          onChange={(e) => setNewPassword(e.target.value)}
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

        <AuthInput
          id="confirmPassword"
          type={showPassword ? 'text' : 'password'}
          label="Confirmar nova senha"
          icon={Lock}
          required
          minLength={6}
          placeholder="Confirme a nova senha"
          value={confirmPassword}
          onChange={(e) => setConfirmPassword(e.target.value)}
          onPaste={(e) => e.preventDefault()}
        />

        {error && <p className="mb-4 text-sm text-wine-600 dark:text-wine-400">{error}</p>}

        <button
          type="submit"
          disabled={isSubmitting}
          className="mt-2 w-full rounded-lg bg-brand-600 px-3 py-2.5 text-sm font-semibold text-white hover:bg-brand-700 disabled:opacity-50"
        >
          {isSubmitting ? 'Salvando...' : 'Redefinir senha'}
        </button>
      </form>
    </>
  )
}
