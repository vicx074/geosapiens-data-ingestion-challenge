import { HttpResponse, http } from 'msw'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { SWRConfig } from 'swr'
import { describe, expect, it } from 'vitest'

import { server } from '../../../../test/server'
import { ImportRecordsPanel } from './ImportRecordsPanel'

const jobId = '11111111-1111-1111-1111-111111111111'

function transaction(id: number) {
  return {
    id,
    sourceRow: id + 1,
    transactionId: `txn-${String(id).padStart(3, '0')}`,
    occurredAt: '2026-08-26T18:00:00Z',
    amount: id * 10.5,
    category: id % 2 === 0 ? 'Alimentação' : 'Transporte',
  }
}

function renderPanel() {
  return render(
    <SWRConfig
      value={{
        provider: () => new Map(),
        dedupingInterval: 0,
        shouldRetryOnError: false,
      }}
    >
      <ImportRecordsPanel
        jobId={jobId}
        acceptedRows={120}
        rejectedRows={2}
        terminal={true}
      />
    </SWRConfig>,
  )
}

describe('ImportRecordsPanel', () => {
  it('navega por cursor mantendo somente a página atual e virtualiza as linhas montadas', async () => {
    const requestedCursors: Array<string | null> = []
    server.use(
      http.get(`http://localhost/api/imports/${jobId}/transactions`, ({ request }) => {
        const after = new URL(request.url).searchParams.get('after')
        requestedCursors.push(after)

        if (after === '100') {
          return HttpResponse.json({
            items: Array.from({ length: 20 }, (_, index) => transaction(index + 101)),
            nextCursor: null,
          })
        }

        return HttpResponse.json({
          items: Array.from({ length: 100 }, (_, index) => transaction(index + 1)),
          nextCursor: 100,
        })
      }),
    )

    const user = userEvent.setup()
    renderPanel()

    expect(await screen.findByText('txn-001')).toBeVisible()
    expect(screen.getAllByTestId('transaction-row').length).toBeLessThan(100)
    expect(screen.getByText('Página 1')).toBeVisible()

    await user.click(screen.getByRole('button', { name: /próxima/i }))

    expect(await screen.findByText('txn-101')).toBeVisible()
    expect(screen.getByText('Página 2')).toBeVisible()
    expect(screen.queryByText('txn-001')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /anterior/i }))

    expect(await screen.findByText('txn-001')).toBeVisible()
    expect(screen.getByText('Página 1')).toBeVisible()
    expect(requestedCursors).toEqual([null, '100', null])
  })

  it('monta somente a coleção ativa e carrega erros quando a aba é selecionada', async () => {
    let transactionRequests = 0
    let errorRequests = 0
    server.use(
      http.get(`http://localhost/api/imports/${jobId}/transactions`, () => {
        transactionRequests += 1
        return HttpResponse.json({ items: [transaction(1)], nextCursor: null })
      }),
      http.get(`http://localhost/api/imports/${jobId}/errors`, () => {
        errorRequests += 1
        return HttpResponse.json({
          items: [
            { sourceRow: 3, code: 'AMOUNT_INVALID', reason: 'Valor monetário inválido.' },
            { sourceRow: 8, code: 'CATEGORY_REQUIRED', reason: 'Categoria obrigatória.' },
          ],
          nextCursor: null,
        })
      }),
    )

    const user = userEvent.setup()
    renderPanel()

    expect(await screen.findByText('txn-001')).toBeVisible()
    expect(transactionRequests).toBe(1)
    expect(errorRequests).toBe(0)

    await user.click(screen.getByRole('tab', { name: /erros/i }))

    expect(await screen.findByText('AMOUNT_INVALID')).toBeVisible()
    expect(screen.getByText('Valor monetário inválido.')).toBeVisible()
    expect(errorRequests).toBe(1)
  })
})
