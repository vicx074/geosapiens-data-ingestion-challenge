# Benchmark de larga escala

Este diretório contém o harness usado para validar a propriedade central do desafio: processar um CSV com **1.000.000 ou mais de linhas** sem transformar o tamanho total do arquivo em consumo equivalente de memória da aplicação.

Os resultados de referência já coletados estão em [`docs/performance.md`](../docs/performance.md). O diretório `benchmarks/results/` continua ignorado pelo Git para não transformar uma execução local em número universal.

## O que é medido

`run_benchmark.py` executa a solução real via Docker Compose e registra:

- SHA exato do código medido;
- SO, arquitetura, CPUs lógicas e memória visível no host;
- versões do Docker, Docker Compose e Python;
- limites de CPU e memória efetivamente aplicados aos containers;
- semente, quantidade de linhas, tamanho e SHA-256 do CSV;
- tempo até `202 Accepted`;
- tempo entre aceite e estado terminal;
- duração durável do Worker e vazão em linhas/s;
- pico de memória observado em API, Worker, PostgreSQL e RabbitMQ por `docker stats`;
- latências de status, primeira página, cursor profundo e analytics;
- tamanho dos índices persistidos relevantes;
- `EXPLAIN (ANALYZE, BUFFERS, VERBOSE, SETTINGS)` das consultas de paginação e analytics.

Para a paginação, o harness remove temporariamente `idx_transactions_import_cursor` dentro de uma transação e executa `ROLLBACK` após o plano. Para analytics, a solução final não mantém o covering index rejeitado pelo benchmark: o harness recria um candidato equivalente somente dentro da transação de medição e o remove automaticamente pelo `ROLLBACK`.

`validate_plans.py` executa uma segunda coleta após `VACUUM (ANALYZE) transactions`. Essa manutenção fica **fora do tempo de ingestão** e atualiza estatísticas/visibility map antes de comparar novamente os planos.

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

`compose.reference.yaml` aplica limites explícitos apenas para tornar a comparação mais reproduzível:

| Serviço | CPU | Memória |
|---|---:|---:|
| PostgreSQL | 1,0 | 768 MiB |
| RabbitMQ | 0,5 | 384 MiB |
| API | 1,0 | 512 MiB |
| Worker | 1,0 | 512 MiB |
| Frontend | 0,25 | 128 MiB |

Esses valores **não são requisitos de produção nem recomendações de capacity planning**. São parâmetros de um cenário versionado.

## Referência coletada no GitHub Actions

Durante o PR que introduziu o harness foi executada uma referência com 1M de linhas em runner hospedado pelo GitHub. O job pesado existiu somente para validar o harness e produzir a evidência inicial; ele foi removido do CI normal depois da coleta para não transformar cada PR em um benchmark caro e variável.

Um runner compartilhado não é tratado como benchmark de hardware controlado. Os números estão publicados em `docs/performance.md` sempre acompanhados do ambiente e das limitações da medição.

## Artefatos

Cada execução cria um diretório em `benchmarks/results/<timestamp>-<sha>/` com:

- `report.json` — contexto e medições estruturadas;
- `summary.md` — resumo legível;
- plano atual da paginação e variante sem o índice de cursor;
- plano atual de analytics e variante com o covering index candidato;
- planos equivalentes após `VACUUM (ANALYZE)`.

Resultados locais não são commitados automaticamente. Se uma nova decisão arquitetural for baseada em benchmark, o documento correspondente deve registrar ambiente, workload e limite da conclusão.
