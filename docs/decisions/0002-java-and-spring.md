# ADR 0002: Java LTS e Spring Boot

- Status: aceito
- Data: 2026-08-23

## Contexto

Java e Spring Boot são obrigatórios. A máquina do avaliador não deve precisar de dependências locais porque a execução ocorrerá em containers.

## Decisão

Usar Eclipse Temurin Java 21, uma versão LTS consolidada, e Spring Boot 3.5.16. Essa linha do Spring Boot continua mantida, é compatível com Java 21 e evita adotar Jakarta EE 11, Spring Framework 7 e Jackson 3 sem uma necessidade do desafio. O Maven Wrapper permitirá comandos reproduzíveis fora do container quando desejado.

Imagens e dependências não usarão a tag `latest`.

## Alternativas rejeitadas

- Java 17: compatível, porém uma base LTS anterior sem necessidade de compatibilidade legada.
- Java 25: LTS atual, mas suas novidades não resolvem um requisito deste desafio e aumentam a novidade da stack.
- Java instalado como pré-requisito: violaria a expectativa de execução integral por Docker Compose.

## Consequências

O projeto privilegia estabilidade e familiaridade para avaliação. Atualizações de patch continuarão deliberadas e reproduzíveis.
