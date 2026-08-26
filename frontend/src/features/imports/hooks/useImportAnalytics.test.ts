import { describe, expect, it } from 'vitest'

import { ANALYTICS_POLL_INTERVAL_MS, getAnalyticsRefreshInterval } from './useImportAnalytics'

describe('useImportAnalytics', () => {
  it('mantém polling mais espaçado enquanto o job não é terminal', () => {
    expect(getAnalyticsRefreshInterval(false)).toBe(ANALYTICS_POLL_INTERVAL_MS)
  })

  it('interrompe o polling quando o estado do job é terminal', () => {
    expect(getAnalyticsRefreshInterval(true)).toBe(0)
  })
})
