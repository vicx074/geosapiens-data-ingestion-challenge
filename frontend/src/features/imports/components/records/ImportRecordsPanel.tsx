import { useState } from 'react'

import { formatInteger } from '../../utils/formatters'
import { ErrorsList } from './ErrorsList'
import { TransactionsList } from './TransactionsList'
import './records.css'

type RecordsTab = 'transactions' | 'errors'

type ImportRecordsPanelProps = {
  jobId: string
  acceptedRows: number
  rejectedRows: number
  terminal: boolean
}

export function ImportRecordsPanel({
  jobId,
  acceptedRows,
  rejectedRows,
  terminal,
}: ImportRecordsPanelProps) {
  const [activeTab, setActiveTab] = useState<RecordsTab>('transactions')

  return (
    <section className="records" aria-labelledby="records-title">
      <header className="records__heading">
        <div>
          <p className="eyebrow">Registros</p>
          <h2 id="records-title">Inspeção paginada</h2>
          <p>
            Cada consulta traz no máximo 100 itens. A virtualização reduz o DOM sem transformar a
            interface em um carregamento infinito.
          </p>
        </div>
      </header>

      <div className="records-tabs" role="tablist" aria-label="Coleções da importação">
        <button
          id="records-tab-transactions"
          className="records-tab"
          type="button"
          role="tab"
          aria-selected={activeTab === 'transactions'}
          onClick={() => setActiveTab('transactions')}
        >
          Transações <span>{formatInteger(acceptedRows)}</span>
        </button>
        <button
          id="records-tab-errors"
          className="records-tab"
          type="button"
          role="tab"
          aria-selected={activeTab === 'errors'}
          onClick={() => setActiveTab('errors')}
        >
          Erros <span>{formatInteger(rejectedRows)}</span>
        </button>
      </div>

      {/* Apenas a aba ativa permanece montada. Assim, o navegador não mantém simultaneamente
          páginas de transações e erros quando o usuário está inspecionando somente uma coleção. */}
      {activeTab === 'transactions' ? (
        <TransactionsList jobId={jobId} terminal={terminal} />
      ) : (
        <ErrorsList jobId={jobId} terminal={terminal} />
      )}
    </section>
  )
}
