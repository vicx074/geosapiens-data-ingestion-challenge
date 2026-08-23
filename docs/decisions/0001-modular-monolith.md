# ADR 0001: Monólito modular com API e Worker

- Status: aceito
- Data: 2026-08-23

## Contexto

API e processamento assíncrono precisam compartilhar regras do job, contratos de persistência e validação. O desafio não exige implantação ou evolução independente por equipes diferentes.

## Decisão

Manter um único backend organizado por módulos funcionais e executá-lo em duas funções: API e Worker. As fronteiras internas seguirão domínio, aplicação e adaptadores.

## Alternativas rejeitadas

- Microservices: adicionariam contratos distribuídos, builds e operação sem uma necessidade do escopo.
- Aplicação síncrona única: manteria o processamento pesado no ciclo HTTP e contrariaria o requisito assíncrono.
- Projetos independentes para API e Worker: duplicariam modelos e configuração antes de existir independência necessária.

## Consequências

O código permanece coeso e testável, enquanto API e Worker podem ter limites de recursos e escala distintos no Compose. Uma mudança compartilhada exige reconstruir o mesmo artefato.
