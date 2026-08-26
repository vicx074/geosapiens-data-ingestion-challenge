# ADR 0015: Validação ponta a ponta do backend assíncrono

- Status: aceito
- Data: 2026-08-26

## Contexto

Os componentes do backend já possuem testes unitários e de integração focados: upload em streaming, persistência de jobs, Transactional Outbox, publicação RabbitMQ, listener, parser CSV, batch, idempotência, status, paginação e analytics. Esses testes isolam falhas com precisão, mas não provam sozinhos que todas as fronteiras funcionam juntas no fluxo previsto pelo system design.

O último marco funcional do backend precisa verificar a composição real sem substituir PostgreSQL ou RabbitMQ por mocks.

## Decisão

Será mantido um teste E2E de backend com PostgreSQL e RabbitMQ reais via Testcontainers. O teste inicia a aplicação Spring com Worker e scheduler do Outbox habilitados e percorre o fluxo:

1. envia um CSV por `POST /imports` usando multipart HTTP;
2. verifica `202 Accepted` e a URL de acompanhamento;
3. aguarda o scheduler real do Outbox publicar o job no RabbitMQ;
4. permite que o listener entregue o job ao Worker real;
5. deixa o Worker abrir o arquivo temporário, fazer parsing streaming e persistir lotes;
6. aguarda um estado terminal durável;
7. comprova o cleanup do CSV temporário;
8. consulta status, transações, erros e analytics pelos endpoints HTTP;
9. confirma que a entrada do Outbox terminou como `PUBLISHED`.

O dataset do teste é deliberadamente pequeno e determinístico, contendo linhas válidas e inválidas. O objetivo do E2E é validar integração e consistência, não medir throughput ou pico de memória.

### Acionamento do Outbox no teste

O E2E não injeta nem chama `PublishPendingIngestionJobs` diretamente. O scheduler de produção é habilitado por configuração e recebe um intervalo curto somente no contexto do teste. Dessa forma, o cenário não cria uma entrada alternativa para a publicação e prova o mesmo encadeamento usado pela aplicação.

Esperar o resultado assíncrono com timeout explícito é preferível a controlar manualmente o caso de uso: o teste verifica comportamento observável e reduz o acoplamento à forma interna de instanciação dos serviços de aplicação.

## Por que não usar o dataset de 1 milhão no CI

Um E2E com 1 milhão de linhas misturaria dois objetivos diferentes: correção funcional e benchmark. Em runner compartilhado, tempo de execução e recursos variam e tornariam a suíte mais lenta e mais instável sem produzir evidência de performance defensável.

O dataset de 1M+ e os planos `EXPLAIN (ANALYZE, BUFFERS)` permanecem no marco de benchmark executado em ambiente controlado e documentado.

## Por que usar infraestrutura real

Mocks de `JobQueuePublisher`, repositórios ou listener fariam o teste ignorar justamente os limites de consistência mais importantes: configuração do broker, serialização da mensagem, publisher confirm, consumo, transações PostgreSQL e Flyway.

Testcontainers aumenta o custo de inicialização da suíte, mas oferece isolamento e reproduz as tecnologias escolhidas no system design sem exigir instalações locais no runner.

## Limites do teste

O E2E não substitui os testes menores. Casos como redelivery, rollback de lote, validações específicas de CSV e paginação profunda continuam cobertos de forma focada, pois reproduzir todas as variantes em um único cenário ponta a ponta tornaria a falha difícil de diagnosticar.

A passagem deste teste demonstra que o caminho feliz com rejeição de linha atravessa todas as fronteiras do backend. Performance e execução plug-and-play do conjunto inteiro continuam sendo validadas em marcos próprios.

## Consequências

O backend passa a ter uma evidência automática de que a topologia implementada funciona de forma integrada. Uma mudança futura em upload, Outbox, RabbitMQ, Worker, persistência ou contratos de leitura que quebre o fluxo principal deverá falhar no CI antes do merge.
