import type { IngestionStatusResponse } from '../model/imports'
import { STATUS_PRESENTATION } from '../model/imports'
import { formatDateTime, formatInteger } from '../utils/formatters'

export function StatusSummary({ status }: { status: IngestionStatusResponse }) {
  const presentation = STATUS_PRESENTATION[status.status]

  return (
    <section className="status-summary" aria-labelledby="status-summary-title">
      <div className="status-summary__topline">
        <div className="status-summary__identity">
          <p className="eyebrow">Importação</p>
          <h1 id="status-summary-title">{status.filename}</h1>
          <p className="status-summary__job">Job {status.jobId}</p>
        </div>

        <div className="status-summary__state" aria-live="polite" aria-atomic="true">
          <span className={`status-badge status-badge--${presentation.tone}`}>{presentation.label}</span>
          <p>{presentation.description}</p>
        </div>
      </div>

      {!status.terminal ? (
        <div className="activity-track" aria-hidden="true">
          <span />
        </div>
      ) : null}

      <div className="counter-grid" aria-label="Contadores confirmados da importação">
        <div className="counter counter--primary">
          <span>Processadas</span>
          <strong>{formatInteger(status.processedRows)}</strong>
          <small>linhas confirmadas em batches</small>
        </div>
        <div className="counter">
          <span>Aceitas</span>
          <strong>{formatInteger(status.acceptedRows)}</strong>
          <small>persistidas como transações</small>
        </div>
        <div className="counter">
          <span>Rejeitadas</span>
          <strong>{formatInteger(status.rejectedRows)}</strong>
          <small>disponíveis para inspeção</small>
        </div>
      </div>

      <dl className="status-metadata">
        <div>
          <dt>Criado</dt>
          <dd>{formatDateTime(status.createdAt)}</dd>
        </div>
        <div>
          <dt>Início</dt>
          <dd>{formatDateTime(status.startedAt)}</dd>
        </div>
        <div>
          <dt>Última atualização</dt>
          <dd>{formatDateTime(status.updatedAt)}</dd>
        </div>
      </dl>

      {!status.terminal ? (
        <p className="partial-note">
          Estes números representam somente lotes já confirmados no PostgreSQL. O total final ainda não
          é estimado pelo navegador.
        </p>
      ) : null}

      {status.failureReason ? (
        <div className="failure-reason" role="alert">
          <strong>Motivo da falha</strong>
          <span>{status.failureReason}</span>
        </div>
      ) : null}
    </section>
  )
}
