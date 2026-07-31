import { Eye, EyeOff, Lock, Mail } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { AuthInput } from '../components/AuthLayout'
import { Logo } from '../theme/Logo'

export function LoginPage() {
  const { login, isAuthenticated } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  if (isAuthenticated) {
    return <Navigate to="/" replace />
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setIsSubmitting(true)
    try {
      await login(email, password)
      navigate('/')
    } catch {
      setError('Email ou senha inválidos')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <>
      <Logo className="mx-auto mb-8 block h-16 w-auto" />
      <h1 className="mb-8 text-2xl font-bold text-gray-800 dark:text-white">Acesse sua conta</h1>

      <form onSubmit={handleSubmit}>
        <AuthInput
          id="email"
          type="email"
          label="Email"
          icon={Mail}
          required
          placeholder="Digite o e-mail"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />

        <AuthInput
          id="password"
          type={showPassword ? 'text' : 'password'}
          label="Senha"
          icon={Lock}
          required
          placeholder="Digite a senha"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
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

        {error && <p className="mb-4 text-sm text-red-600 dark:text-red-400">{error}</p>}

        <button
          type="submit"
          disabled={isSubmitting}
          className="mt-2 w-full rounded-lg bg-brand-600 px-3 py-2.5 text-sm font-semibold text-white hover:bg-brand-700 disabled:opacity-50"
        >
          {isSubmitting ? 'Entrando...' : 'Acessar'}
        </button>
      </form>

      <p className="mt-6 text-center text-sm text-gray-600 dark:text-stone-400">
        Ainda não tem uma conta?{' '}
        <Link to="/register" className="font-medium text-brand-700 hover:underline dark:text-brand-400">
          Cadastre seu restaurante
        </Link>
      </p>
    </>
  )
}
