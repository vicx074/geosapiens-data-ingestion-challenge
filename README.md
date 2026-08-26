# GeoSapiens Data Ingestion Challenge

Solução para ingestão, processamento e consulta de arquivos CSV com mais de um milhão de registros, sem carregar o arquivo completo em memória.

A implementação prioriza os pontos destacados no desafio: memória limitada no backend, processamento assíncrono, APIs eficientes em alto volume, estratégia de indexação medida e renderização limpa no React.

## Execução rápida

A entrega depende somente de Docker e Docker Compose no host.

Na raiz do repositório:

```bash
docker compose up
```

Na primeira execução, o Compose constrói as imagens locais e sobe PostgreSQL, RabbitMQ, API, Worker e frontend. Depois que os health checks concluírem, abra:

```text
http://localhost:8080
```

A API fica atrás do mesmo Nginx em `/api`; por exemplo:

```text
http://localhost:8080/api/actuator/health
```

Para reconstruir explicitamente as imagens após alterações locais:

```bash
docker compose up --build
```

Para encerrar e remover também os volumes de dados locais:

```bash
docker compose down --volumes
```

> Remover os volumes apaga PostgreSQL, RabbitMQ e uploads temporários desse ambiente local.

## Objetivos entregues

- upload de CSV grande sem materializar o arquivo completo na aplicação;
- `202 Accepted` com identificador de acompanhamento;
- processamento assíncrono com RabbitMQ;
- leitura progressiva, limite por registro e persistência em batches;
- estado durável, erros por linha e idempotência diante de redelivery;
- paginação por cursor para milhões de registros;
- analytics agregado no PostgreSQL;
- React com polling, dashboard e listas paginadas/virtualizadas;
- execução completa com `docker compose up`;
- harness reproduzível e referência real com 1.000.000 de linhas.

## Arquitetura

A solução adota um monólito modular com duas funções de execução da mesma aplicação Spring Boot: API e Worker. RabbitMQ desacopla recebimento e processamento, PostgreSQL armazena jobs e dados importados, e um volume Docker compartilha temporariamente o CSV entre API e Worker.

```text
Usuário
  |
  v
React + Nginx
  |
  | /api
  v
Spring Boot API -----> PostgreSQL
  |        |
  |        +---------> RabbitMQ -----> Spring Boot Worker -----> PostgreSQL
  |                                         |
  +-----> volume CSV temporário <-----------+
```

O [System Design](docs/decisions/system-design-geosapiens.png) é a referência visual da topologia. As decisões e alternativas estão em [ARCHITECTURE.md](ARCHITECTURE.md) e [docs/decisions](docs/decisions). A rastreabilidade do enunciado está em [docs/requirements.md](docs/requirements.md).

No Compose, `backend-api` e `backend-worker` usam a **mesma imagem Spring Boot**. A API executa HTTP e Transactional Outbox; o Worker executa somente o consumo assíncrono. Isso materializa as duas funções do System Design sem criar dois projetos ou duplicar regras. A decisão está no ADR 0021.

## Backend

O backend utiliza Java 21 e Spring Boot. Para desenvolvimento fora do Compose, com JDK 21 disponível:

```bash
cd backend
./mvnw test
```

No Windows:

```powershell
cd backend
.\mvnw.cmd test
```

### Memória e processamento

O upload é gravado progressivamente no volume temporário. O Worker abre o CSV pelo identificador do job e percorre o conteúdo com Apache Commons CSV sem carregar o arquivo inteiro.

Além do streaming, cada registro lógico é limitado antes que o Commons CSV materialize seus campos. O valor inicial é `4096` caracteres e pode ser alterado por `CSV_MAX_RECORD_CHARACTERS`. A barreira entende campos quoted com quebras de linha, impedindo que um único registro patológico contorne o limite dividindo-se em várias linhas físicas. `amount` também é validado contra a precisão `NUMERIC(19,2)` antes de entrar no batch. A decisão está no ADR 0017.

As linhas classificadas são acumuladas em lotes limitados e persistidas via JDBC batch. Cada lote confirma transações válidas, erros de linha e progresso do job na mesma transação PostgreSQL. `UNIQUE (import_id, source_row)` e `ON CONFLICT DO NOTHING` tornam a persistência idempotente diante de redelivery.

O tamanho inicial do lote é `1000` e permanece configurável por `WORKER_BATCH_SIZE`. A referência de 1M documentada em [docs/performance.md](docs/performance.md) usou batch `1000`; isso registra o valor testado, mas não o transforma em configuração universal de produção.

Depois que o job alcança estado terminal durável, o Worker remove o CSV temporário. O orçamento de falha de processamento é a entrega original mais uma redelivery. Depois disso, a mensagem converge para DLQ mesmo se PostgreSQL estiver indisponível para registrar `FAILED`, evitando requeue sem limite. O cenário está no ADR 0016.

### Status

O upload responde `202 Accepted` com `Location: /imports/{id}`. O status é consultado por polling:

```http
GET /imports/{id}
```

A resposta usa o estado persistido no PostgreSQL como fonte de verdade e informa estado, linhas processadas/aceitas/rejeitadas, timestamps e motivo de falha quando aplicável.

Não é retornado percentual estimado enquanto o total de linhas não for conhecido de forma durável. `processedRows` representa trabalho confirmado em batches.

### Erros

```http
GET /imports/{id}/errors?limit=50&after=1250
```

A resposta usa keyset pagination por `sourceRow`. O limite padrão é 50 e o máximo é 200. A consulta reutiliza a constraint única `(import_id, source_row)`, que já fornece índice compatível; não existe índice redundante somente para esse endpoint. ADR 0012.

### Transações

```http
GET /imports/{id}/transactions?limit=50&after=1000
```

A keyset pagination usa o `id` persistido como cursor. O limite padrão é 50 e o máximo é 200. A consulta usa `WHERE import_id = ? AND id > ? ORDER BY id` e não executa `COUNT(*)` por página; busca uma linha adicional para descobrir continuação. ADR 0013.

Enquanto o Worker processa, a lista representa somente transações já commitadas. `nextCursor = null` não substitui o estado do job.

### Analytics

```http
GET /imports/{id}/analytics
```

A resposta contém `transactionCount`, `totalAmount`, `byCategory` e `byMonth`. O mês é representado como `YYYY-MM` e calculado em UTC.

As três visões são produzidas na mesma instrução SQL com `GROUPING SETS`, evitando somar milhões de registros em Java e mantendo os resultados no mesmo snapshot.

Um covering index de analytics foi criado inicialmente como hipótese. O benchmark de 1M mostrou que PostgreSQL continuava escolhendo `Seq Scan` com e sem o candidato; depois de `VACUUM (ANALYZE)`, a variante sem o índice terminou em ~`1323,6 ms` e a variante com o candidato em ~`1479,2 ms`. A solução final o remove em `V7__remove_unjustified_analytics_covering_index.sql` em vez de manter custo de escrita sem benefício demonstrado. ADR 0022.

### Observabilidade

O Worker registra logs estruturados com campos como `jobId`, `status`, `processedRows`, `acceptedRows`, `rejectedRows`, `durationMs` e ação tomada, sem registrar valores financeiros ou conteúdo bruto do CSV.

Micrometer expõe:

```text
ingestion.worker.deliveries
ingestion.worker.delivery.duration
```

As métricas usam somente `outcome=ack|redelivery|dead_letter`; `jobId` não é tag para evitar cardinalidade não limitada. Actuator expõe `health`, `info` e `metrics`.

Telemetria é *best effort*: falha de métrica não muda ACK, redelivery, DLQ nem estado do job. PostgreSQL continua sendo a fonte de verdade. ADR 0018.

## Frontend

O frontend é uma SPA em **React + TypeScript + Vite**.

A stack foi mantida intencionalmente pequena:

```text
React + TypeScript + Vite
React Router       -> navegação
SWR                -> estado remoto e polling
TanStack Virtual   -> limite de elementos no DOM
React local state  -> estado puramente visual
```

Não foram adicionados Redux, Zustand, Axios, TanStack Query, TanStack Table ou chunking de CSV no navegador sem necessidade concreta.

### Upload e acompanhamento

O cliente envia o `File` como um único multipart para `POST /imports`. React não lê milhões de linhas, não converte o CSV para JSON/Base64 e não divide o arquivo em chunks de aplicação.

O `POST` não possui retry automático cego: sem idempotency key, repetir a requisição depois de uma resposta perdida poderia criar outro job.

No ambiente Docker, Nginx encaminha `/api` para a API com `proxy_request_buffering off`, preservando a intenção de streaming também no proxy.

SWR faz polling de `GET /imports/{id}` enquanto `terminal=false` e para quando o job chega a estado terminal. O `jobId` vive em `/imports/:id`, portanto refresh ou acesso direto recuperam o contexto pela API.

### Dashboard

O dashboard consome somente `/imports/{id}/analytics`; o navegador não percorre páginas de transações para reconstruir métricas.

As visualizações simples são componentes React + TypeScript com HTML/CSS semântico. Uma engine de charts não foi adicionada porque o escopo atual não exige zoom, múltiplas séries complexas ou interação avançada.

### Alta volumetria no DOM

As listas combinam duas proteções:

- keyset pagination limita banco, rede e memória do cliente;
- TanStack Virtual limita quantas linhas ficam montadas no DOM.

Cada página mantém no máximo 100 registros no cliente, somente a coleção ativa permanece montada e não existe infinite scroll acumulando páginas sem limite. A decisão está no ADR 0020.

A direção de UI/UX, responsividade, estados e acessibilidade está em [docs/frontend-design.md](docs/frontend-design.md).

## Dataset de 1M+

O contrato das colunas está em [docs/data-contract.md](docs/data-contract.md). O gerador usa somente a biblioteca padrão do Python, escreve uma linha por vez e produz por padrão 1 milhão de registros determinísticos:

```bash
python tools/generate_dataset.py
```

Sem Python no host, use o profile de ferramentas do Compose:

```bash
docker compose --profile tools run --rm dataset-generator
```

O arquivo é criado em:

```text
data/generated/transactions-1000000.csv
```

O serviço `dataset-generator` não sobe no `docker compose up`; é ferramenta de entrega, não parte da topologia de runtime.

## Validação automática

O CI normal possui quatro frentes:

- **Backend:** `mvn verify`, incluindo Testcontainers;
- **Frontend:** `npm ci`, typecheck, testes de comportamento e build de produção;
- **Dataset e benchmark helpers:** testes do gerador/harness e amostra determinística;
- **Docker Compose:** validação do YAML, build das imagens e smoke da solução completa.

O smoke abre o frontend/Nginx, verifica `/api/actuator/health`, envia CSV real por multipart, espera o Worker concluir e consulta analytics e transações pelo mesmo proxy.

O benchmark de 1M **não roda em todo PR**. O job pesado foi usado para produzir a referência inicial e depois removido do CI normal; o harness permanece versionado em [`benchmarks/`](benchmarks/).

## Referência de performance com 1M

A execução documentada em [docs/performance.md](docs/performance.md) usou 1.000.000 de linhas válidas, seed 42, API e Worker limitados a 512 MiB cada, Worker com concorrência 2, prefetch 1 e batch 1000.

Resultados principais dessa execução específica em runner hospedado pelo GitHub:

| Medida | Resultado |
|---|---:|
| Worker | `92,195 s` |
| Vazão | `10.846,57 linhas/s` |
| Pico observado API | `~185,2 MiB` |
| Pico observado Worker | `~152,9 MiB` |
| Status p50 | `3,295 ms` |
| Primeira página p50 | `8,538 ms` |
| Cursor profundo p50 | `5,626 ms` |
| Analytics p50 | `1.189,740 ms` |

Esses valores **não são SLA nem capacity planning**. O host é compartilhado e o pico de memória é amostrado por `docker stats`. O relatório completo registra ambiente, dataset, limitações e planos PostgreSQL.

## Documentação complementar

- [Arquitetura](ARCHITECTURE.md)
- [Rastreabilidade de requisitos](docs/requirements.md)
- [Evidências de performance](docs/performance.md)
- [Direção do frontend](docs/frontend-design.md)
- [ADRs](docs/decisions)
- [Harness do benchmark](benchmarks/README.md)
