import { Check, CreditCard, ExternalLink, Mail, MessageCircle, TriangleAlert } from 'lucide-react'
import { useEffect, useState } from 'react'
import { BackLink } from '../components/BackLink'
import { SUPPORT_EMAIL, SUPPORT_WHATSAPP_URL } from '../config/support'
import { Logo } from '../theme/Logo'

const MERCADO_PAGO_SIGNUP_URL = 'https://www.mercadopago.com.br'
const MERCADO_PAGO_PANEL_URL = 'https://www.mercadopago.com.br/developers/panel'
const PROGRESS_STORAGE_KEY = 'mora:card-setup-guide-progress'

function ExternalLinkButton({ href, children }: { href: string; children: React.ReactNode }) {
  return (
    <a
      href={href}
      target="_blank"
      rel="noopener noreferrer"
      className="inline-flex items-center gap-1.5 rounded-lg bg-brand-600 px-3 py-1.5 text-xs font-semibold text-white transition-colors hover:bg-brand-700"
    >
      {children}
      <ExternalLink className="h-3.5 w-3.5" />
    </a>
  )
}

interface Step {
  id: string
  title: string
  body: React.ReactNode
  warning?: React.ReactNode
  action?: React.ReactNode
}

const STEPS: Step[] = [
  {
    id: 'create-account',
    title: 'Crie sua conta no Mercado Pago',
    body: (
      <>
        Cadastre-se com o CNPJ do restaurante. Essa conta é sua: é nela que o dinheiro dos pagamentos com cartão cai,
        direto, sem passar pelo Morá.
      </>
    ),
    action: <ExternalLinkButton href={MERCADO_PAGO_SIGNUP_URL}>Criar conta no Mercado Pago</ExternalLinkButton>,
  },
  {
    id: 'create-app',
    title: 'Crie uma aplicação no painel',
    body: (
      <>
        Acesse <strong>Suas integrações → Criar aplicação</strong> e escolha o produto <strong>Checkout Pro</strong>.
        Pode dar o nome que quiser, ex.: "Morá".
      </>
    ),
    action: <ExternalLinkButton href={MERCADO_PAGO_PANEL_URL}>Abrir painel Mercado Pago</ExternalLinkButton>,
  },
  {
    id: 'access-token',
    title: 'Copie o Access Token de produção',
    body: (
      <>
        Na aplicação criada, abra <strong>Credenciais</strong> e olhe o alternador entre as abas{' '}
        <strong>"Teste"</strong> e <strong>"Produtivas"</strong>. Se ficar na aba de Teste, o Access Token gerado{' '}
        <strong>não funciona</strong> pra receber pagamentos reais, só simulações. Troque pra{' '}
        <strong>Produtivas</strong> e copie o Access Token mostrado ali. Se for a primeira vez, o Mercado Pago pode
        pedir alguns dados do negócio antes de liberar a credencial de produção.
      </>
    ),
    warning: 'Trate o Access Token como uma senha: quem tiver esse código consegue gerar e estornar cobranças na sua conta.',
    action: <ExternalLinkButton href={MERCADO_PAGO_PANEL_URL}>Abrir painel Mercado Pago</ExternalLinkButton>,
  },
  {
    id: 'webhook-secret',
    title: 'Copie a Assinatura secreta',
    body: (
      <>
        Ainda na mesma aplicação, abra <strong>Webhooks → Configurar notificações</strong> e copie o valor em{' '}
        <strong>Assinatura secreta</strong>, mais abaixo na tela.
        <br />
        <br />
        Se ela pedir uma URL antes de mostrar esse valor, pode colar qualquer coisa nesse campo (ex.:{' '}
        <code className="rounded bg-gray-100 px-1.5 py-0.5 text-xs dark:bg-white/10">https://exemplo.com</code>): o
        Morá nunca usa a URL cadastrada aqui, ele já manda a URL certa, identificando o seu restaurante, em cada
        cobrança que gera. Só a Assinatura secreta importa.
      </>
    ),
    warning: 'Também é uma senha: quem tiver esse código pode forjar avisos de pagamento falso.',
    action: <ExternalLinkButton href={MERCADO_PAGO_PANEL_URL}>Abrir painel Mercado Pago</ExternalLinkButton>,
  },
  {
    id: 'paste-in-mora',
    title: 'Cole os dois no Morá',
    body: (
      <>
        No Morá, vá em{' '}
        <code className="rounded bg-gray-100 px-1.5 py-0.5 text-xs dark:bg-white/10">
          Configurações → Pagamento com cartão (Mercado Pago)
        </code>
        , cole o Access Token e a Assinatura secreta nos dois campos e clique em <strong>Salvar</strong>.
      </>
    ),
  },
]

export function CardSetupGuidePage() {
  const [completed, setCompleted] = useState<Set<string>>(() => {
    try {
      const stored = localStorage.getItem(PROGRESS_STORAGE_KEY)
      return stored ? new Set(JSON.parse(stored)) : new Set()
    } catch {
      return new Set()
    }
  })

  useEffect(() => {
    try {
      localStorage.setItem(PROGRESS_STORAGE_KEY, JSON.stringify([...completed]))
    } catch {
      // Best-effort only - a private window or blocked storage just means progress isn't remembered.
    }
  }, [completed])

  function toggleStep(id: string) {
    setCompleted((prev) => {
      const next = new Set(prev)
      if (next.has(id)) {
        next.delete(id)
      } else {
        next.add(id)
      }
      return next
    })
  }

  const totalSteps = STEPS.length
  const doneCount = completed.size
  const progressPercent = Math.round((doneCount / totalSteps) * 100)

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-stone-950">
      <main className="mx-auto max-w-3xl px-4 py-10">
        <BackLink to="/settings" className="mb-6">
          Voltar
        </BackLink>

        <Logo className="mx-auto mb-8 block h-20 w-auto" />

        <div className="rounded-2xl border border-gray-200 bg-white p-6 shadow-sm dark:border-stone-800 dark:bg-stone-900 sm:p-8">
          <p className="mb-1 text-xs font-semibold uppercase tracking-wide text-sage-600 dark:text-sage-400">
            Guia rápido · {totalSteps} passos
          </p>
          <h1 className="mb-2 text-2xl font-bold text-gray-800 dark:text-white">Como ativar o pagamento com cartão</h1>
          <p className="mb-4 text-sm leading-relaxed text-gray-600 dark:text-stone-400">
            Conecte a conta Mercado Pago do seu restaurante e o cliente passa a pagar no cartão pelo próprio celular
            (QR Code no Caixa, ou direto no cardápio digital), e o dinheiro cai <strong>direto na sua conta</strong>,
            sem passar pelo Morá. Só precisa ser feito uma vez, e leva uns 10 minutos.
          </p>

          <div className="mb-8 flex items-center gap-3">
            <div className="h-2 flex-1 overflow-hidden rounded-full bg-gray-100 dark:bg-white/10">
              <div
                className="h-full rounded-full bg-sage-500 transition-all duration-300"
                style={{ width: `${progressPercent}%` }}
              />
            </div>
            <span className="shrink-0 text-xs font-medium text-gray-500 dark:text-stone-400">
              {doneCount}/{totalSteps} concluídos
            </span>
          </div>

          <ol className="space-y-6">
            {STEPS.map((step, index) => {
              const isDone = completed.has(step.id)
              return (
                <li key={step.id} className="flex gap-4 border-t border-gray-100 pt-6 first:border-t-0 first:pt-0 dark:border-white/10">
                  <button
                    type="button"
                    onClick={() => toggleStep(step.id)}
                    aria-pressed={isDone}
                    aria-label={isDone ? 'Marcar passo como não concluído' : 'Marcar passo como concluído'}
                    className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-sm font-bold transition-colors ${
                      isDone
                        ? 'bg-sage-500 text-white'
                        : 'bg-brand-100 text-brand-600 hover:bg-brand-200 dark:bg-brand-500/15 dark:text-brand-400 dark:hover:bg-brand-500/25'
                    }`}
                  >
                    {isDone ? <Check className="h-4 w-4" /> : index + 1}
                  </button>
                  <div className="min-w-0 flex-1">
                    <h2
                      className={`mb-1 text-sm font-semibold ${
                        isDone ? 'text-gray-400 line-through dark:text-stone-600' : 'text-gray-800 dark:text-white'
                      }`}
                    >
                      {step.title}
                    </h2>
                    <div
                      className={`text-sm leading-relaxed ${
                        isDone ? 'text-gray-400 dark:text-stone-600' : 'text-gray-600 dark:text-stone-400'
                      }`}
                    >
                      {step.body}
                    </div>
                    {step.warning && (
                      <div className="mt-3 flex items-start gap-2 rounded-lg border border-wine-300 bg-wine-100 px-3 py-2 text-xs text-wine-700 dark:border-wine-700 dark:bg-wine-500/10 dark:text-wine-400">
                        <TriangleAlert className="mt-0.5 h-3.5 w-3.5 shrink-0" />
                        <span>{step.warning}</span>
                      </div>
                    )}
                    {step.action && <div className="mt-3">{step.action}</div>}
                  </div>
                </li>
              )
            })}
          </ol>

          {doneCount === totalSteps && (
            <div className="mt-8 rounded-xl border border-sage-300 bg-sage-100 p-4 dark:border-sage-500/30 dark:bg-sage-500/10">
              <h2 className="mb-1 flex items-center gap-1.5 text-sm font-semibold text-sage-700 dark:text-sage-400">
                <CreditCard className="h-4 w-4" />
                Pronto! Cartão ativo
              </h2>
              <p className="text-sm leading-relaxed text-gray-700 dark:text-stone-300">
                A partir de agora, o Caixa e o cardápio digital passam a mostrar a opção de cobrar no cartão. Cartão
                recusado mostra uma mensagem específica pro cliente tentar de novo, a comanda nunca fica travada. Se
                algum aviso de pagamento falhar por qualquer motivo, dá pra marcar como paga na mão, do jeito que já
                funciona hoje.
              </p>
            </div>
          )}

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
