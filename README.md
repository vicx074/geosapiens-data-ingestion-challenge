# GeoSapiens Data Ingestion Challenge

Solução para ingestão, processamento e consulta de arquivos CSV com mais de um milhão de registros, sem carregar o arquivo completo em memória.

O projeto está em construção incremental. Cada marco mantém as decisões e limitações atuais documentadas antes de avançar para o próximo requisito.

## Objetivos verificáveis

- receber um CSV grande por upload em streaming;
- retornar `202 Accepted` com um identificador de acompanhamento;
- processar o arquivo de forma assíncrona;
- limitar memória, concorrência e tamanho dos lotes;
- persistir registros e erros com fronteiras transacionais explícitas;
- impedir duplicações causadas por redelivery;
- consultar milhões de registros por cursor e índices orientados às consultas;
- exibir progresso, agregações e uma lista eficiente no React;
- executar toda a solução com `docker compose up`.

## Arquitetura

A solução adota um monólito modular com duas funções de execução do backend: API e Worker. RabbitMQ desacopla o recebimento do arquivo de seu processamento, PostgreSQL armazena jobs e dados importados, e um volume Docker compartilha temporariamente o CSV entre API e Worker.

O [system design](docs/decisions/system-design-geosapiens.png) é a referência visual da topologia adotada. As decisões e alternativas estão registradas em [ARCHITECTURE.md](ARCHITECTURE.md) e em [docs/decisions](docs/decisions). A rastreabilidade do enunciado está em [docs/requirements.md](docs/requirements.md).

## Backend

O backend utiliza Java 21 e Spring Boot 3.5.16. Com uma JDK 21 disponível, os testes podem ser executados sem instalar uma versão global do Maven:

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

Depois que o job alcança estado terminal durável, o Worker remove o CSV temporário. Mensagens redelivered de jobs já terminais e a própria limpeza são tratadas de forma idempotente. O orçamento de falha de processamento é a entrega original mais uma redelivery; após isso a mensagem converge para DLQ mesmo se o PostgreSQL estiver indisponível para registrar `FAILED`, evitando requeue sem limite. Esse cenário de reconciliação está detalhado no ADR 0016.

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

A consulta reutiliza a constraint única `(import_id, source_row)`, que já fornece um índice compatível. Não foi criado um índice adicional apenas para este endpoint porque ele seria redundante. A decisão e as alternativas estão registradas no ADR 0012.

### Transações da importação

As transações válidas também são listadas em páginas limitadas:

```http
GET /imports/{id}/transactions?limit=50&after=1000
```

A keyset pagination usa o `id` persistido como cursor. `nextCursor` deve ser enviado como `after` na página seguinte. O limite padrão é 50 e o máximo é 200.

A consulta usa `WHERE import_id = ? AND id > ? ORDER BY id`, sustentada pelo índice `(import_id, id)`. O endpoint não executa `COUNT(*)` por página: busca uma linha adicional apenas para descobrir se existe continuação. Os detalhes e trade-offs estão no ADR 0013.

Enquanto o Worker ainda processa o arquivo, a lista representa apenas transações já commitadas. `nextCursor = null` não substitui o estado do job; o cliente deve consultar `GET /imports/{id}` para saber se a importação terminou.

### Analytics da importação

O dashboard obtém total, categoria e mês diretamente do PostgreSQL:

```http
GET /imports/{id}/analytics
```

A resposta contém `transactionCount`, `totalAmount`, `byCategory` e `byMonth`. O mês é representado como `YYYY-MM` e calculado explicitamente em UTC.

As três visões são produzidas na mesma instrução SQL com `GROUPING SETS`, evitando somar milhões de registros em Java e mantendo os resultados no mesmo snapshot. O índice `idx_transactions_analytics_by_import` filtra por `import_id` e inclui `category`, `occurred_at` e `amount` para permitir acesso coberto quando o planner considerar vantajoso. O custo/benefício será confirmado no benchmark com `EXPLAIN (ANALYZE, BUFFERS)`; a decisão completa está no ADR 0014.

### Observabilidade

O console usa logging estruturado Logstash do próprio Spring Boot. O Worker registra campos estáveis como `jobId`, `status`, `processedRows`, `acceptedRows`, `rejectedRows`, `durationMs` e a ação tomada, sem registrar valores financeiros ou conteúdo bruto do CSV.

O Micrometer expõe duas métricas específicas do Worker:

```text
ingestion.worker.deliveries
ingestion.worker.delivery.duration
```

Ambas usam somente `outcome=ack|redelivery|dead_letter`. `jobId` não é usado como tag para evitar cardinalidade não limitada. Actuator expõe `health`, `info` e `metrics`; por exemplo:

```http
GET /actuator/metrics/ingestion.worker.deliveries
```

As métricas são auxiliares e *best effort*: falha de telemetria não muda ACK, redelivery, DLQ nem estado do job. O PostgreSQL continua sendo a fonte de verdade. A decisão está no ADR 0018.

## Frontend

O frontend será uma SPA em **React + TypeScript + Vite**, implementada como o componente React já previsto no System Design.

A estratégia inicial é intencionalmente pequena:

```text
React + TypeScript + Vite
React Router       -> navegação
SWR                -> estado remoto e polling
TanStack Virtual   -> limite de elementos no DOM
React local state  -> estado puramente visual
```

Não entram inicialmente Redux, Zustand, Axios, TanStack Query, TanStack Table ou chunking do CSV no navegador. Cada dependência só será adicionada diante de um problema concreto que justifique seu custo.

### Upload

O cliente envia o `File` como um único multipart para `POST /imports`. O React não lê milhões de linhas, não converte o CSV para JSON/Base64 e não divide o arquivo em chunks de aplicação. O streaming e o batch processing continuam no backend.

O `POST` não terá retry automático cego: sem idempotency key no contrato de upload, repetir a requisição depois de uma resposta perdida poderia criar outro job.

### Estado e polling

SWR gerencia status, analytics, transações e erros. O polling de `GET /imports/{id}` permanece ativo enquanto a importação não é terminal e é interrompido ao receber `terminal=true`.

Estado transitório de UI, como arquivo selecionado, aba e navegação local por cursor, fica no próprio React. Não existe store global apenas por convenção.

### Renderização com grande volume

As listas usam duas proteções distintas:

- keyset pagination no backend limita consulta, transferência e quantidade de objetos mantidos no cliente;
- TanStack Virtual limita as linhas efetivamente montadas no DOM.

A interface não acumulará infinite scroll ilimitado em memória. Virtualizar somente o DOM não resolveria o crescimento de um array contendo todas as páginas já carregadas.

O identificador do job vive na rota `/imports/:id`, permitindo refresh ou acesso direto sem perder o contexto.

Os critérios de arquitetura estão no ADR 0019 e a direção de UI/UX, responsividade, estados e acessibilidade em [docs/frontend-design.md](docs/frontend-design.md).

A execução integral por Docker Compose será adicionada junto aos serviços previstos no system design. Até esse marco, a existência do Maven Wrapper não transforma Java instalado em requisito da entrega final.

## Dataset

O contrato das colunas está em [docs/data-contract.md](docs/data-contract.md). O gerador usa somente a biblioteca padrão do Python, escreve uma linha por vez e produz por padrão 1 milhão de registros determinísticos:

```bash
python tools/generate_dataset.py
```

É possível criar um arquivo menor para desenvolvimento:

```bash
python tools/generate_dataset.py --rows 10000 --output data/generated/transactions-10000.csv
```

O comando informa quantidade, semente e SHA-256. A execução do gerador também será disponibilizada por container para preservar o requisito plug-and-play da entrega final.
