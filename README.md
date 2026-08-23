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

## Backend

O backend utiliza Java 21 e Spring Boot 3.5.16. Com uma JDK 21 disponível, os testes podem ser executados sem instalar uma versão global do Maven:

```bash
cd backend
./mvnw test
```

No Windows:

```powershell
cd backend
.\mvnw.cmd test
```

A execução integral por Docker Compose será adicionada junto aos serviços previstos no system design. Até esse marco, a existência do Maven Wrapper não transforma Java instalado em requisito da entrega final.

## Dataset

O contrato das colunas está em [docs/data-contract.md](docs/data-contract.md). O gerador usa somente a biblioteca padrão do Python, escreve uma linha por vez e produz por padrão 1 milhão de registros determinísticos:

```bash
python tools/generate_dataset.py
```

É possível criar um arquivo menor para desenvolvimento:

```bash
python tools/generate_dataset.py --rows 10000 --output data/generated/transactions-10000.csv
```

O comando informa quantidade, semente e SHA-256. A execução do gerador também será disponibilizada por container para preservar o requisito plug-and-play da entrega final.
