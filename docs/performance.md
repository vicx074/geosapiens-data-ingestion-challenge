# Evidências de performance

## Escopo

Este documento registra a execução de referência usada para validar os pontos de maior volumetria do desafio. Os números abaixo descrevem **uma execução específica** e não são apresentados como capacidade garantida de produção.

O benchmark é reproduzível pelo harness em `benchmarks/`. A execução documentada ocorreu em um runner hospedado pelo GitHub, portanto o host físico e a carga externa não são controlados pelo projeto. O valor dessa execução é provar o fluxo com 1M de linhas, registrar limites explícitos e comparar decisões de consulta/índice com o mesmo workload.

## Código e ambiente medidos

- PR: `#19`
- SHA executado pelo checkout de pull request: `42953f465b36b7fe13a48aab60eeee3c02edae21`
- head do PR no início da execução: `93af07b9ccf10274d14f52a7ea7358359473ff4b`
- SO reportado: `Linux-6.17.0-1022-azure-x86_64-with-glibc2.39`
- arquitetura: `x86_64`
- CPUs lógicas visíveis no runner: `4`
- memória visível no host: `16.766.402.560` bytes
- Docker: `28.0.4`
- Docker Compose: `2.38.2`
- Python: `3.13.15`

O checkout de workflows de `pull_request` usa o merge ref gerado pelo GitHub. Por isso o SHA medido pelo processo é o merge SHA acima, enquanto o head do branch era `93af07b...`.

## Perfil de containers

| Serviço | CPU | Limite de memória |
|---|---:|---:|
| API | 1,0 | 512 MiB |
| Worker | 1,0 | 512 MiB |
| PostgreSQL | 1,0 | 768 MiB |
| RabbitMQ | 0,5 | 384 MiB |

Configuração do Worker:

- concorrência: `2`;
- prefetch: `1`;
- batch size: `1000`;
- limite por registro CSV: `4096` caracteres.

Esses valores são parâmetros do cenário de referência, não recomendações de capacity planning.

## Dataset

- linhas de dados: `1.000.000`;
- seed: `42`;
- tamanho: `55.640.069` bytes (~`53,1 MiB`);
- SHA-256: `65e6aa1e5ceb2b977ac316808b0cbc2d6fe90e1bdd9e73a7b58cb9e48fdfa012`;
- registros aceitos: `1.000.000`;
- registros rejeitados: `0`.

O arquivo foi gerado pelo `tools/generate_dataset.py` e não é versionado no repositório.

## Ingestão

| Medida | Resultado |
|---|---:|
| Upload até `202 Accepted` | `0,538 s` |
| `202 Accepted` até estado terminal | `93,662 s` |
| `startedAt` → `finishedAt` do Worker | `92,195 s` |
| Vazão pelo tempo durável do Worker | `10.846,57 linhas/s` |
| Estado final | `COMPLETED` |

O tempo de upload mede o envio no ambiente do runner para o Nginx/API local do mesmo host. Ele não representa tempo de upload pela Internet.

## Memória observada

O harness amostra `docker stats`. Portanto os valores abaixo são **picos observados durante a amostragem**, e não uma afirmação de máximo absoluto de heap/RSS.

| Serviço | Pico observado | Limite do cenário |
|---|---:|---:|
| API | `194.196.275` bytes (~`185,2 MiB`) | `512 MiB` |
| Worker | `160.327.270` bytes (~`152,9 MiB`) | `512 MiB` |
| PostgreSQL | `209.715.200` bytes (`200 MiB`) | `768 MiB` |
| RabbitMQ | `182.347.366` bytes (~`173,9 MiB`) | `384 MiB` |

A execução concluiu sem OOM dentro desses limites. Isso é coerente com a arquitetura baseada em streaming, limite de registro e batch limitado. Uma única execução de 1M não prova, isoladamente, que todo crescimento futuro é constante; essa propriedade também é sustentada pela forma como o código evita materializar o arquivo e acumular páginas/lotes sem limite.

## Latência das APIs após a importação

Cada leitura foi amostrada cinco vezes no mesmo ambiente.

| Consulta | p50 | p95 | máximo |
|---|---:|---:|---:|
| Status | `3,295 ms` | `3,352 ms` | `3,352 ms` |
| Primeira página de transações | `8,538 ms` | `10,699 ms` | `10,699 ms` |
| Cursor profundo (`after=900000`) | `5,626 ms` | `6,443 ms` | `6,443 ms` |
| Analytics | `1.189,740 ms` | `1.347,084 ms` | `1.347,084 ms` |

Status e paginação permaneceram na faixa de milissegundos com 1M de registros. Analytics é a consulta mais cara porque agrega todas as transações do import no PostgreSQL, sem cache ou tabela de pré-agregação. No escopo atual, o resultado de aproximadamente 1,2 s p50 é aceito como trade-off de simplicidade. Se um requisito futuro exigir latência substancialmente menor para analytics, pré-agregação ou outra estratégia deverá ser medida antes de ser adicionada.

## Índices e `EXPLAIN (ANALYZE, BUFFERS)`

### Analytics

A hipótese inicial era manter `idx_transactions_analytics_by_import`, um covering index por `import_id` incluindo `category`, `occurred_at` e `amount`.

A execução final comparou a consulta real de `GROUPING SETS` com o runtime final **sem** esse índice e com um candidato equivalente criado apenas dentro da transação de benchmark. Depois de `VACUUM (ANALYZE) transactions`:

- sem covering index: PostgreSQL escolheu `Seq Scan`; `Execution Time: 1323,589 ms`;
- com covering index candidato: PostgreSQL também escolheu `Seq Scan`; `Execution Time: 1479,195 ms`.

O candidato não mudou o plano e não demonstrou benefício no workload medido. Por isso a migration `V7__remove_unjustified_analytics_covering_index.sql` remove o índice da configuração final. O ADR 0022 registra a decisão.

Isso não significa que um índice desse tipo nunca possa ajudar. Significa apenas que **este projeto não mantém um índice com custo de escrita/armazenamento sem benefício demonstrado no cenário representativo disponível**.

### Paginação de transações

A consulta profunda usa:

```sql
WHERE import_id = ? AND id > ?
ORDER BY id
LIMIT ?
```

O runtime mantém `idx_transactions_import_cursor (import_id, id)`. No benchmark de um único import, o planner conseguiu usar `transactions_pkey` tanto com quanto sem o índice composto; após `VACUUM (ANALYZE)`, a execução ficou em aproximadamente `0,175 ms` com o schema normal e `0,189 ms` durante a variante que remove temporariamente o índice.

Esse resultado **não prova ganho do índice composto**, porque o dataset medido possui apenas um import e seus ids são naturalmente contíguos. O índice é mantido porque o contrato da consulta começa por `import_id` e precisa continuar por `id`; com imports processados concorrentemente, ids podem ficar intercalados. Uma decisão futura de removê-lo exige medição que represente esse cenário, não extrapolação do benchmark de um único import.

Na execução medida, tanto `idx_transactions_import_cursor` quanto a constraint única de idempotência ocupavam `40.624.128` bytes (~`38,7 MiB`) cada.

## O que esta evidência comprova

A execução demonstra, no cenário descrito:

- geração e ingestão real de 1M de linhas;
- conclusão assíncrona sem OOM sob limites explícitos;
- throughput e memória observada registrados pelo harness;
- status e paginação com latência baixa no dataset medido;
- analytics executado no PostgreSQL sem transportar 1M de registros para Java/React;
- comparação de planos antes de defender um índice;
- remoção de um índice candidato quando a evidência não justificou mantê-lo.

## O que esta evidência não comprova

Ela não deve ser usada para afirmar:

- capacidade de produção em determinado hardware;
- SLA universal;
- comportamento sob dezenas de uploads concorrentes;
- benefício do índice de cursor em imports intercalados;
- pico absoluto de memória entre amostras;
- escalabilidade linear para qualquer tamanho de arquivo.

O harness fica versionado para que essas perguntas possam ser medidas quando existir um requisito que as torne necessárias.
