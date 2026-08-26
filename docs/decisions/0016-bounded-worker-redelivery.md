# ADR 0016: Redelivery limitada mesmo quando o registro de falha falha

- Status: aceito
- Data: 2026-08-26

## Contexto

O ADR 0009 definiu uma política simples para o Worker: a primeira falha de processamento solicita uma redelivery e uma nova falha envia a mensagem para a DLQ. A implementação seguia essa regra quando conseguia persistir o job como `FAILED`, mas havia uma lacuna quando a própria persistência desse estado falhava.

Nesse cenário, por exemplo durante indisponibilidade prolongada do PostgreSQL, o listener reenfileirava novamente uma mensagem que já havia consumido seu orçamento de retry. Como o sinal `redelivered` continuaria verdadeiro nas entregas seguintes, a mesma falha poderia gerar um loop de consumo, acesso ao banco e requeue sem convergência.

O system design já prevê `RabbitMQ` com retry limitado e DLQ. A correção deve fazer a implementação cumprir essa decisão sem adicionar serviços, armazenamento de retry ou uma nova topologia de filas sem necessidade demonstrada.

## Decisão

O orçamento do Worker permanece em no máximo duas entregas para uma falha de processamento: a entrega original e uma redelivery.

- na primeira falha, quando a mensagem ainda não está marcada como `redelivered`, o listener usa `basicNack(..., requeue=true)`;
- se uma mensagem já redelivered falhar novamente, o listener tenta persistir o job como `FAILED`;
- se essa persistência funcionar, a mensagem é rejeitada sem requeue e segue para a DLQ;
- se a persistência de `FAILED` também falhar, a mensagem igualmente é rejeitada sem requeue e segue para a DLQ.

No último caso, o estado do job pode permanecer não terminal até reconciliação operacional. Esse é um trade-off explícito: preservar a mensagem na DLQ e interromper um possível redelivery loop é preferível a atacar indefinidamente uma dependência indisponível apenas para tentar registrar o estado terminal.

O log desse caminho deve informar que a mensagem foi preservada na DLQ e que o job pode exigir reconciliação. A DLQ mantém a referência necessária para inspeção e eventual replay depois que a infraestrutura voltar a ficar saudável.

## Por que não persistir um contador adicional de retry

O problema pode ocorrer justamente quando o PostgreSQL está indisponível. Colocar o contador de tentativas na mesma dependência que falhou não garante que o limite possa ser atualizado ou consultado nesse cenário.

Além disso, a política atual exige somente uma redelivery. O próprio sinal `redelivered` do RabbitMQ é suficiente para distinguir a primeira entrega de uma entrega posterior; criar outra tabela e protocolo de coordenação não acrescentaria valor proporcional ao escopo.

## Por que não adicionar retry queue, quorum queue ou backoff agora

Essas estratégias são válidas quando existe requisito para várias tentativas, atraso entre tentativas ou política configurável de poison messages. O desafio atual não exige essas propriedades e o system design já especifica retry limitado seguido de DLQ.

Adicionar nova fila, TTL, exchange de retry ou alterar o tipo da fila apenas para suportar mais tentativas aumentaria a topologia sem requisito correspondente. Se benchmarks ou requisitos futuros mostrarem necessidade de `N > 1` retries com backoff, essa decisão deverá ser revista e o system design atualizado antes da implementação.

## Alternativas rejeitadas

- reenfileirar até conseguir persistir `FAILED`: pode gerar redelivery loop e ampliar uma indisponibilidade do PostgreSQL;
- ACK da mensagem depois da falha: remove a possibilidade de inspeção/replay e perde a evidência operacional;
- contador de retry persistido no PostgreSQL: não resolve o cenário em que o próprio PostgreSQL está indisponível;
- adicionar uma nova topologia de retry agora: complexidade sem requisito que justifique múltiplas tentativas ou backoff.

## Consequências

A política de falha passa a convergir sempre para ACK em sucesso ou DLQ depois do orçamento de redelivery, inclusive quando o registro do estado terminal falha. O Worker deixa de possuir um caminho conhecido de requeue sem limite.

Como consequência deliberada, uma falha simultânea de processamento e de persistência de `FAILED` pode deixar o job em `PROCESSING` enquanto sua mensagem está na DLQ. Esse estado deve ser tratado como incidente reconciliável, não como motivo para retry infinito automático.
