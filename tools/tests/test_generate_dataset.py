from __future__ import annotations

import csv
import json
import os
import re
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "generate_dataset.py"


class GenerateDatasetTest(unittest.TestCase):

    def test_should_generate_expected_contract(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "dataset.csv"

            result = self.run_generator(output, rows=10, seed=42)

            summary = json.loads(result.stdout)
            self.assertEqual(summary["rows"], 10)
            self.assertEqual(summary["seed"], 42)
            self.assertEqual(len(summary["sha256"]), 64)

            with output.open(encoding="utf-8", newline="") as generated_file:
                rows = list(csv.reader(generated_file))

            self.assertEqual(
                rows[0],
                ["transaction_id", "occurred_at", "amount", "category"],
            )
            self.assertEqual(len(rows), 11)
            self.assertEqual(rows[1][0], "txn-000000000001")
            self.assertTrue(rows[1][1].endswith("Z"))
            self.assertRegex(rows[1][2], re.compile(r"^-?\d+\.\d{2}$"))
            self.assertIn(
                rows[1][3],
                {
                    "alimentação",
                    "transporte",
                    "moradia",
                    "saúde",
                    "educação",
                    "lazer",
                    "serviços",
                    "outros",
                },
            )
            self.assertNotIn(b"\r\n", output.read_bytes())

    def test_should_be_deterministic_for_same_parameters(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            first = Path(directory) / "first.csv"
            second = Path(directory) / "second.csv"

            first_summary = json.loads(
                self.run_generator(first, rows=100, seed=123).stdout
            )
            second_summary = json.loads(
                self.run_generator(second, rows=100, seed=123).stdout
            )

            self.assertEqual(first_summary["sha256"], second_summary["sha256"])
            self.assertEqual(first.read_bytes(), second.read_bytes())

    def test_should_require_force_to_replace_existing_file(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "dataset.csv"
            output.write_text("conteúdo original", encoding="utf-8")

            result = self.run_generator(output, rows=1, seed=42, check=False)

            self.assertNotEqual(result.returncode, 0)
            self.assertEqual(output.read_text(encoding="utf-8"), "conteúdo original")

    @staticmethod
    def run_generator(
        output: Path,
        rows: int,
        seed: int,
        check: bool = True,
    ) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [
                sys.executable,
                str(SCRIPT),
                "--output",
                str(output),
                "--rows",
                str(rows),
                "--seed",
                str(seed),
            ],
            check=check,
            capture_output=True,
            text=True,
            encoding="utf-8",
            env=os.environ | {"PYTHONUTF8": "1"},
        )


if __name__ == "__main__":
    unittest.main()
