import { HttpResponse, http } from 'msw'
import { describe, expect, it } from 'vitest'

import { server } from '../../../test/server'
import { buildImportFormData, getImportErrors, getImportTransactions } from './imports.api'

describe('contratos HTTP da importação', () => {
  it('preserva o File e o nome original no campo esperado pelo backend', () => {
    const file = new File(['transaction_id,occurred_at,amount,category'], 'dataset.csv', {
      type: 'text/csv',
    })

    const formData = buildImportFormData(file)
    const uploaded = formData.get('file')

    expect(uploaded).toBeInstanceOf(File)
    expect((uploaded as File).name).toBe('dataset.csv')
    expect((uploaded as File).size).toBe(file.size)
  })

  it('envia limite e cursor somente quando existe página anterior', async () => {
    const jobId = '11111111-1111-1111-1111-111111111111'
    const queries: string[] = []
    server.use(
      http.get(`http://localhost/api/imports/${jobId}/transactions`, ({ request }) => {
        queries.push(new URL(request.url).search)
        return HttpResponse.json({ items: [], nextCursor: null })
      }),
      http.get(`http://localhost/api/imports/${jobId}/errors`, ({ request }) => {
        queries.push(new URL(request.url).search)
        return HttpResponse.json({ items: [], nextCursor: null })
      }),
    )

    await getImportTransactions(jobId, 100, null)
    await getImportErrors(jobId, 100, 250)

    expect(queries).toEqual(['?limit=100', '?limit=100&after=250'])
  })
})
