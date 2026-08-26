# Roadmap de implementação

## Objetivo

Este documento adapta a sequência de commits definida no planejamento inicial ao estado real do repositório.

A sequência é uma orientação de implementação, não uma meta artificial de quantidade de commits. Cada marco deve possuir uma intenção clara, manter os testes relevantes verdes e registrar a justificativa técnica quando houver decisão durável.

O histórico Git continua sendo a fonte de verdade sobre o que efetivamente foi implementado. Este roadmap existe para deixar explícito o que já foi concluído e qual é a próxima ordem de trabalho.

## Sequência original e estado atual

O planejamento inicial previa, em ordem: contrato e arquitetura, fundação do backend, ciclo de vida do job, upload streaming, publicação confiável, Worker streaming, batch persistence, status, paginação, analytics/índices, integração real, frontend, benchmark, Docker Compose e documentação final.

A implementação real preservou essa direção, mas alguns marcos foram divididos quando surgiram responsabilidades independentes ou hardenings justificados por testes e revisão.

### Concluído

- [x] `docs: define requisitos, arquitetura e critérios de validação`
- [x] `build: inicia backend com Java 21 e Spring Boot`
- [x] `feat: modela ciclo de vida dos jobs de ingestão`
- [x] `feat: recebe e armazena uploads em streaming`
- [x] `feat: publica jobs de ingestão de forma confiável`
- [x] `feat: processa CSV em streaming com concorrência limitada`
- [x] `feat: persiste transações e erros em lotes`
- [x] `feat: expõe status consistente da importação`
- [x] paginação por cursor de erros e transações
- [x] `feat: adiciona agregações e índices orientados às consultas`
- [x] `test: valida processamento assíncrono ponta a ponta`
- [x] `fix: limita redelivery após falha definitiva`
- [x] `fix: reforça limites de memória e observabilidade do backend`
- [x] `docs: define arquitetura e critérios do frontend React`

Os dois commits de hardening do backend não existiam no planejamento inicial. Eles foram adicionados porque a revisão encontrou problemas concretos: possibilidade de requeue sem limite em uma borda de falha, limite de memória por registro CSV e lacunas de observabilidade já prometidas na arquitetura.

A documentação de arquitetura do frontend também foi antecipada antes do primeiro `.tsx` para evitar que decisões de estado, renderização e UI/UX surgissem de forma incidental durante a implementação.

## Próximos marcos

### 1. `feat: estrutura frontend React e base visual`

Criar a aplicação React + TypeScript + Vite e a fundação definida no ADR 0019:

- estrutura `app/pages/features/shared`;
- React Router;
- SWR e cliente HTTP baseado em `fetch`;
- tokens e estilos globais mínimos;
- shell/layout responsivo;
- configuração de Vitest, Testing Library e mocks HTTP;
- job de frontend no CI com testes e build de produção.

Este marco não deve antecipar dashboard ou tabela de alta volumetria. A intenção é provar que a fundação arquitetural, visual e de testes está saudável.

### 2. `feat: implementa upload e acompanhamento no React`

Entregar o primeiro fluxo funcional completo:

- seleção/dropzone acessível de CSV;
- envio do `File` como um único multipart para `POST /imports`;
- sem `FileReader`, Base64, JSON de milhões de linhas ou chunking no browser;
- navegação para `/imports/:id` após `202 Accepted`;
- polling do status com SWR;
- interrupção do polling quando `terminal=true`;
- estados `RECEIVED`, `QUEUED`, `PROCESSING`, `COMPLETED`, `COMPLETED_WITH_ERRORS` e `FAILED`;
- loading, erro de conexão, retry explícito de leitura e conteúdo parcial;
- testes de comportamento do fluxo.

Não haverá retry automático cego do `POST /imports` enquanto o contrato não possuir idempotency key.

### 3. `feat: adiciona dashboard de analytics`

Implementar a leitura de `GET /imports/{id}/analytics`:

- hierarquia dos indicadores;
- total e agregações por categoria/mês;
- gráficos simples a partir dos dados agregados pelo PostgreSQL;
- estados de loading, erro, vazio e snapshot parcial durante processamento;
- acessibilidade textual dos gráficos;
- carregamento da biblioteca gráfica sem prejudicar o fluxo inicial quando houver benefício real.

A biblioteca de gráficos deve ser escolhida neste marco, com base no caso concreto, e não antecipadamente apenas por convenção.

### 4. `feat: adiciona listagens paginadas e virtualizadas`

Implementar transações e erros com foco explícito em alta volumetria:

- keyset pagination usando os cursores do backend;
- histórico mínimo para navegação anterior/próxima;
- páginas limitadas no estado do cliente;
- TanStack Virtual para limitar as linhas montadas no DOM;
- sem infinite scroll acumulando páginas sem limite;
- estados de loading, erro e vazio;
- responsividade adequada para dados tabulares;
- testes que comprovem que o DOM permanece limitado.

### 5. `test: valida qualidade e responsividade do frontend`

Fechar a camada React sem transformar qualidade em correção tardia. Os marcos anteriores já devem nascer acessíveis e responsivos; este commit consolida a evidência final:

- larguras de 360, 390, 768, 1024, 1280 e 1440 px;
- navegação por teclado e foco visível;
- `prefers-reduced-motion`;
- estados de falha, vazio e conteúdo parcial;
- ausência de ações falsas, dados fictícios e componentes sem comportamento;
- revisão visual de hierarquia, densidade e consistência;
- build de produção e suíte de frontend verdes.

Depois deste marco, o frontend deve ser considerado congelado salvo correção encontrada por integração ou benchmark.

### 6. `build: completa execução plug-and-play com Docker Compose`

Integrar os componentes previstos no System Design:

- frontend;
- Spring Boot API;
- Spring Boot Worker;
- PostgreSQL;
- RabbitMQ;
- volume temporário compartilhado;
- healthchecks e dependências de inicialização;
- variáveis documentadas;
- execução em ambiente limpo com `docker compose up`.

O Compose vem antes do benchmark final porque será a referência reproduzível da entrega e permitirá medir os serviços sob limites explícitos de recursos.

### 7. `perf: valida ingestão com 1M+ e planos de execução`

Executar o benchmark em ambiente controlado usando o dataset determinístico:

- versão do código;
- hardware e sistema operacional;
- limites de CPU/memória dos containers;
- semente, quantidade de linhas e tamanho do arquivo;
- batch size, prefetch e concorrência;
- tempo total e vazão;
- pico de memória;
- latência das consultas críticas;
- `EXPLAIN (ANALYZE, BUFFERS)` das queries principais;
- comparação do custo/benefício dos índices, especialmente o índice de analytics.

Configurações só serão defendidas como adequadas depois dessa evidência.

### 8. `docs: registra resultados, limites e instruções finais`

Fechar a entrega para o avaliador:

- `docker compose up` como caminho principal;
- URLs e fluxo de uso;
- geração do dataset 1M+;
- resultados do benchmark;
- estratégia de memória, batch e índices;
- decisões principais e limitações conhecidas;
- smoke test do fluxo upload -> processamento -> dashboard -> listagens;
- revisão final de consistência entre README, ARCHITECTURE, requisitos e ADRs.

## Regra para os próximos commits

Um marco pode ser dividido se aparecer uma responsabilidade independente que mereça teste e revisão próprios. Dois marcos também podem ser combinados se a separação produzir apenas commits artificiais.

O critério continua sendo:

- uma intenção clara por commit;
- build válido;
- testes relevantes verdes;
- documentação atualizada quando o comportamento ou trade-off mudar;
- comentários em PT-BR somente para decisões, invariantes e restrições não óbvias;
- nenhuma tecnologia adicionada sem um problema concreto que a justifique;
- nenhuma alteração que contradiga o System Design sem atualizar primeiro a decisão arquitetural correspondente.
