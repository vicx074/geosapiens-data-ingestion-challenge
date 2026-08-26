CREATE TABLE transactions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    import_id UUID NOT NULL,
    source_row BIGINT NOT NULL,
    transaction_id VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    category VARCHAR(100) NOT NULL,
    CONSTRAINT fk_transactions_import
        FOREIGN KEY (import_id) REFERENCES ingestion_jobs (id) ON DELETE CASCADE,
    CONSTRAINT uq_transactions_import_source_row
        UNIQUE (import_id, source_row),
    CONSTRAINT ck_transactions_source_row
        CHECK (source_row >= 2),
    CONSTRAINT ck_transactions_transaction_id
        CHECK (BTRIM(transaction_id) <> ''),
    CONSTRAINT ck_transactions_amount
        CHECK (amount <> 0),
    CONSTRAINT ck_transactions_category
        CHECK (BTRIM(category) <> '')
);

CREATE TABLE ingestion_errors (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    import_id UUID NOT NULL,
    source_row BIGINT NOT NULL,
    error_code VARCHAR(64) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    CONSTRAINT fk_ingestion_errors_import
        FOREIGN KEY (import_id) REFERENCES ingestion_jobs (id) ON DELETE CASCADE,
    CONSTRAINT uq_ingestion_errors_import_source_row
        UNIQUE (import_id, source_row),
    CONSTRAINT ck_ingestion_errors_source_row
        CHECK (source_row >= 2),
    CONSTRAINT ck_ingestion_errors_code
        CHECK (BTRIM(error_code) <> ''),
    CONSTRAINT ck_ingestion_errors_reason
        CHECK (BTRIM(reason) <> '')
);

-- Índices de leitura serão adicionados somente junto às consultas reais.
-- As constraints únicas acima existem para idempotência do processamento, não como otimização genérica.
