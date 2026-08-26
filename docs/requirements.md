# Requisitos e critérios de aceite

Este documento separa requisitos do enunciado, decisões da solução e evidências existentes. Uma decisão arquitetural não é apresentada como requisito do desafio.

| ID | Origem | Necessidade | Implementação | Evidência |
|---|---|---|---|---|
| R01 | Obrigatório | Backend Java com Spring Boot | Aplicação Spring Boot Java 21 containerizada | `mvn verify`, testes Testcontainers e smoke no Compose |
| R02 | Obrigatório | Frontend React | SPA React + TypeScript + Vite containerizada | Typecheck, testes de comportamento, build de produção e smoke no Compose |
| R03 | Obrigatório | CSV com mais de 1 milhão de linhas | Gerador determinístico versionado | Testes do gerador e referência real com 1.000.000 de linhas, seed 42 e SHA-256 registrado |
| R04 | Obrigatório | Não carregar o arquivo inteiro em RAM | Upload multipart em disco; Worker com parser progressivo, limite de registro antes da materialização e buffer de lote limitado | Testes de storage/parser/batch, oversized/multiline, E2E e referência 1M concluída sem OOM sob limites explícitos |
| R05 | Obrigatório | Processamento assíncrono | `202 Accepted` após arquivo, job e Outbox duráveis; Worker RabbitMQ com concorrência e prefetch limitados | Testes de contrato, mensageria, E2E com PostgreSQL + RabbitMQ reais e smoke do Compose |
| R06 | Obrigatório | Batch insert | JDBC batch configurável; transações, erros e progresso confirmados na mesma transação por lote | Testes de integração/rollback, E2E e benchmark com batch size 1000 registrado |
| R07 | Obrigatório | Status e erros | `GET /imports/{id}` expõe estado e contadores duráveis; `GET /imports/{id}/errors` expõe rejeições em páginas limitadas | Testes de caso de uso, contrato HTTP, persistência, continuidade entre páginas e E2E |
| R08 | Obrigatório | Paginação eficiente | Keyset pagination em erros por `source_row` e transações por `id`, sem offsets profundos | Testes de continuidade/isolamento, contrato HTTP e referência 1M com cursor profundo p50 de 5,626 ms |
| R09 | Obrigatório | Agregação otimizada | `GET /imports/{id}/analytics` calcula total, categoria e mês no PostgreSQL com `GROUPING SETS` | Testes de resultado/precisão/isolamento, E2E e referência 1M com analytics p50 de 1.189,740 ms |
| R10 | Obrigatório | Índices adequados | Erros reutilizam `(import_id, source_row)`; transações mantêm `(import_id, id)`; covering index candidato de analytics foi removido após `EXPLAIN` não demonstrar benefício | Migrações, testes de schema, planos com `EXPLAIN (ANALYZE, BUFFERS)` e ADR 0022 |
| R11 | Obrigatório | Interface responsiva e renderização limpa | Página de 100 itens por cursor limita rede/memória e TanStack Virtual limita linhas montadas no DOM; somente a coleção ativa permanece montada | Testes de cursor, substituição da página atual, quantidade de rows virtualizadas, estados de erro e acessibilidade básica |
| R12 | Obrigatório | Execução plug-and-play | Compose materializa frontend/Nginx, API, Worker, RabbitMQ, PostgreSQL e volume temporário compartilhado; `/api` mantém o backend interno e o upload sem buffering no proxy | `docker compose config`, build das imagens e smoke E2E via Nginx no CI |
| D01 | Diferencial | Mensageria | RabbitMQ como fila de trabalho com ACK manual, no máximo uma redelivery de processamento e DLQ mesmo quando não é possível persistir `FAILED` após o orçamento de retry | Testes de ACK, redelivery, convergência para DLQ, falha ao persistir estado terminal e E2E com broker real |
| S01 | Decisão | Atualização de status | SWR faz polling em `GET /imports/{id}` sobre estado persistido e interrompe a revalidação quando `terminal=true`; resposta do backend usa `no-store` | Testes de polling, interrupção em estado terminal, reconexão/erro e E2E |
| S02 | Decisão | Evitar duplicação | `UNIQUE (import_id, source_row)` e inserts idempotentes por lote | Teste de reprocessamento sem duplicação de dados ou progresso |
| S03 | Decisão | Armazenamento intermediário | Volume Docker temporário removido depois do estado terminal | Testes de storage/Worker e E2E de cleanup |
| S04 | Decisão | Observabilidade mínima | Logs estruturados por job; métricas de entregas/duração do Worker com outcomes de baixa cardinalidade; Actuator para JVM/HTTP/RabbitMQ | Teste das métricas customizadas, inspeção de `/actuator/metrics` e logs sem dados financeiros |
| S05 | Decisão | Estado do frontend | SWR gerencia somente server-state; estado visual permanece local ao React; Redux/Zustand não entram sem necessidade concreta | Testes de comportamento sem store global e revisão da fronteira `features/shared` |
| S06 | Decisão | Upload no navegador | CSV é enviado como um único multipart; não há parse completo, Base64, JSON de milhões de linhas nem chunking de aplicação no browser | Teste de upload com `File`/`FormData` e inspeção do fluxo no frontend |
| S07 | Decisão | Memória e DOM no frontend | Cliente guarda somente a página atual no cache por coleção, preserva apenas histórico de cursores e virtualiza linhas visíveis; não há infinite scroll acumulativo | Teste com 100 itens confirmando menos rows montadas no DOM, troca de página sem manter registros anteriores e montagem somente da aba ativa |

## Evidência de larga escala

A referência versionada em [`docs/performance.md`](performance.md) executou o fluxo real com:

- 1.000.000 de linhas válidas;
- seed `42`;
- batch size `1000`;
- Worker com concorrência `2` e prefetch `1`;
- API e Worker limitados a `512 MiB` cada;
- Worker concluindo em `92,195 s`, aproximadamente `10.846,57 linhas/s`;
- pico observado de aproximadamente `185,2 MiB` na API e `152,9 MiB` no Worker;
- status p50 `3,295 ms`;
- primeira página p50 `8,538 ms`;
- cursor profundo p50 `5,626 ms`;
- analytics p50 `1.189,740 ms`.

Os valores foram coletados em runner hospedado pelo GitHub e são descritos como **referência**, não como SLA ou capacity planning. O relatório registra host, versões, limites de containers, SHA do dataset e limitações da medição.

A comparação de planos mostrou que o covering index de analytics não alterava o `Seq Scan` no workload medido. Depois de `VACUUM (ANALYZE)`, a consulta ficou em aproximadamente `1323,589 ms` sem o candidato e `1479,195 ms` com ele. A solução final remove o índice em vez de manter custo de escrita sem ganho demonstrado.

O benchmark possui apenas um import. Por isso ele não é apresentado como prova de ganho do índice `(import_id, id)` em ids intercalados de imports concorrentes. Esse limite está documentado explicitamente.

## Validação contínua

O workflow normal de CI executa quatro frentes:

- **Backend:** `mvn verify`, incluindo Testcontainers;
- **Frontend:** `npm ci`, typecheck, testes de comportamento e build de produção;
- **Dataset e benchmark helpers:** testes do gerador e do harness, mais uma amostra determinística;
- **Docker Compose:** validação do YAML, build das imagens e smoke da solução completa.

O cenário E2E do backend atravessa upload HTTP, armazenamento temporário, Transactional Outbox, RabbitMQ, Worker, persistência PostgreSQL, cleanup e endpoints de leitura usando as tecnologias reais do System Design.

O smoke do Compose envia um CSV pelo Nginx, acompanha o processamento até `COMPLETED` e consulta analytics/transações pelo proxy. Ele comprova empacotamento e integração, mas não substitui o benchmark de 1M.

O job de 1M usado para produzir a referência inicial foi removido do CI normal depois da coleta. O harness permanece versionado e reproduzível para evitar executar um benchmark caro e variável em cada pull request.
