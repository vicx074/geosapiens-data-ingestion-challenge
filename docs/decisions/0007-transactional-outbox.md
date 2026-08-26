# ADR 0007: Transactional Outbox para publicação dos jobs

- Status: aceito
- Data: 2026-08-25

## Contexto

O upload precisa criar um job durável e solicitar seu processamento assíncrono. PostgreSQL e RabbitMQ não participam da mesma transação. Se a aplicação confirmar o banco e falhar antes de publicar, o job poderá permanecer sem processamento. Se publicar antes e falhar ao gravar o job, o Worker receberá uma referência inexistente.

Publisher confirms informam que o RabbitMQ aceitou uma publicação, mas não tornam atômicos o commit do PostgreSQL e o envio ao broker. Portanto, confirmações isoladas não fecham essa janela.

## Decisão

Gravar o job e uma entrada do Outbox na mesma transação do PostgreSQL. Como existe somente uma solicitação inicial de processamento por job, `job_id` também será a chave da entrada. Ela conterá instante de criação, quantidade de tentativas, próxima tentativa, último erro e estado da publicação.

Um publicador interno da função API buscará um lote limitado de entradas disponíveis e publicará mensagens persistentes no RabbitMQ. Cada mensagem conterá somente `jobId`; o caminho temporário continuará derivado desse identificador e não será duplicado no broker.

O publicador usará `mandatory` e publisher confirms correlacionados. Uma entrada somente será marcada como publicada depois de confirmação positiva e ausência de retorno por rota inexistente. Falhas terão tentativa, motivo resumido e próximo instante persistidos. Depois do limite configurado, o Outbox e o job serão encerrados como falha.

A seleção de entradas usará ordem estável e `FOR UPDATE SKIP LOCKED` em transação curta. Isso limita o lote e evita que publicadores concorrentes reivindiquem a mesma entrada. O lock não permanecerá aberto durante a espera pelo RabbitMQ; a entrada será primeiro marcada com uma reivindicação temporária recuperável.

O envio permanece pelo menos uma vez. Se o processo cair depois da confirmação do RabbitMQ e antes de marcar o Outbox, a mensagem poderá ser publicada novamente. O Worker deverá tratar `jobId` e `(import_id, source_row)` de forma idempotente.

## Alternativas rejeitadas

- Publicar depois do commit sem Outbox: mantém uma janela em que o job existe, mas nenhuma mensagem solicita seu processamento.
- Publicar antes do commit: permite que o Worker receba um job que ainda não existe ou cujo commit falhou.
- Apenas publisher confirms: confirma a aceitação pelo RabbitMQ, mas não coordena essa aceitação com a transação do PostgreSQL.
- Transação AMQP: não cria uma transação distribuída com PostgreSQL e reduz a vazão sem resolver atomicidade entre os dois recursos.
- Retry somente em memória: perde tentativas na reinicialização e não oferece estado consultável.
- CDC com Debezium: automatizaria a leitura do log do banco, mas adicionaria componentes operacionais sem necessidade para um único evento e o escopo local do desafio.

## Consequências

O endpoint poderá responder `202 Accepted` depois que arquivo, job e intenção de publicação estiverem duráveis, sem esperar o processamento. A indisponibilidade temporária do RabbitMQ não perde o pedido.

O modelo aceita duplicação de publicação e exige consumidor idempotente. O Outbox adiciona tabela, rotina de reivindicação, limpeza e métricas, mas não adiciona um serviço à topologia do system design.

## Referências

- [Publisher confirms e segurança de dados no RabbitMQ](https://www.rabbitmq.com/docs/publishers)
- [Publisher confirms e returns no Spring AMQP](https://docs.spring.io/spring-amqp/reference/amqp/template.html)
- [`SKIP LOCKED` no PostgreSQL](https://www.postgresql.org/docs/current/sql-select.html)
