# ADR 0005: Máquina de estados do job

- Status: aceito
- Data: 2026-08-23

## Contexto

Upload, publicação, processamento, redelivery e falhas podem atualizar o mesmo job em momentos diferentes. Strings livres e transições implícitas permitiriam estados contraditórios.

## Decisão

Modelar estados e transições no domínio. O fluxo nominal será `RECEIVED`, `QUEUED`, `PROCESSING` e um dos estados terminais `COMPLETED`, `COMPLETED_WITH_ERRORS` ou `FAILED`.

Estados terminais serão imutáveis para redelivery. Contadores serão persistidos por lote, junto com os dados correspondentes. Concorrência e transições inválidas serão testadas.

## Alternativas rejeitadas

- Booleanos independentes: permitem combinações inválidas.
- Estado controlado apenas pelo RabbitMQ: não oferece consulta durável para a API.
- Atualização de progresso por linha: cria escrita excessiva e reduz a vazão.

## Consequências

O domínio ganha regras explícitas e auditáveis. A interface pode observar progresso ligeiramente defasado em até um lote, compromisso necessário para evitar escrita por linha.
