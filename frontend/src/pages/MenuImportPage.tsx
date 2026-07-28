import { useMutation, useQueryClient } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import { AlertTriangle, CheckCircle2, FileSpreadsheet, Trash2, Upload } from 'lucide-react'
import { useEffect, useMemo, useState, type ChangeEvent } from 'react'
import {
  commitMenuImport,
  uploadMenuExcel,
  type MenuImportCommitResult,
  type MenuImportProductPayload,
} from '../api/menuImport'
import { useAuth } from '../auth/AuthContext'

interface DraftRow {
  tempId: string
  name: string
  description: string
  price: string
  categoryName: string
  duplicate: boolean
}

interface StoredDraft {
  step: 'upload' | 'review'
  draftRows: DraftRow[]
  warnings: string[]
}

// Keeps the in-progress review draft alive across navigation (e.g. the user
// clicks another page before confirming/canceling) - cleared once the
// import is confirmed or explicitly canceled.
const STORAGE_KEY = 'menuImportDraft'

function loadStoredDraft(): StoredDraft | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    return raw ? (JSON.parse(raw) as StoredDraft) : null
  } catch {
    return null
  }
}

export function MenuImportPage() {
  const { user } = useAuth()
  const canManage = user?.role === 'OWNER' || user?.role === 'MANAGER'
  const queryClient = useQueryClient()

  const [step, setStep] = useState<'upload' | 'review'>(() => loadStoredDraft()?.step ?? 'upload')
  const [isExtracting, setIsExtracting] = useState(false)
  const [extractError, setExtractError] = useState<string | null>(null)
  const [warnings, setWarnings] = useState<string[]>(() => loadStoredDraft()?.warnings ?? [])
  const [draftRows, setDraftRows] = useState<DraftRow[]>(() => loadStoredDraft()?.draftRows ?? [])
  const [commitResult, setCommitResult] = useState<MenuImportCommitResult | null>(null)

  useEffect(() => {
    if (step === 'upload' && draftRows.length === 0) {
      sessionStorage.removeItem(STORAGE_KEY)
      return
    }
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ step, draftRows, warnings }))
  }, [step, draftRows, warnings])

  const categoryNameOptions = useMemo(
    () => Array.from(new Set(draftRows.map((row) => row.categoryName).filter(Boolean))),
    [draftRows],
  )

  const categorySummary = useMemo(() => {
    const counts = new Map<string, number>()
    for (const row of draftRows) {
      const key = row.categoryName || '(sem categoria)'
      counts.set(key, (counts.get(key) ?? 0) + 1)
    }
    return Array.from(counts.entries())
  }, [draftRows])

  const commitMutation = useMutation({
    mutationFn: (products: MenuImportProductPayload[]) => commitMenuImport(products),
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: ['categories'] })
      queryClient.invalidateQueries({ queryKey: ['products'] })
      sessionStorage.removeItem(STORAGE_KEY)
      setCommitResult(result)
    },
    onError: () => setExtractError('Não foi possível importar o cardápio. Tente novamente.'),
  })

  async function handleFileChange(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) return

    setIsExtracting(true)
    setExtractError(null)
    try {
      const preview = await uploadMenuExcel(file)
      const categoryNameByTempId = new Map(preview.categories.map((category) => [category.tempId, category.name]))
      setDraftRows(
        preview.products.map((product) => ({
          tempId: product.tempId,
          name: product.name,
          description: product.description ?? '',
          price: product.price != null ? String(product.price) : '',
          categoryName: categoryNameByTempId.get(product.categoryTempId) ?? '',
          duplicate: product.duplicate,
        })),
      )
      setWarnings(preview.warnings)
      setCommitResult(null)
      setStep('review')
    } catch (err) {
      if (isAxiosError(err) && err.response?.status === 422) {
        setExtractError(err.response.data?.message ?? 'Não foi possível extrair os dados da planilha.')
      } else if (isAxiosError(err) && err.response?.status === 400) {
        setExtractError('Arquivo inválido. Envie um arquivo .xlsx.')
      } else {
        setExtractError('Não foi possível processar o arquivo. Tente novamente.')
      }
    } finally {
      setIsExtracting(false)
    }
  }

  function updateRow(index: number, field: keyof DraftRow, value: string) {
    setDraftRows((prev) => prev.map((row, i) => (i === index ? { ...row, [field]: value } : row)))
  }

  function removeRow(index: number) {
    setDraftRows((prev) => prev.filter((_, i) => i !== index))
  }

  function startOver() {
    setStep('upload')
    setDraftRows([])
    setWarnings([])
    setExtractError(null)
    setCommitResult(null)
  }

  const hasMissingPrice = draftRows.some((row) => !row.price || Number(row.price) <= 0)
  const canConfirm = draftRows.length > 0 && !hasMissingPrice && !commitMutation.isPending

  function handleConfirm() {
    setExtractError(null)
    commitMutation.mutate(
      draftRows.map((row) => ({
        name: row.name,
        description: row.description || undefined,
        price: Number(row.price),
        categoryName: row.categoryName,
      })),
    )
  }

  if (!canManage) {
    return <p className="text-sm text-gray-500">Você não tem permissão para acessar esta página.</p>
  }

  return (
    <div>
      <h1 className="mb-4 flex items-center gap-2 rounded-xl border border-gray-200 bg-white p-4 text-lg font-semibold text-gray-800 shadow-xs">
        <FileSpreadsheet className="h-5 w-5 text-brand-600" />
        Importar cardápio
      </h1>

      {step === 'upload' && (
        <div className="rounded-xl border border-gray-200 bg-white p-6 shadow-sm">
          <p className="mb-4 text-sm text-gray-600">
            Envie uma planilha Excel (.xlsx) com o cardápio. A IA identifica categorias e produtos automaticamente -
            você revisa tudo antes de confirmar, nada é salvo sem sua aprovação.
          </p>

          <label className="flex cursor-pointer flex-col items-center justify-center gap-2 rounded-lg border-2 border-dashed border-gray-300 px-6 py-10 text-sm text-gray-500 hover:border-brand-400 hover:bg-brand-50">
            <Upload className="h-6 w-6 text-gray-400" />
            {isExtracting ? 'Analisando planilha...' : 'Clique para selecionar um arquivo .xlsx'}
            <input
              type="file"
              accept=".xlsx"
              className="hidden"
              onChange={handleFileChange}
              disabled={isExtracting}
            />
          </label>

          {extractError && <p className="mt-4 text-sm text-red-600">{extractError}</p>}
        </div>
      )}

      {step === 'review' && commitResult && (
        <div className="rounded-xl border border-gray-200 bg-white p-6 shadow-sm">
          <p className="mb-4 flex items-center gap-2 text-sm font-medium text-green-700">
            <CheckCircle2 className="h-5 w-5" />
            Importação concluída
          </p>
          <ul className="mb-4 space-y-1 text-sm text-gray-600">
            <li>Categorias criadas: {commitResult.categoriesCreated}</li>
            <li>Categorias reaproveitadas: {commitResult.categoriesReused}</li>
            <li>Produtos criados: {commitResult.productsCreated}</li>
          </ul>
          {commitResult.skipped.length > 0 && (
            <div className="mb-4 rounded-md border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800">
              <p className="mb-1 font-medium">{commitResult.skipped.length} produto(s) não foram importados:</p>
              <ul className="list-inside list-disc">
                {commitResult.skipped.map((item) => (
                  <li key={item.productName}>
                    {item.productName} - {item.reason}
                  </li>
                ))}
              </ul>
            </div>
          )}
          <button
            type="button"
            onClick={startOver}
            className="rounded-md bg-brand-600 px-3 py-2 text-sm font-medium text-white hover:bg-brand-700"
          >
            Importar outra planilha
          </button>
        </div>
      )}

      {step === 'review' && !commitResult && (
        <div>
          {warnings.length > 0 && (
            <div className="mb-4 flex items-start gap-2 rounded-md border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800">
              <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
              <ul className="list-inside list-disc">
                {warnings.map((warning) => (
                  <li key={warning}>{warning}</li>
                ))}
              </ul>
            </div>
          )}

          {draftRows.length === 0 ? (
            <p className="text-sm text-gray-500">Nenhum produto foi identificado nessa planilha.</p>
          ) : (
            <>
              <div className="mb-4 rounded-md border border-gray-200 bg-gray-50 p-3 text-sm text-gray-700">
                <p className="mb-1 font-medium">
                  {categorySummary.length} categoria(s) · {draftRows.length} produto(s) identificado(s)
                </p>
                <ul className="flex flex-wrap gap-x-4 gap-y-1 text-gray-600">
                  {categorySummary.map(([name, count]) => (
                    <li key={name}>
                      {name}: {count}
                    </li>
                  ))}
                </ul>
              </div>

              <datalist id="menu-import-categories">
                {categoryNameOptions.map((name) => (
                  <option key={name} value={name} />
                ))}
              </datalist>

              {/* Mobile: stacked cards */}
              <div className="space-y-2 sm:hidden">
                {draftRows.map((row, index) => (
                  <div key={row.tempId} className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm">
                    <div className="mb-2 flex items-start justify-between gap-2">
                      <input
                        type="text"
                        value={row.name}
                        onChange={(e) => updateRow(index, 'name', e.target.value)}
                        className="w-full rounded-md border border-gray-300 px-2 py-1 text-sm font-medium focus:border-brand-500 focus:outline-none"
                      />
                      <button
                        type="button"
                        onClick={() => removeRow(index)}
                        title="Remover"
                        aria-label="Remover"
                        className="shrink-0 rounded-md p-1.5 text-gray-500 hover:bg-red-50 hover:text-red-700"
                      >
                        <Trash2 className="h-4 w-4" />
                      </button>
                    </div>
                    {row.duplicate && (
                      <span className="mb-2 inline-block rounded-full bg-amber-100 px-2 py-0.5 text-xs text-amber-700">
                        Possível duplicata
                      </span>
                    )}
                    <label className="mb-1 block text-xs font-medium text-gray-500">Descrição</label>
                    <input
                      type="text"
                      maxLength={255}
                      value={row.description}
                      onChange={(e) => updateRow(index, 'description', e.target.value)}
                      className="mb-2 w-full rounded-md border border-gray-300 px-2 py-1 text-sm focus:border-brand-500 focus:outline-none"
                    />
                    <label className="mb-1 block text-xs font-medium text-gray-500">Categoria</label>
                    <input
                      type="text"
                      list="menu-import-categories"
                      value={row.categoryName}
                      onChange={(e) => updateRow(index, 'categoryName', e.target.value)}
                      className="mb-2 w-full rounded-md border border-gray-300 px-2 py-1 text-sm focus:border-brand-500 focus:outline-none"
                    />
                    <label className="mb-1 block text-xs font-medium text-gray-500">Preço</label>
                    <input
                      type="number"
                      min="0.01"
                      step="0.01"
                      value={row.price}
                      onChange={(e) => updateRow(index, 'price', e.target.value)}
                      className="w-full rounded-md border border-gray-300 px-2 py-1 text-sm focus:border-brand-500 focus:outline-none"
                    />
                  </div>
                ))}
              </div>

              {/* Desktop: table */}
              <div className="hidden overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm sm:block">
                <table className="w-full text-sm">
                  <thead className="bg-gray-50 text-left text-gray-500">
                    <tr>
                      <th className="px-4 py-2 font-medium">Produto</th>
                      <th className="px-4 py-2 font-medium">Descrição</th>
                      <th className="px-4 py-2 font-medium">Categoria</th>
                      <th className="px-4 py-2 font-medium">Preço</th>
                      <th className="px-4 py-2" />
                    </tr>
                  </thead>
                  <tbody>
                    {draftRows.map((row, index) => (
                      <tr key={row.tempId} className="border-t border-gray-100">
                        <td className="px-4 py-2 align-top">
                          <input
                            type="text"
                            value={row.name}
                            onChange={(e) => updateRow(index, 'name', e.target.value)}
                            className="w-full rounded-md border border-gray-300 px-2 py-1 text-sm focus:border-brand-500 focus:outline-none"
                          />
                          {row.duplicate && (
                            <span className="mt-1 inline-block rounded-full bg-amber-100 px-2 py-0.5 text-xs text-amber-700">
                              Possível duplicata
                            </span>
                          )}
                        </td>
                        <td className="px-4 py-2 align-top">
                          <input
                            type="text"
                            maxLength={255}
                            value={row.description}
                            onChange={(e) => updateRow(index, 'description', e.target.value)}
                            className="w-full rounded-md border border-gray-300 px-2 py-1 text-sm focus:border-brand-500 focus:outline-none"
                          />
                        </td>
                        <td className="px-4 py-2 align-top">
                          <input
                            type="text"
                            list="menu-import-categories"
                            value={row.categoryName}
                            onChange={(e) => updateRow(index, 'categoryName', e.target.value)}
                            className="w-full rounded-md border border-gray-300 px-2 py-1 text-sm focus:border-brand-500 focus:outline-none"
                          />
                        </td>
                        <td className="px-4 py-2 align-top">
                          <input
                            type="number"
                            min="0.01"
                            step="0.01"
                            value={row.price}
                            onChange={(e) => updateRow(index, 'price', e.target.value)}
                            className="w-24 rounded-md border border-gray-300 px-2 py-1 text-sm focus:border-brand-500 focus:outline-none"
                          />
                        </td>
                        <td className="px-4 py-2 text-right align-top">
                          <button
                            type="button"
                            onClick={() => removeRow(index)}
                            title="Remover"
                            aria-label="Remover"
                            className="rounded-md p-1.5 text-gray-500 hover:bg-red-50 hover:text-red-700"
                          >
                            <Trash2 className="h-4 w-4" />
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </>
          )}

          {extractError && <p className="mt-4 text-sm text-red-600">{extractError}</p>}

          <div className="mt-4 flex flex-col gap-2 sm:flex-row">
            <button
              type="button"
              onClick={handleConfirm}
              disabled={!canConfirm}
              className="rounded-md bg-brand-600 px-3 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50"
            >
              {commitMutation.isPending ? 'Importando...' : 'Confirmar importação'}
            </button>
            <button
              type="button"
              onClick={startOver}
              className="rounded-md border border-gray-300 px-3 py-2 text-sm text-gray-700 hover:bg-gray-100"
            >
              Cancelar
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
