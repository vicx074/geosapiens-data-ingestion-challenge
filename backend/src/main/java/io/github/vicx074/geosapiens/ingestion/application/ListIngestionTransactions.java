package io.github.vicx074.geosapiens.ingestion.application;

import io.github.vicx074.geosapiens.ingestion.application.port.out.IngestionJobRepository;
import io.github.vicx074.geosapiens.ingestion.application.port.out.IngestionTransactionQuery;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public final class ListIngestionTransactions {

  public static final int DEFAULT_PAGE_SIZE = 50;
  public static final int MAX_PAGE_SIZE = 200;

  private final IngestionJobRepository jobs;
  private final IngestionTransactionQuery transactions;

  public ListIngestionTransactions(
      IngestionJobRepository jobs,
      IngestionTransactionQuery transactions) {
    this.jobs = jobs;
    this.transactions = transactions;
  }

  public IngestionTransactionPage execute(UUID jobId, int limit, Long after) {
    Objects.requireNonNull(jobId, "O identificador da importação é obrigatório.");
    validatePagination(limit, after);

    if (jobs.findById(jobId).isEmpty()) {
      throw new IngestionJobNotFoundException(jobId);
    }

    long cursor = after == null ? 0 : after;

    // Uma linha extra informa se há continuação sem executar COUNT(*) em cada página.
    List<IngestionTransactionRecord> candidates =
        transactions.findAfter(jobId, cursor, limit + 1);
    if (candidates.size() <= limit) {
      return new IngestionTransactionPage(candidates, null);
    }

    List<IngestionTransactionRecord> pageItems = List.copyOf(candidates.subList(0, limit));
    long nextCursor = pageItems.get(pageItems.size() - 1).id();
    return new IngestionTransactionPage(pageItems, nextCursor);
  }

  private static void validatePagination(int limit, Long after) {
    if (limit < 1 || limit > MAX_PAGE_SIZE) {
      throw new InvalidIngestionPaginationException(
          "O limite da página deve estar entre 1 e %d.".formatted(MAX_PAGE_SIZE));
    }
    if (after != null && after < 0) {
      throw new InvalidIngestionPaginationException("O cursor 'after' não pode ser negativo.");
    }
  }
}
