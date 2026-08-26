-- O benchmark de 1M mostrou o mesmo Seq Scan com e sem a cobertura, enquanto o índice
-- adicionava custo de escrita e cerca de 59 MB no cenário medido. A evidência prevalece.
DROP INDEX IF EXISTS idx_transactions_analytics_by_import;
