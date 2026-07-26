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

const CATEGORY_ICON_RULES: Array<{ keywords: string[]; icon: LucideIcon }> = [
  { keywords: ['pizza'], icon: Pizza },
  { keywords: ['cerveja', 'vinho', 'chopp', 'drink'], icon: Wine },
  { keywords: ['bebida', 'suco', 'refrigerante', 'agua', 'água'], icon: CupSoda },
  { keywords: ['salada'], icon: Salad },
  { keywords: ['sorvete', 'sobremesa', 'doce'], icon: IceCreamCone },
  { keywords: ['bolo', 'torta'], icon: Cake },
  { keywords: ['biscoito', 'cookie'], icon: Cookie },
  { keywords: ['massa', 'macarrao', 'macarrão', 'lasanha', 'nhoque', 'espaguete', 'fettuccine'], icon: Soup },
  { keywords: ['lanche', 'sanduiche', 'sanduíche', 'burger', 'hamburguer', 'hambúrguer'], icon: Sandwich },
  { keywords: ['frango', 'ave'], icon: Drumstick },
  { keywords: ['carne', 'churrasco', 'espeto', 'bovino'], icon: Beef },
  { keywords: ['sushi', 'peixe', 'japon'], icon: Fish },
  { keywords: ['cafe', 'café', 'expresso'], icon: Coffee },
  { keywords: ['pao', 'pão', 'padaria'], icon: Wheat },
]

export function getCategoryIcon(categoryName: string): LucideIcon {
  const normalized = categoryName
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .toLowerCase()

  for (const rule of CATEGORY_ICON_RULES) {
    if (rule.keywords.some((keyword) => normalized.includes(keyword.normalize('NFD').replace(/\p{Diacritic}/gu, '')))) {
      return rule.icon
    }
  }
  return UtensilsCrossed
}
