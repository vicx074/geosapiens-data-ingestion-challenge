import { useParams } from 'react-router-dom'

export function ImportDetailsPage() {
  const { id } = useParams()

  return (
    <div className="page page--details">
      <section className="intro" aria-labelledby="import-page-title">
        <p className="eyebrow">Importação</p>
        <h1 id="import-page-title">Contexto preservado pela URL.</h1>
        <p className="intro__description">
          A rota já identifica o job para que refresh e acesso direto possam reconstruir a tela a partir
          do estado remoto no próximo marco.
        </p>
        {id ? <code className="job-id">{id}</code> : null}
      </section>
    </div>
  )
}
