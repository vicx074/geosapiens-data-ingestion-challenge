import { requestJson } from '../../../shared/api/http-client'
import type {
  AcceptedIngestionResponse,
  IngestionAnalyticsResponse,
  IngestionErrorPageResponse,
  IngestionStatusResponse,
  IngestionTransactionPageResponse,
} from '../model/imports'

export function buildImportFormData(file: File) {
  const formData = new FormData()
  formData.append('file', file, file.name)
  return formData
}

export async function createImport(file: File, signal?: AbortSignal) {
  return requestJson<AcceptedIngestionResponse>('/imports', {
    method: 'POST',
    body: buildImportFormData(file),
    signal,
  })
}

export function getImportStatus(jobId: string, signal?: AbortSignal) {
  return requestJson<IngestionStatusResponse>(`/imports/${encodeURIComponent(jobId)}`, {
    signal,
  })
}

export function getImportAnalytics(jobId: string, signal?: AbortSignal) {
  return requestJson<IngestionAnalyticsResponse>(`/imports/${encodeURIComponent(jobId)}/analytics`, {
    signal,
  })
}

function buildCursorPath(jobId: string, resource: 'transactions' | 'errors', limit: number, after: number | null) {
  const query = new URLSearchParams({ limit: String(limit) })
  if (after !== null) {
    query.set('after', String(after))
  }

  return `/imports/${encodeURIComponent(jobId)}/${resource}?${query.toString()}`
}

export function getImportTransactions(
  jobId: string,
  limit: number,
  after: number | null,
  signal?: AbortSignal,
) {
  return requestJson<IngestionTransactionPageResponse>(
    buildCursorPath(jobId, 'transactions', limit, after),
    { signal },
  )
}

export function getImportErrors(jobId: string, limit: number, after: number | null, signal?: AbortSignal) {
  return requestJson<IngestionErrorPageResponse>(buildCursorPath(jobId, 'errors', limit, after), {
    signal,
  })
}
