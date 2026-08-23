# ADR 0003: RabbitMQ para processamento assíncrono

- Status: aceito
- Data: 2026-08-23

## Contexto

O upload deve responder antes do processamento de milhões de linhas. O trabalho precisa ser limitado para não transferir picos diretamente ao PostgreSQL e deve permitir recuperação após falha do Worker.

## Decisão

Usar RabbitMQ como fila de trabalho, com mensagens persistentes, confirmação manual, prefetch limitado, tentativas finitas e DLQ. Um job representa um arquivo; a concorrência ocorre inicialmente entre arquivos.

## Alternativas rejeitadas

- Executor em memória: perde trabalho ao reiniciar e não coordena múltiplos processos.
- Kafka: retenção, replay e múltiplos grupos de consumo não são necessidades atuais.
- Processamento dentro do request: viola o requisito assíncrono e prende recursos HTTP ao arquivo inteiro.

## Consequências

Há um componente operacional adicional, compensado por backpressure, redelivery e separação clara entre API e Worker. A consistência entre criação do job e publicação ainda exige uma decisão própria antes da implementação.
