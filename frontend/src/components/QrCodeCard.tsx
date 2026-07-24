import { Check, Copy, ExternalLink, QrCode } from 'lucide-react'
import { QRCodeCanvas } from 'qrcode.react'
import { useState } from 'react'

interface QrCodeCardProps {
  title: string
  url: string
  helperText?: string
}

export function QrCodeCard({ title, url, helperText }: QrCodeCardProps) {
  const [linkCopied, setLinkCopied] = useState(false)

  return (
    <div className="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm">
      <div className="flex items-center gap-2 border-b border-gray-100 bg-gradient-to-r from-brand-50 to-white px-6 py-4">
        <QrCode className="h-5 w-5 text-brand-600" />
        <h2 className="text-sm font-semibold text-gray-800">{title}</h2>
      </div>

      <div className="flex flex-col items-center gap-4 p-6">
        <div className="rounded-xl border border-gray-200 bg-white p-3 shadow-md">
          <QRCodeCanvas value={url} size={168} />
        </div>
        <p className="max-w-[220px] text-center text-xs text-gray-400">
          {helperText ?? 'Clique com o botão direito pra salvar e imprimir'}
        </p>

        <div className="w-full">
          <p className="mb-1 text-xs font-semibold tracking-wide text-gray-400 uppercase">Link público</p>
          <p className="truncate rounded-md bg-gray-50 px-3 py-2 text-sm text-gray-700">{url}</p>
        </div>

        <div className="flex w-full gap-2">
          <a
            href={url}
            target="_blank"
            rel="noreferrer"
            className="flex flex-1 items-center justify-center gap-1.5 rounded-md bg-brand-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-brand-700"
          >
            <ExternalLink className="h-4 w-4" />
            Abrir
          </a>
          <button
            type="button"
            onClick={() => {
              navigator.clipboard.writeText(url)
              setLinkCopied(true)
              setTimeout(() => setLinkCopied(false), 2000)
            }}
            className="flex flex-1 items-center justify-center gap-1.5 rounded-md border border-gray-300 px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-100"
          >
            {linkCopied ? <Check className="h-4 w-4 text-green-600" /> : <Copy className="h-4 w-4" />}
            {linkCopied ? 'Copiado!' : 'Copiar link'}
          </button>
        </div>
      </div>
    </div>
  )
}
