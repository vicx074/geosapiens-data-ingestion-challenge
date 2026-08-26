-- A chave filtra uma importação; INCLUDE mantém no próprio índice as colunas consumidas pelo dashboard.
-- O benefício real do index-only scan será validado em dados representativos com EXPLAIN ANALYZE.
CREATE INDEX idx_transactions_analytics_by_import
    ON transactions (import_id)
    INCLUDE (category, occurred_at, amount);
