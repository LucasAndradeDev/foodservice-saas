import { Mail, MessageCircle } from 'lucide-react'
import { SUPPORT_EMAIL, SUPPORT_WHATSAPP_URL } from '../config/support'
import { Modal } from './Modal'

interface SupportModalProps {
  onClose: () => void
}

export function SupportModal({ onClose }: SupportModalProps) {
  return (
    <Modal title="Fale conosco" onClose={onClose}>
      <p className="mb-4 text-sm text-gray-600 dark:text-stone-400">
        Bug, dúvida ou sugestão? Fala com a gente.
      </p>

      <div className="flex flex-col gap-2">
        <a
          href={SUPPORT_WHATSAPP_URL}
          target="_blank"
          rel="noopener noreferrer"
          className="flex items-center gap-3 rounded-lg border border-gray-200 px-4 py-3 text-sm font-medium text-gray-700 hover:bg-gray-50 dark:border-white/10 dark:text-stone-300 dark:hover:bg-white/5"
        >
          <MessageCircle className="h-5 w-5 text-sage-600 dark:text-sage-400" />
          WhatsApp
        </a>

        <a
          href={`mailto:${SUPPORT_EMAIL}`}
          className="flex items-center gap-3 rounded-lg border border-gray-200 px-4 py-3 text-sm font-medium text-gray-700 hover:bg-gray-50 dark:border-white/10 dark:text-stone-300 dark:hover:bg-white/5"
        >
          <Mail className="h-5 w-5 text-brand-600 dark:text-brand-400" />
          Email
        </a>
      </div>
    </Modal>
  )
}
