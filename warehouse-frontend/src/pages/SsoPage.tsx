import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

export function SsoPage() {
  const { exchangeHandoffToken } = useAuth()
  const navigate = useNavigate()
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    // The token travels in the URL fragment (#token=...), not the query string: the fragment is
    // never sent in the HTTP request line, so it never lands in a server access log or a
    // cross-origin Referer header - only the browser itself ever sees it. Read it manually since
    // it isn't part of location.search.
    const token = new URLSearchParams(window.location.hash.replace(/^#/, '')).get('token')
    // Clear it from the visible URL immediately so it doesn't linger in this tab's address bar
    // longer than necessary.
    window.history.replaceState(null, '', window.location.pathname)
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
