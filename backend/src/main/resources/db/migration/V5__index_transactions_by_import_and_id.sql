-- Sustenta a keyset pagination de GET /imports/{id}/transactions sem OFFSET profundo.
CREATE INDEX idx_transactions_import_cursor
    ON transactions (import_id, id);
