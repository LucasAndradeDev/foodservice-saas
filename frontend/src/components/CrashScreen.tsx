import { AlertTriangle } from 'lucide-react'
import { Button } from './Button'

export function CrashScreen() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-4 bg-gray-50 px-4 text-center dark:bg-stone-950">
      <AlertTriangle className="h-10 w-10 text-wine-600" />
      <div>
        <p className="font-medium text-gray-900 dark:text-stone-100">Algo deu errado.</p>
        <p className="text-sm text-gray-500 dark:text-stone-400">Recarregue a página para continuar.</p>
      </div>
      <Button onClick={() => window.location.reload()}>Recarregar</Button>
    </div>
  )
}
