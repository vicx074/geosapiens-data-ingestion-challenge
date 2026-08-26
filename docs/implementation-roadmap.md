# Roadmap de implementação

## Objetivo

Este documento registra a sequência real de construção do desafio. Ele não é uma meta artificial de quantidade de commits: cada marco existe porque fechou uma responsabilidade observável, um hardening justificado ou uma evidência necessária.

O histórico Git continua sendo a fonte de verdade sobre os commits efetivamente mergeados.

## Marcos concluídos

### Fundação e backend

- [x] requisitos, System Design e critérios de validação;
- [x] Java 21 + Spring Boot;
- [x] ciclo de vida durável dos jobs;
- [x] armazenamento temporário e upload em streaming;
- [x] Transactional Outbox e publicação confiável;
- [x] Worker RabbitMQ com prefetch/concorrência limitados;
- [x] parser CSV progressivo;
- [x] persistência JDBC em batches;
- [x] idempotência diante de redelivery;
- [x] status e erros;
- [x] paginação por cursor de erros e transações;
- [x] analytics no PostgreSQL;
- [x] teste E2E com PostgreSQL e RabbitMQ reais;
- [x] limite de redelivery após falha definitiva;
- [x] limite de tamanho por registro CSV;
- [x] observabilidade mínima.

Os hardenings de redelivery, limite por registro e observabilidade não foram adicionados para aumentar escopo. Eles surgiram de revisão técnica de riscos concretos do caminho de ingestão.

### Frontend

- [x] arquitetura React + TypeScript + Vite e critérios de UI/UX;
- [x] fundação feature-first, Router, SWR, testes e CI;
- [x] upload multipart e polling do job;
- [x] dashboard de analytics;
- [x] paginação server-side e TanStack Virtual;
- [x] estados de loading/erro/vazio/conteúdo parcial, teclado, foco e reduced motion cobertos nos fluxos relevantes.

A implementação mantém estado remoto no SWR e estado visual local ao React. Redux/Zustand, Axios, TanStack Query/Table e outras dependências não entraram sem problema concreto para resolver.

### Empacotamento

- [x] imagens de backend e frontend;
- [x] API e Worker como funções da mesma aplicação Spring Boot;
- [x] PostgreSQL e RabbitMQ;
- [x] volume temporário compartilhado;
- [x] Nginx como frontend/proxy `/api` com buffering do upload desabilitado;
- [x] `docker compose up` como caminho principal;
- [x] smoke do Compose no CI atravessando upload → fila → Worker → PostgreSQL → consultas.

### Performance e evidências

- [x] gerador determinístico de 1M+;
- [x] harness reproduzível de benchmark;
- [x] referência real com 1.000.000 de linhas e limites explícitos;
- [x] coleta de throughput, memória observada e latências;
- [x] `EXPLAIN (ANALYZE, BUFFERS)` de paginação e analytics;
- [x] revalidação pós-`VACUUM (ANALYZE)`;
- [x] remoção do covering index de analytics quando a medição não demonstrou benefício;
- [x] `package-lock.json` versionado e `npm ci` usado no CI/build;
- [x] documentação final de resultados e limitações.

A referência medida está em `docs/performance.md`. O benchmark pesado não permanece no CI normal; somente o harness e seus helpers são testados continuamente.

## Estado da entrega

Não há feature obrigatória do enunciado marcada como pendente neste roadmap.

Antes do envio, a validação operacional final é:

1. CI normal verde no SHA candidato;
2. smoke do Docker Compose verde;
3. revisão de consistência entre README, `ARCHITECTURE.md`, `docs/requirements.md`, ADRs e código;
4. merge do PR final;
5. confirmação do `main` após o merge.

Itens como Kafka, Redis, Kubernetes, WebSocket, cache de analytics, pré-agregação, upload em chunks e múltiplos Workers para um mesmo arquivo permanecem fora do escopo porque o desafio não exige suas propriedades e a evidência atual não justifica adicioná-los.

## Regra usada durante a implementação

- uma intenção clara por marco;
- build válido;
- testes relevantes verdes antes do merge;
- documentação atualizada quando comportamento ou trade-off muda;
- comentários em PT-BR apenas para decisões, invariantes e restrições não óbvias;
- nenhuma tecnologia adicionada sem problema concreto que a justifique;
- nenhuma alteração de topologia sem revisar antes o System Design;
- nenhum número de performance apresentado sem contexto e limite da medição.
