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

  // Renders in-flow (as a strip above the page content) rather than as a fixed floating
  // card: a fixed corner overlay has no way to know what content is behind it, and ended up
  // covering real data on tall pages (e.g. the charts in Relatórios). A strip that pushes
  // content down instead of floating over it can't overlap anything, on any screen.
  return (
    <AnimatePresence initial={false}>
      {shouldShow && (
        <motion.div
          key={justVerified ? 'verified' : 'pending'}
          initial={{ opacity: 0, height: 0 }}
          animate={{ opacity: 1, height: 'auto' }}
          exit={{ opacity: 0, height: 0 }}
          transition={{ duration: 0.2, ease: 'easeOut' }}
          className="overflow-hidden"
        >
          {justVerified ? (
            <div className="flex flex-wrap items-center gap-x-3 gap-y-1.5 border-b border-green-200 bg-green-50 px-4 py-2.5 sm:px-6 dark:border-green-500/20 dark:bg-green-500/10">
              <CheckCircle2 className="h-4 w-4 shrink-0 text-green-600 dark:text-green-400" />
              <p className="text-sm text-green-900 dark:text-green-300">
                <span className="font-semibold">Email confirmado!</span> Sua conta agora tem acesso total.
              </p>
              <button
                type="button"
                onClick={() => setJustVerified(false)}
                aria-label="Fechar"
                className="ml-auto shrink-0 rounded-md p-1 text-green-700/60 transition hover:bg-green-100 hover:text-green-800 dark:text-green-400/60 dark:hover:bg-white/5 dark:hover:text-green-300"
              >
                <X className="h-4 w-4" />
              </button>
            </div>
          ) : (
            <div className="flex flex-wrap items-center gap-x-3 gap-y-1.5 border-b border-brand-200 bg-brand-50 px-4 py-2.5 sm:px-6 dark:border-brand-500/20 dark:bg-brand-500/10">
              <Mail className="h-4 w-4 shrink-0 text-brand-700 dark:text-brand-400" />
              <p className="text-sm text-brand-900 dark:text-brand-200">
                <span className="font-semibold">Confirme seu email.</span>{' '}
                {feedback ?? `Enviamos um link de confirmação para ${user?.email ?? 'seu email'}.`}
              </p>
              <button
                type="button"
                onClick={handleResend}
                disabled={isSending}
                className="shrink-0 text-sm font-semibold text-brand-700 underline decoration-brand-300 underline-offset-2 transition hover:text-brand-800 disabled:opacity-50 dark:text-brand-400 dark:decoration-brand-500/40 dark:hover:text-brand-300"
              >
                {isSending ? 'Enviando...' : 'Reenviar email'}
              </button>
              <button
                type="button"
                onClick={() => setIsDismissed(true)}
                aria-label="Fechar"
                className="ml-auto shrink-0 rounded-md p-1 text-brand-700/60 transition hover:bg-brand-100 hover:text-brand-800 dark:text-brand-400/60 dark:hover:bg-white/5 dark:hover:text-brand-300"
              >
                <X className="h-4 w-4" />
              </button>
            </div>
          )}
        </motion.div>
      )}
    </AnimatePresence>
  )
}
