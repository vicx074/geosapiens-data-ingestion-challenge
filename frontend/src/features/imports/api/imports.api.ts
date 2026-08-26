import { requestJson } from '../../../shared/api/http-client'
import type { AcceptedIngestionResponse, IngestionStatusResponse } from '../model/imports'

export async function createImport(file: File, signal?: AbortSignal) {
  const formData = new FormData()
  formData.append('file', file, file.name)

  return requestJson<AcceptedIngestionResponse>('/imports', {
    method: 'POST',
    body: formData,
    signal,
  })
}

export function getImportStatus(jobId: string, signal?: AbortSignal) {
  return requestJson<IngestionStatusResponse>(`/imports/${encodeURIComponent(jobId)}`, {
    signal,
  })
}
