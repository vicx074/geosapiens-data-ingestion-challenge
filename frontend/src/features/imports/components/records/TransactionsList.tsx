import { useRef } from 'react'
import { useVirtualizer } from '@tanstack/react-virtual'

import { useImportTransactionsPage } from '../../hooks/useImportRecordsPages'
import { formatCurrency, formatDateTime, formatInteger } from '../../utils/formatters'
import { CursorPagination } from './CursorPagination'

const TRANSACTION_ROW_HEIGHT = 58

type TransactionsListProps = {
  jobId: string
  terminal: boolean
}

export function TransactionsList({ jobId, terminal }: TransactionsListProps) {
  const viewportRef = useRef<HTMLDivElement>(null)
  const page = useImportTransactionsPage(jobId)
  const items = page.data?.items ?? []
  const virtualizer = useVirtualizer({
    count: items.length,
    getScrollElement: () => viewportRef.current,
    estimateSize: () => TRANSACTION_ROW_HEIGHT,
    overscan: 5,
    initialRect: { width: 960, height: 348 },
  })

  if (page.isLoading && !page.data) {
    return (
      <div className="records-feedback" aria-live="polite" aria-busy="true">
        <strong>Carregando transações confirmadas…</strong>
        <span>A API retorna somente uma página limitada por cursor.</span>
      </div>
    )
  }

  if (page.error && !page.data) {
    return (
      <div className="records-feedback records-feedback--error" role="alert">
        <div>
          <strong>Não foi possível carregar as transações.</strong>
          <span>O status e os analytics continuam independentes desta consulta.</span>
        </div>
        <button className="button button--secondary" type="button" onClick={() => void page.refreshPage()}>
          Tentar novamente
        </button>
      </div>
    )
  }

  return (
    <div className="records-page" role="tabpanel" aria-labelledby="records-tab-transactions">
      {page.error && page.data ? (
        <div className="records-inline-warning" role="status">
          <strong>Última página preservada.</strong>
          <span>A atualização falhou; os registros confirmados anteriormente continuam visíveis.</span>
        </div>
      ) : null}

      {page.navigationError ? (
        <div className="records-inline-warning records-inline-warning--error" role="alert">
          <strong>Não foi possível trocar de página.</strong>
          <span>A página atual foi mantida para não perder o contexto da navegação.</span>
        </div>
      ) : null}

      {items.length === 0 ? (
        <div className="records-empty">
          <strong>Nenhuma transação confirmada neste snapshot.</strong>
          <span>
            {terminal
              ? 'A importação terminou sem transações aceitas.'
              : 'Novas transações aparecerão depois que um lote válido for confirmado.'}
          </span>
        </div>
      ) : (
        <div
          className="records-table records-table--transactions"
          role="table"
          aria-label="Transações confirmadas"
          aria-colcount={5}
          aria-rowcount={items.length + 1}
        >
          <div className="records-table__horizontal">
            <div className="records-table__inner records-table__inner--transactions">
              <div role="rowgroup">
                <div className="records-table__header" role="row">
                  <span role="columnheader">Linha</span>
                  <span role="columnheader">Transação</span>
                  <span role="columnheader">Data</span>
                  <span role="columnheader">Valor</span>
                  <span role="columnheader">Categoria</span>
                </div>
              </div>

              <div
                ref={viewportRef}
                className="records-table__viewport"
                aria-busy={page.isNavigating || page.isValidating}
              >
                <div
                  className="records-table__canvas"
                  role="rowgroup"
                  style={{ height: `${virtualizer.getTotalSize()}px` }}
                >
                  {virtualizer.getVirtualItems().map((virtualRow) => {
                    const item = items[virtualRow.index]
                    return (
                      <div
                        key={item.id}
                        className="records-table__row records-table__row--transactions"
                        role="row"
                        aria-rowindex={virtualRow.index + 2}
                        data-testid="transaction-row"
                        style={{
                          height: `${virtualRow.size}px`,
                          transform: `translateY(${virtualRow.start}px)`,
                        }}
                      >
                        <span role="cell">{formatInteger(item.sourceRow)}</span>
                        <span role="cell" title={item.transactionId}>
                          {item.transactionId}
                        </span>
                        <span role="cell">{formatDateTime(item.occurredAt)}</span>
                        <span role="cell" className="records-table__number">
                          {formatCurrency(item.amount)}
                        </span>
                        <span role="cell" title={item.category}>
                          {item.category}
                        </span>
                      </div>
                    )
                  })}
                </div>
              </div>
            </div>
          </div>
        </div>
      )}

      <CursorPagination
        pageNumber={page.pageNumber}
        canPrevious={page.canPrevious}
        canNext={page.canNext}
        terminal={terminal}
        busy={page.isNavigating}
        refreshing={page.isValidating && !page.isLoading}
        onPrevious={page.previousPage}
        onNext={page.nextPage}
        onRefresh={page.refreshPage}
      />
    </div>
  )
}
