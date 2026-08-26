# Requisitos e critérios de aceite

Este documento separa requisitos do enunciado, decisões da solução e evidências esperadas. Uma decisão arquitetural não deve ser apresentada como requisito do desafio.

| ID | Origem | Necessidade | Implementação planejada | Evidência |
|---|---|---|---|---|
| R01 | Obrigatório | Backend Java com Spring Boot | Aplicação Spring Boot containerizada | Build e teste de inicialização |
| R02 | Obrigatório | Frontend React | Aplicação React containerizada | Build e teste de interface |
| R03 | Obrigatório | CSV com mais de 1 milhão de linhas | Gerador determinístico versionado | Contagem e checksum dos parâmetros |
| R04 | Obrigatório | Não carregar o arquivo inteiro em RAM | Upload com multipart em disco; Worker com parser progressivo e buffer de lote limitado | Testes de storage/parser/batch e benchmark |
| R05 | Obrigatório | Processamento assíncrono | `202 Accepted` após arquivo, job e Outbox duráveis; Worker RabbitMQ com concorrência e prefetch limitados | Testes de contrato, parser, listener e integração |
| R06 | Obrigatório | Batch insert | JDBC batch configurável; transações, erros e progresso confirmados na mesma transação por lote | Testes de integração, rollback e benchmark |
| R07 | Obrigatório | Status e erros | `GET /imports/{id}` expõe estado e contadores duráveis; detalhes dos erros serão paginados em endpoint próprio | Testes de caso de uso, contrato HTTP, estados terminais e futuro endpoint de erros |
| R08 | Obrigatório | Paginação eficiente | Paginação por cursor | Plano de execução e teste de continuidade |
| R09 | Obrigatório | Agregação otimizada | SQL no PostgreSQL | Resultado, latência e plano de execução |
| R10 | Obrigatório | Índices adequados | Índices derivados das consultas reais | Migração e `EXPLAIN ANALYZE` |
| R11 | Obrigatório | Interface responsiva | Paginação server-side e lista virtualizada | Teste de interface e inspeção do DOM |
| R12 | Obrigatório | Execução plug-and-play | Docker Compose e variáveis documentadas | Execução limpa de `docker compose up` |
| D01 | Diferencial | Mensageria | RabbitMQ como fila de trabalho com ACK manual, redelivery limitado e DLQ | Testes de ACK, redelivery e DLQ |
| S01 | Decisão | Atualização de status | Polling em `GET /imports/{id}` sobre estado persistido; resposta com `no-store` | Teste do contrato HTTP e futura interrupção do polling no React em estado terminal |
| S02 | Decisão | Evitar duplicação | `UNIQUE (import_id, source_row)` e inserts idempotentes por lote | Teste de reprocessamento sem duplicação de dados ou progresso |
| S03 | Decisão | Armazenamento intermediário | Volume Docker temporário removido depois do estado terminal | Teste entre API e Worker e teste de cleanup |

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

O workflow de CI executa o backend completo, inclusive os testes Testcontainers, e valida separadamente o gerador determinístico. O benchmark oficial não roda em runner compartilhado porque seus resultados precisam de recursos de hardware controlados.
