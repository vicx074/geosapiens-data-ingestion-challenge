import { useCallback } from 'react'

import { getImportErrors, getImportTransactions } from '../api/imports.api'
import { useCursorPage } from './useCursorPage'

export const RECORDS_PAGE_SIZE = 100

export function useImportTransactionsPage(jobId: string) {
  const loadPage = useCallback(
    (after: number | null) => getImportTransactions(jobId, RECORDS_PAGE_SIZE, after),
    [jobId],
  )

  return useCursorPage({
    cacheKey: `import:${jobId}:transactions:current-page`,
    loadPage,
  })
}

export function useImportErrorsPage(jobId: string) {
  const loadPage = useCallback(
    (after: number | null) => getImportErrors(jobId, RECORDS_PAGE_SIZE, after),
    [jobId],
  )

  return useCursorPage({
    cacheKey: `import:${jobId}:errors:current-page`,
    loadPage,
  })
}
