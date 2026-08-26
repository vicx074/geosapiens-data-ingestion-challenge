import { Link, Outlet } from 'react-router-dom'

export function AppShell() {
  return (
    <div className="app-shell">
      <header className="app-header">
        <div className="app-header__inner">
          <Link className="brand" to="/" aria-label="GeoSapiens Data Ingestion — início">
            <span className="brand__mark" aria-hidden="true">
              GS
            </span>
            <span className="brand__copy">
              <strong>Data Ingestion</strong>
              <span>GeoSapiens challenge</span>
            </span>
          </Link>

          <span className="app-header__context">Operações de dados</span>
        </div>
      </header>

      <main className="app-main">
        <Outlet />
      </main>
    </div>
  )
}
