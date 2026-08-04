import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { verifyEmail } from '../api/auth'
import { useAuth } from '../auth/AuthContext'
import { Logo } from '../theme/Logo'

type Status = 'verifying' | 'success' | 'error'

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

  if (status === 'verifying') {
    return (
      <>
        <Logo className="mx-auto mb-8 block h-16 w-auto" />
        <h1 className="mb-4 text-2xl font-bold text-gray-800 dark:text-white">Confirmando seu email...</h1>
        <p className="text-sm text-gray-600 dark:text-stone-400">Só um instante.</p>
      </>
    )
  }

  if (status === 'error') {
    return (
      <>
        <Logo className="mx-auto mb-8 block h-16 w-auto" />
        <h1 className="mb-4 text-2xl font-bold text-gray-800 dark:text-white">Link inválido ou expirado</h1>
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
    )
  }

  return (
    <>
      <Logo className="mx-auto mb-8 block h-16 w-auto" />
      <h1 className="mb-4 text-2xl font-bold text-gray-800 dark:text-white">Email confirmado!</h1>
      <p className="mb-8 text-sm text-gray-600 dark:text-stone-400">Sua conta Morá está ativada.</p>
      <button
        type="button"
        onClick={() => navigate(isAuthenticated ? '/' : '/login')}
        className="block w-full rounded-lg bg-brand-600 px-3 py-2.5 text-center text-sm font-semibold text-white hover:bg-brand-700"
      >
        {isAuthenticated ? 'Ir para o início' : 'Ir para o login'}
      </button>
    </>
  )
}
