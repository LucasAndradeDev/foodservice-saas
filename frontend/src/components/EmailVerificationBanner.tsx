import { isAxiosError } from 'axios'
import { AnimatePresence, motion } from 'framer-motion'
import { CheckCircle2, Mail, X } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { useAuth } from '../auth/AuthContext'

const SUCCESS_DISPLAY_MS = 5000

export function EmailVerificationBanner() {
  const { user, resendVerificationEmail } = useAuth()
  const [isSending, setIsSending] = useState(false)
  const [feedback, setFeedback] = useState<string | null>(null)
  const [isDismissed, setIsDismissed] = useState(false)
  const [justVerified, setJustVerified] = useState(false)
  const wasUnverified = useRef(false)

  // Detects the false -> true transition (picked up by AppLayout's polling, or by
  // coming back to this tab) so we can show a "confirmed!" message instead of just
  // letting the pending card silently vanish.
  useEffect(() => {
    if (user?.emailVerified === false) {
      wasUnverified.current = true
      return
    }
    if (user?.emailVerified === true && wasUnverified.current) {
      wasUnverified.current = false
      setJustVerified(true)
      const timeout = setTimeout(() => setJustVerified(false), SUCCESS_DISPLAY_MS)
      return () => clearTimeout(timeout)
    }
  }, [user?.emailVerified])

  async function handleResend() {
    setIsSending(true)
    setFeedback(null)
    try {
      await resendVerificationEmail()
      setFeedback('Email reenviado! Confira sua caixa de entrada.')
    } catch (err) {
      if (isAxiosError(err) && err.response?.status === 429) {
        setFeedback('Muitas tentativas. Aguarde alguns minutos e tente novamente.')
      } else {
        setFeedback('Não foi possível reenviar agora. Tente de novo em instantes.')
      }
    } finally {
      setIsSending(false)
    }
  }

  const showPending = user?.emailVerified === false && !isDismissed
  const shouldShow = showPending || justVerified

  return (
    <div className="pointer-events-none fixed inset-x-4 bottom-20 z-20 flex justify-center sm:inset-x-auto sm:right-6 sm:bottom-6 sm:justify-end">
      <AnimatePresence mode="wait">
        {shouldShow && (
          <motion.div
            key={justVerified ? 'verified' : 'pending'}
            initial={{ opacity: 0, y: 16, scale: 0.97 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: 8, scale: 0.97 }}
            transition={{ duration: 0.2, ease: 'easeOut' }}
            className="pointer-events-auto flex w-full max-w-sm items-start gap-3 rounded-xl border border-gray-200 bg-white p-4 shadow-xl dark:border-white/10 dark:bg-stone-900"
          >
            {justVerified ? (
              <>
                <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-green-50 text-green-600 dark:bg-green-500/15 dark:text-green-400">
                  <CheckCircle2 className="h-5 w-5" />
                </span>
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-semibold text-gray-800 dark:text-white">Email confirmado!</p>
                  <p className="mt-0.5 text-sm text-gray-500 dark:text-stone-400">Sua conta agora tem acesso total.</p>
                </div>
                <button
                  type="button"
                  onClick={() => setJustVerified(false)}
                  aria-label="Fechar"
                  className="shrink-0 rounded-md p-1 text-gray-400 transition hover:bg-gray-100 hover:text-gray-600 dark:text-stone-500 dark:hover:bg-white/5 dark:hover:text-stone-300"
                >
                  <X className="h-4 w-4" />
                </button>
              </>
            ) : (
              <>
                <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-brand-50 text-brand-700 dark:bg-brand-500/15 dark:text-brand-400">
                  <Mail className="h-4 w-4" />
                </span>

                <div className="min-w-0 flex-1">
                  <p className="text-sm font-semibold text-gray-800 dark:text-white">Confirme seu email</p>
                  <p className="mt-0.5 text-sm text-gray-500 dark:text-stone-400">
                    {feedback ?? `Enviamos um link de confirmação para ${user?.email ?? 'seu email'}.`}
                  </p>
                  <button
                    type="button"
                    onClick={handleResend}
                    disabled={isSending}
                    className="mt-3 w-full rounded-lg bg-brand-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition hover:bg-brand-700 active:scale-[0.98] disabled:opacity-50"
                  >
                    {isSending ? 'Enviando...' : 'Reenviar email'}
                  </button>
                </div>

                <button
                  type="button"
                  onClick={() => setIsDismissed(true)}
                  aria-label="Fechar"
                  className="shrink-0 rounded-md p-1 text-gray-400 transition hover:bg-gray-100 hover:text-gray-600 dark:text-stone-500 dark:hover:bg-white/5 dark:hover:text-stone-300"
                >
                  <X className="h-4 w-4" />
                </button>
              </>
            )}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}
