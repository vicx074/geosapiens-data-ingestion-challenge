import { describe, expect, it } from 'vitest'

import type { IngestionStatusResponse } from '../model/imports'
import { getStatusRefreshInterval, STATUS_POLL_INTERVAL_MS } from './useImportStatus'

function status(terminal: boolean): IngestionStatusResponse {
  return {
    jobId: 'job-123',
    filename: 'dataset.csv',
    status: terminal ? 'COMPLETED' : 'PROCESSING',
    processedRows: 10,
    acceptedRows: 10,
    rejectedRows: 0,
    terminal,
    createdAt: '2026-08-26T18:00:00Z',
    queuedAt: '2026-08-26T18:00:01Z',
    startedAt: '2026-08-26T18:00:02Z',
    finishedAt: terminal ? '2026-08-26T18:00:03Z' : null,
    updatedAt: '2026-08-26T18:00:03Z',
    failureReason: null,
  }
}

describe('política de polling', () => {
  it('mantém polling enquanto o estado não é terminal', () => {
    expect(getStatusRefreshInterval()).toBe(STATUS_POLL_INTERVAL_MS)
    expect(getStatusRefreshInterval(status(false))).toBe(STATUS_POLL_INTERVAL_MS)
  })

  it('interrompe polling quando o backend informa terminal=true', () => {
    expect(getStatusRefreshInterval(status(true))).toBe(0)
  })
})
