import { HttpResponse, http } from 'msw'
import { describe, expect, it } from 'vitest'

import { server } from '../../test/server'
import { HttpError, requestJson, resolveApiUrl } from './http-client'

describe('http-client', () => {
  it('resolve a API no mesmo origin por padrão', () => {
    expect(resolveApiUrl('/foundation')).toBe('http://localhost/api/foundation')
  })

  it('desserializa respostas JSON bem-sucedidas', async () => {
    server.use(
      http.get('http://localhost/api/foundation', () =>
        HttpResponse.json({ status: 'ok' }),
      ),
    )

    await expect(requestJson('/foundation')).resolves.toEqual({ status: 'ok' })
  })

  it('preserva status e detalhe de Problem Details', async () => {
    server.use(
      http.get('http://localhost/api/failure', () =>
        HttpResponse.json(
          { title: 'Serviço indisponível', detail: 'Tente novamente mais tarde.', status: 503 },
          { status: 503 },
        ),
      ),
    )

    const request = requestJson('/failure')

    await expect(request).rejects.toBeInstanceOf(HttpError)
    await expect(request).rejects.toMatchObject({
      status: 503,
      detail: 'Tente novamente mais tarde.',
    })
  })
})
