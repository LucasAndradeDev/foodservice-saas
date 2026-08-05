import { isAxiosError } from 'axios'
import { Mail } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { forgotPassword } from '../api/auth'
import { AuthInput } from '../components/AuthLayout'
import { Logo } from '../theme/Logo'

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [submitted, setSubmitted] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setIsSubmitting(true)
    try {
      await forgotPassword(email)
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
          Se <strong>{email}</strong> estiver cadastrado, você vai receber um link para redefinir sua senha em
          instantes.
        </p>
        <Link
          to="/login"
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
        Digite seu email e enviaremos um link para você criar uma nova senha.
      </p>

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

        {error && <p className="mb-4 text-sm text-wine-600 dark:text-wine-400">{error}</p>}

        <button
          type="submit"
          disabled={isSubmitting}
          className="mt-2 w-full rounded-lg bg-brand-600 px-3 py-2.5 text-sm font-semibold text-white hover:bg-brand-700 disabled:opacity-50"
        >
          {isSubmitting ? 'Enviando...' : 'Enviar link de recuperação'}
        </button>
      </form>

      <p className="mt-6 text-center text-sm text-gray-600 dark:text-stone-400">
        Lembrou a senha?{' '}
        <Link to="/login" className="font-medium text-brand-700 hover:underline dark:text-brand-400">
          Entrar
        </Link>
      </p>
    </>
  )
}
