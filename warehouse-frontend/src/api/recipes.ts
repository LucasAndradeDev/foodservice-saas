import { http } from './http'

export interface RecipeItem {
  id: string
  ingredientId: string
  ingredientName: string
  quantityPerUnit: number
}

export interface Recipe {
  id: string
  moraProductId: string
  moraProductName: string
  items: RecipeItem[]
}

export interface RecipeItemPayload {
  ingredientId: string
  quantityPerUnit: number
}

export interface CreateRecipePayload {
  moraProductId: string
  moraProductName: string
  items: RecipeItemPayload[]
}

export interface UpdateRecipePayload {
  moraProductName?: string
  items?: RecipeItemPayload[]
}

export function listRecipes() {
  return http.get<Recipe[]>('/recipes').then((res) => res.data)
}

export function createRecipe(payload: CreateRecipePayload) {
  return http.post<Recipe>('/recipes', payload).then((res) => res.data)
}

export function updateRecipe(id: string, payload: UpdateRecipePayload) {
  return http.put<Recipe>(`/recipes/${id}`, payload).then((res) => res.data)
}
