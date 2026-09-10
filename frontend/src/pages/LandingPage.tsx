import { AnimatePresence, motion, type Variants } from 'framer-motion'
import {
  ArrowRight,
  ArrowUp,
  BarChart3,
  Check,
  CreditCard,
  Lock,
  Mail,
  Menu,
  MessageCircle,
  QrCode,
  ShieldCheck,
  Smartphone,
  TrendingUp,
  UtensilsCrossed,
  Wallet,
  X,
} from 'lucide-react'
import type { ComponentType, ReactNode } from 'react'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Button } from '../components/Button'
import { SALES_WHATSAPP_URL, SUPPORT_EMAIL } from '../config/support'
import { Logo } from '../theme/Logo'
import { ThemeToggleButton } from '../theme/ThemeToggleButton'

const NAV_LINKS = [
  { label: 'Módulos', href: '#modulos' },
  { label: 'Por que Morá', href: '#por-que' },
  { label: 'Contato', href: '#contato' },
]

const footerLinkClass =
  'relative inline-block w-fit text-gray-600 transition hover:text-gray-900 after:absolute after:-bottom-0.5 after:left-0 after:h-px after:w-0 after:bg-gray-900 after:transition-all after:duration-200 hover:after:w-full dark:text-stone-400 dark:hover:text-white dark:after:bg-white'

const STATS = [
  { value: '5', label: 'perfis de acesso, cada um vendo só o que precisa' },
  { value: '2', label: 'formas de pagamento direto pelo celular do cliente' },
  { value: '1', label: 'sistema só pra mesa, cardápio, delivery e caixa' },
]

const WHY_MORA = [
  {
    icon: UtensilsCrossed,
    title: 'Tudo em um sistema só',
    description:
      'Mesas, cardápio, delivery, pagamento e relatório na mesma tela, sem integrar cinco ferramentas nem exportar planilha de um app pro outro.',
  },
  {
    icon: Smartphone,
    title: 'Fácil pra equipe usar',
    description:
      'Interface simples, pensada pra quem tá no corre do salão, não pra quem tem tempo de fazer curso. Qualquer garçom aprende em poucos minutos.',
  },
  {
    icon: BarChart3,
    title: 'Pensado pro seu negócio',
    description:
      'Hamburgueria, pizzaria, bar, churrascaria ou casa de sushi: qualquer restaurante com atendimento em mesa encontra seu fluxo aqui dentro.',
  },
]

// Shared scroll-reveal variants: a parent fades/slides in and staggers its motion children,
// which only need `variants={itemVariants}` (no own initial/animate) to inherit the sequence.
const containerVariants: Variants = {
  hidden: { opacity: 0, y: 28 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.5, ease: 'easeOut', staggerChildren: 0.08 } },
}

const itemVariants: Variants = {
  hidden: { opacity: 0, y: 14 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.4, ease: 'easeOut' } },
}

const scaleItemVariants: Variants = {
  hidden: { opacity: 0, scale: 0.85 },
  visible: { opacity: 1, scale: 1, transition: { duration: 0.35, ease: 'easeOut' } },
}

const barVariants: Variants = {
  hidden: { scaleY: 0, opacity: 0 },
  visible: { scaleY: 1, opacity: 1, transition: { duration: 0.5, ease: 'easeOut' } },
}

interface ModuleFeature {
  title: string
  description: string
}

interface Module {
  eyebrow: string
  title: string
  description: string
  icon: ComponentType<{ className?: string }>
  features: ModuleFeature[]
  scenario: string
  visual: ReactNode
}

function BrowserFrame({ children, path }: { children: ReactNode; path: string }) {
  return (
    <motion.div
      initial="hidden"
      whileInView="visible"
      viewport={{ once: true, margin: '-60px' }}
      variants={containerVariants}
      className="overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-xl shadow-gray-900/5 dark:border-white/10 dark:bg-stone-900 dark:shadow-black/20"
    >
      <div className="flex items-center gap-2 border-b border-gray-100 bg-gray-50 px-4 py-2.5 dark:border-white/10 dark:bg-stone-950/60">
        <span className="h-2.5 w-2.5 rounded-full bg-gray-300 dark:bg-white/15" />
        <span className="h-2.5 w-2.5 rounded-full bg-gray-300 dark:bg-white/15" />
        <span className="h-2.5 w-2.5 rounded-full bg-gray-300 dark:bg-white/15" />
        <span className="ml-3 rounded-md bg-white px-2.5 py-0.5 text-[11px] text-gray-400 dark:bg-stone-900 dark:text-stone-500">
          {path}
        </span>
      </div>
      <div className="p-4">{children}</div>
    </motion.div>
  )
}

const TABLE_STATUS: { label: string; className: string }[] = [
  { label: '01', className: 'bg-sage-100 text-sage-700 dark:bg-sage-500/10 dark:text-sage-400' },
  { label: '02', className: 'bg-brand-100 text-brand-700 dark:bg-brand-500/10 dark:text-brand-400' },
  { label: '03', className: 'bg-sage-100 text-sage-700 dark:bg-sage-500/10 dark:text-sage-400' },
  { label: '04', className: 'bg-gray-100 text-gray-400 dark:bg-white/5 dark:text-stone-500' },
  { label: '05', className: 'bg-brand-100 text-brand-700 dark:bg-brand-500/10 dark:text-brand-400' },
  { label: '06', className: 'bg-sage-100 text-sage-700 dark:bg-sage-500/10 dark:text-sage-400' },
]

function DashboardMockup() {
  return (
    <BrowserFrame path="app.mora.com/tables">
      <div className="grid grid-cols-3 gap-2.5">
        {TABLE_STATUS.map((table) => (
          <motion.div
            key={table.label}
            variants={scaleItemVariants}
            className={`flex aspect-square flex-col items-center justify-center rounded-xl text-sm font-bold ${table.className}`}
          >
            {table.label}
          </motion.div>
        ))}
      </div>
      <div className="mt-4 grid grid-cols-3 gap-2.5 text-center">
        <motion.div variants={itemVariants} className="rounded-lg bg-gray-50 px-2 py-2 dark:bg-white/5">
          <p className="text-sm font-bold text-gray-900 dark:text-white">R$ 2.480</p>
          <p className="text-[11px] text-gray-500 dark:text-stone-500">Hoje</p>
        </motion.div>
        <motion.div variants={itemVariants} className="rounded-lg bg-gray-50 px-2 py-2 dark:bg-white/5">
          <p className="text-sm font-bold text-gray-900 dark:text-white">18</p>
          <p className="text-[11px] text-gray-500 dark:text-stone-500">Pedidos</p>
        </motion.div>
        <motion.div variants={itemVariants} className="rounded-lg bg-gray-50 px-2 py-2 dark:bg-white/5">
          <p className="text-sm font-bold text-gray-900 dark:text-white">4/6</p>
          <p className="text-[11px] text-gray-500 dark:text-stone-500">Ocupadas</p>
        </motion.div>
      </div>
    </BrowserFrame>
  )
}

function KitchenMockup() {
  const items = [
    { name: '2x Hambúrguer artesanal', status: 'Preparando', tone: 'text-brand-700 dark:text-brand-400' },
    { name: '1x Batata rústica', status: 'Pronto', tone: 'text-sage-700 dark:text-sage-400' },
    { name: '3x Suco natural', status: 'Recebido', tone: 'text-gray-500 dark:text-stone-400' },
  ]
  return (
    <BrowserFrame path="app.mora.com/kitchen">
      <div className="space-y-2">
        {items.map((item) => (
          <motion.div
            key={item.name}
            variants={itemVariants}
            whileHover={{ x: 4 }}
            className="flex items-center justify-between rounded-lg border border-gray-100 px-3 py-2.5 dark:border-white/10"
          >
            <span className="text-sm font-medium text-gray-800 dark:text-stone-200">{item.name}</span>
            <span className={`text-xs font-semibold ${item.tone}`}>{item.status}</span>
          </motion.div>
        ))}
      </div>
    </BrowserFrame>
  )
}

function PhoneMenuMockup() {
  return (
    <motion.div
      initial="hidden"
      whileInView="visible"
      viewport={{ once: true, margin: '-60px' }}
      variants={containerVariants}
      className="flex justify-center"
    >
      <div className="w-56 rounded-[2rem] border-8 border-gray-900 bg-white shadow-xl dark:border-stone-100 dark:bg-stone-900">
        <div className="flex items-center justify-between px-3 pt-3">
          <QrCode className="h-4 w-4 text-gray-400 dark:text-stone-500" />
          <span className="text-[10px] font-semibold uppercase tracking-wide text-gray-400 dark:text-stone-500">Mesa 07</span>
        </div>
        <div className="space-y-2 px-3 py-3">
          {['Hambúrguer artesanal', 'Batata rústica', 'Suco natural'].map((item, index) => (
            <motion.div
              key={item}
              variants={itemVariants}
              className="flex items-center gap-2 rounded-lg bg-gray-50 p-2 dark:bg-white/5"
            >
              <span className="h-8 w-8 shrink-0 rounded-md bg-brand-100 dark:bg-brand-500/20" />
              <div className="flex-1">
                <p className="text-[11px] font-semibold leading-tight text-gray-800 dark:text-stone-200">{item}</p>
                <p className="text-[10px] text-gray-400 dark:text-stone-500">R$ {(24 + index * 6).toFixed(2)}</p>
              </div>
            </motion.div>
          ))}
        </div>
        <div className="flex gap-2 px-3 pb-4">
          <motion.span
            variants={itemVariants}
            whileTap={{ scale: 0.95 }}
            className="flex flex-1 items-center justify-center rounded-lg bg-brand-600 px-2 py-1.5 text-center text-[10px] font-semibold text-white"
          >
            Chamar garçom
          </motion.span>
          <motion.span
            variants={itemVariants}
            whileTap={{ scale: 0.95 }}
            className="flex flex-1 items-center justify-center rounded-lg border border-gray-200 px-2 py-1.5 text-center text-[10px] font-semibold text-gray-600 dark:border-white/15 dark:text-stone-300"
          >
            Pedir a conta
          </motion.span>
        </div>
      </div>
    </motion.div>
  )
}

function PaymentSplitMockup() {
  const people = [
    { name: 'Pessoa 1', value: 'R$ 42,30', paid: true },
    { name: 'Pessoa 2', value: 'R$ 42,30', paid: true },
    { name: 'Pessoa 3', value: 'R$ 42,30', paid: false },
  ]
  return (
    <BrowserFrame path="app.mora.com/checkout">
      <div className="space-y-2">
        {people.map((person) => (
          <motion.div
            key={person.name}
            variants={itemVariants}
            className="flex items-center justify-between rounded-lg border border-gray-100 px-3 py-2.5 dark:border-white/10"
          >
            <span className="text-sm font-medium text-gray-800 dark:text-stone-200">{person.name}</span>
            <div className="flex items-center gap-2">
              <span className="text-sm text-gray-600 dark:text-stone-400">{person.value}</span>
              {person.paid ? (
                <motion.span
                  initial={{ scale: 0 }}
                  whileInView={{ scale: 1 }}
                  viewport={{ once: true }}
                  transition={{ type: 'spring', stiffness: 400, damping: 15, delay: 0.3 }}
                  className="flex h-5 w-5 items-center justify-center rounded-full bg-sage-100 text-sage-700 dark:bg-sage-500/10 dark:text-sage-400"
                >
                  <Check className="h-3 w-3" />
                </motion.span>
              ) : (
                <span className="rounded-full bg-brand-100 px-2 py-0.5 text-[10px] font-semibold text-brand-700 dark:bg-brand-500/10 dark:text-brand-400">
                  Pix pendente
                </span>
              )}
            </div>
          </motion.div>
        ))}
      </div>
      <motion.div
        variants={itemVariants}
        className="mt-3 flex items-center justify-between rounded-lg bg-gray-50 px-3 py-2 dark:bg-white/5"
      >
        <span className="text-xs font-semibold uppercase tracking-wide text-gray-500 dark:text-stone-500">2 de 3 pagos</span>
        <CreditCard className="h-4 w-4 text-gray-400 dark:text-stone-500" />
      </motion.div>
    </BrowserFrame>
  )
}

function ReportsMockup() {
  const bars = [40, 65, 50, 80, 60, 95, 70]
  return (
    <BrowserFrame path="app.mora.com/reports">
      <div className="flex items-end justify-between gap-1.5" style={{ height: '96px' }}>
        {bars.map((height, index) => (
          <motion.div
            key={index}
            variants={barVariants}
            style={{ height: `${height}%`, transformOrigin: 'bottom' }}
            className="flex-1 rounded-t-md bg-brand-200 dark:bg-brand-500/25"
          />
        ))}
      </div>
      <motion.div
        variants={itemVariants}
        className="mt-3 flex items-center gap-2 rounded-lg bg-gray-50 px-3 py-2 dark:bg-white/5"
      >
        <TrendingUp className="h-4 w-4 text-sage-600 dark:text-sage-400" />
        <span className="text-xs font-semibold text-gray-700 dark:text-stone-300">Faturamento 18% acima da semana passada</span>
      </motion.div>
    </BrowserFrame>
  )
}

function SecurityMockup() {
  const badges = ['Dados protegidos', 'Backup diário', 'Acesso seguro']
  return (
    <motion.div
      initial="hidden"
      whileInView="visible"
      viewport={{ once: true, margin: '-60px' }}
      variants={containerVariants}
      className="flex flex-col items-center gap-6 py-6"
    >
      <motion.div
        variants={scaleItemVariants}
        animate={{ scale: [1, 1.05, 1] }}
        transition={{ scale: { duration: 2.5, repeat: Infinity, ease: 'easeInOut' } }}
        className="flex h-28 w-28 items-center justify-center rounded-full bg-brand-100 dark:bg-brand-500/10"
      >
        <ShieldCheck className="h-12 w-12 text-brand-700 dark:text-brand-400" />
      </motion.div>
      <div className="flex flex-wrap justify-center gap-2">
        {badges.map((badge) => (
          <motion.span
            key={badge}
            variants={itemVariants}
            className="rounded-full border border-gray-200 bg-white px-3 py-1.5 text-xs font-semibold text-gray-600 shadow-sm dark:border-white/10 dark:bg-stone-900 dark:text-stone-300"
          >
            {badge}
          </motion.span>
        ))}
      </div>
    </motion.div>
  )
}

const MODULES: Module[] = [
  {
    eyebrow: 'Módulo 01',
    title: 'O salão, organizado em tempo real',
    description:
      'Chega de decorar quem pediu o quê. A grade de mesas mostra o status de cada uma ao vivo, e cada comanda guarda o histórico completo da noite.',
    icon: UtensilsCrossed,
    visual: <KitchenMockup />,
    features: [
      { title: 'Grade de mesas por status', description: 'Livre, ocupada ou fechando a conta, agrupadas por área do salão.' },
      { title: 'Comandas e pedidos', description: 'Uma comanda por mesa, vários pedidos ao longo da noite.' },
      { title: 'Cozinha em tempo real', description: 'Fila por status com alerta quando um item passa do tempo de preparo.' },
      { title: 'Alerta de mesa esquecida', description: 'Aviso quando uma mesa ocupada fica tempo demais sem pedido novo.' },
    ],
    scenario:
      'Sexta à noite, salão cheio: o garçom abre a mesa 12 pelo celular, manda dois hambúrgueres pra cozinha e, seis minutos depois, vê o alerta de demora, antes mesmo de o cliente perguntar cadê o pedido.',
  },
  {
    eyebrow: 'Módulo 02',
    title: 'O cliente pede sozinho, sem baixar nada',
    description:
      'Um QR Code na mesa abre o cardápio direto no navegador do celular do cliente. Sem aplicativo, sem cadastro, sem senha.',
    icon: Smartphone,
    visual: <PhoneMenuMockup />,
    features: [
      { title: 'Pedido pelo celular', description: 'O cliente monta o carrinho e acompanha o status em tempo real.' },
      { title: 'Chamar garçom / pedir a conta', description: 'Dois botões resolvem o que hoje é um aceno no ar.' },
      { title: 'Reserva de mesa online', description: 'O sistema acha a mesa certa sozinho e manda um link de acompanhamento.' },
      { title: 'Delivery próprio', description: 'Mesmo cardápio, mesma cozinha, com aviso por WhatsApp quando o pedido sai.' },
    ],
    scenario:
      'O cliente senta, escaneia o QR Code da mesa 7, pede sem esperar o garçom ficar livre, e acompanha pelo próprio celular o momento em que o prato sai da cozinha.',
  },
  {
    eyebrow: 'Módulo 03',
    title: 'Da conta ao caixa, sem conta de cabeça',
    description:
      'Pix e cartão direto no sistema, divisão de conta sem calculadora, e um caixa que fecha batendo com o que está na gaveta.',
    icon: Wallet,
    visual: <PaymentSplitMockup />,
    features: [
      { title: 'Pix e cartão integrados', description: 'Confirmação cai na comanda sozinha, sem conferência manual.' },
      { title: 'Divisão de conta', description: 'Em partes iguais ou valor livre, com mais de uma forma de pagamento.' },
      { title: 'Split por consumo', description: 'Cada pessoa paga exatamente o que pediu, com desconto rateado.' },
      { title: 'Caixa com sangria', description: 'Abertura, fechamento e retiradas conferidas contra o valor contado.' },
    ],
    scenario:
      'Mesa de quatro pede a conta: cada pessoa paga a própria parte pelo Pix, direto do celular. A comanda fecha sozinha assim que a soma bate.',
  },
  {
    eyebrow: 'Módulo 04',
    title: 'Números pra decidir, não só pra olhar',
    description:
      'Um painel pro dia a dia e relatórios pra quem quer entender o negócio: o que vende mais, quando o salão lota, e quanto cada garçom vende.',
    icon: BarChart3,
    visual: <ReportsMockup />,
    features: [
      { title: 'Painel operacional', description: 'Mesas, pedidos em preparo e faturamento do dia, de relance.' },
      { title: 'Margem por produto', description: 'Ficha de custo mostra a margem real de cada venda.' },
      { title: 'Horário de pico', description: 'Mapa de calor por dia e hora, pra saber quando reforçar a equipe.' },
      { title: 'Desempenho por garçom', description: 'Vendas e tempo médio calculados do histórico real de pedidos.' },
    ],
    scenario:
      'Toda segunda de manhã, o dono abre o relatório da semana e vê, em um minuto, qual produto deu mais lucro e em que horário precisou de mais um garçom.',
  },
  {
    eyebrow: 'Módulo 05',
    title: 'Feito pra te dar tranquilidade',
    description: 'O que o cliente final não vê, mas você sente falta quando não tem.',
    icon: ShieldCheck,
    visual: <SecurityMockup />,
    features: [
      { title: 'Seus dados só seus', description: 'As informações do seu restaurante nunca se misturam com as de outro cliente do sistema.' },
      { title: 'Cópia de segurança automática', description: 'Todos os dias, uma cópia do seu histórico é guardada em local separado.' },
      { title: 'Resolvido antes de virar problema', description: 'Qualquer falha é avisada pra equipe na hora, antes de chegar no seu salão.' },
      { title: 'Acesso protegido', description: 'Login individual pra cada funcionário, com bloqueio automático em tentativas suspeitas.' },
    ],
    scenario:
      'Se o servidor cair no meio do jantar de sábado, a cópia da noite anterior já está guardada em outro lugar, e qualquer erro chega pra equipe antes de virar mesa parada.',
  },
]

export function LandingPage() {
  const navigate = useNavigate()
  const [mobileOpen, setMobileOpen] = useState(false)

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-stone-950">
      <motion.header
        initial={{ y: -24, opacity: 0 }}
        animate={{ y: 0, opacity: 1 }}
        transition={{ duration: 0.4, ease: 'easeOut' }}
        className="sticky top-0 z-20 px-3 pt-3 sm:px-6 sm:pt-4"
      >
        <div className="mx-auto flex max-w-5xl items-center justify-between gap-2 rounded-2xl border border-gray-200/70 bg-white/80 px-4 py-2.5 shadow-lg shadow-gray-900/5 backdrop-blur-md dark:border-white/10 dark:bg-stone-900/80">
          <Logo className="h-7 w-auto" />
          <nav className="hidden items-center gap-1 sm:flex">
            {NAV_LINKS.map((link) => (
              <a
                key={link.href}
                href={link.href}
                className="rounded-full px-3 py-1.5 text-sm font-medium text-gray-600 transition hover:bg-gray-100 hover:text-gray-900 dark:text-stone-400 dark:hover:bg-white/10 dark:hover:text-white"
              >
                {link.label}
              </a>
            ))}
          </nav>
          <div className="hidden items-center gap-2 sm:flex">
            <ThemeToggleButton />
            <Link
              to="/login"
              className="rounded-full px-3 py-2 text-sm font-medium text-gray-700 transition hover:bg-gray-100 dark:text-stone-300 dark:hover:bg-white/10"
            >
              Entrar
            </Link>
            <Button onClick={() => navigate('/register')} className="rounded-full">
              Criar minha conta
            </Button>
          </div>
          <div className="flex items-center gap-1 sm:hidden">
            <ThemeToggleButton />
            <button
              type="button"
              onClick={() => setMobileOpen((open) => !open)}
              aria-label={mobileOpen ? 'Fechar menu' : 'Abrir menu'}
              className="flex h-9 w-9 items-center justify-center rounded-full text-gray-600 transition hover:bg-gray-100 dark:text-stone-300 dark:hover:bg-white/10"
            >
              {mobileOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
            </button>
          </div>
        </div>

        <AnimatePresence>
          {mobileOpen && (
            <motion.div
              initial={{ opacity: 0, y: -8, scale: 0.98, height: 0 }}
              animate={{ opacity: 1, y: 0, scale: 1, height: 'auto' }}
              exit={{ opacity: 0, y: -8, scale: 0.98, height: 0 }}
              transition={{ duration: 0.2, ease: 'easeOut' }}
              className="mx-auto mt-2 max-w-5xl overflow-hidden rounded-2xl border border-gray-200/70 bg-white shadow-lg sm:hidden dark:border-white/10 dark:bg-stone-900"
            >
              <div className="p-2">
                {NAV_LINKS.map((link) => (
                  <a
                    key={link.href}
                    href={link.href}
                    onClick={() => setMobileOpen(false)}
                    className="block rounded-xl px-3 py-2.5 text-center text-sm font-medium text-gray-700 transition hover:bg-gray-100 dark:text-stone-200 dark:hover:bg-white/10"
                  >
                    {link.label}
                  </a>
                ))}
                <div className="my-1 border-t border-gray-100 dark:border-white/10" />
                <div className="flex items-center gap-2 px-1 pt-1">
                  <Link
                    to="/login"
                    onClick={() => setMobileOpen(false)}
                    className="flex-1 rounded-full border border-gray-300 px-3.5 py-2 text-center text-sm font-medium text-gray-700 transition hover:bg-gray-50 dark:border-white/15 dark:text-stone-200 dark:hover:bg-white/5"
                  >
                    Entrar
                  </Link>
                  <Button
                    onClick={() => {
                      setMobileOpen(false)
                      navigate('/register')
                    }}
                    className="flex-1 justify-center rounded-full"
                  >
                    Criar conta
                  </Button>
                </div>
              </div>
            </motion.div>
          )}
        </AnimatePresence>
      </motion.header>

      <section className="relative overflow-hidden bg-white dark:bg-stone-950">
        <motion.div
          animate={{ x: [0, 30, 0], y: [0, 20, 0] }}
          transition={{ duration: 18, repeat: Infinity, ease: 'easeInOut' }}
          className="pointer-events-none absolute left-1/2 top-0 h-[36rem] w-[36rem] -translate-x-1/2 -translate-y-1/3 rounded-full bg-brand-100/60 blur-3xl dark:bg-brand-500/10"
        />
        <motion.div
          animate={{ x: [0, -24, 0], y: [0, -16, 0] }}
          transition={{ duration: 14, repeat: Infinity, ease: 'easeInOut' }}
          className="pointer-events-none absolute -right-40 top-1/3 h-[26rem] w-[26rem] rounded-full bg-sage-100/50 blur-3xl dark:bg-sage-500/10"
        />
        <div className="relative mx-auto grid max-w-6xl gap-12 px-6 py-20 sm:py-28 lg:grid-cols-2 lg:items-center">
          <motion.div initial="hidden" animate="visible" variants={containerVariants}>
            <motion.p
              variants={itemVariants}
              className="inline-flex items-center rounded-full border border-gray-200 bg-white px-3 py-1 text-xs font-semibold uppercase tracking-widest text-brand-700 dark:border-white/10 dark:bg-stone-900 dark:text-brand-400"
            >
              Gestão de restaurantes
            </motion.p>
            <motion.h1
              variants={itemVariants}
              className="font-display mt-5 text-4xl font-bold leading-[1.1] tracking-tight text-gray-900 dark:text-white sm:text-5xl lg:text-6xl"
            >
              O sistema completo pra{' '}
              <span className="bg-gradient-to-r from-brand-600 to-brand-400 bg-clip-text text-transparent dark:from-brand-400 dark:to-brand-200">
                tocar seu restaurante
              </span>
            </motion.h1>
            <motion.p variants={itemVariants} className="mt-6 max-w-lg text-lg text-gray-600 dark:text-stone-400">
              Mesas, cozinha, cardápio digital, pagamento e relatório num só sistema. Sem papel, sem planilha, sem grupo de
              WhatsApp pra controlar pedido.
            </motion.p>
            <motion.div variants={itemVariants} className="mt-8 flex flex-wrap items-center gap-3">
              <Button onClick={() => navigate('/register')} className="transition hover:-translate-y-0.5 hover:shadow-lg">
                Criar conta grátis
                <ArrowRight className="h-4 w-4" />
              </Button>
              <a
                href={SALES_WHATSAPP_URL}
                target="_blank"
                rel="noreferrer"
                className="inline-flex items-center gap-2 rounded-lg border border-gray-300 px-3.5 py-2 text-sm font-medium text-gray-700 transition hover:-translate-y-0.5 hover:bg-gray-50 hover:shadow-md dark:border-white/15 dark:text-stone-200 dark:hover:bg-white/5"
              >
                <MessageCircle className="h-4 w-4" />
                Falar no WhatsApp
              </a>
            </motion.div>
          </motion.div>
          <motion.div
            initial={{ opacity: 0, scale: 0.92, x: 24 }}
            animate={{ opacity: 1, scale: 1, x: 0 }}
            transition={{ duration: 0.6, delay: 0.15, ease: 'easeOut' }}
            className="relative"
          >
            <motion.div
              initial={{ opacity: 0, y: 6 }}
              animate={{ opacity: 1, y: [0, -6, 0] }}
              transition={{ opacity: { duration: 0.4, delay: 0.9 }, y: { duration: 3, repeat: Infinity, ease: 'easeInOut', delay: 1 } }}
              className="pointer-events-none absolute -right-3 -top-6 z-10 flex items-center gap-2 rounded-full bg-white px-3 py-2 text-xs font-semibold text-gray-700 shadow-lg dark:bg-stone-800 dark:text-stone-200"
            >
              <motion.span
                animate={{ scale: [1, 1.4, 1], opacity: [1, 0.6, 1] }}
                transition={{ duration: 1.6, repeat: Infinity, ease: 'easeInOut' }}
                className="flex h-2 w-2 rounded-full bg-sage-500"
              />
              Mesa 07 pediu a conta
            </motion.div>
            <motion.div
              initial={{ opacity: 0, y: 6 }}
              animate={{ opacity: 1, y: [0, -6, 0] }}
              transition={{ opacity: { duration: 0.4, delay: 1.1 }, y: { duration: 3.4, repeat: Infinity, ease: 'easeInOut', delay: 1.2 } }}
              className="pointer-events-none absolute -bottom-5 -left-5 z-10 flex items-center gap-2 rounded-full bg-white px-3 py-2 text-xs font-semibold text-gray-700 shadow-lg dark:bg-stone-800 dark:text-stone-200"
            >
              <Check className="h-3.5 w-3.5 text-sage-600 dark:text-sage-400" />
              Pix recebido
            </motion.div>
            <DashboardMockup />
          </motion.div>
        </div>
      </section>

      <motion.section
        initial="hidden"
        whileInView="visible"
        viewport={{ once: true, margin: '-80px' }}
        variants={containerVariants}
        className="relative z-10 mx-auto -mt-10 max-w-5xl px-6"
      >
        <div className="grid grid-cols-1 gap-8 rounded-3xl border border-gray-100 bg-white p-8 shadow-xl shadow-gray-900/5 sm:grid-cols-3 dark:border-white/10 dark:bg-stone-900 dark:shadow-black/20">
          {STATS.map((stat) => (
            <motion.div key={stat.label} variants={itemVariants}>
              <p className="font-display text-3xl font-bold text-gray-900 dark:text-white">{stat.value}</p>
              <p className="mt-1 text-sm text-gray-600 dark:text-stone-400">{stat.label}</p>
            </motion.div>
          ))}
        </div>
      </motion.section>

      <section id="por-que" className="mx-auto max-w-6xl px-6 py-24">
        <motion.div
          initial="hidden"
          whileInView="visible"
          viewport={{ once: true, margin: '-80px' }}
          variants={containerVariants}
        >
          <motion.p
            variants={itemVariants}
            className="text-xs font-semibold uppercase tracking-widest text-brand-600 dark:text-brand-400"
          >
            Por que Morá
          </motion.p>
          <motion.h2
            variants={itemVariants}
            className="font-display mt-3 max-w-xl text-2xl font-bold text-gray-900 dark:text-white sm:text-3xl"
          >
            Feito pra operação real, não só pra demonstração
          </motion.h2>

          <div className="mt-10 grid gap-6 sm:grid-cols-3">
            {WHY_MORA.map((item) => (
              <motion.div
                key={item.title}
                variants={itemVariants}
                whileHover={{ y: -6 }}
                transition={{ duration: 0.2 }}
                className="rounded-2xl border border-gray-100 bg-white p-6 shadow-sm transition-shadow hover:shadow-md dark:border-white/10 dark:bg-stone-900"
              >
                <span className="flex h-10 w-10 items-center justify-center rounded-lg bg-brand-100 text-brand-700 dark:bg-brand-500/10 dark:text-brand-400">
                  <item.icon className="h-5 w-5" />
                </span>
                <p className="mt-4 font-semibold text-gray-900 dark:text-white">{item.title}</p>
                <p className="mt-2 text-sm text-gray-600 dark:text-stone-400">{item.description}</p>
              </motion.div>
            ))}
          </div>
        </motion.div>
      </section>

      <section id="modulos" className="mx-auto max-w-6xl px-6 pb-24">
        <motion.div
          initial="hidden"
          whileInView="visible"
          viewport={{ once: true, margin: '-80px' }}
          variants={containerVariants}
        >
          <motion.p
            variants={itemVariants}
            className="text-xs font-semibold uppercase tracking-widest text-brand-600 dark:text-brand-400"
          >
            Explore o sistema
          </motion.p>
          <motion.h2
            variants={itemVariants}
            className="font-display mt-3 max-w-xl text-2xl font-bold text-gray-900 dark:text-white sm:text-3xl"
          >
            Um módulo pra cada parte da operação
          </motion.h2>
        </motion.div>

        <div className="mt-10 flex flex-col gap-6">
          {MODULES.map((mod, index) => (
            <motion.div
              key={mod.title}
              initial="hidden"
              whileInView="visible"
              viewport={{ once: true, margin: '-80px' }}
              variants={containerVariants}
              className="rounded-3xl border border-gray-100 bg-white p-8 shadow-sm transition-shadow hover:shadow-md sm:p-10 dark:border-white/10 dark:bg-stone-900"
            >
              <div className="grid gap-10 lg:grid-cols-2 lg:items-center">
                <div className={index % 2 === 1 ? 'lg:order-2' : ''}>
                  <motion.div variants={itemVariants} className="flex items-center gap-3">
                    <span className="flex h-10 w-10 items-center justify-center rounded-lg bg-brand-100 text-brand-700 dark:bg-brand-500/10 dark:text-brand-400">
                      <mod.icon className="h-5 w-5" />
                    </span>
                    <p className="text-xs font-semibold uppercase tracking-widest text-brand-600 dark:text-brand-400">
                      {mod.eyebrow}
                    </p>
                  </motion.div>
                  <motion.h3 variants={itemVariants} className="font-display mt-4 text-2xl font-bold text-gray-900 dark:text-white">
                    {mod.title}
                  </motion.h3>
                  <motion.p variants={itemVariants} className="mt-3 text-base text-gray-600 dark:text-stone-400">
                    {mod.description}
                  </motion.p>

                  <div className="mt-6 grid gap-4 sm:grid-cols-2">
                    {mod.features.map((feature) => (
                      <motion.div key={feature.title} variants={itemVariants} className="flex gap-2.5">
                        <Check className="mt-0.5 h-4 w-4 shrink-0 text-brand-600 dark:text-brand-400" />
                        <div>
                          <p className="text-sm font-semibold text-gray-900 dark:text-white">{feature.title}</p>
                          <p className="mt-0.5 text-sm text-gray-600 dark:text-stone-400">{feature.description}</p>
                        </div>
                      </motion.div>
                    ))}
                  </div>

                  <motion.div
                    variants={itemVariants}
                    className="mt-6 rounded-xl border-l-4 border-brand-600 bg-gray-50 p-4 dark:bg-stone-950/60"
                  >
                    <p className="text-xs font-semibold uppercase tracking-widest text-gray-500 dark:text-stone-500">Cenário</p>
                    <p className="mt-2 text-sm italic text-gray-700 dark:text-stone-300">{mod.scenario}</p>
                  </motion.div>
                </div>
                <div className={index % 2 === 1 ? 'lg:order-1' : ''}>{mod.visual}</div>
              </div>
            </motion.div>
          ))}
        </div>
      </section>

      <section id="contato" className="relative overflow-hidden bg-brand-900">
        <motion.div
          animate={{ scale: [1, 1.1, 1] }}
          transition={{ duration: 10, repeat: Infinity, ease: 'easeInOut' }}
          className="pointer-events-none absolute -right-24 -top-24 h-72 w-72 rounded-full bg-brand-600/50"
        />
        <motion.div
          initial="hidden"
          whileInView="visible"
          viewport={{ once: true, margin: '-80px' }}
          variants={containerVariants}
          className="relative mx-auto max-w-4xl px-6 py-20 text-center"
        >
          <motion.h2 variants={itemVariants} className="font-display text-3xl font-bold text-white sm:text-4xl">
            Vamos conversar?
          </motion.h2>
          <motion.p variants={itemVariants} className="mx-auto mt-4 max-w-xl text-brand-50">
            Tire suas dúvidas antes de criar sua conta, ou fale com a gente pra conhecer o Morá de perto.
          </motion.p>
          <motion.div variants={itemVariants} className="mt-8 flex flex-wrap items-center justify-center gap-3">
            <motion.button
              type="button"
              whileHover={{ y: -2 }}
              whileTap={{ scale: 0.97 }}
              onClick={() => navigate('/register')}
              className="inline-flex items-center gap-1.5 rounded-lg bg-white px-3.5 py-2 text-sm font-semibold text-brand-800 shadow-md transition-shadow hover:shadow-lg"
            >
              Criar conta grátis
            </motion.button>
            <motion.a
              href={SALES_WHATSAPP_URL}
              target="_blank"
              rel="noreferrer"
              whileHover={{ y: -2 }}
              whileTap={{ scale: 0.97 }}
              className="inline-flex items-center gap-2 rounded-lg border border-white/20 px-4 py-2.5 text-sm font-medium text-white hover:bg-white/10"
            >
              <MessageCircle className="h-4 w-4" />
              Falar no WhatsApp
            </motion.a>
            <motion.a
              href={`mailto:${SUPPORT_EMAIL}`}
              whileHover={{ y: -2 }}
              whileTap={{ scale: 0.97 }}
              className="inline-flex items-center gap-2 rounded-lg border border-white/20 px-4 py-2.5 text-sm font-medium text-white hover:bg-white/10"
            >
              <Mail className="h-4 w-4" />
              Enviar e-mail
            </motion.a>
          </motion.div>
        </motion.div>
      </section>

      <motion.footer
        initial="hidden"
        whileInView="visible"
        viewport={{ once: true, margin: '-80px' }}
        variants={containerVariants}
        className="relative border-t border-gray-200 bg-white dark:border-white/10 dark:bg-stone-950"
      >
        <div className="pointer-events-none absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-brand-300 to-transparent dark:via-brand-500/40" />
        <div className="mx-auto grid max-w-6xl grid-cols-2 gap-x-6 gap-y-10 px-6 py-14 sm:gap-10 lg:grid-cols-4">
          <motion.div
            variants={itemVariants}
            className="col-span-2 flex flex-col items-center text-center sm:col-span-1 sm:items-start sm:text-left"
          >
            <Logo className="h-7 w-auto" />
            <p className="mt-3 flex items-center gap-1.5 text-sm text-gray-500 dark:text-stone-500">
              <Lock className="h-3.5 w-3.5" />
              Seus dados, sempre protegidos.
            </p>
          </motion.div>
          <motion.div variants={itemVariants} className="flex flex-col items-center text-center sm:items-start sm:text-left">
            <p className="text-xs font-semibold uppercase tracking-widest text-gray-400 dark:text-stone-500">Produto</p>
            <div className="mt-3 flex flex-col items-center gap-3 text-sm sm:items-start">
              <a href="#modulos" className={footerLinkClass}>
                Módulos
              </a>
              <a href="#por-que" className={footerLinkClass}>
                Por que Morá
              </a>
              <Link to="/login" className={footerLinkClass}>
                Entrar
              </Link>
            </div>
          </motion.div>
          <motion.div variants={itemVariants} className="flex flex-col items-center text-center sm:items-start sm:text-left">
            <p className="text-xs font-semibold uppercase tracking-widest text-gray-400 dark:text-stone-500">Contato</p>
            <div className="mt-3 flex flex-col items-center gap-3 text-sm sm:items-start">
              <a href={SALES_WHATSAPP_URL} target="_blank" rel="noreferrer" className={footerLinkClass}>
                Falar no WhatsApp
              </a>
              <a href={`mailto:${SUPPORT_EMAIL}`} className={footerLinkClass}>
                Enviar e-mail
              </a>
            </div>
          </motion.div>
          <motion.div
            variants={itemVariants}
            className="col-span-2 flex flex-col items-center text-center sm:col-span-1 sm:items-start sm:text-left"
          >
            <p className="text-xs font-semibold uppercase tracking-widest text-gray-400 dark:text-stone-500">Legal</p>
            <div className="mt-3 flex flex-col items-center gap-3 text-sm sm:items-start">
              <Link to="/terms" className={footerLinkClass}>
                Termos de uso
              </Link>
              <Link to="/privacy" className={footerLinkClass}>
                Política de privacidade
              </Link>
            </div>
          </motion.div>
        </div>
        <div className="border-t border-gray-100 dark:border-white/5">
          <div className="mx-auto flex max-w-6xl items-center justify-between px-6 py-5">
            <p className="text-sm text-gray-500 dark:text-stone-500">© {new Date().getFullYear()} Morá</p>
            <motion.button
              type="button"
              onClick={() => window.scrollTo({ top: 0, behavior: 'smooth' })}
              whileHover={{ y: -2 }}
              whileTap={{ scale: 0.95 }}
              className="flex items-center gap-1.5 rounded-full border border-gray-200 px-3 py-1.5 text-xs font-medium text-gray-600 transition hover:bg-gray-50 dark:border-white/10 dark:text-stone-400 dark:hover:bg-white/5"
            >
              Voltar ao topo
              <ArrowUp className="h-3.5 w-3.5" />
            </motion.button>
          </div>
        </div>
      </motion.footer>
    </div>
  )
}
