import { Link } from 'react-router-dom'

export function NotFoundPage() {
  return (
    <div className="page page--compact">
      <section className="intro" aria-labelledby="not-found-title">
        <p className="eyebrow">404</p>
        <h1 id="not-found-title">Esta rota não existe.</h1>
        <p className="intro__description">
          Volte ao início para continuar pelo fluxo de ingestão.
        </p>
        <Link className="text-link" to="/">
          Voltar ao início
        </Link>
      </section>
    </div>
  )
}
