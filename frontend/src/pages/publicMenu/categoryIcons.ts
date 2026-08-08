import {
  Beef,
  Cake,
  Coffee,
  Cookie,
  CupSoda,
  Drumstick,
  Fish,
  IceCreamCone,
  Pizza,
  Salad,
  Sandwich,
  Soup,
  UtensilsCrossed,
  Wheat,
  Wine,
  type LucideIcon,
} from 'lucide-react'

const CATEGORY_RULES: Array<{ keywords: string[]; icon: LucideIcon; emoji: string }> = [
  { keywords: ['combo'], icon: UtensilsCrossed, emoji: '🍱' },
  { keywords: ['pizza'], icon: Pizza, emoji: '🍕' },
  { keywords: ['cerveja', 'vinho', 'chopp', 'drink'], icon: Wine, emoji: '🍷' },
  { keywords: ['bebida', 'suco', 'refrigerante', 'agua', 'água'], icon: CupSoda, emoji: '🥤' },
  { keywords: ['salada'], icon: Salad, emoji: '🥗' },
  { keywords: ['sorvete', 'sobremesa', 'doce'], icon: IceCreamCone, emoji: '🍨' },
  { keywords: ['bolo', 'torta'], icon: Cake, emoji: '🍰' },
  { keywords: ['biscoito', 'cookie'], icon: Cookie, emoji: '🍪' },
  { keywords: ['massa', 'macarrao', 'macarrão', 'lasanha', 'nhoque', 'espaguete', 'fettuccine'], icon: Soup, emoji: '🍜' },
  { keywords: ['lanche', 'sanduiche', 'sanduíche', 'burger', 'hamburguer', 'hambúrguer'], icon: Sandwich, emoji: '🍔' },
  { keywords: ['frango', 'ave'], icon: Drumstick, emoji: '🍗' },
  { keywords: ['carne', 'churrasco', 'espeto', 'bovino'], icon: Beef, emoji: '🥩' },
  { keywords: ['sushi', 'peixe', 'japon'], icon: Fish, emoji: '🍣' },
  { keywords: ['cafe', 'café', 'expresso'], icon: Coffee, emoji: '☕' },
  { keywords: ['pao', 'pão', 'padaria'], icon: Wheat, emoji: '🥖' },
]

function normalize(text: string) {
  return text
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .toLowerCase()
}

function findRule(categoryName: string) {
  const normalized = normalize(categoryName)
  return CATEGORY_RULES.find((rule) => rule.keywords.some((keyword) => normalized.includes(normalize(keyword))))
}

export function getCategoryIcon(categoryName: string): LucideIcon {
  return findRule(categoryName)?.icon ?? UtensilsCrossed
}

export function getCategoryEmoji(categoryName: string): string {
  return findRule(categoryName)?.emoji ?? '🍽️'
}
