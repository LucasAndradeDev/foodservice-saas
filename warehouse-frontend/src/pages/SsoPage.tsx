import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

export function SsoPage() {
  const [searchParams] = useSearchParams()
  const { exchangeHandoffToken } = useAuth()
  const navigate = useNavigate()
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const token = searchParams.get('token')
    if (!token) {
      setError('Link inválido - faltando o token de acesso.')
      return
    }
    exchangeHandoffToken(token)
      .then(() => navigate('/ingredients', { replace: true }))
      .catch(() => setError('Link expirado ou inválido. Volte ao Morá e clique em "Armazém Morá" de novo.'))
    // Runs once on mount - the handoff token is single-use, retrying with the same one on a
    // dependency change would just fail again.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50 px-4 dark:bg-stone-950">
      <div className="max-w-sm text-center">
        {error ? (
          <p className="text-sm text-wine-600 dark:text-wine-400">{error}</p>
        ) : (
          <p className="text-sm text-gray-500 dark:text-stone-400">Entrando no Armazém Morá...</p>
        )}
      </div>
    </div>
  )
}
