# ADR 0018: Observabilidade mínima do backend

- Status: aceito
- Data: 2026-08-26

## Contexto

O `ARCHITECTURE.md` define que o backend deve tornar observáveis duração, resultado, contadores e falhas do processamento sem transformar telemetria em fonte de verdade do estado do job. O projeto já inclui Spring Boot Actuator, mas depender apenas das métricas automáticas de HTTP/JVM não mostra como o Worker assíncrono está convergindo no RabbitMQ.

A observabilidade precisa continuar proporcional ao desafio: oferecer evidência útil para diagnóstico e benchmark sem adicionar Prometheus, Grafana, tracing distribuído ou outro serviço ao system design.

## Decisão

A observabilidade será implementada somente na infraestrutura.

O adapter RabbitMQ registra, para cada entrega concluída pelo listener:

- contador `ingestion.worker.deliveries`;
- timer `ingestion.worker.delivery.duration`;
- tag de baixa cardinalidade `outcome` com os valores `ack`, `redelivery` ou `dead_letter`.

O `jobId` não será usado como tag de métrica. Identificadores únicos gerariam cardinalidade crescente no backend de métricas e não são dimensão adequada para agregação. O `jobId` permanece nos logs, onde é útil para correlação de um processamento específico.

Spring Boot Actuator continua responsável pelas métricas automáticas de JVM, processo, HTTP e conexão RabbitMQ. O endpoint `/actuator/metrics` é exposto junto de `health` e `info` para inspeção local e para a futura execução via Docker Compose.

Os logs de console usam o formato estruturado Logstash suportado nativamente pelo Spring Boot. O listener utiliza a API fluente do SLF4J para adicionar campos estáveis como `jobId`, `status`, `processedRows`, `acceptedRows`, `rejectedRows`, `durationMs`, `action` e `requiresReconciliation` quando aplicável.

Nenhum valor financeiro, categoria de transação ou conteúdo bruto de CSV é colocado nos logs de processamento.

## Por que medir a entrega no adapter RabbitMQ

A duração observada representa o trabalho feito entre o recebimento da mensagem e a decisão de ACK, redelivery ou DLQ. Essa fronteira corresponde ao comportamento operacional do Worker e já está na camada de infraestrutura, portanto não exige que domínio ou aplicação conheçam Micrometer.

Instrumentar `ProcessIngestionJob` diretamente com `MeterRegistry` colocaria uma dependência de observabilidade externa dentro da camada de aplicação. Criar uma porta apenas para duas métricas também adicionaria abstração sem necessidade: o adapter já possui todas as informações necessárias para medir sua própria entrega.

## Cardinalidade

Somente resultados pertencentes a um conjunto pequeno e fechado são usados como tags. Campos como `jobId`, nome de arquivo, motivo da falha e categoria não entram em tags de métrica.

Isso permite agregar taxas de ACK, redelivery e DLQ sem criar uma série temporal por importação.

## Alternativas rejeitadas

- adicionar Prometheus/Grafana ao Compose agora: infraestrutura extra sem requisito do desafio;
- usar `jobId` como tag: cardinalidade não limitada;
- métricas apenas de HTTP: não mostram o Worker assíncrono;
- logs apenas em texto livre: dificultam correlação e extração de campos;
- instrumentar domínio ou casos de uso com Micrometer: viola a direção de dependências da Clean Architecture adotada.

## Consequências

O backend passa a expor métricas suficientes para observar o comportamento do Worker e já possui métricas automáticas da JVM/HTTP. Logs estruturados registram os detalhes por job sem transformar métricas em armazenamento de estado.

A telemetria é auxiliar. O estado persistido em PostgreSQL continua sendo a única fonte de verdade para status, contadores e falhas de importação.
