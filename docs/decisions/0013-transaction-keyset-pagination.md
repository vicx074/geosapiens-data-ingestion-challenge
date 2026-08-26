# ADR 0013: Keyset pagination das transações por identificador persistido

- Status: aceito
- Data: 2026-08-26

## Contexto

O desafio exige listar milhões de registros sem degradar a API conforme o usuário avança para páginas profundas. As transações válidas já são persistidas com `id BIGINT GENERATED ALWAYS AS IDENTITY`, `import_id` e `source_row`.

`OFFSET` atende páginas pequenas, mas obriga o PostgreSQL a localizar e descartar uma quantidade crescente de linhas antes de devolver a página. Para uma lista que pode chegar a milhões de registros, esse custo cresce justamente no cenário que o desafio pede para tratar.

## Decisão

`GET /imports/{id}/transactions` usará keyset pagination com o identificador persistido como cursor:

```sql
WHERE import_id = :importId
  AND id > :afterId
ORDER BY id
LIMIT :maxRows
```

O contrato HTTP recebe `after` opcional, limite padrão de 50 e máximo de 200. O caso de uso busca `limit + 1` registros para determinar se existe continuação sem executar `COUNT(*)` a cada página. Quando há continuação, `nextCursor` corresponde ao `id` do último item efetivamente devolvido.

A camada HTTP depende de `ListIngestionTransactions`; o caso de uso depende de `IngestionJobRepository` e da porta `IngestionTransactionQuery`; somente o adaptador PostgreSQL conhece SQL e `JdbcClient`.

## Por que `id` como cursor

`source_row` também é estável e possui uma constraint única por importação. Ela é a melhor chave para os erros porque a principal informação de uma rejeição é justamente a linha que falhou.

Na listagem das transações, o cursor representa a posição do registro persistido, não a semântica do arquivo de origem. `id` é imutável, monotônico e já é a chave primária da linha. `source_row` continua sendo retornado como metadado para rastreabilidade, mas não define o contrato de navegação da coleção de transações.

Essa escolha exige um índice adicional `(import_id, id)`. O custo extra de escrita e armazenamento é aceito porque a listagem eficiente de milhões de transações é um requisito explícito e essa consulta concreta passa a existir neste marco.

## Índice

A migração cria:

```sql
CREATE INDEX idx_transactions_import_cursor
    ON transactions (import_id, id);
```

O primeiro campo atende o filtro obrigatório por importação e o segundo atende simultaneamente o predicado `id > cursor` e a ordenação crescente. O índice pela chave primária `id` isolada não substitui esse acesso porque não agrupa os registros da importação consultada.

Não serão adicionadas colunas com `INCLUDE` neste momento. Um índice covering aumentaria ainda mais o custo de escrita e o tamanho do índice sem benchmark que prove benefício. O plano real será medido com `EXPLAIN (ANALYZE, BUFFERS)` no dataset representativo.

## Consistência durante processamento

A listagem mostra apenas linhas já commitadas. Como o Worker pode continuar inserindo transações enquanto o cliente pagina, não existe snapshot global entre requisições. O cursor monotônico evita repetir registros já vistos; novas linhas ficam depois dos ids existentes.

`nextCursor = null` significa que não havia outra linha commitada naquele instante, não que o job necessariamente terminou. O cliente deve usar `GET /imports/{id}` como fonte de verdade para saber se a importação alcançou estado terminal.

## Alternativas rejeitadas

- `OFFSET / LIMIT`: o trabalho cresce com páginas profundas e não é adequado ao volume alvo.
- `source_row` como cursor das transações: tecnicamente válido e aproveitaria um índice existente, mas acoplaria a navegação da coleção persistida à posição no arquivo CSV; nesta listagem o `id` é a identidade natural de armazenamento.
- cursor pelo `transaction_id` do CSV: o contrato não garante que esse campo seja monotônico nem apropriado para ordenação de paginação.
- cursor opaco codificado: ocultaria o valor interno, mas adicionaria versionamento e codificação sem necessidade funcional atual.
- índice covering: aumenta custo de escrita e espaço sem evidência de que heap fetch seja o gargalo.

## Consequências

A API passa a oferecer paginação de custo estável em relação à profundidade da navegação, com payload limitado e sem contagem total obrigatória em cada requisição. O próximo marco pode construir agregações sobre consultas específicas e criar somente os índices que seus filtros e agrupamentos justificarem.
