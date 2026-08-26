# ADR 0014: Agregações financeiras no PostgreSQL

- Status: aceito; seção de índice revisada pelo ADR 0022
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

A agregação mensal converte `occurred_at` explicitamente para UTC antes de truncar o mês. O contrato do dataset usa UTC, e a consulta não depende do timezone configurado na JVM ou na sessão do banco.

Valores permanecem `NUMERIC(19,2)` no PostgreSQL e `BigDecimal` na aplicação. Não há conversão para `double` no cálculo financeiro.

## Índice orientado à consulta — decisão revisada

Este ADR originalmente criou o seguinte candidato:

```sql
CREATE INDEX idx_transactions_analytics_by_import
    ON transactions (import_id)
    INCLUDE (category, occurred_at, amount);
```

A hipótese era permitir que PostgreSQL considerasse index-only scan para uma consulta filtrada por `import_id` e que consome `category`, `occurred_at` e `amount`.

A própria decisão condicionava a permanência do índice a benchmark com dados representativos. Essa validação foi executada com 1M de linhas e o planner continuou escolhendo `Seq Scan` com e sem o candidato. O ADR 0022 e a migration V7 removem o índice da configuração final.

O restante deste ADR — agregação no PostgreSQL, `GROUPING SETS`, UTC e `BigDecimal` — permanece vigente.

## Alternativas rejeitadas

- Agregar em Java: exigiria transportar e percorrer milhões de registros fora do banco.
- Três consultas SQL independentes: simplificariam cada SQL, mas poderiam produzir totais internamente diferentes durante processamento concorrente e fariam mais round-trips.
- Materialized view: adicionaria política de refresh e consistência sem requisito de dashboard histórico pré-calculado.
- Redis ou cache de aplicação: adicionariam invalidação e outro componente fora do System Design sem necessidade medida.
- Índices separados para categoria e mês: ampliariam custo de escrita sem predicado de produto que justifique a manutenção.

## Consequências

O payload do dashboard cresce com a quantidade de categorias e meses, não com a quantidade de transações. A API continua no fluxo `React -> Spring Boot API -> PostgreSQL` previsto no System Design, sem serviço novo.

O endpoint usa `Cache-Control: no-store`: durante o processamento, cada chamada representa um snapshot consistente dos lotes já commitados, mas não substitui `GET /imports/{id}` para determinar estado terminal.

A latência da consulta e a revisão do índice estão documentadas em `docs/performance.md` e no ADR 0022.
