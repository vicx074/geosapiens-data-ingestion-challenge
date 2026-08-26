# ADR 0019 — Arquitetura do frontend React, estado remoto e renderização

## Status

Aceita.

## Contexto

O desafio exige um frontend em React capaz de:

- enviar um CSV grande sem processar milhões de linhas no navegador;
- acompanhar o processamento assíncrono;
- exibir métricas agregadas;
- listar grandes volumes sem sobrecarregar o DOM;
- manter renderização limpa e experiência responsiva.

O System Design já define a relação `React -> API`, com `POST /imports`, polling de status, leitura das agregações e paginação das coleções. Esta decisão detalha a implementação interna do componente React sem alterar a topologia.

## Decisão

O frontend será implementado como SPA com **React, TypeScript e Vite**.

### Estado remoto

**SWR** será usado exclusivamente para estado proveniente da API:

- status da importação;
- analytics;
- páginas de transações;
- páginas de erros.

O polling de `GET /imports/{id}` usa intervalo periódico enquanto o job não é terminal e é interrompido quando `terminal=true`.

SWR foi escolhido porque o frontend é predominantemente orientado a leitura e revalidação de estado remoto. O fluxo possui poucas escritas e não exige optimistic updates, invalidação complexa entre dezenas de entidades ou coordenação de mutations que justificariam uma solução mais abrangente.

### Estado local

Estado puramente visual permanece local ao React, por exemplo:

- arquivo selecionado;
- aba ativa;
- cursor atual e histórico necessário à navegação;
- abertura de componentes transitórios;
- preferências temporárias de interface.

Redux e Zustand não serão introduzidos sem um problema concreto de estado compartilhado que não possa ser resolvido de forma local.

### Upload

O navegador envia o **arquivo CSV como um único multipart** para `POST /imports`.

O frontend não:

- lê o arquivo inteiro com `FileReader`;
- converte o conteúdo para JSON ou Base64;
- separa as linhas em chunks de aplicação;
- processa ou valida milhões de registros localmente.

O processamento em streaming e batch continua sendo responsabilidade do backend, conforme o System Design. Dividir o upload em chunks no browser exigiria protocolo adicional de ordenação, idempotência, remontagem, finalização e cleanup sem requisito que justifique essa complexidade.

Não haverá retry automático cego do `POST /imports`: uma queda de conexão depois de o backend aceitar o arquivo poderia repetir a criação do job. Uma política diferente só será adotada se o contrato de upload ganhar idempotency key explícita.

### Paginação e virtualização

As coleções usam duas proteções complementares:

1. **keyset pagination no backend** limita banco, rede e memória do cliente;
2. **TanStack Virtual** limita a quantidade de linhas montadas simultaneamente no DOM.

Não será usado infinite scroll ilimitado acumulando todas as páginas já visitadas. O cliente mantém um conjunto limitado de registros e navega por cursor, preservando memória previsível durante uso prolongado.

TanStack Table não será usado inicialmente porque a aplicação não necessita de uma engine de tabela com ordenação, filtros e estado de colunas complexos no cliente.

### Rotas

React Router será usado somente para navegação da SPA. O identificador da importação fará parte da URL (`/imports/:id`) para que refresh e acesso direto preservem o contexto.

### Gráficos

Gráficos simples serão renderizados a partir de `/imports/{id}/analytics`; o navegador não recalcula agregações percorrendo as transações. A biblioteca gráfica deverá ser carregada de forma compatível com o bundle e não poderá transformar o dashboard em dependência para o fluxo inicial de upload.

### Organização do código

A estrutura seguirá feature-first leve:

```text
src/
├── app/
├── pages/
├── features/
│   └── imports/
│       ├── api/
│       ├── components/
│       ├── hooks/
│       ├── model/
│       └── utils/
└── shared/
    ├── api/
    ├── ui/
    ├── lib/
    ├── config/
    └── styles/
```

As dependências apontam de `app/pages` para `features` e destas para `shared`. `shared` não conhece regras específicas de `imports`.

Componentes visuais, acesso remoto e regras de apresentação não serão concentrados em arquivos monolíticos.

## Renderização e UX

A interface é um sistema operacional de dados, não uma landing page. A prioridade é:

1. clareza operacional;
2. velocidade de uso;
3. hierarquia da informação;
4. estados e feedback;
5. consistência;
6. acessibilidade;
7. movimento discreto e funcional.

Cada fluxo relevante deve considerar ao menos os estados aplicáveis de loading, success, warning, error, empty, no-results e falha de conexão.

Não será exibido percentual fictício de processamento. Enquanto o backend não fornecer um total durável, a UI apresenta estado e contadores confirmados (`processedRows`, `acceptedRows`, `rejectedRows`) com indicador indeterminado.

Acessibilidade é requisito de implementação: HTML semântico, foco visível, navegação por teclado, labels, mensagens associadas, contraste adequado e suporte a `prefers-reduced-motion`.

## Alternativas consideradas

### TanStack Query

É uma solução robusta para server-state e mutations. Não foi escolhida inicialmente porque o fluxo atual é predominantemente de leitura/revalidação e não possui complexidade de mutations ou invalidação que justifique seu conjunto adicional de recursos. Deve ser reconsiderada se o produto ganhar operações de escrita interdependentes, optimistic updates ou cache complexo.

### Estado remoto manual com `useEffect`

Foi rejeitado porque exigiria implementar manualmente cache, deduplicação, revalidação, polling, tratamento de foco/reconexão e concorrência de requests, aumentando código incidental.

### Redux/Zustand

Foram rejeitados no primeiro escopo por ausência de estado global complexo do cliente.

### Infinite scroll sem limite

Foi rejeitado porque virtualizar o DOM não impede o crescimento da memória JavaScript quando todas as páginas permanecem acumuladas.

### Upload em chunks no frontend

Foi rejeitado porque o backend já recebe o arquivo em streaming e processa de forma assíncrona. Chunking de aplicação criaria um novo protocolo e mudaria o System Design sem requisito correspondente.

## Consequências

### Positivas

- cada dependência resolve um problema explícito do desafio;
- estado remoto e estado visual possuem responsabilidades claras;
- polling não vira lógica artesanal espalhada em componentes;
- navegação e refresh preservam o job atual;
- rede, memória JavaScript e DOM permanecem limitados por mecanismos distintos;
- o frontend continua pequeno o suficiente para ser compreendido durante a avaliação técnica.

### Custos

- SWR e TanStack Virtual adicionam duas dependências ao bundle;
- navegação por cursor exige guardar histórico mínimo para retorno à página anterior;
- virtualização exige cuidado com acessibilidade, altura das linhas e comportamento responsivo;
- a decisão deverá ser revisada se o escopo de mutations crescer significativamente.

## Relação com o System Design

Nenhum componente novo é adicionado. Continuam válidas as relações:

```text
Usuário -> React
React -> API: POST /imports
React -> API: polling GET /imports/{id}
React -> API -> PostgreSQL: status, paginação e agregações
```

SWR, React Router e TanStack Virtual são detalhes internos do componente React e não alteram a topologia arquitetural.