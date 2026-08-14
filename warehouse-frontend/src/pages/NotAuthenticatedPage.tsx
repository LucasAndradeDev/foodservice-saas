import { Warehouse } from 'lucide-react'

export function NotAuthenticatedPage() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50 px-4 text-center dark:bg-stone-950">
      <div className="max-w-sm">
        <span className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-xl bg-teal-600 text-white">
          <Warehouse className="h-6 w-6" />
        </span>
        <h1 className="mb-2 text-lg font-semibold text-gray-800 dark:text-white">Armazém Morá</h1>
        <p className="text-sm text-gray-500 dark:text-stone-400">
          Acesse pelo painel do Morá: clique em "Armazém Morá" no menu lateral.
        </p>
      </div>
    </div>
  )
}
