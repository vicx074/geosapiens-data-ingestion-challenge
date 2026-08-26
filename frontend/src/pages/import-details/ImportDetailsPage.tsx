import { Link, useParams } from 'react-router-dom'

import { AnalyticsDashboard } from '../../features/imports/components/analytics/AnalyticsDashboard'
import { StatusSummary } from '../../features/imports/components/StatusSummary'
import { useImportStatus } from '../../features/imports/hooks/useImportStatus'
import { HttpError } from '../../shared/api/http-client'

function readErrorCopy(error: unknown) {
  if (error instanceof HttpError && error.status === 404) {
    return {
      title: 'Importação não encontrada',
      description: 'Confira o identificador do job ou inicie uma nova importação.',
    }
  }

  return {
    title: 'Não foi possível atualizar o status',
    description: 'O job não foi marcado como falho. A interface apenas perdeu contato com a API.',
  }
}

export function ImportDetailsPage() {
  const { id } = useParams()
  const { data, error, isLoading, isValidating, mutate } = useImportStatus(id)

  if (!id) {
    return null
  }

  return (
    <div className="page page--details">
      <div className="details-toolbar">
        <Link className="text-link text-link--back" to="/">
          ← Nova importação
        </Link>
        {data && !data.terminal ? (
          <span className="sync-state">{isValidating ? 'Atualizando…' : 'Acompanhamento ativo'}</span>
        ) : null}
      </div>

      {isLoading && !data ? (
        <section className="status-loading" aria-live="polite" aria-busy="true">
          <span className="status-loading__bar" aria-hidden="true" />
          <p>Consultando o estado durável da importação…</p>
        </section>
      ) : null}

      {error && !data ? (
        <section className="read-error" role="alert">
          <p className="eyebrow">Status indisponível</p>
          <h1>{readErrorCopy(error).title}</h1>
          <p>{readErrorCopy(error).description}</p>
          <div className="read-error__actions">
            <button className="button button--primary" type="button" onClick={() => void mutate()}>
              Tentar consultar novamente
            </button>
            <Link className="button button--secondary" to="/">
              Voltar ao início
            </Link>
          </div>
        </section>
      ) : null}

      {data ? (
        <>
          <StatusSummary status={data} />
          {error ? (
            <div className="stale-warning" role="status">
              <strong>Última atualização preservada.</strong>
              <span>A API está temporariamente indisponível; os dados abaixo não foram descartados.</span>
              <button className="button button--ghost" type="button" onClick={() => void mutate()}>
                Atualizar agora
              </button>
            </div>
          ) : null}
          <AnalyticsDashboard jobId={data.jobId} status={data.status} terminal={data.terminal} />
        </>
      ) : null}
    </div>
  )
}
