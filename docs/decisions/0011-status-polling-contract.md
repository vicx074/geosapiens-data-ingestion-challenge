# ADR 0011: Contrato de status para polling

- Status: aceito
- Data: 2026-08-26

## Contexto

O upload já responde `202 Accepted` e informa `Location: /imports/{id}`. O system design define polling entre React e API para acompanhar o processamento assíncrono. Depois da persistência por lote, os contadores de `ingestion_jobs` representam linhas efetivamente confirmadas no PostgreSQL e podem ser usados como fonte durável de progresso.

O mesmo arquivo pode conter milhões de linhas inválidas. Portanto, incluir todos os erros no payload consultado repetidamente faria o tamanho da resposta crescer com o dataset e contrariaria o objetivo de manter API e frontend responsivos.

## Decisão

`GET /imports/{id}` será o contrato de polling do status.

A camada HTTP chama o caso de uso `GetIngestionStatus`, e o caso de uso depende da porta `IngestionJobRepository` já existente. O controller não acessa SQL, `JdbcClient` nem detalhes do adaptador PostgreSQL.

A resposta contém apenas estado e metadados de tamanho constante:

- identificador e nome do arquivo;
- estado atual;
- linhas processadas, aceitas e rejeitadas;
- indicador de estado terminal;
- instantes relevantes do ciclo de vida;
- motivo de falha quando o job termina como `FAILED`.

Os contadores retornados são os contadores persistidos do job. Não haverá contador paralelo em memória na API.

A resposta usa `Cache-Control: no-store` para evitar que caches intermediários devolvam um estado antigo durante o polling.

Uma importação inexistente retorna `404` em `application/problem+json` por meio do tratamento centralizado de exceções.

## Progresso percentual

O endpoint não inventará um percentual enquanto o total de linhas não for conhecido de forma durável. Descobrir o total antes de processar exigiria uma passagem adicional pelo arquivo ou outra estratégia de contabilização. Neste marco, `processedRows` é a medida objetiva disponível durante o processamento.

O frontend poderá mostrar atividade e quantidade processada. Percentual só será adicionado se houver uma fonte de `totalRows` cuja obtenção não viole os objetivos de streaming e desempenho.

## Erros detalhados

O status expõe `rejectedRows`, mas não um array de erros. Os detalhes de `ingestion_errors` serão consultados por endpoint paginado próprio, permitindo payload limitado e navegação independente do polling.

## Alternativas rejeitadas

- Controller consultando `JdbcClient` diretamente: mistura adaptador HTTP com persistência e viola a direção de dependências adotada no projeto.
- Guardar progresso em memória: perde estado em reinício da API e pode divergir dos lotes já confirmados.
- Retornar todos os erros no status: transforma uma consulta frequente em resposta potencialmente proporcional a milhões de linhas.
- SSE ou WebSocket: adicionam conexão persistente sem necessidade demonstrada; polling já atende ao fluxo unidirecional previsto no system design.
- Percentual estimado sem total conhecido: apresenta precisão inexistente ao usuário.

## Consequências

O polling permanece barato e previsível em tamanho. A API lê uma linha de `ingestion_jobs`, cuja chave primária já atende à busca por identificador. Nenhum índice adicional é necessário para este endpoint. O próximo marco de erros ou listagem poderá adicionar seus próprios índices somente quando a consulta concreta existir.
