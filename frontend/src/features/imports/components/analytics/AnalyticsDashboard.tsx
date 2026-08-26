import type { IngestionStatus } from '../../model/imports'
import { useImportAnalytics } from '../../hooks/useImportAnalytics'
import { formatCurrency, formatInteger } from '../../utils/formatters'
import { CategoryDistribution } from './CategoryDistribution'
import { MonthlySeries } from './MonthlySeries'
import './analytics.css'

type AnalyticsDashboardProps = {
  jobId: string
  status: IngestionStatus
  terminal: boolean
}

function snapshotCopy(status: IngestionStatus, terminal: boolean) {
  if (status === 'FAILED') {
    return {
      label: 'Snapshot antes da falha',
      description: 'Os valores abaixo incluem somente lotes que já tinham sido confirmados antes da falha definitiva.',
    }
  }

  if (terminal) {
    return {
      label: 'Snapshot final',
      description: 'Os valores abaixo representam todas as transações aceitas e confirmadas desta importação.',
    }
  }

  return {
    label: 'Snapshot parcial',
    description: 'Os valores crescem conforme novos lotes são confirmados pelo Worker no PostgreSQL.',
  }
}

export function AnalyticsDashboard({ jobId, status, terminal }: AnalyticsDashboardProps) {
  const { data, error, isLoading, isValidating, mutate } = useImportAnalytics(jobId, terminal)
  const snapshot = snapshotCopy(status, terminal)

  return (
    <section className="analytics" aria-labelledby="analytics-title">
      <header className="analytics__heading">
        <div>
          <p className="eyebrow">Analytics</p>
          <h2 id="analytics-title">Dados confirmados</h2>
          <p>{snapshot.description}</p>
        </div>
        <div className="analytics__snapshot" role="status">
          <strong>{snapshot.label}</strong>
          {!terminal && data ? <span>{isValidating ? 'Atualizando snapshot…' : 'Atualização automática ativa'}</span> : null}
        </div>
      </header>

      {isLoading && !data ? (
        <div className="analytics-loading" aria-live="polite" aria-busy="true">
          <div className="analytics-loading__metric" />
          <div className="analytics-loading__metric" />
          <div className="analytics-loading__panel" />
          <span className="visually-hidden">Carregando os indicadores confirmados da importação.</span>
        </div>
      ) : null}

      {error && !data ? (
        <div className="analytics-error" role="alert">
          <div>
            <strong>Não foi possível carregar os analytics.</strong>
            <span>O status da importação continua disponível; esta consulta pode ser repetida separadamente.</span>
          </div>
          <button className="button button--secondary" type="button" onClick={() => void mutate()}>
            Tentar analytics novamente
          </button>
        </div>
      ) : null}

      {data ? (
        <>
          {error ? (
            <div className="analytics-stale" role="status">
              <strong>Último snapshot preservado.</strong>
              <span>A atualização dos analytics falhou, mas os valores confirmados anteriormente continuam visíveis.</span>
              <button className="button button--ghost" type="button" onClick={() => void mutate()}>
                Atualizar analytics
              </button>
            </div>
          ) : null}

          <div className="analytics-summary">
            <article className="analytics-summary__primary">
              <span>Valor confirmado</span>
              <strong>{formatCurrency(data.totalAmount)}</strong>
              <small>Soma calculada no PostgreSQL sobre as transações aceitas.</small>
            </article>
            <article className="analytics-summary__secondary">
              <span>Transações no snapshot</span>
              <strong>{formatInteger(data.transactionCount)}</strong>
              <small>Somente linhas já persistidas e confirmadas.</small>
            </article>
          </div>

          {data.transactionCount === 0 ? (
            <div className="analytics-empty">
              <strong>Aguardando dados confirmados.</strong>
              <span>O dashboard será preenchido quando o primeiro lote válido for persistido.</span>
            </div>
          ) : (
            <div className="analytics-grid">
              <CategoryDistribution data={data.byCategory} />
              <MonthlySeries data={data.byMonth} />
            </div>
          )}
        </>
      ) : null}
    </section>
  )
}
