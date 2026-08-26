import { useRef } from 'react'
import { useVirtualizer } from '@tanstack/react-virtual'

import { useImportErrorsPage } from '../../hooks/useImportRecordsPages'
import { formatInteger } from '../../utils/formatters'
import { CursorPagination } from './CursorPagination'

const ERROR_ROW_HEIGHT = 68

type ErrorsListProps = {
  jobId: string
  terminal: boolean
}

export function ErrorsList({ jobId, terminal }: ErrorsListProps) {
  const viewportRef = useRef<HTMLDivElement>(null)
  const page = useImportErrorsPage(jobId)
  const items = page.data?.items ?? []
  const virtualizer = useVirtualizer({
    count: items.length,
    getScrollElement: () => viewportRef.current,
    estimateSize: () => ERROR_ROW_HEIGHT,
    overscan: 4,
    initialRect: { width: 960, height: 340 },
  })

  if (page.isLoading && !page.data) {
    return (
      <div className="records-feedback" aria-live="polite" aria-busy="true">
        <strong>Carregando linhas rejeitadas…</strong>
        <span>Os erros também são consultados em páginas limitadas por cursor.</span>
      </div>
    )
  }

  if (page.error && !page.data) {
    return (
      <div className="records-feedback records-feedback--error" role="alert">
        <div>
          <strong>Não foi possível carregar os erros.</strong>
          <span>O processamento não é alterado por uma falha nesta consulta de leitura.</span>
        </div>
        <button className="button button--secondary" type="button" onClick={() => void page.refreshPage()}>
          Tentar novamente
        </button>
      </div>
    )
  }

  return (
    <div className="records-page" role="tabpanel" aria-labelledby="records-tab-errors">
      {page.error && page.data ? (
        <div className="records-inline-warning" role="status">
          <strong>Última página preservada.</strong>
          <span>A atualização dos erros falhou, mas a página confirmada continua visível.</span>
        </div>
      ) : null}

      {page.navigationError ? (
        <div className="records-inline-warning records-inline-warning--error" role="alert">
          <strong>Não foi possível trocar de página.</strong>
          <span>A página atual foi mantida para preservar o contexto.</span>
        </div>
      ) : null}

      {items.length === 0 ? (
        <div className="records-empty">
          <strong>Nenhuma rejeição confirmada neste snapshot.</strong>
          <span>
            {terminal
              ? 'A importação terminou sem erros de linha nesta coleção.'
              : 'Erros aparecerão aqui caso novas linhas sejam rejeitadas durante o processamento.'}
          </span>
        </div>
      ) : (
        <div
          className="records-table records-table--errors"
          role="table"
          aria-label="Linhas rejeitadas"
          aria-colcount={3}
          aria-rowcount={items.length + 1}
        >
          <div className="records-table__horizontal">
            <div className="records-table__inner records-table__inner--errors">
              <div role="rowgroup">
                <div className="records-table__header records-table__header--errors" role="row">
                  <span role="columnheader">Linha</span>
                  <span role="columnheader">Código</span>
                  <span role="columnheader">Motivo</span>
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
                        key={`${item.sourceRow}-${item.code}`}
                        className="records-table__row records-table__row--errors"
                        role="row"
                        aria-rowindex={virtualRow.index + 2}
                        data-testid="error-row"
                        style={{
                          height: `${virtualRow.size}px`,
                          transform: `translateY(${virtualRow.start}px)`,
                        }}
                      >
                        <span role="cell">{formatInteger(item.sourceRow)}</span>
                        <code role="cell">{item.code}</code>
                        <span role="cell" title={item.reason}>
                          {item.reason}
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
