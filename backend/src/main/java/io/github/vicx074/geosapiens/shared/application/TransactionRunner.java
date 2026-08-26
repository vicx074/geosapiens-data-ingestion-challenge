package io.github.vicx074.geosapiens.shared.application;

import java.util.function.Supplier;

public interface TransactionRunner {

  <T> T required(Supplier<T> action);
}
