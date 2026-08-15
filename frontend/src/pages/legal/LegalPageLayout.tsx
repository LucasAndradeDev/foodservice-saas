import type { ReactNode } from 'react'
import { BackLink } from '../../components/BackLink'
import { Logo } from '../../theme/Logo'

interface LegalPageLayoutProps {
  title: string
  lastUpdated: string
  children: ReactNode
}

export function LegalPageLayout({ title, lastUpdated, children }: LegalPageLayoutProps) {
  return (
    <div className="min-h-screen bg-gray-50 dark:bg-stone-950">
      <main className="mx-auto max-w-3xl px-4 py-10">
        <BackLink to="/" className="mb-6">
          Voltar
        </BackLink>

        <Logo className="mb-6 h-12 w-auto" />

        <div className="rounded-2xl border border-gray-200 bg-white p-6 shadow-sm dark:border-stone-800 dark:bg-stone-900 sm:p-8">
          <h1 className="mb-1 text-2xl font-bold text-gray-800 dark:text-white">{title}</h1>
          <p className="mb-6 text-xs text-gray-400 dark:text-stone-500">Última atualização: {lastUpdated}</p>
          {children}
        </div>
      </main>
    </div>
  )
}

export function LegalSection({ title, children }: { title?: string; children: ReactNode }) {
  return (
    <section className="mb-5">
      {title && <h2 className="mb-2 text-base font-semibold text-gray-800 dark:text-white">{title}</h2>}
      <div className="space-y-2 text-sm leading-relaxed text-gray-600 dark:text-stone-400">{children}</div>
    </section>
  )
}

export function LegalNotice({ children }: { children: ReactNode }) {
  return (
    <div className="mb-6 rounded-lg border border-amber-300 bg-amber-50 p-3 text-xs leading-relaxed text-amber-800 dark:border-amber-900 dark:bg-amber-950/40 dark:text-amber-300">
      {children}
    </div>
  )
}
