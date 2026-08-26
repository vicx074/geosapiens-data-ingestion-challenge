import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'

import { AppRoutes } from './AppRouter'

function renderRoute(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AppRoutes />
    </MemoryRouter>,
  )
}

describe('AppRoutes', () => {
  it('renderiza a entrada principal com hierarquia semântica', () => {
    renderRoute('/')

    expect(screen.getByRole('heading', { level: 1, name: /dados grandes, fluxo simples/i })).toBeVisible()
    expect(screen.getByRole('heading', { level: 2, name: /do arquivo à análise/i })).toBeVisible()
  })

  it('preserva o identificador da importação na rota', () => {
    renderRoute('/imports/job-123')

    expect(screen.getByRole('heading', { level: 1, name: /contexto preservado pela url/i })).toBeVisible()
    expect(screen.getByText('job-123')).toBeVisible()
  })

  it('oferece recuperação quando a rota não existe', () => {
    renderRoute('/rota-inexistente')

    expect(screen.getByRole('heading', { level: 1, name: /esta rota não existe/i })).toBeVisible()
    expect(screen.getByRole('link', { name: /voltar ao início/i })).toHaveAttribute('href', '/')
  })
})
