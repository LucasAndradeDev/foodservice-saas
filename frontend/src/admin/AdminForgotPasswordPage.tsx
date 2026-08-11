import { isAxiosError } from 'axios'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { adminForgotPassword } from '../api/admin'
import { Logo } from '../theme/Logo'

export function AdminForgotPasswordPage() {
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [submitted, setSubmitted] = useState(false)

  async function handleSend() {
    setError(null)
    setIsSubmitting(true)
    try {
      await adminForgotPassword()
      setSubmitted(true)
    } catch (err) {
      if (isAxiosError(err) && err.response?.status === 429) {
        setError('Muitas tentativas. Aguarde alguns minutos e tente novamente.')
      } else {
        setError('Não foi possível processar o pedido. Tente novamente.')
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  if (submitted) {
    return (
      <>
        <Logo className="mx-auto mb-8 block h-16 w-auto" />
        <h1 className="mb-4 text-2xl font-bold text-gray-800 dark:text-white">Verifique seu email</h1>
        <p className="mb-8 text-sm text-gray-600 dark:text-stone-400">
          Enviamos um link para redefinir a senha do admin em instantes.
        </p>
        <Link
          to="/admin/login"
          className="block w-full rounded-lg bg-brand-600 px-3 py-2.5 text-center text-sm font-semibold text-white hover:bg-brand-700"
        >
          Voltar para o login
        </Link>
      </>
    )
  }

  return (
    <>
      <Logo className="mx-auto mb-8 block h-16 w-auto" />
      <h1 className="mb-2 text-2xl font-bold text-gray-800 dark:text-white">Esqueceu sua senha?</h1>
      <p className="mb-8 text-sm text-gray-600 dark:text-stone-400">
        Enviaremos um link para o email do admin para você criar uma nova senha.
      </p>

      {error && <p className="mb-4 text-sm text-wine-600 dark:text-wine-400">{error}</p>}

      <button
        type="button"
        onClick={handleSend}
        disabled={isSubmitting}
        className="mt-2 w-full rounded-lg bg-brand-600 px-3 py-2.5 text-sm font-semibold text-white hover:bg-brand-700 disabled:opacity-50"
      >
        {isSubmitting ? 'Enviando...' : 'Enviar link de recuperação'}
      </button>

      <p className="mt-6 text-center text-sm text-gray-600 dark:text-stone-400">
        Lembrou a senha?{' '}
        <Link to="/admin/login" className="font-medium text-brand-700 hover:underline dark:text-brand-400">
          Entrar
        </Link>
      </p>
    </>
  )
}
