# Revisão final contra o desafio

## Critério usado

Esta revisão compara a entrega com o enunciado do desafio sem transformar decisões internas em requisitos e sem extrapolar evidências.

O enunciado exige, em resumo:

- Java + Spring Boot no backend;
- CSV grande com gerador/dataset de 1M+;
- processamento assíncrono;
- streaming e batch inserts;
- status, paginação eficiente e agregação otimizada;
- PostgreSQL com índices adequados;
- React com upload/feedback, dashboard e listagem sem sobrecarga do DOM;
- execução plug-and-play via Docker Compose;
- README explicando memória, batch e índices.

Os pontos destacados para avaliação mais rigorosa são memória no backend, tempo das APIs com alto volume, estratégia de indexação e renderização limpa no React.

## Matriz final

| Área | Estado | Evidência principal | Limite da conclusão |
|---|---|---|---|
| Backend Java/Spring Boot | entregue | build + `mvn verify` + Testcontainers | não implica HA |
| CSV 1M+ | entregue | gerador determinístico + referência real de 1M | dataset de referência contém apenas linhas válidas |
| Assincronia | entregue | `202`, Outbox, RabbitMQ, Worker e E2E | um arquivo não é dividido entre Workers |
| Memória | entregue | streaming, limite por registro, batch limitado e referência sem OOM em 512 MiB | pico é amostrado; não é máximo absoluto |
| Batch insert | entregue | JDBC batch + transação por lote + testes de rollback | batch 1000 é valor testado, não ótimo universal |
| Status | entregue | endpoint durável + polling React | não há percentual sem total durável |
| Paginação | entregue | keyset pagination + referência com cursor profundo | benchmark de um único import não prova ganho do índice composto em imports intercalados |
| Analytics | entregue | `GROUPING SETS` no PostgreSQL + E2E + benchmark | p50 ~1,19 s no cenário de referência; não há cache/pré-agregação |
| Índices | entregue | constraints/índice de cursor + `EXPLAIN` + ADR 0022 | covering index de analytics foi removido porque não ajudou no workload medido |
| React | entregue | React + TS + Vite, SWR, Router, testes e build | não há benchmark de FPS ou memória do browser |
| Renderização de listas | entregue | página limitada + TanStack Virtual + testes de DOM | virtualização não substitui teste visual em todo dispositivo possível |
| Dashboard | entregue | analytics agregado + componentes React | gráficos são simples por decisão de escopo |
| Docker Compose | entregue | build + healthchecks + smoke completo no CI | não representa orquestração/HA de produção |
| Documentação | entregue | README, Architecture, Requirements, ADRs e performance | números de runner compartilhado são referência, não SLA |

## Decisões mantidas simples de propósito

### Polling em vez de SSE/WebSocket

O requisito é acompanhamento do processamento. Polling sobre estado durável resolve o fluxo unidirecional sem adicionar conexão persistente, recuperação de sessão e infraestrutura sem necessidade demonstrada.

### RabbitMQ em vez de Kafka

O problema é uma fila de trabalho por arquivo, não replay de eventos ou múltiplos consumidores independentes. RabbitMQ atende ao fluxo e é um diferencial explicitamente permitido pelo desafio.

### SWR em vez de store global/TanStack Query

O frontend é predominantemente leitura de server-state: status, analytics e páginas. O único write relevante é o upload. SWR cobre polling/cache/revalidação sem introduzir uma camada mais abrangente de mutations que o escopo não usa.

### Sem chunking no browser

O navegador envia o arquivo como multipart. O backend já grava progressivamente e processa em background. Chunking de aplicação exigiria protocolo de montagem, ordenação, retomada e idempotência sem requisito correspondente.

### Sem pré-agregação de analytics

A referência mediu ~1,19 s p50 para analytics com 1M. É a API mais cara, mas ainda não existe SLA que justifique adicionar tabela agregada e sincronização transacional. Essa é uma melhoria futura condicionada a requisito, não uma pendência funcional.

## Decisões alteradas pela evidência

O covering index de analytics foi inicialmente introduzido como candidato, não como verdade definitiva. A comparação com 1M mostrou `Seq Scan` com e sem ele e não demonstrou ganho. A migration V7 remove o índice e o ADR 0022 registra a revisão.

Essa mudança é intencional: a estratégia de indexação do desafio é tratada como decisão baseada na consulta e no plano real, não como quantidade de índices criada.

## Limitações conhecidas

A entrega não afirma possuir:

- alta disponibilidade de API, PostgreSQL, RabbitMQ ou storage;
- benchmark de dezenas de imports concorrentes;
- processamento paralelo de um mesmo arquivo por vários Workers;
- retomada de upload por chunks;
- SLA de analytics abaixo de 1 segundo;
- capacity planning de produção;
- garantia de memória máxima entre amostras do benchmark;
- benefício medido do índice de cursor em imports intercalados;
- teste visual manual em todo navegador/dispositivo existente.

Esses pontos não são escondidos nem apresentados como requisitos atendidos.

## Critério de envio

A entrega está pronta para envio quando o SHA candidato passar no CI normal com:

- Backend;
- Frontend;
- Dataset e benchmark helpers;
- Docker Compose.

Depois do merge final, o `main` deve ser conferido novamente. Nenhuma nova feature deve ser adicionada somente para aumentar a sofisticação da solução.
