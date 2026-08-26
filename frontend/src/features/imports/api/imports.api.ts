import { requestJson } from '../../../shared/api/http-client'
import type {
  AcceptedIngestionResponse,
  IngestionAnalyticsResponse,
  IngestionStatusResponse,
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
