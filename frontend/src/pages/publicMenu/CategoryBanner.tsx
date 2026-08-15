import { motion } from 'framer-motion'
import { ChevronDown } from 'lucide-react'
import type { PublicMenuCategory } from '../../api/publicMenu'
import { getCategoryIcon } from './categoryIcons'

interface CategoryBannerProps {
  category: PublicMenuCategory
  isOpen: boolean
  onToggle: () => void
}

export function CategoryBanner({ category, isOpen, onToggle }: CategoryBannerProps) {
  const bannerImage = category.bannerImageUrl ?? category.products.find((product) => product.imageUrl)?.imageUrl ?? null
  const Icon = getCategoryIcon(category.name)

  return (
    <button
      type="button"
      onClick={onToggle}
      aria-expanded={isOpen}
      className="relative mb-3 h-24 w-full overflow-hidden rounded-2xl text-left"
    >
      {bannerImage ? (
        <>
          <img src={bannerImage} alt="" className="absolute inset-0 h-full w-full object-cover" />
          <div className="absolute inset-0 bg-gradient-to-t from-black/90 via-black/55 to-black/30" />
          <div className="absolute inset-0 bg-[radial-gradient(ellipse_65%_60%_at_50%_50%,rgba(0,0,0,0.55),transparent_70%)]" />
        </>
      ) : (
        <>
          <div className="absolute inset-0 bg-gradient-to-br from-brand-600 to-brand-800" />
          <Icon className="absolute -bottom-3 -right-3 h-24 w-24 text-white/10" />
        </>
      )}

      <div className="relative flex h-full flex-col items-center justify-center gap-1.5 px-4 text-center">
        <h2 className="font-elegant text-3xl font-bold italic leading-none text-white [text-shadow:0_2px_10px_rgb(0_0_0_/_0.85)]">{category.name}</h2>
        <span className="h-px w-10 bg-white/50" />
      </div>

      <motion.span
        animate={{ rotate: isOpen ? 0 : -180 }}
        transition={{ duration: 0.25, ease: 'easeInOut' }}
        className="absolute right-3 top-3 flex h-7 w-7 items-center justify-center rounded-full bg-black/30 text-white backdrop-blur-sm"
      >
        <ChevronDown className="h-4 w-4" />
      </motion.span>
    </button>
  )
}
