import { BrowserRouter, Route, Routes } from 'react-router-dom'

import { AppShell } from '../layout/AppShell'
import { ImportDetailsPage } from '../../pages/import-details/ImportDetailsPage'
import { NotFoundPage } from '../../pages/not-found/NotFoundPage'
import { UploadPage } from '../../pages/upload/UploadPage'

export function AppRoutes() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route index element={<UploadPage />} />
        <Route path="imports/:id" element={<ImportDetailsPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}

export function AppRouter() {
  return (
    <BrowserRouter>
      <AppRoutes />
    </BrowserRouter>
  )
}
