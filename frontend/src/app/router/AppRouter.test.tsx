import { HttpResponse, http } from 'msw'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { SWRConfig } from 'swr'
import { describe, expect, it } from 'vitest'

import { swrFetcher } from '../../shared/api/http-client'
import { server } from '../../test/server'
import { AppRoutes } from './AppRouter'

function renderRoute(path: string) {
  return render(
    <SWRConfig
      value={{
        provider: () => new Map(),
        fetcher: swrFetcher,
        dedupingInterval: 0,
        shouldRetryOnError: false,
      }}
    >
      <MemoryRouter initialEntries={[path]}>
        <AppRoutes />
      </MemoryRouter>
    </SWRConfig>,
  )
}

describe('AppRoutes', () => {
  it('renderiza a entrada principal com hierarquia semântica', () => {
    renderRoute('/')

    expect(screen.getByRole('heading', { level: 1, name: /dados grandes, fluxo simples/i })).toBeVisible()
    expect(screen.getByRole('heading', { level: 2, name: /do arquivo à análise/i })).toBeVisible()
  })

  it('preserva o identificador da importação na rota e reconstrói o estado remoto', async () => {
    const jobId = '11111111-1111-1111-1111-111111111111'
    server.use(
      http.get(`http://localhost/api/imports/${jobId}`, () =>
        HttpResponse.json({
          jobId,
          filename: 'transactions.csv',
          status: 'PROCESSING',
          processedRows: 10,
          acceptedRows: 9,
          rejectedRows: 1,
          terminal: false,
          createdAt: '2026-08-26T18:00:00Z',
          queuedAt: '2026-08-26T18:00:01Z',
          startedAt: '2026-08-26T18:00:02Z',
          finishedAt: null,
          updatedAt: '2026-08-26T18:00:03Z',
          failureReason: null,
        }),
      ),
      http.get(`http://localhost/api/imports/${jobId}/analytics`, () =>
        HttpResponse.json({
          transactionCount: 0,
          totalAmount: 0,
          byCategory: [],
          byMonth: [],
        }),
      ),
    )

    renderRoute(`/imports/${jobId}`)

    expect(await screen.findByRole('heading', { level: 1, name: 'transactions.csv' })).toBeVisible()
    expect(screen.getByText(`Job ${jobId}`)).toBeVisible()
    expect(await screen.findByText(/aguardando dados confirmados/i)).toBeVisible()
  })

  it('oferece recuperação quando a rota não existe', () => {
    renderRoute('/rota-inexistente')

    expect(screen.getByRole('heading', { level: 1, name: /esta rota não existe/i })).toBeVisible()
    expect(screen.getByRole('link', { name: /voltar ao início/i })).toHaveAttribute('href', '/')
  })
})
