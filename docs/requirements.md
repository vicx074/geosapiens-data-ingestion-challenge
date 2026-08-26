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
| R11 | Obrigatório | Interface responsiva e renderização limpa | Paginação server-side limita dados carregados e TanStack Virtual limita linhas montadas no DOM; layout trata estados, teclado, responsividade e conteúdo parcial | Testes de interface, inspeção do DOM virtualizado, acessibilidade básica e smoke test responsivo |
| R12 | Obrigatório | Execução plug-and-play | Docker Compose e variáveis documentadas | Execução limpa de `docker compose up` |
| D01 | Diferencial | Mensageria | RabbitMQ como fila de trabalho com ACK manual, no máximo uma redelivery de processamento e DLQ mesmo quando não é possível persistir `FAILED` após o orçamento de retry | Testes de ACK, primeira redelivery, convergência para DLQ, falha ao persistir estado terminal e E2E com broker real |
| S01 | Decisão | Atualização de status | SWR faz polling em `GET /imports/{id}` sobre estado persistido e interrompe a revalidação quando `terminal=true`; resposta do backend usa `no-store` | Teste do polling, interrupção em estado terminal, reconexão/erro e E2E |
| S02 | Decisão | Evitar duplicação | `UNIQUE (import_id, source_row)` e inserts idempotentes por lote | Teste de reprocessamento sem duplicação de dados ou progresso |
| S03 | Decisão | Armazenamento intermediário | Volume Docker temporário removido depois do estado terminal | Testes de storage/Worker e E2E de cleanup |
| S04 | Decisão | Observabilidade mínima | Logs estruturados por job; métricas de entregas/duração do Worker com outcomes de baixa cardinalidade; Actuator para JVM/HTTP/RabbitMQ | Teste das métricas customizadas, inspeção de `/actuator/metrics` e logs sem dados financeiros |
| S05 | Decisão | Estado do frontend | SWR gerencia somente server-state; estado visual permanece local ao React; Redux/Zustand não entram sem necessidade concreta | Testes de comportamento sem dependência de store global e revisão da fronteira `features/shared` |
| S06 | Decisão | Upload no navegador | CSV é enviado como um único multipart; não há parse completo, Base64, JSON de milhões de linhas nem chunking de aplicação no browser | Teste de upload com `File`/`FormData` e inspeção de que o frontend não materializa o conteúdo |
| S07 | Decisão | Memória e DOM no frontend | Cliente mantém páginas limitadas por cursor e não acumula infinite scroll ilimitado; virtualização restringe elementos montados | Teste de navegação por cursor, inspeção da quantidade de rows no DOM e uso prolongado sem acúmulo ilimitado de páginas |

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

O frontend deverá adicionar ao CI build de produção e testes de comportamento dos fluxos críticos, incluindo upload, polling terminal, estados de erro e navegação paginada. O gerador determinístico é validado separadamente. O benchmark oficial não roda em runner compartilhado porque seus resultados precisam de recursos de hardware controlados.
