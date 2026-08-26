from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).resolve().parents[1] / "run_benchmark.py"
SPEC = importlib.util.spec_from_file_location("run_benchmark", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
benchmark = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(benchmark)


class BenchmarkHelpersTest(unittest.TestCase):
    def test_parse_memory_bytes_understands_docker_binary_units(self) -> None:
        self.assertEqual(512 * 1024, benchmark.parse_memory_bytes("512KiB"))
        self.assertEqual(128 * 1024 * 1024, benchmark.parse_memory_bytes("128MiB"))
        self.assertEqual(2 * 1024**3, benchmark.parse_memory_bytes("2GiB"))

    def test_percentile_uses_nearest_rank_without_inventing_samples(self) -> None:
        values = [1.0, 2.0, 3.0, 4.0, 100.0]
        self.assertEqual(3.0, benchmark.percentile(values, 0.50))
        self.assertEqual(100.0, benchmark.percentile(values, 0.95))

    def test_transaction_plan_matches_keyset_shape_used_by_backend(self) -> None:
        sql = benchmark.transaction_sql("11111111-1111-1111-1111-111111111111", 900_000)

        self.assertIn("WHERE import_id = '11111111-1111-1111-1111-111111111111'::uuid", sql)
        self.assertIn("AND id > 900000", sql)
        self.assertIn("ORDER BY id", sql)
        self.assertIn("LIMIT 101", sql)
        self.assertNotIn("OFFSET", sql)

    def test_analytics_plan_keeps_grouping_sets_and_utc_contract(self) -> None:
        sql = benchmark.analytics_sql("11111111-1111-1111-1111-111111111111")

        self.assertIn("GROUP BY GROUPING SETS", sql)
        self.assertIn("occurred_at AT TIME ZONE 'UTC'", sql)
        self.assertIn("WHERE import_id = '11111111-1111-1111-1111-111111111111'::uuid", sql)


if __name__ == "__main__":
    unittest.main()
