package io.github.vicx074.geosapiens.ingestion.infrastructure.persistence;

import io.github.vicx074.geosapiens.ingestion.application.IngestionAnalytics;
import io.github.vicx074.geosapiens.ingestion.application.IngestionAnalytics.CategoryAggregate;
import io.github.vicx074.geosapiens.ingestion.application.IngestionAnalytics.MonthAggregate;
import io.github.vicx074.geosapiens.ingestion.application.port.out.IngestionAnalyticsQuery;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresIngestionAnalyticsQuery implements IngestionAnalyticsQuery {

  private static final String FETCH_ANALYTICS = """
      SELECT
          CASE
              WHEN GROUPING(category) = 0 THEN 'CATEGORY'
              WHEN GROUPING(month_start) = 0 THEN 'MONTH'
              ELSE 'TOTAL'
          END AS aggregation_type,
          category,
          month_start,
          COUNT(*) AS transaction_count,
          COALESCE(SUM(amount), 0::numeric) AS total_amount
      FROM (
          SELECT
              category,
              DATE_TRUNC('month', occurred_at AT TIME ZONE 'UTC')::date AS month_start,
              amount
          FROM transactions
          WHERE import_id = :importId
      ) filtered_transactions
      GROUP BY GROUPING SETS ((), (category), (month_start))
      """;

  private final JdbcClient jdbcClient;

  public PostgresIngestionAnalyticsQuery(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  @Override
  public IngestionAnalytics fetch(UUID importId) {
    Objects.requireNonNull(importId, "O identificador da importação é obrigatório.");

    // GROUPING SETS produz total, categoria e mês na mesma instrução e, portanto, no mesmo snapshot.
    List<AnalyticsRow> rows = jdbcClient.sql(FETCH_ANALYTICS)
        .param("importId", importId)
        .query((resultSet, rowNumber) -> new AnalyticsRow(
            AggregationType.valueOf(resultSet.getString("aggregation_type")),
            resultSet.getString("category"),
            toYearMonth(resultSet.getDate("month_start")),
            resultSet.getLong("transaction_count"),
            resultSet.getBigDecimal("total_amount")))
        .list();

    long totalCount = -1;
    BigDecimal totalAmount = null;
    List<CategoryAggregate> byCategory = new ArrayList<>();
    List<MonthAggregate> byMonth = new ArrayList<>();

    for (AnalyticsRow row : rows) {
      switch (row.type()) {
        case TOTAL -> {
          if (totalAmount != null) {
            throw new IllegalStateException("A consulta de analytics retornou mais de um total geral.");
          }
          totalCount = row.transactionCount();
          totalAmount = row.totalAmount();
        }
        case CATEGORY -> byCategory.add(new CategoryAggregate(
            row.category(), row.transactionCount(), row.totalAmount()));
        case MONTH -> byMonth.add(new MonthAggregate(
            row.month(), row.transactionCount(), row.totalAmount()));
      }
    }

    if (totalAmount == null) {
      throw new IllegalStateException("A consulta de analytics não retornou o total geral.");
    }

    byCategory.sort(Comparator.comparing(CategoryAggregate::category));
    byMonth.sort(Comparator.comparing(MonthAggregate::month));
    return new IngestionAnalytics(totalCount, totalAmount, byCategory, byMonth);
  }

  private static YearMonth toYearMonth(Date monthStart) {
    return monthStart == null ? null : YearMonth.from(monthStart.toLocalDate());
  }

  private enum AggregationType {
    TOTAL,
    CATEGORY,
    MONTH
  }

  private record AnalyticsRow(
      AggregationType type,
      String category,
      YearMonth month,
      long transactionCount,
      BigDecimal totalAmount) {
  }
}
