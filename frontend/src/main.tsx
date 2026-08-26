import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'

import { AppProviders } from './app/providers/AppProviders'
import { AppRouter } from './app/router/AppRouter'
import './app/styles/tokens.css'
import './app/styles/global.css'

const root = document.getElementById('root')

if (!root) {
  throw new Error('Elemento raiz da aplicação não foi encontrado.')
}

createRoot(root).render(
  <StrictMode>
    <AppProviders>
      <AppRouter />
    </AppProviders>
  </StrictMode>,
)
