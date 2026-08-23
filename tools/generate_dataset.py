#!/usr/bin/env python3
"""Gera um CSV financeiro determinístico sem acumular registros em memória."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import os
import tempfile
from datetime import UTC, datetime, timedelta
from pathlib import Path


DEFAULT_ROWS = 1_000_000
DEFAULT_SEED = 42
DEFAULT_OUTPUT = Path("data/generated/transactions-1000000.csv")
START_AT = datetime(2024, 1, 1, tzinfo=UTC)
PERIOD_IN_SECONDS = 731 * 24 * 60 * 60
CATEGORIES = (
    "alimentação",
    "transporte",
    "moradia",
    "saúde",
    "educação",
    "lazer",
    "serviços",
    "outros",
)


def positive_integer(value: str) -> int:
    parsed = int(value)
    if parsed <= 0:
        raise argparse.ArgumentTypeError("O valor deve ser maior que zero.")
    return parsed


def row_values(row_number: int, seed: int) -> tuple[str, str, str, str]:
    digest = hashlib.blake2b(
        f"{seed}:{row_number}".encode("ascii"), digest_size=16
    ).digest()
    timestamp_offset = int.from_bytes(digest[0:8], "big") % PERIOD_IN_SECONDS
    amount_in_cents = int.from_bytes(digest[8:12], "big") % 2_000_001 - 1_000_000
    category_index = int.from_bytes(digest[12:16], "big") % len(CATEGORIES)

    if amount_in_cents == 0:
        amount_in_cents = 1

    occurred_at = (START_AT + timedelta(seconds=timestamp_offset)).isoformat()
    absolute_cents = abs(amount_in_cents)
    amount_sign = "-" if amount_in_cents < 0 else ""
    amount = f"{amount_sign}{absolute_cents // 100}.{absolute_cents % 100:02d}"
    return (
        f"txn-{row_number:012d}",
        occurred_at.replace("+00:00", "Z"),
        amount,
        CATEGORIES[category_index],
    )


def generate(output: Path, rows: int, seed: int, force: bool) -> None:
    output = output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)

    if output.exists() and not force:
        raise FileExistsError(
            f"O arquivo {output} já existe. Use --force para substituí-lo."
        )

    partial_path: Path | None = None
    try:
        with tempfile.NamedTemporaryFile(
            mode="w",
            encoding="utf-8",
            newline="",
            dir=output.parent,
            prefix=f"{output.name}.",
            suffix=".part",
            delete=False,
        ) as partial_file:
            partial_path = Path(partial_file.name)
            writer = csv.writer(partial_file, lineterminator="\n")
            writer.writerow(("transaction_id", "occurred_at", "amount", "category"))

            for row_number in range(1, rows + 1):
                writer.writerow(row_values(row_number, seed))

        # A substituição ocorre somente depois do fechamento para nunca publicar um CSV incompleto.
        os.replace(partial_path, output)
    except BaseException:
        if partial_path is not None:
            partial_path.unlink(missing_ok=True)
        raise


def file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as generated_file:
        while chunk := generated_file.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--rows", type=positive_integer, default=DEFAULT_ROWS)
    parser.add_argument("--seed", type=int, default=DEFAULT_SEED)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--force", action="store_true")
    return parser.parse_args()


def main() -> None:
    arguments = parse_arguments()
    generate(arguments.output, arguments.rows, arguments.seed, arguments.force)
    summary = {
        "output": str(arguments.output.resolve()),
        "rows": arguments.rows,
        "seed": arguments.seed,
        "sha256": file_sha256(arguments.output.resolve()),
    }
    print(json.dumps(summary, ensure_ascii=False))


if __name__ == "__main__":
    main()
