import { HttpResponse, http } from 'msw'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { SWRConfig } from 'swr'
import { describe, expect, it } from 'vitest'

import { swrFetcher } from '../../shared/api/http-client'
import { server } from '../../test/server'
import { ImportDetailsPage } from './ImportDetailsPage'

const processingStatus = {
  jobId: '11111111-1111-1111-1111-111111111111',
  filename: 'transactions.csv',
  status: 'PROCESSING',
  processedRows: 12450,
  acceptedRows: 12300,
  rejectedRows: 150,
  terminal: false,
  createdAt: '2026-08-26T18:00:00Z',
  queuedAt: '2026-08-26T18:00:01Z',
  startedAt: '2026-08-26T18:00:02Z',
  finishedAt: null,
  updatedAt: '2026-08-26T18:00:03Z',
  failureReason: null,
} as const

const analyticsSnapshot = {
  transactionCount: 12300,
  totalAmount: 845230.75,
  byCategory: [
    { category: 'Alimentação', transactionCount: 7300, totalAmount: 512400.5 },
    { category: 'Transporte', transactionCount: 5000, totalAmount: 332830.25 },
  ],
  byMonth: [
    { month: '2026-07', transactionCount: 5900, totalAmount: 403120.25 },
    { month: '2026-08', transactionCount: 6400, totalAmount: 442110.5 },
  ],
}

function renderDetails() {
  server.use(
    http.get(`http://localhost/api/imports/${processingStatus.jobId}/analytics`, () =>
      HttpResponse.json(analyticsSnapshot),
    ),
  )

  return render(
    <SWRConfig
      value={{
        provider: () => new Map(),
        fetcher: swrFetcher,
        dedupingInterval: 0,
        shouldRetryOnError: false,
      }}
    >
      <MemoryRouter initialEntries={[`/imports/${processingStatus.jobId}`]}>
        <Routes>
          <Route path="/imports/:id" element={<ImportDetailsPage />} />
        </Routes>
      </MemoryRouter>
    </SWRConfig>,
  )
}

describe('ImportDetailsPage', () => {
  it('mostra somente contadores duráveis durante o processamento e analytics do snapshot confirmado', async () => {
    server.use(
      http.get(`http://localhost/api/imports/${processingStatus.jobId}`, () =>
        HttpResponse.json(processingStatus),
      ),
    )

    renderDetails()

    expect(await screen.findByText('Processando')).toBeVisible()
    expect(screen.getByText('12.450')).toBeVisible()
    expect(screen.getByText('12.300')).toBeVisible()
    expect(screen.getByText('150')).toBeVisible()
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument()
    expect(screen.getByText(/total final ainda não é estimado/i)).toBeVisible()

    expect(await screen.findByRole('heading', { level: 2, name: /dados confirmados/i })).toBeVisible()
    expect(screen.getByText('R$ 845.230,75')).toBeVisible()
    expect(screen.getByText('Snapshot parcial')).toBeVisible()
  })

  it('distingue falha de leitura do estado FAILED e permite reconsulta explícita', async () => {
    let requests = 0
    server.use(
      http.get(`http://localhost/api/imports/${processingStatus.jobId}`, () => {
        requests += 1
        if (requests === 1) {
          return HttpResponse.json({ title: 'Indisponível' }, { status: 503 })
        }
        return HttpResponse.json({ ...processingStatus, status: 'COMPLETED', terminal: true })
      }),
    )

    const user = userEvent.setup()
    renderDetails()

    expect(await screen.findByText(/não foi possível atualizar o status/i)).toBeVisible()
    expect(screen.getByText(/job não foi marcado como falho/i)).toBeVisible()

    await user.click(screen.getByRole('button', { name: /tentar consultar novamente/i }))

    expect(await screen.findByText('Concluído')).toBeVisible()
    expect(requests).toBe(2)
    expect(await screen.findByText('Snapshot final')).toBeVisible()
  })
})
