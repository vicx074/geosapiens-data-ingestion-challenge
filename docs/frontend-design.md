# Direção de frontend e critérios de UI/UX

## Objetivo

O frontend deve demonstrar domínio de React e performance sem sacrificar clareza operacional. O produto é uma interface de ingestão e análise de dados, não uma landing page promocional.

A qualidade visual será tratada como requisito funcional: hierarquia, estados, acessibilidade, responsividade e densidade precisam sustentar o uso com grande volume de dados.

## Princípios

Ordem de prioridade:

1. clareza operacional;
2. velocidade de uso;
3. hierarquia da informação;
4. estados e feedback;
5. consistência visual;
6. acessibilidade;
7. movimento discreto e funcional.

Não usar efeitos para esconder uma arquitetura de informação fraca.

## Direção visual

A direção inicial é um **console de operações de dados**:

- superfícies neutras e contraste confortável;
- tipografia com hierarquia forte;
- cor de destaque usada para ação e estado, não como decoração dominante;
- densidade baixa/média no upload e resumo;
- densidade média/alta na tabela;
- bordas, sombras e raios usados com moderação;
- sem glassmorphism como padrão;
- sem gradientes decorativos, blobs ou cards idênticos apenas para preencher espaço;
- animações rápidas e discretas, preferindo `transform` e `opacity`.

A aparência final deve ser autoral, mas a originalidade não pode reduzir legibilidade ou produtividade.

## Arquitetura da informação

### Tela de upload

Tarefa principal: iniciar uma importação.

Prioridade visual:

1. título/contexto curto;
2. dropzone/seleção de CSV;
3. arquivo escolhido e validações básicas de metadados;
4. ação de envio;
5. explicação curta do fluxo `upload -> fila -> processamento -> análise`;
6. feedback de erro e instrução de recuperação.

A dropzone precisa possuir `input[type=file]` real e ser utilizável por teclado.

### Detalhes da importação

Rota planejada:

```text
/imports/:id
```

Prioridade:

1. identidade do arquivo/importação;
2. estado atual do job;
3. contadores confirmados;
4. analytics;
5. transações;
6. erros.

O refresh da página não deve perder a importação porque o `jobId` pertence à URL.

### Status

Estados de domínio:

```text
RECEIVED
QUEUED
PROCESSING
COMPLETED
COMPLETED_WITH_ERRORS
FAILED
```

Enquanto o total durável não existir, não exibir porcentagem calculada ou estimada. Exibir contadores confirmados e um indicador de atividade indeterminado.

Mudanças automáticas de status devem ser anunciadas de forma não intrusiva para tecnologia assistiva.

### Dashboard

Usar apenas métricas retornadas pela API. Não recalcular agregações percorrendo páginas de transações no navegador.

Indicadores devem ter pesos diferentes conforme importância. Evitar uma fileira de cards visualmente idênticos sem hierarquia.

Visualizações planejadas:

- total de transações aceitas/processadas quando fizer sentido para o contexto;
- valor total;
- distribuição por categoria;
- série por mês.

Gráficos precisam ter legenda/descrição textual suficiente para não depender apenas de cor.

### Transações

A tabela deve combinar:

- keyset pagination do backend;
- página limitada no cliente;
- virtualização das linhas visíveis;
- loading sem salto excessivo de layout;
- estado vazio;
- erro recuperável;
- navegação anterior/próxima por cursor;
- responsividade sem comprimir todas as colunas até ficarem ilegíveis.

Em telas pequenas, priorizar os campos essenciais e reorganizar detalhes secundários em vez de manter uma tabela desktop comprimida.

### Erros

Erros de linha usam endpoint paginado próprio. A interface deve explicar:

- qual linha falhou;
- código/motivo disponível;
- que outras linhas válidas podem ter sido processadas;
- quando o job completo terminou com erros ou falhou definitivamente.

Não esconder informação crítica somente em toast.

## Estados obrigatórios

Para cada funcionalidade relevante, verificar os estados aplicáveis:

- default;
- hover;
- focus;
- active;
- disabled;
- loading;
- success;
- warning;
- error;
- empty;
- no results;
- falha de conexão;
- conteúdo parcial durante processamento.

## Responsividade

Larguras mínimas de inspeção:

```text
360px
390px
768px
1024px
1280px
1440px
```

Regras:

- não depender de hover;
- manter áreas de toque confortáveis;
- preservar foco e navegação por teclado;
- reordenar conteúdo conforme prioridade;
- não transformar tabela em conteúdo ilegível;
- simplificar movimento em telas menores;
- evitar scroll horizontal na página inteira; se uma região de dados precisar dele, mantê-lo contido e explícito.

## Acessibilidade

Obrigatório:

- HTML semântico;
- labels e nomes acessíveis;
- foco visível;
- navegação por teclado;
- contraste adequado;
- status que não dependa apenas de cor;
- mensagens de erro associadas ao contexto;
- suporte a `prefers-reduced-motion`;
- títulos em ordem lógica;
- botões reais para ações e links reais para navegação.

## Performance de renderização

- não renderizar milhares de linhas simultaneamente;
- não acumular páginas ilimitadamente em memória;
- evitar Effects usados apenas para derivar estado que pode ser calculado no render;
- não aplicar `memo`, `useMemo` e `useCallback` indiscriminadamente;
- medir antes de introduzir otimizações complexas;
- lazy-load de recursos pesados que não participem do primeiro fluxo quando houver benefício comprovável;
- animações não devem causar relayout contínuo nem bloquear interação.

## Estrutura de código

Estrutura inicial:

```text
src/
├── app/
│   ├── router/
│   ├── providers/
│   └── styles/
├── pages/
│   ├── upload/
│   └── import-details/
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

Regras:

- separar acesso remoto de componentes visuais;
- separar regra de apresentação de primitives compartilhadas;
- evitar arquivo monolítico para toda a página;
- `shared` não depende de `features/imports`;
- não criar abstrações genéricas sem segundo caso concreto;
- não criar design system enorme antes de existirem componentes reais repetidos.

## Critérios de aceite do frontend

Antes de considerar o frontend concluído, validar:

- upload por mouse e teclado;
- navegação para o job retornado pelo `202 Accepted`;
- polling ativo apenas enquanto necessário;
- polling interrompido em estado terminal;
- refresh preservando `/imports/:id`;
- estados de loading/erro/vazio nas consultas;
- analytics usando dados do backend;
- paginação por cursor sem `OFFSET` no cliente;
- número limitado de elementos montados na tabela virtualizada;
- layout funcional nas larguras definidas;
- foco visível e navegação por teclado;
- redução de movimento respeitada;
- nenhuma ação falsa, dado fictício ou botão sem comportamento;
- testes de comportamento para os fluxos críticos.