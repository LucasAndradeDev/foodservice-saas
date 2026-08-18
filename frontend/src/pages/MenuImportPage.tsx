import { useMutation, useQueryClient } from '@tanstack/react-query'
import { isAxiosError } from 'axios'
import { AlertTriangle, CheckCircle2, FileSpreadsheet, LoaderCircle, Trash2, Upload } from 'lucide-react'
import { useEffect, useMemo, useState, type ChangeEvent } from 'react'
import {
  commitMenuImport,
  uploadMenuDocuments,
  uploadMenuExcel,
  type MenuImportCommitResult,
  type MenuImportProductPayload,
} from '../api/menuImport'
import { useAuth } from '../auth/AuthContext'
import { Button } from '../components/Button'
import { PageHeader } from '../components/PageHeader'
import { Table, TableHead, TableRow } from '../components/Table'

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
    const files = event.target.files ? Array.from(event.target.files) : []
    event.target.value = ''
    if (files.length === 0) return

    const isSpreadsheet = files.length === 1 && files[0].name.toLowerCase().endsWith('.xlsx')

    setIsExtracting(true)
    setExtractError(null)
    try {
      const preview = isSpreadsheet ? await uploadMenuExcel(files[0]) : await uploadMenuDocuments(files)
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
      if (isAxiosError(err) && (err.response?.status === 422 || err.response?.status === 400)) {
        setExtractError(err.response.data?.message ?? 'Não foi possível processar o arquivo enviado.')
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

  const missingPriceCount = draftRows.filter((row) => !row.price || Number(row.price) <= 0).length
  const hasMissingPrice = missingPriceCount > 0
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
    return <p className="text-sm text-gray-500 dark:text-stone-400">Você não tem permissão para acessar esta página.</p>
  }

  return (
    <div>
      <div className="mb-5 rounded-2xl border border-gray-200 bg-white p-5 shadow-sm dark:border-white/10 dark:bg-stone-900">
        <PageHeader icon={FileSpreadsheet} title="Importar cardápio" />
      </div>

      {step === 'upload' && (
        <div className="rounded-xl border border-gray-200 bg-white p-6 shadow-sm dark:border-white/10 dark:bg-stone-900">
          <p className="mb-4 text-sm text-gray-600 dark:text-stone-400">
            Envie uma planilha Excel (.xlsx), um PDF ou fotos do seu cardápio (uma foto por página, se for mais de
            uma). A IA identifica categorias e produtos automaticamente - você revisa tudo antes de confirmar, nada
            é salvo sem sua aprovação.
          </p>

          <label className="flex cursor-pointer flex-col items-center justify-center gap-2 rounded-lg border-2 border-dashed border-gray-300 px-6 py-10 text-sm text-gray-500 hover:border-brand-400 hover:bg-brand-50 dark:border-white/10 dark:text-stone-400 dark:hover:border-brand-400 dark:hover:bg-brand-500/10">
            {isExtracting ? (
              <LoaderCircle className="h-6 w-6 animate-spin text-brand-500" />
            ) : (
              <Upload className="h-6 w-6 text-gray-400 dark:text-stone-500" />
            )}
            {isExtracting
              ? 'Analisando cardápio...'
              : 'Clique para selecionar um .xlsx, um PDF, ou uma ou mais fotos'}
            <input
              type="file"
              accept=".xlsx,.pdf,image/jpeg,image/png,image/webp"
              multiple
              className="hidden"
              onChange={handleFileChange}
              disabled={isExtracting}
            />
          </label>

          {extractError && <p className="mt-4 text-sm text-wine-600 dark:text-wine-400">{extractError}</p>}
        </div>
      )}

      {step === 'review' && commitResult && (
        <div className="rounded-xl border border-gray-200 bg-white p-6 shadow-sm dark:border-white/10 dark:bg-stone-900">
          <p className="mb-4 flex items-center gap-2 text-sm font-medium text-green-700">
            <CheckCircle2 className="h-5 w-5" />
            Importação concluída
          </p>
          <ul className="mb-4 space-y-1 text-sm text-gray-600 dark:text-stone-400">
            <li>Categorias criadas: {commitResult.categoriesCreated}</li>
            <li>Categorias reaproveitadas: {commitResult.categoriesReused}</li>
            <li>Produtos criados: {commitResult.productsCreated}</li>
          </ul>
          {commitResult.skipped.length > 0 && (
            <div className="mb-4 rounded-md border border-amber-200 bg-amber-50 dark:border-amber-500/30 dark:bg-amber-500/10 p-3 text-sm text-amber-800 dark:text-amber-400">
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
            <div className="mb-4 flex items-start gap-2 rounded-md border border-amber-200 bg-amber-50 dark:border-amber-500/30 dark:bg-amber-500/10 p-3 text-sm text-amber-800 dark:text-amber-400">
              <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
              <ul className="list-inside list-disc">
                {warnings.map((warning) => (
                  <li key={warning}>{warning}</li>
                ))}
              </ul>
            </div>
          )}

          {draftRows.length === 0 ? (
            <p className="text-sm text-gray-500 dark:text-stone-400">Nenhum produto foi identificado nessa planilha.</p>
          ) : (
            <>
              <div className="mb-4 rounded-md border border-gray-200 bg-gray-50 p-3 text-sm text-gray-700 dark:border-white/10 dark:bg-white/5 dark:text-stone-300">
                <p className="mb-1 font-medium">
                  {categorySummary.length} categoria(s) · {draftRows.length} produto(s) identificado(s)
                </p>
                <ul className="flex flex-wrap gap-x-4 gap-y-1 text-gray-600 dark:text-stone-400">
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
                  <div
                    key={row.tempId}
                    className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-white/10 dark:bg-stone-900"
                  >
                    <div className="mb-2 flex items-start justify-between gap-2">
                      <input
                        type="text"
                        value={row.name}
                        onChange={(e) => updateRow(index, 'name', e.target.value)}
                        className="w-full rounded-md border border-gray-300 px-2 py-1 text-sm font-medium focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
                      />
                      <button
                        type="button"
                        onClick={() => removeRow(index)}
                        title="Remover"
                        aria-label="Remover"
                        className="shrink-0 rounded-md p-1.5 text-gray-500 hover:bg-red-50 hover:text-red-700 dark:text-stone-400 dark:hover:bg-red-500/10 dark:hover:text-red-400"
                      >
                        <Trash2 className="h-4 w-4" />
                      </button>
                    </div>
                    {row.duplicate && (
                      <span className="mb-2 inline-block rounded-full bg-amber-100 px-2 py-0.5 text-xs text-amber-700 dark:bg-amber-500/10 dark:text-amber-400">
                        Possível duplicata
                      </span>
                    )}
                    <label className="mb-1 block text-xs font-medium text-gray-500 dark:text-stone-400">Descrição</label>
                    <input
                      type="text"
                      maxLength={255}
                      value={row.description}
                      onChange={(e) => updateRow(index, 'description', e.target.value)}
                      className="mb-2 w-full rounded-md border border-gray-300 px-2 py-1 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
                    />
                    <label className="mb-1 block text-xs font-medium text-gray-500 dark:text-stone-400">Categoria</label>
                    <input
                      type="text"
                      list="menu-import-categories"
                      value={row.categoryName}
                      onChange={(e) => updateRow(index, 'categoryName', e.target.value)}
                      className="mb-2 w-full rounded-md border border-gray-300 px-2 py-1 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
                    />
                    <label className="mb-1 block text-xs font-medium text-gray-500 dark:text-stone-400">Preço</label>
                    <input
                      type="number"
                      min="0.01"
                      step="0.01"
                      value={row.price}
                      onChange={(e) => updateRow(index, 'price', e.target.value)}
                      className="w-full rounded-md border border-gray-300 px-2 py-1 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
                    />
                  </div>
                ))}
              </div>

              {/* Desktop: table */}
              <div className="hidden sm:block">
                <Table>
                  <TableHead>
                    <tr>
                      <th className="px-4 py-2 font-medium">Produto</th>
                      <th className="px-4 py-2 font-medium">Descrição</th>
                      <th className="px-4 py-2 font-medium">Categoria</th>
                      <th className="px-4 py-2 font-medium">Preço</th>
                      <th className="px-4 py-2" />
                    </tr>
                  </TableHead>
                  <tbody>
                    {draftRows.map((row, index) => (
                      <TableRow key={row.tempId}>
                        <td className="px-4 py-2 align-top">
                          <input
                            type="text"
                            value={row.name}
                            onChange={(e) => updateRow(index, 'name', e.target.value)}
                            className="w-full rounded-md border border-gray-300 px-2 py-1 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
                          />
                          {row.duplicate && (
                            <span className="mt-1 inline-block rounded-full bg-gold-100 px-2 py-0.5 text-xs text-gold-700 dark:bg-gold-500/10 dark:text-gold-400">
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
                            className="w-full rounded-md border border-gray-300 px-2 py-1 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
                          />
                        </td>
                        <td className="px-4 py-2 align-top">
                          <input
                            type="text"
                            list="menu-import-categories"
                            value={row.categoryName}
                            onChange={(e) => updateRow(index, 'categoryName', e.target.value)}
                            className="w-full rounded-md border border-gray-300 px-2 py-1 text-sm focus:border-brand-500 focus:outline-none dark:border-white/10 dark:bg-stone-800 dark:text-white dark:focus:border-brand-400"
                          />
                        </td>
                        <td className="px-4 py-2 align-top">
                          <input
                            type="number"
                            min="0.01"
                            step="0.01"
                            value={row.price}
                            onChange={(e) => updateRow(index, 'price', e.target.value)}
                            className={`w-24 rounded-md border px-2 py-1 text-sm focus:outline-none dark:bg-stone-800 dark:text-white ${
                              !row.price || Number(row.price) <= 0
                                ? 'border-wine-400 focus:border-wine-500 dark:border-wine-500/60'
                                : 'border-gray-300 focus:border-brand-500 dark:border-white/10 dark:focus:border-brand-400'
                            }`}
                          />
                        </td>
                        <td className="px-4 py-2 text-right align-top">
                          <button
                            type="button"
                            onClick={() => removeRow(index)}
                            title="Remover"
                            aria-label="Remover"
                            className="rounded-md p-1.5 text-gray-500 hover:bg-wine-100 hover:text-wine-700 dark:text-stone-400 dark:hover:bg-wine-500/10 dark:hover:text-wine-400"
                          >
                            <Trash2 className="h-4 w-4" />
                          </button>
                        </td>
                      </TableRow>
                    ))}
                  </tbody>
                </Table>
              </div>
            </>
          )}

          {extractError && <p className="mt-4 text-sm text-wine-600 dark:text-wine-400">{extractError}</p>}

          {hasMissingPrice && (
            <p className="mt-4 text-sm font-medium text-wine-600 dark:text-wine-400">
              {missingPriceCount === 1
                ? '1 produto sem preço — role para revisar.'
                : `${missingPriceCount} produtos sem preço — role para revisar.`}
            </p>
          )}

          <div className="mt-4 flex flex-col gap-2 sm:flex-row">
            <Button
              type="button"
              onClick={handleConfirm}
              disabled={!canConfirm}
              title={hasMissingPrice ? 'Preencha o preço de todos os produtos antes de confirmar' : undefined}
            >
              {commitMutation.isPending ? 'Importando...' : 'Confirmar importação'}
            </Button>
            <button
              type="button"
              onClick={startOver}
              className="rounded-md border border-gray-300 px-3 py-2 text-sm text-gray-700 hover:bg-gray-100 dark:border-white/10 dark:text-stone-300 dark:hover:bg-white/5"
            >
              Cancelar
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
