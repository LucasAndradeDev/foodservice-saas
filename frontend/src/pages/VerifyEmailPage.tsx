import { CheckCircle2, Loader2, XCircle } from 'lucide-react'
import { motion } from 'framer-motion'
import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { verifyEmail } from '../api/auth'
import { useAuth } from '../auth/AuthContext'
import { Logo } from '../theme/Logo'

type Status = 'verifying' | 'success' | 'error'

const STATUS_BADGE = {
  verifying: 'bg-gray-100 text-gray-400 dark:bg-white/5 dark:text-stone-500',
  success: 'bg-sage-100 text-sage-600 dark:bg-sage-500/10 dark:text-sage-400',
  error: 'bg-wine-100 text-wine-600 dark:bg-wine-500/10 dark:text-wine-400',
} as const

export function VerifyEmailPage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token')
  const { isAuthenticated, refreshUser } = useAuth()
  const navigate = useNavigate()
  const [status, setStatus] = useState<Status>(token ? 'verifying' : 'error')
  const hasRun = useRef(false)

  useEffect(() => {
    if (!token || hasRun.current) return
    hasRun.current = true

    verifyEmail(token)
      .then(() => {
        setStatus('success')
        if (isAuthenticated) {
          // Best-effort: the verification already succeeded server-side, so a failed
          // refresh here shouldn't turn this into an error state.
          refreshUser().catch(() => {})
        }
      })
      .catch(() => setStatus('error'))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token])

  return (
    <>
      <Logo className="mx-auto mb-10 block h-16 w-auto" />

      <motion.div
        initial={{ opacity: 0, y: 8 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.3 }}
        className="text-center"
      >
        <div className={`mx-auto mb-6 flex h-16 w-16 items-center justify-center rounded-full ${STATUS_BADGE[status]}`}>
          {status === 'verifying' && <Loader2 className="h-8 w-8 animate-spin" />}
          {status === 'success' && <CheckCircle2 className="h-8 w-8" />}
          {status === 'error' && <XCircle className="h-8 w-8" />}
        </div>

        {status === 'verifying' && (
          <>
            <h1 className="mb-2 text-2xl font-bold text-gray-800 dark:text-white">Confirmando seu email...</h1>
            <p className="text-sm text-gray-600 dark:text-stone-400">Só um instante.</p>
          </>
        )}

        {status === 'error' && (
          <>
            <h1 className="mb-2 text-2xl font-bold text-gray-800 dark:text-white">Link inválido ou expirado</h1>
            <p className="mb-8 text-sm text-gray-600 dark:text-stone-400">
              Esse link de confirmação não é mais válido. {isAuthenticated ? 'Você pode pedir um novo email de dentro do sistema.' : 'Faça login e solicite um novo email de confirmação.'}
            </p>
            <Link
              to={isAuthenticated ? '/' : '/login'}
              className="block w-full rounded-lg bg-brand-600 px-3 py-2.5 text-center text-sm font-semibold text-white hover:bg-brand-700"
            >
              {isAuthenticated ? 'Voltar ao início' : 'Ir para o login'}
            </Link>
          </>
        )}

        {status === 'success' && (
          <>
            <h1 className="mb-2 text-2xl font-bold text-gray-800 dark:text-white">Email confirmado!</h1>
            <p className="mb-8 text-sm text-gray-600 dark:text-stone-400">Sua conta Morá está ativada.</p>
            <button
              type="button"
              onClick={() => navigate(isAuthenticated ? '/' : '/login')}
              className="block w-full rounded-lg bg-brand-600 px-3 py-2.5 text-center text-sm font-semibold text-white hover:bg-brand-700"
            >
              {isAuthenticated ? 'Ir para o início' : 'Ir para o login'}
            </button>
          </>
        )}
      </motion.div>
    </>
  )
}
