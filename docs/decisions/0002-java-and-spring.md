# ADR 0002: Java LTS e Spring Boot

- Status: aceito
- Data: 2026-08-23

## Contexto

Java e Spring Boot são obrigatórios. A máquina do avaliador não deve precisar de dependências locais porque a execução ocorrerá em containers.

## Decisão

Usar Eclipse Temurin Java 21, uma versão LTS consolidada, e uma versão estável e fixada do Spring Boot compatível com ela. O Maven Wrapper permitirá comandos reproduzíveis fora do container quando desejado.

A versão exata do Spring Boot será registrada no commit de bootstrap, após validação na documentação oficial. Imagens e dependências não usarão a tag `latest`.

## Alternativas rejeitadas

- Java 17: compatível, porém uma base LTS anterior sem necessidade de compatibilidade legada.
- Java 25: LTS atual, mas suas novidades não resolvem um requisito deste desafio e aumentam a novidade da stack.
- Java instalado como pré-requisito: violaria a expectativa de execução integral por Docker Compose.

## Consequências

O projeto privilegia estabilidade e familiaridade para avaliação. Atualizações de patch continuarão deliberadas e reproduzíveis.
