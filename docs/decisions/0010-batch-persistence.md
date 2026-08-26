# ADR 0010: Persistência idempotente por lote

- Status: aceito
- Data: 2026-08-26

## Contexto

O Worker já percorre o CSV progressivamente e classifica cada linha como transação válida ou erro. O system design exige que o Worker grave esses resultados no PostgreSQL sem acumular o arquivo inteiro em memória, mantendo progresso consistente mesmo quando o RabbitMQ redeliver uma mensagem.

## Decisão

As linhas produzidas pelo parser serão acumuladas em um buffer limitado e persistidas quando o lote alcançar `app.worker.batch-size`. O valor inicial é `1000` linhas. Ele é uma hipótese operacional para reduzir round-trips sem elevar demais a memória por consumer e será validado pelo benchmark; não é apresentado como tamanho universalmente ótimo.

Cada flush abre uma única transação de banco que:

1. executa JDBC batch para as transações válidas;
2. executa JDBC batch para os erros de linha;
3. atualiza os contadores do `ingestion_jobs` somente pelas linhas realmente inseridas.

Se a atualização do job falhar, os inserts do mesmo lote também sofrem rollback. Assim, dados e progresso não divergem por uma falha entre operações.

As tabelas `transactions` e `ingestion_errors` preservam `import_id` e `source_row` e possuem `UNIQUE (import_id, source_row)`. Os inserts usam `ON CONFLICT DO NOTHING`. Em uma redelivery, um lote já confirmado não é duplicado e as contagens retornadas pelo driver impedem que o progresso seja somado novamente.

O progresso só pode ser atualizado quando o driver informa a quantidade de linhas afetadas por cada operação do batch. `SUCCESS_NO_INFO` é tratado como erro porque aceitar uma contagem desconhecida quebraria a garantia de idempotência dos contadores.

Depois que todo o CSV é percorrido, o caso de uso compara os totais persistidos com os totais observados pelo parser antes de concluir o job. Essa verificação impede marcar como concluída uma importação cujo estado persistido não represente todas as linhas classificadas.

Com transações e erros duráveis, o arquivo temporário passa a ser removido depois que o job alcança estado terminal. A remoção usa `deleteIfExists`, portanto uma redelivery de um job já concluído também é idempotente. Falha de I/O na limpeza não é silenciosa e permanece sujeita à política de redelivery do Worker.

## Schema e índices

Este marco cria apenas as constraints necessárias à integridade e à idempotência. Índices destinados à paginação e às agregações não são adicionados antecipadamente; serão criados junto às consultas reais e validados com `EXPLAIN (ANALYZE, BUFFERS)`, como definido no `ARCHITECTURE.md`.

## Alternativas rejeitadas

- `saveAll` com JPA/Hibernate: adiciona gerenciamento de entidades e overhead sem necessidade para uma carga tabular orientada a throughput.
- Um `INSERT` e um commit por linha: multiplica round-trips e custo transacional em milhões de registros.
- Uma única transação para o arquivo inteiro: mantém uma transação longa, amplia retenção de recursos e perde progresso durável entre falhas.
- `COPY` diretamente na tabela final: pode oferecer throughput maior, mas não resolve sozinho a classificação de erros nem o `ON CONFLICT` necessário ao redelivery; exigiria staging e mais complexidade para um requisito ainda não medido.
- Contar todas as linhas lidas como progresso: uma redelivery somaria novamente linhas já persistidas.

## Consequências

A memória do Worker passa a variar principalmente com o parser e o lote configurado, não com o tamanho total do arquivo. Redelivery pode reler o CSV, mas lotes já confirmados convergem para no-op no banco e não duplicam dados nem progresso. O próximo marco pode construir o endpoint de status sobre contadores que agora representam persistência efetivamente durável.
