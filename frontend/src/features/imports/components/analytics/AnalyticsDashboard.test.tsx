import { HttpResponse, http } from 'msw'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { SWRConfig } from 'swr'
import { describe, expect, it } from 'vitest'

import { swrFetcher } from '../../../../shared/api/http-client'
import { server } from '../../../../test/server'
import { AnalyticsDashboard } from './AnalyticsDashboard'

const jobId = '11111111-1111-1111-1111-111111111111'
const analyticsSnapshot = {
  transactionCount: 25,
  totalAmount: 1250.5,
  byCategory: [
    { category: 'Alimentação', transactionCount: 15, totalAmount: 900.5 },
    { category: 'Transporte', transactionCount: 10, totalAmount: 350 },
  ],
  byMonth: [
    { month: '2026-07', transactionCount: 12, totalAmount: 700 },
    { month: '2026-08', transactionCount: 13, totalAmount: 550.5 },
  ],
}

function renderDashboard(options?: { terminal?: boolean; status?: 'PROCESSING' | 'COMPLETED' | 'FAILED' }) {
  return render(
    <SWRConfig
      value={{
        provider: () => new Map(),
        fetcher: swrFetcher,
        dedupingInterval: 0,
        shouldRetryOnError: false,
      }}
    >
      <AnalyticsDashboard
        jobId={jobId}
        status={options?.status ?? 'PROCESSING'}
        terminal={options?.terminal ?? false}
      />
    </SWRConfig>,
  )
}

describe('AnalyticsDashboard', () => {
  it('apresenta somente o snapshot agregado recebido da API', async () => {
    server.use(
      http.get(`http://localhost/api/imports/${jobId}/analytics`, () => HttpResponse.json(analyticsSnapshot)),
    )

    renderDashboard()

    expect(await screen.findByText('Snapshot parcial')).toBeVisible()
    expect(screen.getByText(/1\.250,50/)).toBeVisible()
    expect(screen.getByText('Alimentação')).toBeVisible()
    expect(screen.getByText(/jul\. de 2026/i)).toBeVisible()
    expect(screen.getByText(/lotes são confirmados/i)).toBeVisible()
  })

  it('mostra estado vazio sem inventar dados antes do primeiro lote confirmado', async () => {
    server.use(
      http.get(`http://localhost/api/imports/${jobId}/analytics`, () =>
        HttpResponse.json({ transactionCount: 0, totalAmount: 0, byCategory: [], byMonth: [] }),
      ),
    )

    renderDashboard()

    expect(await screen.findByText(/aguardando dados confirmados/i)).toBeVisible()
    expect(screen.queryByRole('heading', { level: 3, name: /por categoria/i })).not.toBeInTheDocument()
  })

  it('mantém erro de analytics isolado do status e permite reconsulta explícita', async () => {
    let requests = 0
    server.use(
      http.get(`http://localhost/api/imports/${jobId}/analytics`, () => {
        requests += 1
        if (requests === 1) {
          return HttpResponse.json({ title: 'Indisponível' }, { status: 503 })
        }
        return HttpResponse.json(analyticsSnapshot)
      }),
    )

    const user = userEvent.setup()
    renderDashboard({ terminal: true, status: 'COMPLETED' })

    expect(await screen.findByText(/não foi possível carregar os analytics/i)).toBeVisible()
    await user.click(screen.getByRole('button', { name: /tentar analytics novamente/i }))

    expect(await screen.findByText('Snapshot final')).toBeVisible()
    expect(screen.getByText(/1\.250,50/)).toBeVisible()
    expect(requests).toBe(2)
  })
})
