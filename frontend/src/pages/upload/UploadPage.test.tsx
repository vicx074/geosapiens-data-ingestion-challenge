import { HttpResponse, http } from 'msw'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useParams } from 'react-router-dom'
import { describe, expect, it } from 'vitest'

import { server } from '../../test/server'
import { UploadPage } from './UploadPage'

function Destination() {
  const { id } = useParams()
  return <p>Destino {id}</p>
}

function renderUpload() {
  return render(
    <MemoryRouter initialEntries={['/']}>
      <Routes>
        <Route path="/" element={<UploadPage />} />
        <Route path="/imports/:id" element={<Destination />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('UploadPage', () => {
  it('envia o File no campo multipart esperado e navega para o job aceito', async () => {
    let receivedFile = false
    let receivedSize = 0

    server.use(
      http.post('http://localhost/api/imports', async ({ request }) => {
        const body = await request.formData()
        const uploaded = body.get('file')
        receivedFile = uploaded !== null
        if (uploaded && typeof uploaded === 'object' && 'size' in uploaded) {
          receivedSize = Number(uploaded.size)
        }

        return HttpResponse.json(
          { jobId: 'job-123', status: 'QUEUED', statusUrl: '/imports/job-123' },
          { status: 202, headers: { Location: '/imports/job-123' } },
        )
      }),
    )

    const user = userEvent.setup()
    renderUpload()
    const file = new File(['transaction_id,occurred_at,amount,category'], 'dataset.csv', { type: 'text/csv' })
    await user.upload(screen.getByLabelText(/selecionar arquivo csv/i), file)
    await user.click(screen.getByRole('button', { name: /iniciar importação/i }))

    expect(await screen.findByText('Destino job-123')).toBeVisible()
    expect(receivedFile).toBe(true)
    expect(receivedSize).toBeGreaterThan(0)
  })

  it('não repete automaticamente um POST cujo resultado ficou incerto', async () => {
    let requests = 0

    server.use(
      http.post('http://localhost/api/imports', () => {
        requests += 1
        return HttpResponse.json(
          { title: 'Serviço indisponível', detail: 'Falha temporária no recebimento.' },
          { status: 503 },
        )
      }),
    )

    const user = userEvent.setup()
    renderUpload()
    await user.upload(screen.getByLabelText(/selecionar arquivo csv/i), new File(['conteúdo'], 'dataset.csv'))
    await user.click(screen.getByRole('button', { name: /iniciar importação/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/falha temporária/i)
    expect(requests).toBe(1)
  })

  it('rejeita CSV vazio sem enviar requisição', async () => {
    let requests = 0
    server.use(
      http.post('http://localhost/api/imports', () => {
        requests += 1
        return HttpResponse.json({})
      }),
    )

    const user = userEvent.setup()
    renderUpload()
    await user.upload(
      screen.getByLabelText(/selecionar arquivo csv/i),
      new File([], 'dataset.csv', { type: 'text/csv' }),
    )

    expect(screen.getByRole('alert')).toHaveTextContent(/arquivo selecionado está vazio/i)
    expect(screen.getByRole('button', { name: /iniciar importação/i })).toBeDisabled()
    expect(requests).toBe(0)
  })
})
