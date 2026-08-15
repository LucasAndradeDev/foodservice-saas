import { http } from './http'

export interface ExtractedCategory {
  tempId: string
  name: string
  matchedCategoryId: string | null
}

export interface ExtractedProduct {
  tempId: string
  categoryTempId: string
  name: string
  description: string | null
  price: number | null
  duplicate: boolean
  duplicateOfProductId: string | null
}

export interface MenuImportPreview {
  categories: ExtractedCategory[]
  products: ExtractedProduct[]
  warnings: string[]
}

export interface MenuImportProductPayload {
  name: string
  description?: string
  price: number
  categoryName: string
}

export interface MenuImportCommitError {
  productName: string
  reason: string
}

export interface MenuImportCommitResult {
  categoriesCreated: number
  categoriesReused: number
  productsCreated: number
  skipped: MenuImportCommitError[]
}

export function uploadMenuExcel(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return http.post<MenuImportPreview>('/menu-import/extract', formData).then((res) => res.data)
}

export function uploadMenuDocuments(files: File[]) {
  const formData = new FormData()
  files.forEach((file) => formData.append('files', file))
  return http.post<MenuImportPreview>('/menu-import/extract-document', formData).then((res) => res.data)
}

export function commitMenuImport(products: MenuImportProductPayload[]) {
  return http.post<MenuImportCommitResult>('/menu-import/commit', { products }).then((res) => res.data)
}
