import type { PropsWithChildren } from 'react'
import { SWRConfig } from 'swr'

import { swrFetcher } from '../../shared/api/http-client'

export function AppProviders({ children }: PropsWithChildren) {
  return (
    <SWRConfig
      value={{
        fetcher: swrFetcher,
        shouldRetryOnError: false,
        revalidateOnFocus: true,
        revalidateOnReconnect: true,
      }}
    >
      {children}
    </SWRConfig>
  )
}
