import { motion } from 'framer-motion'
import { ArrowRightLeft, BellRing, Package, Warehouse } from 'lucide-react'
import { PageHeader } from '../components/PageHeader'

const EASE_OUT: [number, number, number, number] = [0.16, 1, 0.3, 1]

const FEATURES = [
  { icon: Package, text: 'Acompanhar a quantidade de cada insumo em tempo real' },
  { icon: ArrowRightLeft, text: 'Registrar entradas e saídas do estoque' },
  { icon: BellRing, text: 'Avisar quando algo estiver perto de acabar' },
]

export function ArmazemMoraComingSoonPage() {
  return (
    <div>
      <div className="mb-5 rounded-2xl border border-gray-200 bg-white p-5 shadow-sm dark:border-white/10 dark:bg-stone-900">
        <PageHeader icon={Warehouse} title="Armazém Morá" />
      </div>

      <motion.div
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4, ease: EASE_OUT }}
        className="flex flex-col items-center gap-4 rounded-2xl border border-gray-100 bg-white px-6 py-14 text-center shadow-sm dark:border-white/5 dark:bg-stone-900"
      >
        <motion.div
          animate={{ y: [0, -10, 0] }}
          transition={{ duration: 2.6, repeat: Infinity, ease: 'easeInOut' }}
          className="flex h-16 w-16 items-center justify-center rounded-2xl bg-brand-50 text-brand-600 dark:bg-brand-500/10 dark:text-brand-400"
        >
          <Warehouse className="h-8 w-8" />
        </motion.div>

        <h2 className="text-lg font-semibold text-gray-800 dark:text-white">Em desenvolvimento</h2>
        <p className="max-w-md text-sm text-gray-500 dark:text-stone-400">
          O Armazém Morá vai ser o controle de estoque do seu restaurante. Ainda não está disponível,
          mas em breve chega por aqui.
        </p>

        <div className="mt-4 flex w-full max-w-sm flex-col gap-3 text-left">
          {FEATURES.map(({ icon: Icon, text }, index) => (
            <motion.div
              key={text}
              initial={{ opacity: 0, x: -8 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ duration: 0.35, ease: EASE_OUT, delay: 0.15 + index * 0.1 }}
              className="flex items-center gap-3 rounded-xl border border-gray-100 bg-gray-50/60 px-4 py-3 dark:border-white/5 dark:bg-white/5"
            >
              <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-white text-brand-600 shadow-sm dark:bg-stone-800 dark:text-brand-400">
                <Icon className="h-4 w-4" />
              </span>
              <span className="text-sm text-gray-600 dark:text-stone-300">{text}</span>
            </motion.div>
          ))}
        </div>
      </motion.div>
    </div>
  )
}
