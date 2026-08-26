import useSWR from 'swr'

import type { IngestionStatusResponse } from '../model/imports'

export const STATUS_POLL_INTERVAL_MS = 2_000

export function getStatusRefreshInterval(latest?: IngestionStatusResponse) {
  return latest?.terminal ? 0 : STATUS_POLL_INTERVAL_MS
}

export function useImportStatus(jobId?: string) {
  const resource = jobId ? `/imports/${encodeURIComponent(jobId)}` : null

  return useSWR<IngestionStatusResponse>(resource, {
    refreshInterval: getStatusRefreshInterval,
    refreshWhenHidden: false,
    refreshWhenOffline: false,
    keepPreviousData: true,
    shouldRetryOnError: false,
  })
}
