import { useMutation, useQuery } from '@tanstack/react-query'
import { CheckCircle2, X } from 'lucide-react'
import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { getFeedbackContext, submitFeedback } from '../api/postMealFeedback'
import { Button } from '../components/Button'
import { StarRating } from '../components/StarRating'
import { formatTableLabel } from '../utils/tableLabel'
import { MenuHero } from './publicMenu/MenuHero'
import { usePublicMenuTheme } from './publicMenu/usePublicMenuTheme'

export function PostMealFeedbackPage() {
  const { slug, tabId } = useParams<{ slug: string; tabId: string }>()
  const { theme, toggleTheme } = usePublicMenuTheme()
  const [rating, setRating] = useState(0)
  const [comment, setComment] = useState('')
  const [dismissed, setDismissed] = useState(false)

  const { data: context, isLoading, isError } = useQuery({
    queryKey: ['postMealFeedback', slug, tabId],
    queryFn: () => getFeedbackContext(slug!, tabId!),
    enabled: !!slug && !!tabId,
    retry: false,
  })

  const submitMutation = useMutation({
    mutationFn: () => submitFeedback(slug!, tabId!, { rating, comment: comment.trim() || undefined }),
  })

  const themeClass = theme === 'dark' ? 'dark' : ''

  if (isLoading) {
    return (
      <div className={`${themeClass} flex min-h-screen items-center justify-center bg-gray-50 p-4 dark:bg-stone-950`}>
        <p className="text-sm text-gray-500 dark:text-stone-400">Carregando...</p>
      </div>
    )
  }

  if (isError || !context) {
    return (
      <div className={`${themeClass} flex min-h-screen items-center justify-center bg-gray-50 p-4 dark:bg-stone-950`}>
        <p className="text-sm text-gray-500 dark:text-stone-400">Não foi possível carregar essa avaliação.</p>
      </div>
    )
  }

  const submitted = context.alreadySubmitted || submitMutation.isSuccess

  return (
    <div className={`${themeClass} min-h-screen bg-gray-50 dark:bg-stone-950`}>
      <MenuHero restaurantName={context.restaurantName} logo={context.logo} theme={theme} onToggleTheme={toggleTheme} />

      <main className="mx-auto max-w-md px-4 py-10">
        {submitted || dismissed ? (
          <div className="flex flex-col items-center gap-3 rounded-2xl border border-gray-200 bg-white p-8 text-center shadow-sm dark:border-stone-800 dark:bg-stone-900">
            <CheckCircle2 className="h-10 w-10 text-green-500" />
            <h1 className="text-lg font-semibold text-gray-900 dark:text-white">
              {submitted ? 'Obrigado pela avaliação!' : 'Sem problemas!'}
            </h1>
            <p className="text-sm text-gray-500 dark:text-stone-400">
              {submitted ? 'Sua opinião ajuda a melhorar o atendimento.' : 'Obrigado por vir aqui, esperamos te ver de novo em breve.'}
            </p>
          </div>
        ) : (
          <div className="relative rounded-2xl border border-gray-200 bg-white p-6 shadow-sm dark:border-stone-800 dark:bg-stone-900">
            <button
              type="button"
              onClick={() => setDismissed(true)}
              aria-label="Fechar"
              className="absolute right-4 top-4 text-gray-400 hover:text-gray-600 dark:text-stone-500 dark:hover:text-stone-300"
            >
              <X className="h-5 w-5" />
            </button>

            <h1 className="pr-6 text-lg font-semibold text-gray-900 dark:text-white">Como foi sua experiência?</h1>
            <p className="mb-6 text-sm text-gray-500 dark:text-stone-400">{formatTableLabel(context.tableNumbers)}</p>

            <div className="mb-6 flex justify-center">
              <StarRating value={rating} onChange={setRating} size="lg" />
            </div>

            <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-stone-300" htmlFor="feedbackComment">
              Comentário <span className="font-normal text-gray-400 dark:text-stone-500">(opcional)</span>
            </label>
            <textarea
              id="feedbackComment"
              rows={3}
              maxLength={500}
              value={comment}
              onChange={(e) => setComment(e.target.value)}
              placeholder="Conte um pouco mais..."
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none dark:border-stone-700 dark:bg-stone-800 dark:text-white"
            />

            {submitMutation.isError && (
              <p className="mb-4 text-sm text-red-600">Não foi possível enviar sua avaliação. Tente novamente.</p>
            )}

            <Button
              type="button"
              disabled={rating === 0 || submitMutation.isPending}
              onClick={() => submitMutation.mutate()}
              className="w-full"
            >
              Enviar avaliação
            </Button>
          </div>
        )}
      </main>
    </div>
  )
}
