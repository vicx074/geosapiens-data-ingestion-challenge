import useSWR from 'swr'

import type { IngestionAnalyticsResponse } from '../model/imports'

export const ANALYTICS_POLL_INTERVAL_MS = 4_000

export function getAnalyticsRefreshInterval(terminal: boolean) {
  return terminal ? 0 : ANALYTICS_POLL_INTERVAL_MS
}

export function useImportAnalytics(jobId?: string, terminal = false) {
  const resource = jobId ? `/imports/${encodeURIComponent(jobId)}/analytics` : null

  return useSWR<IngestionAnalyticsResponse>(resource, {
    // Analytics representa somente lotes já confirmados. Atualizamos em frequência menor que o status
    // porque o endpoint é mais caro e não precisa acompanhar cada transição do job em tempo quase real.
    refreshInterval: getAnalyticsRefreshInterval(terminal),
    refreshWhenHidden: false,
    refreshWhenOffline: false,
    keepPreviousData: true,
    shouldRetryOnError: false,
  })
}
