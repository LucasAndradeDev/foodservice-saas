import { Mail, MessageCircle, TriangleAlert } from 'lucide-react'
import { BackLink } from '../components/BackLink'
import { SUPPORT_EMAIL, SUPPORT_WHATSAPP_URL } from '../config/support'
import { Logo } from '../theme/Logo'

const STEPS = [
  {
    title: 'Crie sua conta no Mercado Pago',
    body: (
      <>
        Acesse <strong>mercadopago.com.br</strong> e cadastre-se com o CNPJ do restaurante. Essa conta é sua — é nela
        que o dinheiro dos pagamentos com cartão cai, direto, sem passar pelo Morá.
      </>
    ),
  },
  {
    title: 'Crie uma aplicação no painel',
    body: (
      <>
        Acesse <strong>developers.mercadopago.com</strong>, entre em{' '}
        <code className="rounded bg-gray-100 px-1.5 py-0.5 text-xs dark:bg-white/10">
          Suas integrações → Criar aplicação
        </code>
        , escolha o produto <strong>Checkout Pro</strong>. Pode dar o nome que quiser, ex: "Morá".
      </>
    ),
  },
  {
    title: 'Copie o Access Token de produção',
    body: (
      <>
        Na aplicação criada, abra <code className="rounded bg-gray-100 px-1.5 py-0.5 text-xs dark:bg-white/10">Credenciais</code> e
        troque para a aba <strong>Produtivas</strong> (não a de "Teste"). Copie o Access Token mostrado ali.
      </>
    ),
    warning: 'Trate o Access Token como uma senha — quem tiver esse código consegue gerar e estornar cobranças na sua conta.',
  },
  {
    title: 'Copie a Assinatura secreta',
    body: (
      <>
        Ainda na mesma aplicação, abra{' '}
        <code className="rounded bg-gray-100 px-1.5 py-0.5 text-xs dark:bg-white/10">
          Webhooks → Configurar notificações
        </code>{' '}
        e copie o valor em <strong>Assinatura secreta</strong>, mais abaixo na tela. Se ela pedir uma URL antes de
        mostrar esse valor, pode colar qualquer coisa nesse campo (ex: <code className="rounded bg-gray-100 px-1.5 py-0.5 text-xs dark:bg-white/10">https://exemplo.com</code>)
        — o Morá nunca usa a URL cadastrada aqui; ele já manda a URL certa, identificando o seu restaurante, em cada
        cobrança que gera. Só a Assinatura secreta importa.
      </>
    ),
    warning: 'Também é uma senha — quem tiver esse código pode forjar avisos de pagamento falso.',
  },
  {
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
  return (
    <div className="min-h-screen bg-gray-50 dark:bg-stone-950">
      <main className="mx-auto max-w-3xl px-4 py-10">
        <BackLink to="/settings" className="mb-6">
          Voltar
        </BackLink>

        <Logo className="mx-auto mb-8 block h-20 w-auto" />

        <div className="rounded-2xl border border-gray-200 bg-white p-6 shadow-sm dark:border-stone-800 dark:bg-stone-900 sm:p-8">
          <p className="mb-1 text-xs font-semibold uppercase tracking-wide text-sage-600 dark:text-sage-400">
            Guia rápido · 5 passos
          </p>
          <h1 className="mb-2 text-2xl font-bold text-gray-800 dark:text-white">Como ativar o pagamento com cartão</h1>
          <p className="mb-8 text-sm leading-relaxed text-gray-600 dark:text-stone-400">
            Conecte a conta Mercado Pago do seu restaurante e o cliente passa a pagar no cartão pelo próprio celular
            (QR Code no Caixa, ou direto no cardápio digital) — o dinheiro cai <strong>direto na sua conta</strong>,
            sem passar pelo Morá. Leva uns 10 minutos e só precisa ser feito uma vez.
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
          </ol>

          <div className="mt-8 rounded-xl border border-sage-300 bg-sage-100 p-4 dark:border-sage-500/30 dark:bg-sage-500/10">
            <h2 className="mb-1 text-sm font-semibold text-sage-700 dark:text-sage-400">Pronto — cartão ativo</h2>
            <p className="text-sm leading-relaxed text-gray-700 dark:text-stone-300">
              A partir de agora, o Caixa e o cardápio digital passam a mostrar a opção de cobrar no cartão. Cartão
              recusado mostra uma mensagem específica pro cliente tentar de novo — a comanda nunca fica travada. Se
              algum aviso de pagamento falhar por qualquer motivo, dá pra marcar como paga na mão, do jeito que já
              funciona hoje.
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
