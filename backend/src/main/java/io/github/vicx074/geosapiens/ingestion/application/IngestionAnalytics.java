package io.github.vicx074.geosapiens.ingestion.application;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;

public record IngestionAnalytics(
    long transactionCount,
    BigDecimal totalAmount,
    List<CategoryAggregate> byCategory,
    List<MonthAggregate> byMonth) {

  public IngestionAnalytics {
    if (transactionCount < 0) {
      throw new IllegalArgumentException("A quantidade total de transações não pode ser negativa.");
    }
    totalAmount = Objects.requireNonNull(totalAmount, "O valor total é obrigatório.");
    byCategory = List.copyOf(Objects.requireNonNull(byCategory, "As agregações por categoria são obrigatórias."));
    byMonth = List.copyOf(Objects.requireNonNull(byMonth, "As agregações por mês são obrigatórias."));

    validateTotals(transactionCount, totalAmount, byCategory, "categoria");
    validateTotals(transactionCount, totalAmount, byMonth, "mês");
  }

  private static void validateTotals(
      long expectedCount,
      BigDecimal expectedAmount,
      List<? extends Aggregate> aggregates,
      String dimension) {
    long groupedCount = aggregates.stream()
        .mapToLong(Aggregate::transactionCount)
        .sum();
    BigDecimal groupedAmount = aggregates.stream()
        .map(Aggregate::totalAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    if (groupedCount != expectedCount || groupedAmount.compareTo(expectedAmount) != 0) {
      throw new IllegalArgumentException(
          "Os totais agregados por %s não correspondem ao total da importação.".formatted(dimension));
    }
  }

  public sealed interface Aggregate permits CategoryAggregate, MonthAggregate {
    long transactionCount();

    BigDecimal totalAmount();
  }

  public record CategoryAggregate(
      String category,
      long transactionCount,
      BigDecimal totalAmount) implements Aggregate {

    public CategoryAggregate {
      category = requireText(category, "A categoria da agregação é obrigatória.");
      validateAggregate(transactionCount, totalAmount);
    }
  }

  public record MonthAggregate(
      YearMonth month,
      long transactionCount,
      BigDecimal totalAmount) implements Aggregate {

    public MonthAggregate {
      month = Objects.requireNonNull(month, "O mês da agregação é obrigatório.");
      validateAggregate(transactionCount, totalAmount);
    }
  }

  private static void validateAggregate(long transactionCount, BigDecimal totalAmount) {
    if (transactionCount < 1) {
      throw new IllegalArgumentException("Uma agregação existente deve conter ao menos uma transação.");
    }
    Objects.requireNonNull(totalAmount, "O valor agregado é obrigatório.");
  }

  private static String requireText(String value, String message) {
    String text = Objects.requireNonNull(value, message).trim();
    if (text.isEmpty()) {
      throw new IllegalArgumentException(message);
    }
    return text;
  }
}
