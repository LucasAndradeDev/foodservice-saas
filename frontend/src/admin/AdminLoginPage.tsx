import { Lock, User } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { AuthInput, AuthLayout } from '../components/AuthLayout'
import { Logo } from '../theme/Logo'
import { useAdminAuth } from './AdminAuthContext'

export function AdminLoginPage() {
  const { login, isAuthenticated } = useAdminAuth()
  const navigate = useNavigate()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  if (isAuthenticated) {
    return <Navigate to="/admin/restaurants" replace />
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setIsSubmitting(true)
    try {
      await login(username, password)
      navigate('/admin/restaurants')
    } catch {
      setError('Usuário ou senha inválidos')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <AuthLayout>
      <Logo className="mx-auto mb-8 block h-16 w-auto" />
      <h1 className="mb-8 text-2xl font-bold text-gray-800 dark:text-white">Painel admin</h1>

      <form onSubmit={handleSubmit}>
        <AuthInput
          id="username"
          type="text"
          label="Usuário"
          icon={User}
          required
          value={username}
          onChange={(e) => setUsername(e.target.value)}
        />

        <AuthInput
          id="password"
          type="password"
          label="Senha"
          icon={Lock}
          required
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />

        {error && <p className="mb-4 text-sm text-wine-600 dark:text-wine-400">{error}</p>}

        <button
          type="submit"
          disabled={isSubmitting}
          className="mt-2 w-full rounded-lg bg-brand-600 px-3 py-2.5 text-sm font-semibold text-white hover:bg-brand-700 disabled:opacity-50"
        >
          {isSubmitting ? 'Entrando...' : 'Acessar'}
        </button>
      </form>

      <p className="mt-6 text-center text-sm text-gray-600 dark:text-stone-400">
        <Link to="/admin/forgot-password" className="font-medium text-brand-700 hover:underline dark:text-brand-400">
          Esqueci minha senha
        </Link>
      </p>
    </AuthLayout>
  )
}
