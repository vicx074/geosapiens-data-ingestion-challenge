# ADR 0009: Worker CSV em streaming e concorrência limitada

- Status: aceito
- Data: 2026-08-26

## Contexto

O upload e a publicação confiável do job já são duráveis, mas o trabalho ainda precisa sair do RabbitMQ e percorrer arquivos com milhões de linhas sem tornar o uso de memória proporcional ao tamanho total do CSV. Também é necessário impedir que múltiplos uploads abram trabalho ilimitado contra o host e o PostgreSQL.

## Decisão

O Worker consome uma mensagem por arquivo e abre o CSV diretamente no armazenamento temporário pela chave derivada do `jobId`. A leitura usa Apache Commons CSV sobre um `Reader` UTF-8 configurado para rejeitar sequências inválidas; nenhuma coleção contém todas as linhas do arquivo.

O cabeçalho precisa ser exatamente `transaction_id,occurred_at,amount,category`. Cada registro é validado durante a iteração e convertido em um evento de linha válida ou rejeitada. O parser mantém somente a linha atual e contadores, deixando a futura persistência em lote consumir esses eventos sem precisar reler ou materializar o arquivo.

A concorrência do listener e o `prefetch` são valores explícitos de `app.worker`. Os valores iniciais são `concurrency=2` e `prefetch=1`: no máximo dois arquivos são processados simultaneamente por instância e cada consumer reserva apenas um job do broker. Esses números são hipóteses e serão recalibrados pelo benchmark, não apresentados como ótimos universais.

O listener utiliza ACK manual. Sucesso ou job já terminal confirma a mensagem. Uma falha de infraestrutura solicita uma redelivery; se a mensagem já tiver sido redelivered e falhar novamente, o job é marcado como `FAILED` e a mensagem é rejeitada para uma DLQ durável. Mensagens com identificador inválido ou sem job correspondente também não entram em retry infinito.

O job é marcado como `PROCESSING` antes da leitura. Neste marco, os contadores são persistidos somente depois que o arquivo inteiro termina de ser percorrido. Isso evita somar progresso duas vezes caso uma falha ocorra antes do ACK, enquanto a persistência idempotente por `source_row` ainda não existe.

## Limite deste marco

As linhas válidas e os detalhes dos erros já são produzidos pelo parser, porém o consumidor de produção ainda os descarta e persiste apenas os contadores finais do job. O próximo marco adicionará `transactions`, `ingestion_errors` e JDBC batch; a partir dele, cada batch confirmará dados, erros e progresso na mesma transação e permitirá progresso incremental durável.

A remoção do arquivo temporário também permanece adiada até esse limite transacional existir. Remover o CSV agora faria um job terminal perder sua única cópia dos dados antes de as transações terem sido persistidas.

## Alternativas rejeitadas

- `Files.readAllLines` ou materialização em `List`: uso de memória cresce com o arquivo e viola o requisito central.
- `String.split(",")`: não implementa corretamente escaping e campos CSV entre aspas.
- Concorrência sem limite: transfere picos do broker diretamente para CPU, disco e banco.
- ACK antes do processamento: pode perder o job se o Worker morrer após a confirmação.
- Retry infinito: mantém mensagens problemáticas circulando sem convergência nem inspeção operacional.

## Consequências

O fluxo assíncrono já chega ao Worker com backpressure explícito e valida o contrato do dataset em memória limitada. O estado do job também converge para terminal em sucesso, erro estrutural ou falhas repetidas. A durabilidade dos registros financeiros continua deliberadamente reservada ao próximo marco.
