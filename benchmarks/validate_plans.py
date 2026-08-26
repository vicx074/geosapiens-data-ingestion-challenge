#!/usr/bin/env python3
"""Revalida planos de leitura após estabilizar estatísticas e visibility map do PostgreSQL."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import run_benchmark as benchmark


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("result_directory", type=Path)
    return parser.parse_args()


def main() -> None:
    arguments = parse_arguments()
    result_directory = arguments.result_directory.resolve()
    report_path = result_directory / "report.json"
    report = json.loads(report_path.read_text(encoding="utf-8"))

    job_id = str(report["ingestion"]["jobId"])
    deep_cursor = int(report["database"]["deepCursor"])

    # A manutenção ocorre fora da medição de ingestão. O objetivo aqui é avaliar o desenho de leitura
    # com estatísticas atualizadas e páginas elegíveis para index-only scan quando o PostgreSQL puder usá-lo.
    benchmark.psql("VACUUM (ANALYZE) transactions;")

    transaction_query = benchmark.transaction_sql(job_id, deep_cursor)
    analytics_query = benchmark.analytics_sql(job_id)
    files = {
        "transactionCurrent": "transaction-current-post-vacuum.txt",
        "transactionWithoutCursorIndex": "transaction-without-cursor-index-post-vacuum.txt",
        "analyticsCurrent": "analytics-current-post-vacuum.txt",
        "analyticsWithoutCoveringIndex": "analytics-without-covering-index-post-vacuum.txt",
    }

    benchmark.write_text(result_directory / files["transactionCurrent"], benchmark.explain(transaction_query))
    benchmark.write_text(
        result_directory / files["transactionWithoutCursorIndex"],
        benchmark.explain_without_index("idx_transactions_import_cursor", transaction_query),
    )
    benchmark.write_text(result_directory / files["analyticsCurrent"], benchmark.explain(analytics_query))
    benchmark.write_text(
        result_directory / files["analyticsWithoutCoveringIndex"],
        benchmark.explain_without_index("idx_transactions_analytics_by_import", analytics_query),
    )

    report["database"]["planValidationMaintenance"] = "VACUUM (ANALYZE) transactions"
    report["database"]["postVacuumPlans"] = files
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    summary_path = result_directory / "summary.md"
    with summary_path.open("a", encoding="utf-8") as summary:
        summary.write("\n\n## Validação pós-VACUUM/ANALYZE\n\n")
        summary.write(
            "Os quatro planos também foram coletados após `VACUUM (ANALYZE) transactions`, "
            "fora do tempo de ingestão, para atualizar estatísticas e permitir avaliar o covering index "
            "em condições compatíveis com index-only scan.\n"
        )

    print(json.dumps({"result": str(result_directory), "postVacuumPlans": files}, ensure_ascii=False))


if __name__ == "__main__":
    main()
