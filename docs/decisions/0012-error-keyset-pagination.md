# ADR 0012: Paginação dos erros por cursor de linha

- Status: aceito
- Data: 2026-08-26

## Contexto

O status da importação informa `rejectedRows`, mas deliberadamente não carrega todos os erros de linha. Um CSV pode produzir uma quantidade muito grande de rejeições, portanto os detalhes precisam de uma consulta própria com payload limitado.

A tabela `ingestion_errors` já preserva `import_id` e `source_row`, e a constraint de idempotência `UNIQUE (import_id, source_row)` cria no PostgreSQL um índice B-tree com a mesma ordem dessas colunas.

## Decisão

Os detalhes serão expostos por:

```http
GET /imports/{id}/errors?limit=50&after=<source_row>
```

`limit` tem valor padrão de 50 e máximo de 200. O limite existe para impedir que um cliente transforme o endpoint paginado em uma leitura sem fronteira.

A paginação será keyset, usando a linha de origem como cursor:

```sql
WHERE import_id = :importId
  AND source_row > :afterSourceRow
ORDER BY source_row
LIMIT :limitPlusOne
```

O caso de uso pede uma linha a mais do que o limite visível. Se a linha adicional existir, há próxima página e `nextCursor` recebe a `sourceRow` do último item devolvido. Isso evita executar `COUNT(*)` em todas as páginas.

`source_row` foi escolhido porque é imutável, único dentro da importação e já possui significado no domínio da ingestão: identifica a linha do CSV que falhou. Como o Worker percorre o arquivo sequencialmente, novos erros confirmados aparecem depois das linhas já percorridas. O redelivery não cria outra ocorrência da mesma linha por causa da constraint única.

Nenhum índice novo será criado neste marco. O índice produzido por `UNIQUE (import_id, source_row)` já atende ao filtro por importação e à navegação ordenada pelo cursor. Criar `(import_id, id)` também para erros duplicaria custo de escrita e armazenamento sem uma consulta que o exigisse.

O endpoint verifica a existência da importação antes de consultar a página. Isso diferencia corretamente uma importação inexistente (`404`) de uma importação válida sem erros (`200` com `items: []`).

A resposta usa `Cache-Control: no-store`, pois os erros podem ser consultados enquanto o Worker ainda está processando e novas páginas podem surgir.

## Clean Architecture

A camada HTTP depende do caso de uso `ListIngestionErrors`. A aplicação declara a porta `IngestionErrorQuery`; somente o adaptador PostgreSQL conhece o SQL. O DTO HTTP não é usado como modelo de persistência ou domínio.

Não foi criada uma nova abstração para o job: o caso de uso reutiliza `IngestionJobRepository`, que já representa a fronteira necessária para confirmar se a importação existe.

## Alternativas rejeitadas

- `OFFSET`: páginas profundas exigem descartar cada vez mais linhas antes de devolver o resultado.
- retornar todos os erros em `GET /imports/{id}`: faria o polling crescer proporcionalmente ao dataset.
- executar `COUNT(*)` a cada página: `rejectedRows` já fornece o total durável de rejeições no status e a listagem não precisa repetir essa agregação.
- cursor pelo `id` interno da tabela: funcionaria, mas `source_row` já é estável, útil ao cliente e possui índice compatível existente.
- criar um segundo índice apenas para a paginação: seria redundante para a consulta escolhida.

## Consequências

A listagem de erros mantém resposta limitada e custo de navegação independente da profundidade da página. A eficácia do índice será validada novamente com `EXPLAIN (ANALYZE, BUFFERS)` no dataset representativo do benchmark final, em vez de concluir desempenho a partir de uma base de testes pequena.
