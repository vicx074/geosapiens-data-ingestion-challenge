import { formatInteger } from '../../utils/formatters'

type CursorPaginationProps = {
  pageNumber: number
  canPrevious: boolean
  canNext: boolean
  terminal: boolean
  busy: boolean
  refreshing: boolean
  onPrevious: () => Promise<void>
  onNext: () => Promise<void>
  onRefresh: () => Promise<unknown>
}

export function CursorPagination({
  pageNumber,
  canPrevious,
  canNext,
  terminal,
  busy,
  refreshing,
  onPrevious,
  onNext,
  onRefresh,
}: CursorPaginationProps) {
  return (
    <div className="cursor-pagination">
      <div>
        <strong>Página {formatInteger(pageNumber)}</strong>
        {!canNext ? (
          <span>
            {terminal
              ? 'Fim dos dados confirmados.'
              : 'Fim do snapshot atual; novas linhas ainda podem ser confirmadas.'}
          </span>
        ) : null}
      </div>

      <div className="cursor-pagination__actions" aria-label="Navegação entre páginas">
        <button
          className="button button--secondary"
          type="button"
          disabled={!canPrevious || busy}
          onClick={() => void onPrevious()}
        >
          ← Anterior
        </button>
        <button
          className="button button--ghost"
          type="button"
          disabled={busy || refreshing}
          onClick={() => void onRefresh()}
        >
          {refreshing ? 'Atualizando…' : 'Atualizar página'}
        </button>
        <button
          className="button button--secondary"
          type="button"
          disabled={!canNext || busy}
          onClick={() => void onNext()}
        >
          Próxima →
        </button>
      </div>
    </div>
  )
}
