# ADR 0022: Remover covering index de analytics após medição

- Status: aceito
- Data: 2026-08-26
- Revisa: seção **Índice orientado à consulta** do ADR 0014

## Contexto

O ADR 0014 introduziu `idx_transactions_analytics_by_import` como candidato para permitir que o PostgreSQL considerasse um index-only scan na agregação por importação. A própria decisão condicionava a permanência do índice a evidência com dados representativos.

A execução de referência com 1.000.000 de transações válidas, seed `42`, comparou a consulta real de `GROUPING SETS` com e sem esse índice e repetiu a análise após `VACUUM (ANALYZE) transactions`.

No cenário pós-manutenção, o PostgreSQL escolheu `Seq Scan` nas duas variantes. O plano com o covering index terminou em aproximadamente `1290,734 ms`, enquanto a variante sem ele terminou em aproximadamente `1270,864 ms`. O índice ocupou `59.465.728` bytes na execução medida.

Esses números pertencem a uma execução de referência em runner hospedado e não são apresentados como capacidade universal. Porém, são suficientes para responder à pergunta arquitetural local: nesse workload, o índice não alterou o plano nem demonstrou benefício que justificasse seu custo de escrita e armazenamento.

## Decisão

A migration `V7__remove_unjustified_analytics_covering_index.sql` remove `idx_transactions_analytics_by_import`.

A consulta de analytics continua filtrando por `import_id`, agregando no PostgreSQL e usando `GROUPING SETS`. Nenhuma regra de aplicação ou contrato HTTP muda.

O benchmark continua capaz de reavaliar a hipótese: cria um covering index equivalente **somente dentro de uma transação de medição**, executa `EXPLAIN (ANALYZE, BUFFERS, VERBOSE, SETTINGS)` e executa `ROLLBACK`. Dessa forma, uma futura mudança de distribuição dos dados pode mostrar benefício sem obrigar o runtime atual a manter um índice sem ganho medido.

O índice `(import_id, id)` da paginação permanece. Ele responde a outro contrato: localizar uma importação e continuar a ordenação por cursor quando registros de imports distintos puderem estar intercalados. O fato de um benchmark de um único import conseguir usar a primary key não elimina essa necessidade sem uma medição que represente concorrência entre imports.

## Alternativas rejeitadas

### Manter o covering index “por precaução”

Rejeitado. Contraria a regra definida no ADR 0014 de validar custo/benefício e adiciona manutenção a cada insert sem evidência de ganho no cenário medido.

### Criar índices separados por categoria e mês

Rejeitado. A consulta sempre começa por `import_id`, e novos índices aumentariam ainda mais o write amplification antes de existir um predicado de produto que os justifique.

### Pré-agregar em outra tabela

Rejeitado neste escopo. Exigiria sincronização transacional adicional e alteraria o desenho de persistência sem necessidade demonstrada pelo tempo observado da consulta.

## Consequências

- a ingestão deixa de manter um B-tree adicional de analytics;
- o banco deixa de armazenar aproximadamente 59 MB extras por 1M de linhas no cenário medido;
- analytics continua sendo calculado no banco, com payload proporcional às dimensões e não ao número de transações;
- a decisão pode ser revista se dados futuros mostrarem seletividade ou frequência de consulta diferentes;
- o System Design permanece inalterado, pois nenhuma topologia ou responsabilidade foi adicionada ou removida.
