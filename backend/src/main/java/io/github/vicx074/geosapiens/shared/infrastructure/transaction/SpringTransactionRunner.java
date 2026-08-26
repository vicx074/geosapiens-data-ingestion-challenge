package io.github.vicx074.geosapiens.shared.infrastructure.transaction;

import io.github.vicx074.geosapiens.shared.application.TransactionRunner;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public final class SpringTransactionRunner implements TransactionRunner {

  private final TransactionTemplate transactionTemplate;

  public SpringTransactionRunner(TransactionTemplate transactionTemplate) {
    this.transactionTemplate = transactionTemplate;
  }

  @Override
  public <T> T required(Supplier<T> action) {
    return transactionTemplate.execute(status -> action.get());
  }
}
