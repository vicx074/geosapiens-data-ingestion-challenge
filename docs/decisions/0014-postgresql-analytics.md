# ADR 0014: Agregações financeiras no PostgreSQL

- Status: aceito
- Data: 2026-08-26

## Contexto

O desafio exige soma de valores e agregações por categoria e por mês sobre arquivos que podem ultrapassar um milhão de registros. Trazer as transações paginadas para Java e somá-las na aplicação aumentaria transferência, memória e tempo sem aproveitar o mecanismo de agregação do PostgreSQL.

As consultas também podem ocorrer enquanto o Worker ainda confirma lotes. Executar total, categorias e meses em três instruções separadas sob o isolamento padrão permitiria que cada instrução observasse um conjunto de commits diferente.

## Decisão

`GET /imports/{id}/analytics` expõe os indicadores necessários ao dashboard:

- quantidade total de transações válidas;
- soma total dos valores;
- quantidade e soma por categoria;
- quantidade e soma por mês UTC.

A camada HTTP chama `GetIngestionAnalytics`. O caso de uso valida a existência da importação e depende da porta `IngestionAnalyticsQuery`. Somente o adaptador PostgreSQL conhece SQL e `JdbcClient`.

O PostgreSQL calcula todas as dimensões em uma única instrução usando `GROUPING SETS`. Total, categorias e meses pertencem, portanto, ao mesmo snapshot da instrução, inclusive quando novos lotes são commitados simultaneamente.

A agregação mensal converte `occurred_at` explicitamente para UTC antes de truncar o mês. O contrato do dataset usa UTC, e a consulta não dependerá do timezone configurado na JVM ou na sessão do banco.

Valores permanecem `NUMERIC(19,2)` no PostgreSQL e `BigDecimal` na aplicação. Não haverá conversão para `double` no cálculo financeiro.

## Índice orientado à consulta

A consulta filtra sempre uma única importação e consome `category`, `occurred_at` e `amount`. Por isso, este marco cria:

```sql
CREATE INDEX idx_transactions_analytics_by_import
    ON transactions (import_id)
    INCLUDE (category, occurred_at, amount);
```

`import_id` é a chave porque participa do predicado. As demais colunas são payload de cobertura, não critérios de ordenação. Isso permite ao PostgreSQL considerar um index-only scan sem aumentar desnecessariamente a chave B-tree.

O índice tem custo real de escrita, armazenamento e manutenção. Sua permanência não será defendida apenas por intuição: o benchmark com dataset representativo deve comparar latência, buffers e plano de execução com `EXPLAIN (ANALYZE, BUFFERS)`. Se o planner preferir o heap e o ganho não compensar o custo, a evidência deve prevalecer e o índice poderá ser revisto.

## Alternativas rejeitadas

- Agregar em Java: exigiria transportar e percorrer milhões de registros fora do banco.
- Três consultas SQL independentes: simplificariam cada SQL, mas poderiam produzir totais internamente diferentes durante processamento concorrente e fariam mais round-trips.
- Materialized view: adicionaria política de refresh e consistência sem requisito de dashboard histórico pré-calculado.
- Redis ou cache de aplicação: adicionariam invalidação e outro componente fora do system design sem necessidade medida.
- Índices separados para categoria e mês imediatamente: ampliariam custo de escrita antes de evidência de que dois índices superam um acesso coberto orientado por importação.

## Consequências

O payload do dashboard cresce com a quantidade de categorias e meses, não com a quantidade de transações. No dataset definido, essas dimensões são pequenas e previsíveis. A API continua no fluxo `React -> Spring Boot API -> PostgreSQL` já previsto no system design, sem serviço novo.

O endpoint usa `Cache-Control: no-store`: durante o processamento, cada chamada representa um snapshot consistente dos lotes já commitados, mas não substitui `GET /imports/{id}` para determinar estado terminal.
