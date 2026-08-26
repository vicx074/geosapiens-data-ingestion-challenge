# ADR 0008: GitHub Actions para integração contínua

- Status: aceito
- Data: 2026-08-25

## Contexto

O projeto será entregue em um repositório GitHub e possui testes de integração que iniciam PostgreSQL e RabbitMQ reais com Testcontainers. O ambiente local atual não oferece virtualização, portanto não consegue executar esses containers. Depender apenas de validação manual deixaria migrations e integração com mensageria sem um gate reproduzível a cada commit.

## Decisão

Executar a integração contínua no GitHub Actions em `ubuntu-24.04`, imagem com Docker disponível para o Testcontainers. O backend usará Temurin 21, a mesma versão principal definida no projeto, e executará `mvnw verify`. O gerador de dados terá um job independente com Python 3.13, testes unitários e geração de uma amostra determinística de 10 mil linhas.

Os jobs são separados porque uma falha no gerador não precisa esperar os containers do backend e porque suas dependências são independentes. O cache nativo do `setup-java` será limitado pelo `pom.xml`; artefatos de build não serão reutilizados como evidência entre jobs.

A CI valida correção funcional, não desempenho. O benchmark de mais de um milhão de registros será executado em ambiente com recursos fixados e relatório próprio, pois tempos obtidos em runners compartilhados não justificam tamanho de lote, concorrência ou índices.

## Alternativas rejeitadas

- Validar somente na máquina do desenvolvedor: não executa Testcontainers no ambiente atual e não protege commits futuros.
- Adotar outro provedor de CI: criaria uma segunda integração e outra credencial sem requisito que compense isso.
- Declarar PostgreSQL e RabbitMQ como services do workflow: duplicaria versões e ciclo de vida já controlados pelos testes Testcontainers.
- Executar o benchmark oficial na CI: runners compartilhados não fornecem estabilidade de hardware suficiente para decisões de desempenho.

## Consequências

Falhas de compilação, migrations e integrações serão visíveis no próprio commit. A indisponibilidade do GitHub Actions continua sendo uma dependência da validação, não da execução local da aplicação nem do system design.

## Referências

- [GitHub Actions para Java com Maven](https://docs.github.com/actions/use-cases-and-examples/building-and-testing/building-and-testing-java-with-maven)
- [Configuração oficial do `setup-java`](https://github.com/actions/setup-java)
- [Software instalado no runner Ubuntu 24.04](https://github.com/actions/runner-images/blob/main/images/ubuntu/Ubuntu2404-Readme.md)
