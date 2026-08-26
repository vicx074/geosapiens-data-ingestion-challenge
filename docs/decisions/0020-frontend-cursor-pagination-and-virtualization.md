# ADR 0020 — Paginação por cursor e virtualização das coleções no frontend

## Status

Aceita.

## Contexto

O backend já expõe duas coleções potencialmente grandes:

- `GET /imports/{id}/transactions`, paginada por cursor interno `id`;
- `GET /imports/{id}/errors`, paginada por `source_row`.

O desafio exige uma interface que permaneça responsiva com alto volume. Virtualizar somente o DOM não resolve crescimento de memória se o navegador continuar acumulando todas as páginas visitadas. Da mesma forma, paginação server-side sem limitar o DOM ainda pode produzir renderizações desnecessariamente grandes em páginas maiores.

Esta decisão detalha a implementação interna do componente React sem alterar o System Design.

## Decisão

### Página limitada no cliente

O frontend solicita **100 itens por página**. Esse valor fica abaixo do limite máximo de 200 aceito pelo backend e mantém um compromisso simples entre quantidade de requests e tamanho do snapshot carregado.

A navegação usa exclusivamente `nextCursor`; não existe cálculo de offset, total de páginas ou `COUNT(*)` adicional para habilitar a interface.

O histórico local guarda somente os cursores necessários para voltar às páginas anteriores. Ao avançar ou retornar, a página anterior é substituída pelos novos registros.

### Cache SWR estável

Cada coleção usa uma única chave lógica no SWR por importação:

- transações;
- erros.

O cursor não faz parte da chave do cache. Ele permanece no estado local do hook de paginação.

Essa escolha é intencional: usar uma chave diferente para cada cursor faria o cache manter uma página distinta para cada trecho visitado. Em uma sessão longa, isso recriaria no JavaScript o crescimento de memória que a paginação pretende evitar.

Uma troca de página busca primeiro o novo snapshot. Se a chamada falhar, a página atual continua visível e o cursor não avança.

### Virtualização

`@tanstack/react-virtual` é usado para limitar a quantidade de linhas montadas simultaneamente no DOM.

A virtualização resolve uma camada diferente da paginação:

1. o backend limita banco, serialização e rede;
2. a página de 100 itens limita a memória do cliente;
3. o virtualizer limita elementos React/DOM montados no viewport.

As linhas usam altura previsível e `overscan` pequeno. A tabela mantém cabeçalhos e roles ARIA; a informação textual não depende da virtualização para existir no modelo da página.

### Uma coleção ativa por vez

A interface usa abas para transações e erros e monta somente a coleção ativa.

Isso evita manter simultaneamente duas páginas, dois virtualizers e duas consultas que o usuário não está inspecionando. Ao trocar de aba, a coleção anterior é desmontada.

### Atualização durante processamento

`nextCursor = null` significa apenas que não existe outra página **no snapshot consultado**. Enquanto o job não for terminal, a interface informa que novos registros ainda podem ser confirmados e oferece atualização explícita da página atual.

As coleções não recebem polling próprio contínuo. O polling de status já informa evolução do job; a listagem é uma ferramenta de inspeção e pode ser atualizada sob demanda. Isso evita adicionar duas consultas periódicas ao status e aos analytics sem necessidade funcional.

## Tratamento de falhas

Falha ao carregar a primeira página exibe retry isolado.

Falha de revalidação preserva os dados já confirmados.

Falha durante navegação não troca o cursor nem descarta a página atual.

Esses erros de leitura não são apresentados como falha do job de ingestão.

## Alternativas consideradas

### Infinite scroll acumulando páginas

Rejeitado. Virtualizar o DOM não impede que arrays e caches cresçam indefinidamente com todas as páginas já carregadas.

### Cursor como parte da chave SWR

Rejeitado neste fluxo porque manteria uma entrada de cache por página visitada. O benefício de voltar instantaneamente para uma página anterior não compensa o crescimento não limitado de memória.

### Paginação por offset no frontend

Rejeitada porque o contrato do backend é keyset pagination e foi desenhado para evitar o custo de offsets profundos.

### TanStack Table

Não foi introduzido. A tela precisa de leitura tabular e virtualização, mas ainda não possui ordenação, filtros, seleção ou estado de colunas que justifiquem uma engine de tabela adicional.

### Polling separado para transações e erros

Rejeitado. A atualização operacional pertence ao status. As coleções são snapshots de inspeção e possuem refresh explícito.

## Consequências

### Positivas

- memória do navegador fica limitada a uma página ativa por coleção;
- DOM permanece pequeno mesmo com páginas de 100 registros;
- cursor do backend é respeitado de ponta a ponta;
- falha de navegação não destrói o contexto atual;
- não há `COUNT(*)`, offset profundo ou infinite scroll ilimitado;
- a aba inativa não mantém consulta nem virtualizer ativos.

### Custos

- voltar para uma página anterior exige nova leitura da API;
- não existe indicação de “página X de Y”, pois isso exigiria um total adicional que o contrato não fornece;
- a tabela horizontal em telas estreitas prioriza legibilidade das colunas em vez de comprimir dados críticos.

## Relação com o System Design

Nenhum serviço ou fluxo arquitetural novo é criado. Permanecem válidas as relações:

```text
React -> API -> PostgreSQL: paginação
React: lista virtualizada
```

SWR, histórico de cursores e TanStack Virtual são detalhes internos do componente React.
