import { Check, Copy, ExternalLink, Landmark, Mail, MessageCircle, TriangleAlert } from 'lucide-react'
import { useEffect, useState } from 'react'
import { BackLink } from '../components/BackLink'
import { SUPPORT_EMAIL, SUPPORT_WHATSAPP_URL } from '../config/support'
import { Logo } from '../theme/Logo'

const WEBHOOK_URL = 'https://mora-backend-ubuw.onrender.com/api/v1/public/payments/webhook'
const WOOVI_SIGNUP_URL = 'https://woovi.com'
const WOOVI_DASHBOARD_URL = 'https://app.woovi.com'
const PROGRESS_STORAGE_KEY = 'mora:pix-setup-guide-progress'

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
    title: 'Crie sua conta na Woovi',
    body: (
      <>
        Cadastre-se com o CNPJ do restaurante. A aprovação costuma sair em minutos. Essa conta é sua: é nela que o
        dinheiro dos pagamentos Pix cai, e só você tem acesso.
      </>
    ),
    action: <ExternalLinkButton href={WOOVI_SIGNUP_URL}>Criar conta na Woovi</ExternalLinkButton>,
  },
  {
    id: 'confirm-production',
    title: 'Confirme que está no ambiente de produção',
    body: (
      <>
        Antes de mexer em qualquer coisa, olhe o topo da tela do painel da Woovi. Se aparecer uma faixa escrita{' '}
        <strong>"Ambiente de Testes"</strong>, você está no modo sandbox: dados e saldo ali são simulados, e qualquer
        chave gerada nesse modo <strong>não funciona</strong> pra receber pagamentos reais. Procure o alternador entre
        ambiente de testes e produção (geralmente perto do nome da empresa, no topo, ou em{' '}
        <span className="whitespace-nowrap">Configurações da Empresa</span>) e troque pra produção antes de continuar.
      </>
    ),
    warning: 'Se pular esse passo, a chave que você vai gerar não vai gerar cobranças reais no cardápio do Morá.',
  },
  {
    id: 'bank-account',
    title: 'Cadastre a conta bancária do restaurante',
    body: (
      <>
        No menu lateral da Woovi, abra <strong>Contas → Registros de contas</strong> e comece o cadastro da conta
        bancária que vai receber o dinheiro das cobranças Pix. Esse passo é obrigatório antes do próximo: sem uma
        conta bancária aprovada, o campo{' '}
        <span className="whitespace-nowrap">"Conta bancária atrelada à aplicação"</span> do próximo passo aparece
        vazio ("Sem itens") e não deixa continuar.
        <br />
        <br />
        Diferente do resto do guia, esse passo não é rápido: a Woovi pede uma verificação completa da empresa (Dados
        da Empresa, Endereço, Contrato Social, BC Protege+ Empresa, Sócios e Documentos, Termos, Revisão) antes de
        aprovar a conta. É o processo normal de abertura de conta em qualquer instituição financeira. Separe antes de
        começar: CNPJ, Contrato Social e os dados dos sócios. A aprovação não é instantânea, então dá pra deixar esse
        passo rodando em paralelo enquanto configura o resto.
      </>
    ),
    warning: 'Esse cadastro só existe no ambiente de produção da Woovi. Não aparece no Ambiente de Testes.',
    action: <ExternalLinkButton href={WOOVI_DASHBOARD_URL}>Abrir painel Woovi</ExternalLinkButton>,
  },
  {
    id: 'create-app',
    title: 'Crie uma aplicação em API/Plugins',
    body: (
      <>
        No menu lateral, abra <strong>API/Plugins</strong> e clique em <strong>+ Nova API/Plugin</strong>. Preencha
        assim:
        <ul className="mt-2 list-disc space-y-1.5 pl-5">
          <li>
            <strong>Nome:</strong> qualquer um, ex.: "Morá".
          </li>
          <li>
            <strong>Tipo:</strong> marque <strong>API REST</strong> (não Plugin, nem Oracle).
          </li>
          <li>
            <strong>Conta bancária atrelada à aplicação:</strong> selecione a conta que você cadastrou no passo
            anterior: é pra ela que o dinheiro dessa aplicação específica vai cair.
          </li>
          <li>
            <strong>Escopos da aplicação:</strong> essas são as permissões que essa chave vai ter na API da Woovi.
            Marque as relacionadas a <strong>cobrança/Pix</strong> (criar e consultar cobranças). Não precisa marcar
            permissões de saque/pagamento, o Morá não usa isso.
          </li>
        </ul>
      </>
    ),
    action: <ExternalLinkButton href={WOOVI_DASHBOARD_URL}>Abrir painel Woovi</ExternalLinkButton>,
  },
  {
    id: 'copy-appid',
    title: 'Copie o AppID',
    body: (
      <>
        Depois de salvar, abra a aplicação que você acabou de criar e procure a linha{' '}
        <code className="rounded bg-gray-100 px-1.5 py-0.5 text-xs dark:bg-white/10">Autorização (AppID)</code> e copie
        o código mostrado ali.
      </>
    ),
    warning: 'Trate o AppID como uma senha: quem tiver esse código consegue gerar cobranças na sua conta.',
  },
  {
    id: 'paste-appid',
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

  function handleCopy() {
    navigator.clipboard.writeText(WEBHOOK_URL)
    setCopied(true)
    setTimeout(() => setCopied(false), 1800)
  }

  const totalSteps = STEPS.length + 1 // +1 for the webhook step, rendered separately below
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
          <h1 className="mb-2 text-2xl font-bold text-gray-800 dark:text-white">Como ativar o Pix</h1>
          <p className="mb-4 text-sm leading-relaxed text-gray-600 dark:text-stone-400">
            Conecte a conta Woovi do seu restaurante e o cliente passa a pagar pelo próprio celular, escaneando um QR
            Code, e o dinheiro cai <strong>direto na sua conta</strong>, sem passar pelo Morá. Só precisa ser feito uma
            vez: a parte de configuração leva uns 10 minutos, mas a aprovação da conta bancária pela Woovi (passo 3)
            pode levar mais tempo.
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

            <li className="flex gap-4 border-t border-gray-100 pt-6 dark:border-white/10">
              <button
                type="button"
                onClick={() => toggleStep('webhook')}
                aria-pressed={completed.has('webhook')}
                aria-label={completed.has('webhook') ? 'Marcar passo como não concluído' : 'Marcar passo como concluído'}
                className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-sm font-bold transition-colors ${
                  completed.has('webhook')
                    ? 'bg-sage-500 text-white'
                    : 'bg-brand-100 text-brand-600 hover:bg-brand-200 dark:bg-brand-500/15 dark:text-brand-400 dark:hover:bg-brand-500/25'
                }`}
              >
                {completed.has('webhook') ? <Check className="h-4 w-4" /> : STEPS.length + 1}
              </button>
              <div className="min-w-0 flex-1">
                <h2
                  className={`mb-1 text-sm font-semibold ${
                    completed.has('webhook') ? 'text-gray-400 line-through dark:text-stone-600' : 'text-gray-800 dark:text-white'
                  }`}
                >
                  Avise a Woovi onde confirmar o pagamento
                </h2>
                <p
                  className={`mb-3 text-sm leading-relaxed ${
                    completed.has('webhook') ? 'text-gray-400 dark:text-stone-600' : 'text-gray-600 dark:text-stone-400'
                  }`}
                >
                  Ainda na aplicação criada no passo 4, abra a aba{' '}
                  <code className="rounded bg-gray-100 px-1.5 py-0.5 text-xs dark:bg-white/10">Webhooks</code> e cole
                  esta URL (é fixa, sempre a mesma, não muda):
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
                <p
                  className={`mt-3 text-sm leading-relaxed ${
                    completed.has('webhook') ? 'text-gray-400 dark:text-stone-600' : 'text-gray-600 dark:text-stone-400'
                  }`}
                >
                  É esse aviso que fecha a comanda sozinha assim que o cliente paga. Sem ele, os pagamentos continuam
                  acontecendo, só que alguém precisa confirmar na mão.
                </p>
              </div>
            </li>
          </ol>

          {doneCount === totalSteps && (
            <div className="mt-8 rounded-xl border border-sage-300 bg-sage-100 p-4 dark:border-sage-500/30 dark:bg-sage-500/10">
              <h2 className="mb-1 flex items-center gap-1.5 text-sm font-semibold text-sage-700 dark:text-sage-400">
                <Landmark className="h-4 w-4" />
                Pronto! Pix ativo
              </h2>
              <p className="text-sm leading-relaxed text-gray-700 dark:text-stone-300">
                A partir de agora, o Caixa e o cardápio digital passam a mostrar a opção de gerar QR Code Pix. Se
                algum aviso de pagamento falhar por qualquer motivo, a comanda nunca fica travada: dá pra marcar como
                paga na mão, do jeito que já funciona hoje.
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
