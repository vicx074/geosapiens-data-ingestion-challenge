-- O benchmark com 1M mostrou Seq Scan com e sem o covering index e não demonstrou ganho
-- que justificasse manter custo adicional de escrita e armazenamento no schema final.
DROP INDEX IF EXISTS idx_transactions_analytics_by_import;
