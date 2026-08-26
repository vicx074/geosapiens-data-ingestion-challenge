# ADR 0021 — Topologia de execução plug-and-play com Docker Compose

## Status

Aceita.

## Contexto

O desafio exige que a solução completa possa ser executada sem dependências locais além do Docker Compose. O System Design já define React, Spring Boot API, Spring Boot Worker, RabbitMQ, PostgreSQL e um volume temporário compartilhado para o CSV.

A implementação do Compose deve materializar essa topologia sem transformar API e Worker em bases de código independentes nem adicionar serviços arquiteturais não previstos.

## Decisão

### Mesma imagem de backend, duas funções de execução

`backend-api` e `backend-worker` usam a mesma imagem Spring Boot e o mesmo código do monólito modular.

A API executa com:

- `WORKER_ENABLED=false`;
- `OUTBOX_PUBLISHER_ENABLED=true`;
- Flyway habilitado;
- servidor HTTP ativo.

O Worker executa com:

- `WORKER_ENABLED=true`;
- `OUTBOX_PUBLISHER_ENABLED=false`;
- Flyway desabilitado;
- `SPRING_MAIN_WEB_APPLICATION_TYPE=none`.

Essa separação evita que o Worker exponha uma segunda API sem necessidade e impede dois publicadores de Outbox no ambiente padrão. O Worker só inicia depois que a API está saudável, o que também garante que as migrations já tenham sido aplicadas.

### Volume temporário compartilhado

API e Worker montam o mesmo volume nomeado em `/data/uploads`.

A API grava o multipart nesse volume e o Worker abre o arquivo pelo identificador persistido. Depois do estado terminal durável, o Worker remove o CSV. O volume não substitui PostgreSQL como fonte de verdade e não é usado para persistência definitiva.

### PostgreSQL e RabbitMQ

PostgreSQL e RabbitMQ usam volumes nomeados próprios e health checks. Os serviços de backend só iniciam depois que suas dependências estão saudáveis.

As versões usadas no Compose acompanham as versões já exercitadas nos testes de integração quando aplicável. Alta disponibilidade continua fora do escopo.

### Frontend e proxy

O frontend é compilado com Node e servido por Nginx. A aplicação usa a URL relativa `/api`, e o Nginx encaminha esse prefixo para `backend-api:8080`, removendo `/api` antes de alcançar os controllers Spring.

O proxy usa `proxy_request_buffering off`. Essa configuração é importante porque o backend foi desenhado para receber o multipart progressivamente; permitir que o proxy materializasse o upload inteiro antes do encaminhamento enfraqueceria a propriedade de streaming na borda da aplicação.

Rotas da SPA usam fallback para `index.html`, preservando acesso direto a `/imports/:id`.

### Porta pública

A execução padrão publica somente o frontend em `http://localhost:8080`. A API continua acessível pelo mesmo endereço sob `/api` por meio do Nginx.

PostgreSQL e RabbitMQ não são publicados no host por padrão porque não fazem parte da interface de uso do avaliador.

### Gerador de dataset

O serviço `dataset-generator` existe somente sob o profile `tools`. Ele não participa da topologia de runtime e não sobe com `docker compose up`.

Seu objetivo é permitir a geração do CSV de 1 milhão de linhas sem exigir Python instalado no host. Portanto, ele é uma ferramenta de entrega, não um novo componente arquitetural.

## Validação

O CI executa `docker compose config` e um smoke test da solução completa:

1. sobe PostgreSQL, RabbitMQ, API, Worker e frontend;
2. verifica a UI e o health endpoint através do Nginx;
3. envia um CSV pequeno via multipart;
4. aguarda o job alcançar estado terminal;
5. exige `COMPLETED`;
6. consulta analytics e transações pelo mesmo proxy;
7. derruba os containers e volumes do teste.

Esse smoke test valida integração e empacotamento, mas não substitui o benchmark de 1 milhão de linhas. O benchmark permanece separado porque precisa registrar hardware, limites de recursos, memória, throughput, latências e planos de execução em ambiente controlado.

## Alternativas consideradas

### API e Worker no mesmo processo

Rejeitado para a entrega padrão. Embora o código seja um monólito modular, o System Design representa funções de execução distintas e o volume temporário já permite separar recebimento e consumo sem duplicar regras.

### Dois projetos Spring independentes

Rejeitado. Duplicaria build, configuração e regras sem necessidade do desafio.

### Expor backend, PostgreSQL e RabbitMQ diretamente no host

Rejeitado como padrão. O frontend e `/api` são suficientes para uso e avaliação; portas adicionais aumentariam superfície e risco de conflito local.

### Buffering padrão do Nginx para uploads

Rejeitado. Poderia fazer o proxy receber o corpo completo antes de encaminhá-lo, contrariando a intenção de streaming de ponta a ponta.

## Relação com o System Design

O Compose apenas materializa componentes já previstos:

```text
Usuário -> React/Nginx -> Spring Boot API
Spring Boot API -> PostgreSQL
Spring Boot API -> Volume temporário
Spring Boot API -> RabbitMQ
RabbitMQ -> Spring Boot Worker
Volume temporário -> Spring Boot Worker
Spring Boot Worker -> PostgreSQL
Spring Boot Worker -> cleanup do volume
```

Não há mudança da topologia arquitetural.
