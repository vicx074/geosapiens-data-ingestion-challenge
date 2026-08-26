# Requisitos e critérios de aceite

Este documento separa requisitos do enunciado, decisões da solução e evidências esperadas. Uma decisão arquitetural não deve ser apresentada como requisito do desafio.

| ID | Origem | Necessidade | Implementação planejada | Evidência |
|---|---|---|---|---|
| R01 | Obrigatório | Backend Java com Spring Boot | Aplicação Spring Boot containerizada | Build e teste de inicialização |
| R02 | Obrigatório | Frontend React | SPA React + TypeScript + Vite containerizada | Build, testes de comportamento e smoke test no Compose |
| R03 | Obrigatório | CSV com mais de 1 milhão de linhas | Gerador determinístico versionado | Contagem e checksum dos parâmetros |
| R04 | Obrigatório | Não carregar o arquivo inteiro em RAM | Upload com multipart em disco; Worker com parser progressivo, limite de registro antes da materialização e buffer de lote limitado | Testes de storage/parser/batch, registro oversized/multiline, E2E do fluxo e benchmark |
| R05 | Obrigatório | Processamento assíncrono | `202 Accepted` após arquivo, job e Outbox duráveis; Worker RabbitMQ com concorrência e prefetch limitados | Testes de contrato, mensageria e E2E com PostgreSQL + RabbitMQ reais |
| R06 | Obrigatório | Batch insert | JDBC batch configurável; transações, erros e progresso confirmados na mesma transação por lote | Testes de integração, rollback, E2E e benchmark |
| R07 | Obrigatório | Status e erros | `GET /imports/{id}` expõe estado e contadores duráveis; `GET /imports/{id}/errors` expõe rejeições em páginas limitadas | Testes de caso de uso, contrato HTTP, persistência, continuidade entre páginas e E2E |
| R08 | Obrigatório | Paginação eficiente | Keyset pagination em erros por `source_row` e transações por `id`, sem offsets profundos | Testes de continuidade, isolamento entre imports, contrato HTTP e E2E |
| R09 | Obrigatório | Agregação otimizada | `GET /imports/{id}/analytics` calcula total, categoria e mês no PostgreSQL com `GROUPING SETS` | Testes de resultado, precisão decimal, isolamento entre imports, E2E e benchmark |
| R10 | Obrigatório | Índices adequados | Erros reutilizam `(import_id, source_row)`; transações usam `(import_id, id)`; analytics usa cobertura por `import_id` com colunas incluídas | Migrações, definição dos índices e futuro `EXPLAIN ANALYZE` em dados representativos |
| R11 | Obrigatório | Interface responsiva e renderização limpa | Página de 100 itens por cursor limita rede/memória e TanStack Virtual limita linhas montadas no DOM; somente a coleção ativa permanece montada | Testes de navegação por cursor, substituição da página atual, quantidade de rows virtualizadas, estados de erro e acessibilidade básica |
| R12 | Obrigatório | Execução plug-and-play | Compose materializa frontend/Nginx, API, Worker, RabbitMQ, PostgreSQL e volume temporário compartilhado; `/api` mantém o backend interno e o upload sem buffering no proxy | `docker compose config`, build das imagens e smoke E2E via Nginx no CI, além de execução limpa de `docker compose up` |
| D01 | Diferencial | Mensageria | RabbitMQ como fila de trabalho com ACK manual, no máximo uma redelivery de processamento e DLQ mesmo quando não é possível persistir `FAILED` após o orçamento de retry | Testes de ACK, primeira redelivery, convergência para DLQ, falha ao persistir estado terminal e E2E com broker real |
| S01 | Decisão | Atualização de status | SWR faz polling em `GET /imports/{id}` sobre estado persistido e interrompe a revalidação quando `terminal=true`; resposta do backend usa `no-store` | Teste do polling, interrupção em estado terminal, reconexão/erro e E2E |
| S02 | Decisão | Evitar duplicação | `UNIQUE (import_id, source_row)` e inserts idempotentes por lote | Teste de reprocessamento sem duplicação de dados ou progresso |
| S03 | Decisão | Armazenamento intermediário | Volume Docker temporário removido depois do estado terminal | Testes de storage/Worker e E2E de cleanup |
| S04 | Decisão | Observabilidade mínima | Logs estruturados por job; métricas de entregas/duração do Worker com outcomes de baixa cardinalidade; Actuator para JVM/HTTP/RabbitMQ | Teste das métricas customizadas, inspeção de `/actuator/metrics` e logs sem dados financeiros |
| S05 | Decisão | Estado do frontend | SWR gerencia somente server-state; estado visual permanece local ao React; Redux/Zustand não entram sem necessidade concreta | Testes de comportamento sem dependência de store global e revisão da fronteira `features/shared` |
| S06 | Decisão | Upload no navegador | CSV é enviado como um único multipart; não há parse completo, Base64, JSON de milhões de linhas nem chunking de aplicação no browser | Teste de upload com `File`/`FormData` e inspeção de que o frontend não materializa o conteúdo |
| S07 | Decisão | Memória e DOM no frontend | Cliente guarda somente a página atual no cache por coleção, preserva apenas o histórico de cursores e virtualiza as linhas visíveis; não há infinite scroll acumulativo | Teste com 100 itens confirmando menos rows montadas no DOM, troca de página sem manter registros anteriores e montagem somente da aba ativa |

## Benchmark obrigatório do projeto

O relatório de desempenho deverá registrar:

- versão do código;
- hardware e sistema operacional;
- limites de CPU e memória dos containers;
- semente e parâmetros do dataset;
- quantidade e tamanho das linhas;
- concorrência, prefetch e tamanho do lote;
- tempo total, vazão e pico de memória;
- latência das consultas críticas;
- planos de execução antes e depois dos índices.

Um número sem esse contexto não será usado para justificar configuração ou desempenho.

## Validação contínua

O workflow de CI executa o backend completo, inclusive os testes Testcontainers. O cenário E2E atravessa upload HTTP, armazenamento temporário, Transactional Outbox, RabbitMQ, Worker, persistência PostgreSQL, cleanup e os endpoints de leitura usando as tecnologias reais escolhidas no system design.

O frontend executa typecheck, testes de comportamento e build de produção no CI. Os fluxos cobertos incluem upload, polling terminal, analytics, estados de erro, paginação por cursor e virtualização das coleções. O gerador determinístico é validado separadamente.

O job de Docker Compose valida a configuração, constrói as imagens e sobe a topologia completa. O smoke envia um CSV pelo Nginx, acompanha o processamento até `COMPLETED` e consulta analytics/transações pelo proxy. Esse smoke comprova empacotamento e integração, mas não substitui o benchmark oficial, que não roda em runner compartilhado porque seus resultados precisam de recursos de hardware controlados.
