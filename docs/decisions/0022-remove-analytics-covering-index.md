# ADR 0022: Remover covering index de analytics após medição

- Status: aceito
- Data: 2026-08-26
- Revisa: seção **Índice orientado à consulta** do ADR 0014

## Contexto

O ADR 0014 introduziu `idx_transactions_analytics_by_import` como candidato para permitir que o PostgreSQL considerasse um index-only scan na agregação por importação. A própria decisão condicionava a permanência do índice a evidência com dados representativos.

A execução de referência final com 1.000.000 de transações válidas, seed `42`, comparou a consulta real de `GROUPING SETS` com o runtime sem esse índice e com um covering index equivalente criado somente dentro da transação de benchmark. A comparação foi repetida depois de `VACUUM (ANALYZE) transactions`, fora do tempo de ingestão.

No cenário pós-manutenção, o PostgreSQL escolheu `Seq Scan` nas duas variantes. Sem o covering index, o plano terminou em aproximadamente `1323,589 ms`. Com o candidato criado para a medição, terminou em aproximadamente `1479,195 ms`.

Esses números pertencem a uma execução de referência em runner hospedado pelo GitHub e não são apresentados como capacidade universal. Porém, respondem à pergunta arquitetural local: nesse workload, o índice não alterou o plano nem demonstrou benefício que justifique manter um B-tree adicional em toda escrita.

## Decisão

A migration `V7__remove_unjustified_analytics_covering_index.sql` remove `idx_transactions_analytics_by_import`.

A consulta de analytics continua filtrando por `import_id`, agregando no PostgreSQL e usando `GROUPING SETS`. Nenhuma regra de aplicação ou contrato HTTP muda.

O benchmark continua capaz de reavaliar a hipótese: cria um covering index equivalente **somente dentro de uma transação de medição**, executa `EXPLAIN (ANALYZE, BUFFERS, VERBOSE, SETTINGS)` e executa `ROLLBACK`. Dessa forma, uma futura mudança de distribuição dos dados pode mostrar benefício sem obrigar o runtime atual a manter um índice sem ganho medido.

O índice `(import_id, id)` da paginação permanece. Ele responde a outro contrato: localizar uma importação e continuar a ordenação por cursor quando registros de imports distintos puderem estar intercalados. O benchmark atual possui um único import e conseguiu usar a primary key mesmo quando o índice composto foi removido temporariamente; por isso essa execução **não comprova ganho do índice de cursor**, mas também não representa o cenário concorrente necessário para justificar sua remoção.

## Alternativas rejeitadas

### Manter o covering index “por precaução”

Rejeitado. Contraria a regra definida no ADR 0014 de validar custo/benefício e adiciona manutenção a cada insert sem evidência de ganho no cenário medido.

### Criar índices separados por categoria e mês

Rejeitado. A consulta sempre começa por `import_id`, e novos índices aumentariam ainda mais o write amplification antes de existir um predicado de produto que os justifique.

### Pré-agregar em outra tabela

Rejeitado neste escopo. Exigiria sincronização transacional adicional e alteraria o desenho de persistência. A referência atual de analytics ficou em aproximadamente `1,19 s` de p50 para 1M de registros; isso é tratado como um trade-off conhecido, não como motivo suficiente para introduzir pré-agregação sem um SLA que a exija.

## Consequências

- a ingestão deixa de manter um B-tree adicional de analytics;
- analytics continua sendo calculado no banco, com payload proporcional às dimensões e não ao número de transações;
- a consulta continua fazendo scan do conjunto do import no workload medido;
- a decisão pode ser revista se dados futuros mostrarem seletividade, frequência de consulta ou SLA diferentes;
- o System Design permanece inalterado, pois nenhuma topologia ou responsabilidade foi adicionada ou removida.
