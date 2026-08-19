import {
  Beef,
  Beer,
  CakeSlice,
  Coffee,
  Cookie,
  CupSoda,
  Donut,
  Drumstick,
  EggFried,
  Fish,
  Hamburger,
  IceCreamCone,
  Martini,
  Pizza,
  Popcorn,
  Salad,
  Sandwich,
  Shrimp,
  Soup,
  UtensilsCrossed,
  Wheat,
  Wine,
  type LucideIcon,
} from 'lucide-react'

import breakfastImg from '../../assets/categoryIcons/breakfast.png'
import comboImg from '../../assets/categoryIcons/combo.png'
import pizzaImg from '../../assets/categoryIcons/pizza.png'
import tacoImg from '../../assets/categoryIcons/taco.png'
import hotdogImg from '../../assets/categoryIcons/hotdog.png'
import wrapImg from '../../assets/categoryIcons/wrap.png'
import dumplingImg from '../../assets/categoryIcons/dumpling.png'
import seafoodImg from '../../assets/categoryIcons/seafood.png'
import fishImg from '../../assets/categoryIcons/fish.png'
import sandwichImg from '../../assets/categoryIcons/sandwich.png'
import friesImg from '../../assets/categoryIcons/fries.png'
import popcornImg from '../../assets/categoryIcons/popcorn.png'
import soupImg from '../../assets/categoryIcons/soup.png'
import pastaImg from '../../assets/categoryIcons/pasta.png'
import saladImg from '../../assets/categoryIcons/salad.png'
import chickenImg from '../../assets/categoryIcons/chicken.png'
import meatImg from '../../assets/categoryIcons/meat.png'
import beerImg from '../../assets/categoryIcons/beer.png'
import wineImg from '../../assets/categoryIcons/wine.png'
import cocktailImg from '../../assets/categoryIcons/cocktail.png'
import drinkImg from '../../assets/categoryIcons/drink.png'
import teaImg from '../../assets/categoryIcons/tea.png'
import coffeeImg from '../../assets/categoryIcons/coffee.png'
import breadImg from '../../assets/categoryIcons/bread.png'
import dessertImg from '../../assets/categoryIcons/dessert.png'
import cakeImg from '../../assets/categoryIcons/cake.png'
import cookieImg from '../../assets/categoryIcons/cookie.png'
import donutImg from '../../assets/categoryIcons/donut.png'
import defaultImg from '../../assets/categoryIcons/default.png'

export interface CategoryIconOption {
  key: string
  label: string
  icon: LucideIcon
  image: string
  keywords: string[]
}

export const CATEGORY_ICON_OPTIONS: CategoryIconOption[] = [
  {
    key: 'breakfast',
    label: 'Café da manhã',
    icon: EggFried,
    image: breakfastImg,
    keywords: ['cafe da manha', 'café da manhã', 'omelete', 'ovos'],
  },
  { key: 'combo', label: 'Combo', icon: UtensilsCrossed, image: comboImg, keywords: ['combo'] },
  { key: 'pizza', label: 'Pizza', icon: Pizza, image: pizzaImg, keywords: ['pizza'] },
  { key: 'taco', label: 'Tacos', icon: Sandwich, image: tacoImg, keywords: ['taco', 'burrito', 'mexicana', 'nachos'] },
  {
    key: 'hotdog',
    label: 'Cachorro-quente',
    icon: Hamburger,
    image: hotdogImg,
    keywords: ['cachorro quente', 'cachorro-quente', 'hotdog', 'hot dog'],
  },
  { key: 'wrap', label: 'Wrap', icon: Sandwich, image: wrapImg, keywords: ['wrap', 'esfiha', 'shawarma', 'arabe', 'árabe'] },
  {
    key: 'dumpling',
    label: 'Pastéis e dim sum',
    icon: UtensilsCrossed,
    image: dumplingImg,
    keywords: ['pastel', 'pasteis', 'pastéis', 'guioza', 'dim sum', 'chinesa'],
  },
  {
    key: 'seafood',
    label: 'Frutos do mar',
    icon: Shrimp,
    image: seafoodImg,
    keywords: ['camarao', 'camarão', 'marisco', 'frutos do mar', 'lagosta', 'ostra'],
  },
  { key: 'fish', label: 'Peixe e sushi', icon: Fish, image: fishImg, keywords: ['sushi', 'peixe', 'japon'] },
  {
    key: 'sandwich',
    label: 'Lanche',
    icon: Hamburger,
    image: sandwichImg,
    keywords: ['lanche', 'sanduiche', 'sanduíche', 'burger', 'hamburguer', 'hambúrguer'],
  },
  {
    key: 'fries',
    label: 'Porções',
    icon: UtensilsCrossed,
    image: friesImg,
    keywords: ['batata frita', 'porcao', 'porção', 'petisco', 'petiscos', 'aperitivo'],
  },
  { key: 'popcorn', label: 'Pipoca', icon: Popcorn, image: popcornImg, keywords: ['pipoca'] },
  { key: 'soup', label: 'Sopa', icon: Soup, image: soupImg, keywords: ['sopa', 'caldo', 'ensopado'] },
  {
    key: 'pasta',
    label: 'Massa',
    icon: Soup,
    image: pastaImg,
    keywords: ['massa', 'macarrao', 'macarrão', 'lasanha', 'nhoque', 'espaguete', 'fettuccine'],
  },
  { key: 'salad', label: 'Salada', icon: Salad, image: saladImg, keywords: ['salada'] },
  { key: 'chicken', label: 'Frango', icon: Drumstick, image: chickenImg, keywords: ['frango', 'ave'] },
  { key: 'meat', label: 'Carne', icon: Beef, image: meatImg, keywords: ['carne', 'churrasco', 'espeto', 'bovino'] },
  { key: 'beer', label: 'Cerveja', icon: Beer, image: beerImg, keywords: ['cerveja', 'chopp', 'chope'] },
  { key: 'wine', label: 'Vinho', icon: Wine, image: wineImg, keywords: ['vinho'] },
  {
    key: 'cocktail',
    label: 'Drinks',
    icon: Martini,
    image: cocktailImg,
    keywords: ['drink', 'coquetel', 'caipirinha', 'caipirosca'],
  },
  {
    key: 'drink',
    label: 'Bebida',
    icon: CupSoda,
    image: drinkImg,
    keywords: ['bebida', 'suco', 'refrigerante', 'agua', 'água', 'refresco'],
  },
  { key: 'tea', label: 'Chá', icon: Coffee, image: teaImg, keywords: ['cha', 'chá'] },
  { key: 'coffee', label: 'Café', icon: Coffee, image: coffeeImg, keywords: ['cafe', 'café', 'expresso'] },
  { key: 'bread', label: 'Pão', icon: Wheat, image: breadImg, keywords: ['pao', 'pão', 'padaria'] },
  { key: 'dessert', label: 'Sobremesa', icon: IceCreamCone, image: dessertImg, keywords: ['sorvete', 'sobremesa', 'doce'] },
  { key: 'cake', label: 'Bolo e torta', icon: CakeSlice, image: cakeImg, keywords: ['bolo', 'torta'] },
  { key: 'cookie', label: 'Biscoito', icon: Cookie, image: cookieImg, keywords: ['biscoito', 'cookie'] },
  { key: 'donut', label: 'Rosquinha', icon: Donut, image: donutImg, keywords: ['rosquinha', 'donut', 'sonho'] },
]

const DEFAULT_ICON_OPTION: CategoryIconOption = {
  key: 'default',
  label: 'Prato',
  icon: UtensilsCrossed,
  image: defaultImg,
  keywords: [],
}

function normalize(text: string) {
  return text
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .toLowerCase()
}

function findRuleByName(categoryName: string) {
  const normalized = normalize(categoryName)
  return CATEGORY_ICON_OPTIONS.find((option) => option.keywords.some((keyword) => normalized.includes(normalize(keyword))))
}

export function getCategoryIconOption(categoryName: string, iconKey?: string | null): CategoryIconOption {
  if (iconKey) {
    const manual = CATEGORY_ICON_OPTIONS.find((option) => option.key === iconKey)
    if (manual) return manual
  }
  return findRuleByName(categoryName) ?? DEFAULT_ICON_OPTION
}

export function getCategoryIcon(categoryName: string, iconKey?: string | null): LucideIcon {
  return getCategoryIconOption(categoryName, iconKey).icon
}

export function getCategoryIconImage(categoryName: string, iconKey?: string | null): string {
  return getCategoryIconOption(categoryName, iconKey).image
}
