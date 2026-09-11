import { isAxiosError } from 'axios'
import { AnimatePresence, motion } from 'framer-motion'
import { Eye, EyeOff, Lock, Mail, MessageCircle, ShieldAlert } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { AuthInput } from '../components/AuthLayout'
import { SUPPORT_EMAIL, SUPPORT_WHATSAPP_URL } from '../config/support'
import { Logo } from '../theme/Logo'

export function LoginPage() {
  const { login, isAuthenticated, user } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [isSuspended, setIsSuspended] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)

  if (isAuthenticated) {
    return <Navigate to={user?.role === 'COURIER' ? '/my-deliveries' : '/dashboard'} replace />
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setIsSuspended(false)
    setIsSubmitting(true)
    try {
      const response = await login(email, password)
      navigate(response.user.role === 'COURIER' ? '/my-deliveries' : '/dashboard')
    } catch (err) {
      if (isAxiosError(err) && err.response?.status === 429) {
        setError('Muitas tentativas de login. Aguarde alguns minutos e tente novamente.')
      } else if (isAxiosError(err) && err.response?.data?.error === 'Restaurant Suspended') {
        // Same exception/HTTP shape covers two different reasons (AuthService#login) - a pending
        // first-time signup isn't "suspended", so it gets its own message and skips the
        // suspended-only support links below.
        if (err.response.data.message === "Restaurant pending approval. You'll be notified by email once it's approved.") {
          setError('Seu cadastro está em análise. Você vai receber um email assim que ele for aprovado.')
        } else {
          setError('Sua conta está temporariamente bloqueada. Entre em contato com o suporte para resolver isso.')
          setIsSuspended(true)
        }
      } else {
        setError('Email ou senha inválidos')
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <>
      <Logo className="mx-auto mb-6 block h-24 w-auto" />
      <h1 className="mb-8 text-center text-2xl font-bold text-gray-800 dark:text-white">Acesse sua conta</h1>

      <form onSubmit={handleSubmit}>
        <AuthInput
          id="email"
          type="email"
          label="Email"
          icon={Mail}
          required
          autoComplete="email"
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
          autoComplete="current-password"
          placeholder="Digite a senha"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
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

        <p className="-mt-3 mb-4 text-right text-sm">
          <Link to="/forgot-password" className="font-medium text-brand-700 hover:underline dark:text-brand-400">
            Esqueceu sua senha?
          </Link>
        </p>

        {error && !isSuspended && (
          <p role="alert" className="mb-2 text-sm text-wine-600 dark:text-wine-400">
            {error}
          </p>
        )}

        <AnimatePresence>
          {isSuspended && (
            <motion.div
              role="alert"
              initial={{ opacity: 0, y: -8, scale: 0.97 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: -8, scale: 0.97 }}
              transition={{ duration: 0.2, ease: 'easeOut' }}
              className="mb-4 rounded-xl border border-wine-200 bg-wine-50 p-4 dark:border-wine-500/20 dark:bg-wine-500/10"
            >
              <div className="flex items-start gap-3">
                <motion.span
                  initial={{ scale: 0.6, rotate: -8 }}
                  animate={{ scale: 1, rotate: 0 }}
                  transition={{ type: 'spring', stiffness: 400, damping: 15, delay: 0.05 }}
                  className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-wine-100 text-wine-600 dark:bg-wine-500/15 dark:text-wine-400"
                >
                  <ShieldAlert className="h-4 w-4" />
                </motion.span>
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-semibold text-wine-900 dark:text-wine-200">Conta bloqueada</p>
                  <p className="mt-0.5 text-sm text-wine-700 dark:text-wine-300">{error}</p>
                </div>
              </div>

              <div className="mt-3 flex flex-col gap-2 sm:flex-row">
                <motion.a
                  whileHover={{ scale: 1.02 }}
                  whileTap={{ scale: 0.98 }}
                  href={SUPPORT_WHATSAPP_URL}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="flex flex-1 items-center justify-center gap-2 rounded-lg border border-wine-200 bg-white px-3 py-2 text-sm font-medium text-gray-700 shadow-sm transition hover:border-sage-300 hover:bg-sage-50 hover:text-sage-800 dark:border-white/10 dark:bg-stone-900 dark:text-stone-300 dark:hover:bg-white/5"
                >
                  <MessageCircle className="h-4 w-4 text-sage-600 dark:text-sage-400" />
                  WhatsApp
                </motion.a>
                <motion.a
                  whileHover={{ scale: 1.02 }}
                  whileTap={{ scale: 0.98 }}
                  href={`mailto:${SUPPORT_EMAIL}`}
                  className="flex flex-1 items-center justify-center gap-2 rounded-lg border border-wine-200 bg-white px-3 py-2 text-sm font-medium text-gray-700 shadow-sm transition hover:border-brand-300 hover:bg-brand-50 hover:text-brand-800 dark:border-white/10 dark:bg-stone-900 dark:text-stone-300 dark:hover:bg-white/5"
                >
                  <Mail className="h-4 w-4 text-brand-600 dark:text-brand-400" />
                  Email
                </motion.a>
              </div>
            </motion.div>
          )}
        </AnimatePresence>

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
