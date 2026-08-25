# ADR 0006: Persistência dos jobs no PostgreSQL

- Status: aceito
- Data: 2026-08-25

## Contexto

API e Worker atualizam o mesmo job em momentos diferentes. O estado precisa sobreviver a reinicializações, permanecer consultável pelo frontend e rejeitar sobrescritas silenciosas quando duas execuções partem da mesma versão.

## Decisão

Persistir `IngestionJob` no PostgreSQL com migrations Flyway e um adaptador baseado em Spring JDBC. A porta da aplicação entrega o agregado acompanhado de uma versão técnica. Atualizações usam a versão lida como condição e incrementam o valor de forma atômica.

O domínio restaura somente combinações de estado, contadores e timestamps que poderiam ser produzidas por suas próprias transições. O banco também aplica constraints para impedir dados inválidos fora da aplicação.

A migration inicial possui apenas a chave primária usada pela consulta por `jobId`. Outros índices serão criados junto das consultas que os exigirem e avaliados com planos de execução.

Os testes de integração usam PostgreSQL 17.11 por Testcontainers. Essa versão principal está consolidada, recebe correções até 2029 e não há requisito do desafio que dependa das novidades do PostgreSQL 18.

## Alternativas rejeitadas

- JPA: esconderia parte do SQL sem beneficiar o modelo atual e não elimina a necessidade de JDBC explícito para batch inserts e consultas de agregação.
- H2 nos testes: não reproduz integralmente tipos, constraints, SQL e concorrência do PostgreSQL.
- Schema criado pela aplicação sem migrations: dificulta revisão, evolução e reprodução da estrutura.
- Lock pessimista em toda atualização: manteria conexões e linhas bloqueadas sem necessidade; a colisão esperada é excepcional e deve ser detectada.
- Índices preventivos por estado ou data: aumentariam o custo de escrita antes da existência de uma consulta que os utilizasse.

## Consequências

Uma atualização concorrente falha explicitamente e o caso de uso decide se deve reler ou encerrar a operação. O SQL permanece visível para revisão e otimização. Os testes de integração exigem Docker, mas validam o mesmo banco utilizado pela aplicação.

## Referências

- [Spring Framework JDBC](https://docs.spring.io/spring-framework/reference/data-access/jdbc/core.html)
- [Migrations de banco no Spring Boot](https://docs.spring.io/spring-boot/how-to/data-initialization.html)
- [Testcontainers no Spring Boot](https://docs.spring.io/spring-boot/reference/testing/testcontainers.html)
- [Política de versões do PostgreSQL](https://www.postgresql.org/support/versioning/)
