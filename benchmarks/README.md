# Benchmark de larga escala

Este diretório contém o harness usado para validar a propriedade central do desafio: processar um CSV com **1.000.000 ou mais de linhas** sem transformar o tamanho total do arquivo em consumo equivalente de memória da aplicação.

## O que é medido

`run_benchmark.py` executa a solução real via Docker Compose e registra:

- SHA exato do código;
- SO, arquitetura, CPUs lógicas e memória visível no host;
- versões do Docker, Docker Compose e Python;
- limites de CPU e memória efetivamente aplicados aos containers;
- semente, quantidade de linhas, tamanho e SHA-256 do CSV;
- tempo até `202 Accepted`;
- tempo entre aceite e estado terminal;
- duração durável do Worker e vazão em linhas/s;
- pico de memória observado em API, Worker, PostgreSQL e RabbitMQ por `docker stats`;
- latências de status, primeira página, cursor profundo e analytics;
- tamanho dos índices relevantes;
- `EXPLAIN (ANALYZE, BUFFERS, VERBOSE, SETTINGS)` das consultas de paginação e analytics.

A comparação sem os índices dedicados usa `DROP INDEX` dentro de uma transação e termina com `ROLLBACK`. A estrutura persistente do banco não é alterada pela comparação.

`validate_plans.py` executa uma segunda coleta dos quatro planos após `VACUUM (ANALYZE) transactions`. Essa manutenção fica **fora do tempo de ingestão** e serve para atualizar estatísticas e o visibility map antes de avaliar o covering index de analytics.

## Dataset

O próprio harness chama `tools/generate_dataset.py`. O gerador escreve progressivamente e usa por padrão:

- 1.000.000 linhas;
- seed `42`;
- somente registros válidos;
- cabeçalho `transaction_id,occurred_at,amount,category`.

Não é necessário manter o CSV gerado no Git.

## Execução recomendada

No diretório raiz do projeto, com Docker disponível:

```bash
export COMPOSE_FILE=compose.yaml:benchmarks/compose.reference.yaml
python benchmarks/run_benchmark.py --rows 1000000 --keep-compose
RESULT_DIR="$(find benchmarks/results -mindepth 1 -maxdepth 1 -type d | sort | tail -n 1)"
python benchmarks/validate_plans.py "$RESULT_DIR"
docker compose down --volumes --remove-orphans
```

No PowerShell:

```powershell
$env:COMPOSE_FILE = "compose.yaml;benchmarks/compose.reference.yaml"
python benchmarks/run_benchmark.py --rows 1000000 --keep-compose
$ResultDir = Get-ChildItem benchmarks/results -Directory | Sort-Object Name | Select-Object -Last 1
python benchmarks/validate_plans.py $ResultDir.FullName
docker compose down --volumes --remove-orphans
```

> O separador de `COMPOSE_FILE` é `:` em Linux/macOS e `;` no Windows.

## Perfil de referência

`compose.reference.yaml` aplica limites explícitos apenas para tornar uma execução de comparação mais reproduzível:

| Serviço | CPU | Memória |
|---|---:|---:|
| PostgreSQL | 1,0 | 768 MiB |
| RabbitMQ | 0,5 | 384 MiB |
| API | 1,0 | 512 MiB |
| Worker | 1,0 | 512 MiB |
| Frontend | 0,25 | 128 MiB |

Esses valores **não são requisitos de produção nem recomendações de capacity planning**. São apenas parâmetros de um cenário de medição versionado.

## Execução de referência no GitHub Actions

O PR que introduziu o harness executa temporariamente uma referência com 1M de linhas em runner hospedado pelo GitHub. Ela prova que o harness, o Compose e as medições funcionam juntos e ajuda a encontrar regressões óbvias.

Um runner compartilhado não é tratado como benchmark oficial porque a carga do host físico não é controlada pelo projeto. Números provenientes dessa execução devem ser descritos como **referência**, nunca como capacidade garantida do sistema.

## Artefatos

Cada execução cria um diretório em `benchmarks/results/<timestamp>-<sha>/` com:

- `report.json` — contexto e medições estruturadas;
- `summary.md` — resumo legível;
- planos atuais e sem os índices dedicados;
- planos equivalentes após `VACUUM (ANALYZE)`.

`benchmarks/results/` permanece no `.gitignore` para não versionar resultados locais como se fossem universais. O relatório final do projeto só deve publicar números acompanhados do ambiente em que foram medidos.
