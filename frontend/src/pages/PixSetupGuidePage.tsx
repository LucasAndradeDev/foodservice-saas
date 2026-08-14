import { ArrowLeft, Check, Copy, Mail, MessageCircle, TriangleAlert } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { SUPPORT_EMAIL, SUPPORT_WHATSAPP_URL } from '../config/support'
import { Logo } from '../theme/Logo'

const WEBHOOK_URL = 'https://mora-backend-ubuw.onrender.com/api/v1/public/payments/webhook'

const STEPS = [
  {
    title: 'Crie sua conta na Woovi',
    body: (
      <>
        Acesse <strong>woovi.com</strong> e cadastre-se com o CNPJ do restaurante. A aprovação costuma sair em
        minutos. Essa conta é sua — é nela que o dinheiro dos pagamentos Pix cai, e só você tem acesso.
      </>
    ),
  },
  {
    title: 'Crie uma aplicação no painel',
    body: (
      <>
        Dentro do painel da Woovi, vá em{' '}
        <code className="rounded bg-gray-100 px-1.5 py-0.5 text-xs dark:bg-white/10">Aplicação → Adicionar</code> e
        crie uma do tipo <strong>API REST</strong>. Pode dar o nome que quiser, ex: "Morá".
      </>
    ),
  },
  {
    title: 'Copie o AppID',
    body: (
      <>
        Na tela de detalhes da aplicação, abra a linha{' '}
        <code className="rounded bg-gray-100 px-1.5 py-0.5 text-xs dark:bg-white/10">Autorização (AppID)</code> e
        copie o código mostrado.
      </>
    ),
    warning: 'Trate o AppID como uma senha — quem tiver esse código consegue gerar cobranças na sua conta.',
  },
  {
    title: 'Cole o AppID no Morá',
    body: (
      <>
        No Morá, vá em{' '}
        <code className="rounded bg-gray-100 px-1.5 py-0.5 text-xs dark:bg-white/10">
          Configurações → Pagamento Pix (Woovi)
        </code>
        , cole o código no campo e clique em <strong>Salvar</strong>.
      </>
    ),
  },
]

export function PixSetupGuidePage() {
  const [copied, setCopied] = useState(false)

  function handleCopy() {
    navigator.clipboard.writeText(WEBHOOK_URL)
    setCopied(true)
    setTimeout(() => setCopied(false), 1800)
  }

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-stone-950">
      <main className="mx-auto max-w-3xl px-4 py-10">
        <Link
          to="/settings"
          className="mb-6 inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 dark:text-stone-400 dark:hover:text-stone-200"
        >
          <ArrowLeft className="h-4 w-4" />
          Voltar
        </Link>

        <Logo className="mb-6 h-12 w-auto" />

        <div className="rounded-2xl border border-gray-200 bg-white p-6 shadow-sm dark:border-stone-800 dark:bg-stone-900 sm:p-8">
          <p className="mb-1 text-xs font-semibold uppercase tracking-wide text-sage-600 dark:text-sage-400">
            Guia rápido · 5 passos
          </p>
          <h1 className="mb-2 text-2xl font-bold text-gray-800 dark:text-white">Como ativar o Pix</h1>
          <p className="mb-8 text-sm leading-relaxed text-gray-600 dark:text-stone-400">
            Conecte a conta Woovi do seu restaurante e o cliente passa a pagar pelo próprio celular, escaneando um QR
            Code — o dinheiro cai <strong>direto na sua conta</strong>, sem passar pelo Morá. Leva uns 10 minutos e só
            precisa ser feito uma vez.
          </p>

          <ol className="space-y-6">
            {STEPS.map((step, index) => (
              <li key={step.title} className="flex gap-4 border-t border-gray-100 pt-6 first:border-t-0 first:pt-0 dark:border-white/10">
                <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-brand-100 text-sm font-bold text-brand-600 dark:bg-brand-500/15 dark:text-brand-400">
                  {index + 1}
                </span>
                <div className="min-w-0">
                  <h2 className="mb-1 text-sm font-semibold text-gray-800 dark:text-white">{step.title}</h2>
                  <p className="text-sm leading-relaxed text-gray-600 dark:text-stone-400">{step.body}</p>
                  {step.warning && (
                    <div className="mt-3 flex items-start gap-2 rounded-lg border border-wine-300 bg-wine-100 px-3 py-2 text-xs text-wine-700 dark:border-wine-700 dark:bg-wine-500/10 dark:text-wine-400">
                      <TriangleAlert className="mt-0.5 h-3.5 w-3.5 shrink-0" />
                      <span>{step.warning}</span>
                    </div>
                  )}
                </div>
              </li>
            ))}

            <li className="flex gap-4 border-t border-gray-100 pt-6 dark:border-white/10">
              <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-brand-100 text-sm font-bold text-brand-600 dark:bg-brand-500/15 dark:text-brand-400">
                5
              </span>
              <div className="min-w-0 flex-1">
                <h2 className="mb-1 text-sm font-semibold text-gray-800 dark:text-white">
                  Avise a Woovi onde confirmar o pagamento
                </h2>
                <p className="mb-3 text-sm leading-relaxed text-gray-600 dark:text-stone-400">
                  Ainda na aplicação criada no passo 2, abra a aba{' '}
                  <code className="rounded bg-gray-100 px-1.5 py-0.5 text-xs dark:bg-white/10">Webhooks</code> e cole
                  esta URL — é fixa, sempre a mesma, não muda:
                </p>
                <div className="flex items-stretch gap-2">
                  <code className="min-w-0 flex-1 overflow-x-auto whitespace-nowrap rounded-lg bg-gray-900 px-3 py-2 text-xs text-gray-100 dark:bg-black">
                    {WEBHOOK_URL}
                  </code>
                  <button
                    type="button"
                    onClick={handleCopy}
                    className="flex shrink-0 items-center gap-1.5 rounded-lg border border-gray-300 px-3 py-2 text-xs font-semibold text-gray-700 hover:bg-gray-50 dark:border-white/10 dark:text-stone-300 dark:hover:bg-white/5"
                  >
                    {copied ? (
                      <>
                        <Check className="h-3.5 w-3.5 text-sage-600 dark:text-sage-400" />
                        Copiado
                      </>
                    ) : (
                      <>
                        <Copy className="h-3.5 w-3.5" />
                        Copiar
                      </>
                    )}
                  </button>
                </div>
                <p className="mt-3 text-sm leading-relaxed text-gray-600 dark:text-stone-400">
                  É esse aviso que fecha a comanda sozinha assim que o cliente paga — sem ele, os pagamentos
                  continuam acontecendo, só que alguém precisa confirmar na mão.
                </p>
              </div>
            </li>
          </ol>

          <div className="mt-8 rounded-xl border border-sage-300 bg-sage-100 p-4 dark:border-sage-500/30 dark:bg-sage-500/10">
            <h2 className="mb-1 text-sm font-semibold text-sage-700 dark:text-sage-400">Pronto — Pix ativo</h2>
            <p className="text-sm leading-relaxed text-gray-700 dark:text-stone-300">
              A partir de agora, o Caixa e o cardápio digital passam a mostrar a opção de gerar QR Code Pix. Se algum
              aviso de pagamento falhar por qualquer motivo, a comanda nunca fica travada: dá pra marcar como paga na
              mão, do jeito que já funciona hoje.
            </p>
          </div>

          <div className="mt-8 flex flex-wrap items-center justify-between gap-3 border-t border-gray-100 pt-6 dark:border-white/10">
            <p className="text-xs text-gray-500 dark:text-stone-500">Ficou com dúvida? Fala com a gente.</p>
            <div className="flex gap-2">
              <a
                href={SUPPORT_WHATSAPP_URL}
                target="_blank"
                rel="noopener noreferrer"
                className="flex items-center gap-1.5 rounded-lg border border-gray-200 px-3 py-1.5 text-xs font-medium text-gray-700 hover:bg-gray-50 dark:border-white/10 dark:text-stone-300 dark:hover:bg-white/5"
              >
                <MessageCircle className="h-3.5 w-3.5 text-sage-600 dark:text-sage-400" />
                WhatsApp
              </a>
              <a
                href={`mailto:${SUPPORT_EMAIL}`}
                className="flex items-center gap-1.5 rounded-lg border border-gray-200 px-3 py-1.5 text-xs font-medium text-gray-700 hover:bg-gray-50 dark:border-white/10 dark:text-stone-300 dark:hover:bg-white/5"
              >
                <Mail className="h-3.5 w-3.5 text-brand-600 dark:text-brand-400" />
                Email
              </a>
            </div>
          </div>
        </div>
      </main>
    </div>
  )
}
