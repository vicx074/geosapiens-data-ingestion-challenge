# GeoSapiens Data Ingestion Challenge

Solução para ingestão, processamento e consulta de arquivos CSV com mais de um milhão de registros, sem carregar o arquivo completo em memória.

O projeto está em construção incremental. O primeiro marco define requisitos, decisões arquiteturais e critérios de validação antes da implementação.

## Objetivos verificáveis

- receber um CSV grande por upload em streaming;
- retornar `202 Accepted` com um identificador de acompanhamento;
- processar o arquivo de forma assíncrona;
- limitar memória, concorrência e tamanho dos lotes;
- persistir registros e erros com fronteiras transacionais explícitas;
- impedir duplicações causadas por redelivery;
- consultar milhões de registros por cursor e índices orientados às consultas;
- exibir progresso, agregações e uma lista eficiente no React;
- executar toda a solução com `docker compose up`.

## Arquitetura

A solução adota um monólito modular com duas funções de execução do backend: API e Worker. RabbitMQ desacopla o recebimento do arquivo de seu processamento, PostgreSQL armazena jobs e dados importados, e um volume Docker compartilha temporariamente o CSV entre API e Worker.

O [system design](docs/decisions/system-design-geosapiens.png) é a referência visual da topologia adotada. As decisões e alternativas estão registradas em [ARCHITECTURE.md](ARCHITECTURE.md) e em [docs/decisions](docs/decisions). A rastreabilidade do enunciado está em [docs/requirements.md](docs/requirements.md).

## Estado atual

Ainda não existe aplicação executável neste marco. Essa restrição é intencional: o esqueleto técnico será introduzido no próximo commit, separado das decisões que o justificam.
