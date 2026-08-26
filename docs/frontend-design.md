# Direção de frontend e critérios de UI/UX

## Objetivo

O frontend demonstra domínio de React e performance sem sacrificar clareza operacional. O produto é uma interface de ingestão e análise de dados, não uma landing page promocional.

A qualidade visual é tratada como requisito funcional: hierarquia, estados, acessibilidade, responsividade e densidade precisam sustentar o uso com grande volume de dados.

## Princípios

Ordem de prioridade:

1. clareza operacional;
2. velocidade de uso;
3. hierarquia da informação;
4. estados e feedback;
5. consistência visual;
6. acessibilidade;
7. movimento discreto e funcional.

Efeitos não são usados para esconder arquitetura de informação fraca.

## Direção visual

A direção adotada é um **console de operações de dados**:

- superfícies neutras e contraste confortável;
- tipografia com hierarquia forte;
- cor de destaque usada para ação e estado, não como decoração dominante;
- densidade baixa/média no upload e resumo;
- densidade média/alta na tabela;
- bordas, sombras e raios usados com moderação;
- sem glassmorphism como padrão;
- sem gradientes decorativos, blobs ou cards idênticos apenas para preencher espaço;
- animações rápidas e discretas, preferindo `transform` e `opacity`.

A aparência busca identidade sem reduzir legibilidade ou produtividade.

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

A dropzone possui `input[type=file]` real e é utilizável por teclado.

### Detalhes da importação

Rota:

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

O refresh da página não perde a importação porque o `jobId` pertence à URL e o estado é reconstruído pela API.

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

Como o total durável não existe no contrato, a UI não exibe porcentagem calculada ou estimada. Exibe contadores confirmados e indicador de atividade durante processamento.

Mudanças automáticas de status são anunciadas de forma não intrusiva para tecnologia assistiva.

### Dashboard

O dashboard usa apenas métricas retornadas pela API. Não recalcula agregações percorrendo páginas de transações no navegador.

Indicadores possuem pesos diferentes conforme importância. A composição evita uma fileira de cards visualmente idênticos sem hierarquia.

Visualizações:

- total de transações;
- valor total;
- distribuição por categoria;
- série por mês.

Os gráficos simples possuem informação textual suficiente para não depender apenas de cor.

### Transações

A listagem combina:

- keyset pagination do backend;
- página limitada no cliente;
- virtualização das linhas visíveis;
- loading sem salto excessivo de layout;
- estado vazio;
- erro recuperável;
- navegação anterior/próxima por cursor;
- responsividade sem comprimir todas as colunas até ficarem ilegíveis.

Em telas pequenas, campos essenciais são priorizados e detalhes secundários são reorganizados.

### Erros

Erros de linha usam endpoint paginado próprio. A interface apresenta:

- qual linha falhou;
- código/motivo disponível;
- que outras linhas válidas podem ter sido processadas;
- quando o job completo terminou com erros ou falhou definitivamente.

Informação crítica não fica somente em toast.

## Estados considerados

Para as funcionalidades relevantes, os testes e componentes consideram os estados aplicáveis entre:

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
- falha de conexão;
- conteúdo parcial durante processamento.

Nem todo componente possui todos esses estados; a lista é uma régua de revisão, não uma exigência artificial de criar variantes sem sentido.

## Responsividade

Larguras de referência usadas no design:

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
- evitar scroll horizontal na página inteira; quando uma região de dados precisa dele, o overflow fica contido.

Essas larguras são critérios de inspeção do layout, não uma alegação de teste visual manual em todo dispositivo real.

## Acessibilidade

A implementação prioriza:

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

- páginas do backend limitam quantidade de dados transferidos;
- TanStack Virtual evita montar toda a página de registros no DOM;
- páginas visitadas não são acumuladas como infinite scroll sem limite;
- Effects não são usados apenas para estado derivável durante render;
- `memo`, `useMemo` e `useCallback` não são aplicados indiscriminadamente;
- otimizações complexas exigem problema medido;
- animações não devem causar relayout contínuo nem bloquear interação.

Não há benchmark de FPS ou memória do navegador nesta entrega; a evidência existente é estrutural, comportamental e de DOM limitado.

## Estrutura de código

Estrutura adotada:

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

- acesso remoto separado de componentes visuais;
- regra de apresentação separada de primitives compartilhadas;
- `shared` não depende de `features/imports`;
- abstrações genéricas só aparecem quando existe segundo caso concreto;
- não existe design system grande criado antes da necessidade.

## Critérios de aceite do frontend

A implementação final é validada por:

- upload por interação e input de arquivo;
- navegação para o job retornado pelo `202 Accepted`;
- polling ativo apenas enquanto necessário;
- polling interrompido em estado terminal;
- refresh preservando `/imports/:id`;
- estados de loading/erro/vazio nas consultas;
- analytics usando dados do backend;
- paginação por cursor;
- número limitado de elementos montados na tabela virtualizada;
- foco visível e navegação por teclado nos fluxos principais;
- `prefers-reduced-motion` respeitado;
- ausência de dados fictícios como fonte do dashboard;
- testes de comportamento, typecheck e build de produção no CI.
