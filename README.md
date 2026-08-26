# GeoSapiens Data Ingestion Challenge

Solução para ingestão, processamento e consulta de arquivos CSV com mais de um milhão de registros, sem carregar o arquivo completo em memória.

A implementação prioriza os pontos mais rigorosos do desafio: memória limitada no backend, processamento assíncrono, consultas eficientes em alto volume, índices orientados às consultas e renderização limpa no React.

## Execução rápida

A entrega foi preparada para depender somente de Docker e Docker Compose no host.

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

## Objetivos verificáveis

- receber um CSV grande por upload em streaming;
- retornar `202 Accepted` com um identificador de acompanhamento;
- processar o arquivo de forma assíncrona;
- limitar memória, concorrência e tamanho dos lotes;
- persistir registros e erros com fronteiras transacionais explícitas;
- impedir duplicações causadas por redelivery;
- consultar milhões de registros por cursor e índices orientados às consultas;
- exibir progresso, agregações e listas eficientes no React;
- executar toda a solução com `docker compose up`.

## Arquitetura

A solução adota um monólito modular com duas funções de execução do mesmo backend: API e Worker. RabbitMQ desacopla recebimento e processamento, PostgreSQL armazena jobs e dados importados, e um volume Docker compartilha temporariamente o CSV entre API e Worker.

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

O [system design](docs/decisions/system-design-geosapiens.png) é a referência visual da topologia adotada. As decisões e alternativas estão em [ARCHITECTURE.md](ARCHITECTURE.md) e [docs/decisions](docs/decisions). A rastreabilidade do enunciado está em [docs/requirements.md](docs/requirements.md).

No Compose, `backend-api` e `backend-worker` usam a **mesma imagem Spring Boot**. A API executa HTTP e Transactional Outbox; o Worker executa somente o consumo assíncrono. Isso materializa as duas funções do System Design sem criar dois projetos ou duplicar regras. A decisão está no ADR 0021.

## Backend

O backend utiliza Java 21 e Spring Boot 3.5.16. Para desenvolvimento fora do Compose, com uma JDK 21 disponível, os testes podem ser executados pelo Maven Wrapper:

```bash
cd backend
./mvnw test
```

No Windows:

```powershell
cd backend
.\mvnw.cmd test
```

O Worker consome jobs do RabbitMQ com concorrência e prefetch limitados, abre o CSV pelo identificador do job e percorre o conteúdo progressivamente com Apache Commons CSV. O cabeçalho e o contrato das linhas são validados durante a leitura, sem materializar o arquivo inteiro.

Além do streaming, cada registro lógico é limitado antes que o Commons CSV materialize seus campos. O valor inicial é `4096` caracteres e pode ser alterado por `CSV_MAX_RECORD_CHARACTERS`. A barreira entende campos quoted com quebras de linha, impedindo que um único registro patológico contorne o limite dividindo-se em várias linhas físicas. `amount` também é validado contra a precisão `NUMERIC(19,2)` usada pelo PostgreSQL antes de entrar no batch. A decisão está no ADR 0017.

As linhas classificadas são acumuladas em lotes limitados e persistidas via JDBC batch. Cada lote confirma transações válidas, erros de linha e progresso do job na mesma transação do PostgreSQL. `UNIQUE (import_id, source_row)` e `ON CONFLICT DO NOTHING` tornam a persistência idempotente diante de redelivery. O tamanho inicial é de 1000 linhas por lote e permanece configurável por `WORKER_BATCH_SIZE`; esse valor só será defendido após benchmark.

Depois que o job alcança estado terminal durável, o Worker remove o CSV temporário. Mensagens redelivered de jobs já terminais e a própria limpeza são tratadas de forma idempotente. O orçamento de falha de processamento é a entrega original mais uma redelivery; após isso a mensagem converge para DLQ mesmo se o PostgreSQL estiver indisponível para registrar `FAILED`, evitando requeue sem limite. Esse cenário está detalhado no ADR 0016.

### Acompanhamento da importação

O upload responde `202 Accepted` com `Location: /imports/{id}`. O status pode ser consultado por polling:

```http
GET /imports/{id}
```

A resposta é limitada em tamanho e usa o estado persistido no PostgreSQL como fonte de verdade. Ela informa estado, linhas processadas/aceitas/rejeitadas, timestamps e motivo de falha quando aplicável.

Não é retornado percentual estimado enquanto o total de linhas não for conhecido de forma durável. `processedRows` representa trabalho efetivamente confirmado em batches.

### Erros da importação

Os detalhes das linhas rejeitadas possuem endpoint próprio para não aumentar o payload do polling:

```http
GET /imports/{id}/errors?limit=50&after=1250
```

A resposta usa keyset pagination ordenada por `sourceRow`. `nextCursor` deve ser enviado como `after` na chamada seguinte. O limite padrão é 50 e o máximo é 200.

A consulta reutiliza a constraint única `(import_id, source_row)`, que já fornece um índice compatível. Não foi criado índice redundante apenas para esse endpoint. A decisão está no ADR 0012.

### Transações da importação

As transações válidas também são listadas em páginas limitadas:

```http
GET /imports/{id}/transactions?limit=50&after=1000
```

A keyset pagination usa o `id` persistido como cursor. `nextCursor` deve ser enviado como `after` na página seguinte. O limite padrão é 50 e o máximo é 200.

A consulta usa `WHERE import_id = ? AND id > ? ORDER BY id`, sustentada pelo índice `(import_id, id)`. O endpoint não executa `COUNT(*)` por página: busca uma linha adicional apenas para descobrir se existe continuação. Os detalhes estão no ADR 0013.

Enquanto o Worker ainda processa o arquivo, a lista representa apenas transações já commitadas. `nextCursor = null` não substitui o estado do job; o cliente consulta `GET /imports/{id}` para saber se a importação terminou.

### Analytics da importação

O dashboard obtém total, categoria e mês diretamente do PostgreSQL:

```http
GET /imports/{id}/analytics
```

A resposta contém `transactionCount`, `totalAmount`, `byCategory` e `byMonth`. O mês é representado como `YYYY-MM` e calculado explicitamente em UTC.

As três visões são produzidas na mesma instrução SQL com `GROUPING SETS`, evitando somar milhões de registros em Java e mantendo os resultados no mesmo snapshot. O índice `idx_transactions_analytics_by_import` filtra por `import_id` e inclui `category`, `occurred_at` e `amount` para permitir acesso coberto quando o planner considerar vantajoso. O custo/benefício será confirmado no benchmark com `EXPLAIN (ANALYZE, BUFFERS)`; a decisão está no ADR 0014.

### Observabilidade

O console usa logging estruturado Logstash do próprio Spring Boot. O Worker registra campos estáveis como `jobId`, `status`, `processedRows`, `acceptedRows`, `rejectedRows`, `durationMs` e a ação tomada, sem registrar valores financeiros ou conteúdo bruto do CSV.

O Micrometer expõe duas métricas específicas do Worker:

```text
ingestion.worker.deliveries
ingestion.worker.delivery.duration
```

Ambas usam somente `outcome=ack|redelivery|dead_letter`. `jobId` não é usado como tag para evitar cardinalidade não limitada. Actuator expõe `health`, `info` e `metrics`.

Telemetria é auxiliar e *best effort*: falha de métrica não muda ACK, redelivery, DLQ nem estado do job. PostgreSQL continua sendo a fonte de verdade. A decisão está no ADR 0018.

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

### Upload

O cliente envia o `File` como um único multipart para `POST /imports`. O React não lê milhões de linhas, não converte o CSV para JSON/Base64 e não divide o arquivo em chunks de aplicação. Streaming e batch processing continuam responsabilidade do backend.

O `POST` não possui retry automático cego: sem idempotency key no contrato de upload, repetir a requisição depois de uma resposta perdida poderia criar outro job.

No ambiente Docker, o Nginx encaminha `/api` para a API com `proxy_request_buffering off`, preservando a intenção de streaming também no proxy.

### Estado e polling

SWR gerencia status, analytics, transações e erros. O polling de `GET /imports/{id}` permanece ativo enquanto a importação não é terminal e para ao receber `terminal=true`.

Estado transitório de UI, como arquivo selecionado, aba e navegação local por cursor, fica no próprio React. Não existe store global apenas por convenção.

### Dashboard

O dashboard consome apenas `/imports/{id}/analytics`. Totais por categoria e mês são calculados pelo PostgreSQL; o navegador não percorre milhões de transações para reconstruir métricas.

As visualizações simples são componentes React + TypeScript com HTML/CSS semântico. Uma engine de charts não foi adicionada porque o escopo atual não exige eixos interativos, zoom ou múltiplas séries complexas.

### Renderização com grande volume

As listas usam proteções complementares:

- keyset pagination no backend limita consulta e transferência;
- cada página mantém no máximo 100 registros no cliente;
- somente a coleção ativa permanece montada;
- TanStack Virtual limita as linhas efetivamente presentes no DOM.

A interface não acumula infinite scroll ilimitado em memória. Virtualizar somente o DOM não resolveria um array contendo todas as páginas já visitadas. A decisão detalhada está no ADR 0020.

O identificador do job vive na rota `/imports/:id`, permitindo refresh ou acesso direto sem perder contexto.

Os critérios de arquitetura estão no ADR 0019 e a direção de UI/UX, responsividade, estados e acessibilidade em [docs/frontend-design.md](docs/frontend-design.md).

## Dataset

O contrato das colunas está em [docs/data-contract.md](docs/data-contract.md). O gerador usa somente a biblioteca padrão do Python, escreve uma linha por vez e produz por padrão 1 milhão de registros determinísticos:

```bash
python tools/generate_dataset.py
```

Também é possível gerar o dataset sem Python instalado no host usando o profile de ferramentas do Compose:

```bash
docker compose --profile tools run --rm dataset-generator
```

O arquivo é criado em:

```text
data/generated/transactions-1000000.csv
```

O serviço `dataset-generator` não sobe em `docker compose up`; ele é apenas uma ferramenta de entrega e não faz parte da topologia de runtime.

## Validação automática

O CI possui quatro frentes complementares:

- **Backend:** `mvn verify`, incluindo testes Testcontainers;
- **Frontend:** typecheck, testes de comportamento e build de produção;
- **Dataset:** testes do gerador e amostra determinística;
- **Docker Compose:** validação do YAML, build das imagens e smoke da solução completa.

O smoke do Compose acessa o frontend/Nginx, verifica `/api/actuator/health`, envia um CSV real por multipart, espera o Worker concluir o job e consulta analytics e transações pelo mesmo proxy.

Esse smoke valida integração e empacotamento. Ele **não** é usado como benchmark de performance.

## Benchmark

O benchmark oficial usa dataset de 1 milhão ou mais de linhas e deve registrar hardware, sistema operacional, limites de CPU/memória dos containers, parâmetros do dataset, concorrência, prefetch, batch size, tempo total, throughput, pico de memória, latência das consultas críticas e `EXPLAIN (ANALYZE, BUFFERS)`.

Nenhum número de performance será defendido sem esse contexto. O benchmark permanece separado do CI compartilhado para evitar conclusões baseadas em hardware variável de runner.
