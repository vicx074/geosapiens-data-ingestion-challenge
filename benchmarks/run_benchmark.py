#!/usr/bin/env python3
"""Executa benchmark reproduzível de ingestão e consultas da solução GeoSapiens."""

from __future__ import annotations

import argparse
import http.client
import json
import math
import os
import platform
import statistics
import subprocess
import sys
import threading
import time
import uuid
from dataclasses import dataclass, field
from datetime import UTC, datetime
from pathlib import Path
from typing import Any
from urllib.parse import urlparse
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_DATASET = ROOT / "data" / "generated" / "transactions-benchmark.csv"
DEFAULT_RESULTS = ROOT / "benchmarks" / "results"
SERVICES_TO_SAMPLE = ("backend-api", "backend-worker", "postgres", "rabbitmq")
TERMINAL_STATUSES = {"COMPLETED", "COMPLETED_WITH_ERRORS", "FAILED"}


class BenchmarkError(RuntimeError):
    """Falha que invalida a execução e não deve produzir conclusão de performance."""


def run_command(
    *command: str,
    cwd: Path = ROOT,
    capture: bool = True,
    check: bool = True,
) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        command,
        cwd=cwd,
        check=check,
        text=True,
        capture_output=capture,
    )


def percentile(values: list[float], quantile: float) -> float:
    if not values:
        raise ValueError("A amostra de latência não pode ser vazia.")
    if not 0 <= quantile <= 1:
        raise ValueError("O quantil deve estar entre zero e um.")
    ordered = sorted(values)
    return ordered[max(0, math.ceil(quantile * len(ordered)) - 1)]


def latency_summary(values: list[float]) -> dict[str, float | int]:
    return {
        "samples": len(values),
        "minMs": round(min(values), 3),
        "p50Ms": round(statistics.median(values), 3),
        "p95Ms": round(percentile(values, 0.95), 3),
        "maxMs": round(max(values), 3),
    }


def parse_memory_bytes(value: str) -> int:
    token = value.strip().split()[0]
    units = {
        "B": 1,
        "kB": 1000,
        "KB": 1000,
        "KiB": 1024,
        "MB": 1000**2,
        "MiB": 1024**2,
        "GB": 1000**3,
        "GiB": 1024**3,
        "TB": 1000**4,
        "TiB": 1024**4,
    }
    for unit in sorted(units, key=len, reverse=True):
        if token.endswith(unit):
            return int(float(token[: -len(unit)]) * units[unit])
    raise ValueError(f"Unidade de memória não reconhecida: {value}")


def host_memory_bytes() -> int | None:
    if os.name == "nt":
        try:
            import ctypes

            class MemoryStatus(ctypes.Structure):
                _fields_ = [
                    ("dwLength", ctypes.c_ulong),
                    ("dwMemoryLoad", ctypes.c_ulong),
                    ("ullTotalPhys", ctypes.c_ulonglong),
                    ("ullAvailPhys", ctypes.c_ulonglong),
                    ("ullTotalPageFile", ctypes.c_ulonglong),
                    ("ullAvailPageFile", ctypes.c_ulonglong),
                    ("ullTotalVirtual", ctypes.c_ulonglong),
                    ("ullAvailVirtual", ctypes.c_ulonglong),
                    ("ullAvailExtendedVirtual", ctypes.c_ulonglong),
                ]

            status = MemoryStatus()
            status.dwLength = ctypes.sizeof(MemoryStatus)
            if ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(status)):
                return int(status.ullTotalPhys)
        except (AttributeError, OSError):
            return None
        return None

    try:
        return int(os.sysconf("SC_PAGE_SIZE") * os.sysconf("SC_PHYS_PAGES"))
    except (AttributeError, OSError, ValueError):
        return None


def http_get_json(url: str, timeout: float = 30) -> dict[str, Any]:
    request = Request(url, headers={"Accept": "application/json"})
    with urlopen(request, timeout=timeout) as response:
        payload = response.read()
        if response.status >= 400:
            raise BenchmarkError(f"GET {url} retornou HTTP {response.status}.")
        return json.loads(payload)


def stream_multipart_file(url: str, file_path: Path, timeout: float = 300) -> dict[str, Any]:
    parsed = urlparse(url)
    if parsed.scheme not in {"http", "https"}:
        raise BenchmarkError("O benchmark suporta somente URLs HTTP/HTTPS.")

    boundary = f"----geosapiens-benchmark-{uuid.uuid4().hex}"
    prefix = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="file"; filename="{file_path.name}"\r\n'
        "Content-Type: text/csv\r\n\r\n"
    ).encode("utf-8")
    suffix = f"\r\n--{boundary}--\r\n".encode("ascii")
    content_length = len(prefix) + file_path.stat().st_size + len(suffix)

    connection_class = http.client.HTTPSConnection if parsed.scheme == "https" else http.client.HTTPConnection
    connection = connection_class(parsed.hostname, parsed.port or (443 if parsed.scheme == "https" else 80), timeout=timeout)
    path = parsed.path or "/"
    if parsed.query:
        path = f"{path}?{parsed.query}"

    try:
        connection.putrequest("POST", path)
        connection.putheader("Content-Type", f"multipart/form-data; boundary={boundary}")
        connection.putheader("Content-Length", str(content_length))
        connection.putheader("Accept", "application/json")
        connection.endheaders()
        connection.send(prefix)
        with file_path.open("rb") as source:
            while chunk := source.read(1024 * 1024):
                connection.send(chunk)
        connection.send(suffix)
        response = connection.getresponse()
        body = response.read()
        if response.status >= 400:
            raise BenchmarkError(
                f"POST {url} retornou HTTP {response.status}: {body.decode('utf-8', errors='replace')}"
            )
        return json.loads(body)
    finally:
        connection.close()


def compose(*arguments: str, capture: bool = True) -> subprocess.CompletedProcess[str]:
    return run_command("docker", "compose", *arguments, capture=capture)


def container_id(service: str) -> str:
    identifier = compose("ps", "-q", service).stdout.strip()
    if not identifier:
        raise BenchmarkError(f"O container do serviço {service} não está em execução.")
    return identifier


def container_limits(identifier: str) -> dict[str, Any]:
    result = run_command("docker", "inspect", "--format", "{{json .HostConfig}}", identifier)
    config = json.loads(result.stdout)
    nano_cpus = int(config.get("NanoCpus") or 0)
    cpu_quota = int(config.get("CpuQuota") or 0)
    cpu_period = int(config.get("CpuPeriod") or 0)
    cpu_limit: float | None = None
    if nano_cpus > 0:
        cpu_limit = nano_cpus / 1_000_000_000
    elif cpu_quota > 0 and cpu_period > 0:
        cpu_limit = cpu_quota / cpu_period
    memory_limit = int(config.get("Memory") or 0)
    return {"memoryBytes": memory_limit or None, "cpus": cpu_limit}


@dataclass
class MemorySampler:
    identifiers: dict[str, str]
    interval_seconds: float
    peaks: dict[str, int] = field(default_factory=dict)
    samples: int = 0
    errors: list[str] = field(default_factory=list)
    _stop: threading.Event = field(default_factory=threading.Event)
    _thread: threading.Thread | None = None

    def start(self) -> None:
        self.peaks = {service: 0 for service in self.identifiers}
        self._thread = threading.Thread(target=self._run, name="docker-memory-sampler", daemon=True)
        self._thread.start()

    def stop(self) -> None:
        self._stop.set()
        if self._thread is not None:
            self._thread.join(timeout=max(5, self.interval_seconds * 3))

    def _run(self) -> None:
        ids = list(self.identifiers.values())
        by_id = {identifier: service for service, identifier in self.identifiers.items()}
        by_prefix = {identifier[:12]: service for service, identifier in self.identifiers.items()}
        while not self._stop.is_set():
            try:
                result = run_command("docker", "stats", "--no-stream", "--format", "{{json .}}", *ids)
                seen = False
                for line in result.stdout.splitlines():
                    if not line.strip():
                        continue
                    item = json.loads(line)
                    identifier = str(item.get("ID") or "")
                    service = by_id.get(identifier) or by_prefix.get(identifier[:12])
                    if service is None:
                        continue
                    usage = str(item.get("MemUsage") or "").split("/")[0].strip()
                    self.peaks[service] = max(self.peaks[service], parse_memory_bytes(usage))
                    seen = True
                if seen:
                    self.samples += 1
            except (subprocess.CalledProcessError, json.JSONDecodeError, ValueError) as exc:
                self.errors.append(str(exc))
            self._stop.wait(self.interval_seconds)


def timed_gets(url: str, samples: int) -> dict[str, float | int]:
    # A chamada de aquecimento não entra na amostra para reduzir ruído de inicialização.
    http_get_json(url)
    values: list[float] = []
    for _ in range(samples):
        started = time.perf_counter()
        http_get_json(url)
        values.append((time.perf_counter() - started) * 1000)
    return latency_summary(values)


def psql(sql: str) -> str:
    return compose(
        "exec", "-T", "postgres", "psql", "-X", "-v", "ON_ERROR_STOP=1",
        "-U", "geosapiens", "-d", "geosapiens", "-P", "pager=off", "-c", sql,
    ).stdout


def psql_scalar(sql: str) -> str:
    return compose(
        "exec", "-T", "postgres", "psql", "-X", "-q", "-t", "-A",
        "-v", "ON_ERROR_STOP=1", "-U", "geosapiens", "-d", "geosapiens", "-c", sql,
    ).stdout.strip()


def transaction_sql(import_id: str, after_id: int) -> str:
    return f"""
SELECT id, source_row, transaction_id, occurred_at, amount, category
FROM transactions
WHERE import_id = '{import_id}'::uuid
  AND id > {after_id}
ORDER BY id
LIMIT 101
""".strip()


def analytics_sql(import_id: str) -> str:
    return f"""
SELECT
    CASE
        WHEN GROUPING(category) = 0 THEN 'CATEGORY'
        WHEN GROUPING(month_start) = 0 THEN 'MONTH'
        ELSE 'TOTAL'
    END AS aggregation_type,
    category,
    month_start,
    COUNT(*) AS transaction_count,
    COALESCE(SUM(amount), 0::numeric) AS total_amount
FROM (
    SELECT
        category,
        DATE_TRUNC('month', occurred_at AT TIME ZONE 'UTC')::date AS month_start,
        amount
    FROM transactions
    WHERE import_id = '{import_id}'::uuid
) filtered_transactions
GROUP BY GROUPING SETS ((), (category), (month_start))
""".strip()


def explain(sql: str) -> str:
    return psql(f"EXPLAIN (ANALYZE, BUFFERS, VERBOSE, SETTINGS) {sql};")


def explain_without_index(index_name: str, sql: str) -> str:
    # DDL é transacional no PostgreSQL; ROLLBACK restaura o estado original após a comparação.
    return psql(
        "BEGIN;\n"
        f"DROP INDEX {index_name};\n"
        f"EXPLAIN (ANALYZE, BUFFERS, VERBOSE, SETTINGS) {sql};\n"
        "ROLLBACK;"
    )


def explain_with_analytics_covering_candidate(sql: str) -> str:
    # O candidato rejeitado é recriado só durante a medição para que o benchmark continue reproduzível
    # sem obrigar a aplicação a pagar seu custo de escrita e armazenamento no runtime final.
    return psql(
        "BEGIN;\n"
        "CREATE INDEX idx_benchmark_analytics_covering_candidate "
        "ON transactions (import_id) INCLUDE (category, occurred_at, amount);\n"
        f"EXPLAIN (ANALYZE, BUFFERS, VERBOSE, SETTINGS) {sql};\n"
        "ROLLBACK;"
    )


def index_sizes() -> dict[str, int]:
    names = ("idx_transactions_import_cursor", "uq_transactions_import_source_row")
    quoted = ", ".join(f"'{name}'" for name in names)
    raw = psql_scalar(
        "SELECT COALESCE(json_object_agg(relname, pg_relation_size(oid)), '{}'::json)::text "
        f"FROM pg_class WHERE relname IN ({quoted});"
    )
    return {key: int(value) for key, value in json.loads(raw).items()}


def iso_duration_seconds(start: str | None, end: str | None) -> float | None:
    if not start or not end:
        return None
    start_at = datetime.fromisoformat(start.replace("Z", "+00:00"))
    end_at = datetime.fromisoformat(end.replace("Z", "+00:00"))
    return (end_at - start_at).total_seconds()


def write_text(path: Path, value: str) -> None:
    path.write_text(value.rstrip() + "\n", encoding="utf-8")


def write_summary(directory: Path, report: dict[str, Any]) -> None:
    ingestion = report["ingestion"]
    dataset = report["dataset"]
    lines = [
        "# Resultado do benchmark",
        "",
        f"- SHA medido: `{report['git']['sha']}`",
        f"- Momento (UTC): `{report['generatedAt']}`",
        f"- Dataset: {dataset['rows']:,} linhas / {dataset['sizeBytes']:,} bytes / seed {dataset['seed']}",
        f"- Upload até `202 Accepted`: {ingestion['uploadSeconds']:.3f} s",
        f"- `202 Accepted` até terminal: {ingestion['acceptedToTerminalSeconds']:.3f} s",
        f"- Worker (`startedAt` → `finishedAt`): {ingestion['workerDurationSeconds']:.3f} s",
        f"- Vazão pelo tempo do Worker: {ingestion['workerRowsPerSecond']:.2f} linhas/s",
        "",
        "## Pico de memória observado",
        "",
    ]
    for service, peak in report["memory"]["peakBytes"].items():
        lines.append(f"- `{service}`: {peak:,} bytes")
    lines.extend(["", "## Latência HTTP", ""])
    for name, values in report["apiLatencyMs"].items():
        lines.append(
            f"- `{name}`: p50 {values['p50Ms']:.3f} ms · p95 {values['p95Ms']:.3f} ms · max {values['maxMs']:.3f} ms"
        )
    lines.extend([
        "",
        "## Planos PostgreSQL",
        "",
        "- `transaction-current.txt`",
        "- `transaction-without-cursor-index.txt`",
        "- `analytics-current.txt`",
        "- `analytics-with-covering-candidate.txt`",
        "",
        "O `report.json` contém ambiente, limites, índices persistidos e medições estruturadas.",
    ])
    write_text(directory / "summary.md", "\n".join(lines))


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--rows", type=int, default=1_000_000)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--dataset", type=Path, default=DEFAULT_DATASET)
    parser.add_argument("--results", type=Path, default=DEFAULT_RESULTS)
    parser.add_argument("--api-base", default="http://localhost:8080/api")
    parser.add_argument("--latency-samples", type=int, default=10)
    parser.add_argument("--stats-interval", type=float, default=1.0)
    parser.add_argument("--processing-timeout", type=float, default=1800.0)
    parser.add_argument("--keep-compose", action="store_true")
    arguments = parser.parse_args()
    if arguments.rows < 1_000_000:
        parser.error("O benchmark oficial deve usar pelo menos 1.000.000 de linhas.")
    if arguments.latency_samples < 3:
        parser.error("Use pelo menos três amostras de latência.")
    if arguments.stats_interval <= 0:
        parser.error("O intervalo de amostragem de memória deve ser positivo.")
    return arguments


def main() -> None:
    arguments = parse_arguments()
    dataset = arguments.dataset.resolve()
    results_root = arguments.results.resolve()
    api_base = arguments.api_base.rstrip("/")
    git_sha = run_command("git", "rev-parse", "HEAD").stdout.strip()
    timestamp = datetime.now(UTC).strftime("%Y%m%dT%H%M%SZ")
    report_directory = results_root / f"{timestamp}-{git_sha[:8]}"
    report_directory.mkdir(parents=True, exist_ok=False)

    generator = run_command(
        sys.executable, str(ROOT / "tools" / "generate_dataset.py"),
        "--rows", str(arguments.rows), "--seed", str(arguments.seed),
        "--output", str(dataset), "--force",
    )
    dataset_info = json.loads(generator.stdout)
    dataset_info["sizeBytes"] = dataset.stat().st_size

    sampler: MemorySampler | None = None
    compose_started = False
    try:
        # O reset impede que dados e cache lógico de uma execução anterior contaminem a medição.
        compose("down", "--volumes", "--remove-orphans", capture=False)
        compose("up", "--build", "-d", "--wait", "--wait-timeout", "240", capture=False)
        compose_started = True
        identifiers = {service: container_id(service) for service in SERVICES_TO_SAMPLE}
        limits = {service: container_limits(identifier) for service, identifier in identifiers.items()}

        sampler = MemorySampler(identifiers, arguments.stats_interval)
        sampler.start()
        upload_started = time.perf_counter()
        accepted = stream_multipart_file(f"{api_base}/imports", dataset)
        upload_seconds = time.perf_counter() - upload_started
        job_id = str(accepted["jobId"])

        accepted_at = time.perf_counter()
        deadline = accepted_at + arguments.processing_timeout
        status: dict[str, Any] | None = None
        while time.perf_counter() < deadline:
            status = http_get_json(f"{api_base}/imports/{job_id}")
            if status.get("status") in TERMINAL_STATUSES and status.get("terminal") is True:
                break
            time.sleep(1)
        else:
            raise BenchmarkError(f"A importação {job_id} não terminou no tempo limite.")
        accepted_to_terminal = time.perf_counter() - accepted_at
        sampler.stop()

        if status is None or status.get("status") != "COMPLETED":
            raise BenchmarkError(f"O dataset válido terminou em estado inesperado: {status and status.get('status')}.")
        if int(status.get("processedRows", -1)) != arguments.rows:
            raise BenchmarkError("O total processado não corresponde ao dataset medido.")
        if int(status.get("acceptedRows", -1)) != arguments.rows or int(status.get("rejectedRows", -1)) != 0:
            raise BenchmarkError("O benchmark de throughput deve conter somente linhas válidas.")

        worker_duration = iso_duration_seconds(status.get("startedAt"), status.get("finishedAt"))
        if worker_duration is None or worker_duration <= 0:
            raise BenchmarkError("Os timestamps duráveis não permitiram calcular a duração do Worker.")

        deep_cursor = int(psql_scalar(
            "SELECT percentile_disc(0.90) WITHIN GROUP (ORDER BY id)::bigint "
            f"FROM transactions WHERE import_id = '{job_id}'::uuid;"
        ))
        latency = {
            "status": timed_gets(f"{api_base}/imports/{job_id}", arguments.latency_samples),
            "transactionsFirstPage": timed_gets(
                f"{api_base}/imports/{job_id}/transactions?limit=100", arguments.latency_samples
            ),
            "transactionsDeepCursor": timed_gets(
                f"{api_base}/imports/{job_id}/transactions?limit=100&after={deep_cursor}", arguments.latency_samples
            ),
            "analytics": timed_gets(f"{api_base}/imports/{job_id}/analytics", arguments.latency_samples),
        }

        transaction_query = transaction_sql(job_id, deep_cursor)
        analytics_query = analytics_sql(job_id)
        write_text(report_directory / "transaction-current.txt", explain(transaction_query))
        write_text(
            report_directory / "transaction-without-cursor-index.txt",
            explain_without_index("idx_transactions_import_cursor", transaction_query),
        )
        write_text(report_directory / "analytics-current.txt", explain(analytics_query))
        write_text(
            report_directory / "analytics-with-covering-candidate.txt",
            explain_with_analytics_covering_candidate(analytics_query),
        )

        report: dict[str, Any] = {
            "schemaVersion": 2,
            "generatedAt": datetime.now(UTC).isoformat(),
            "git": {"sha": git_sha},
            "host": {
                "platform": platform.platform(),
                "machine": platform.machine(),
                "processor": platform.processor() or None,
                "logicalCpus": os.cpu_count(),
                "memoryBytes": host_memory_bytes(),
            },
            "runtime": {
                "python": platform.python_version(),
                "docker": run_command("docker", "--version").stdout.strip(),
                "dockerCompose": run_command("docker", "compose", "version").stdout.strip(),
                "containerLimits": limits,
                "worker": {
                    "concurrency": 2,
                    "prefetch": 1,
                    "batchSize": 1000,
                    "csvMaxRecordCharacters": 4096,
                },
            },
            "dataset": dataset_info,
            "ingestion": {
                "jobId": job_id,
                "status": status["status"],
                "uploadSeconds": round(upload_seconds, 6),
                "acceptedToTerminalSeconds": round(accepted_to_terminal, 6),
                "workerDurationSeconds": round(worker_duration, 6),
                "workerRowsPerSecond": round(arguments.rows / worker_duration, 3),
                "processedRows": int(status["processedRows"]),
                "acceptedRows": int(status["acceptedRows"]),
                "rejectedRows": int(status["rejectedRows"]),
            },
            "memory": {
                "statsIntervalSeconds": arguments.stats_interval,
                "samples": sampler.samples,
                "peakBytes": sampler.peaks,
                "samplingErrors": sampler.errors,
            },
            "apiLatencyMs": latency,
            "database": {
                "deepCursor": deep_cursor,
                "persistentIndexSizesBytes": index_sizes(),
                "plans": {
                    "transactionCurrent": "transaction-current.txt",
                    "transactionWithoutCursorIndex": "transaction-without-cursor-index.txt",
                    "analyticsCurrent": "analytics-current.txt",
                    "analyticsWithCoveringCandidate": "analytics-with-covering-candidate.txt",
                },
            },
        }
        (report_directory / "report.json").write_text(
            json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
        write_summary(report_directory, report)
        print(json.dumps({"result": str(report_directory), "jobId": job_id}, ensure_ascii=False))
    finally:
        if sampler is not None:
            sampler.stop()
        if compose_started and not arguments.keep_compose:
            compose("down", "--volumes", "--remove-orphans", capture=False)


if __name__ == "__main__":
    main()
