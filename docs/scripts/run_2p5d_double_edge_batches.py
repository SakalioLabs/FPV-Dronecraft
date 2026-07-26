#!/usr/bin/env python3
"""Run checkpointed double-edge quadrature in bounded subprocess batches."""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
from pathlib import Path


VALIDATOR = (
    Path(__file__).resolve().parent
    / "validate_2p5d_double_edge_nested_quadrature.py"
)


def next_batch_index(output_directory: Path, label: str) -> int:
    index = 1
    while (output_directory / f"{label}-batch{index:02d}.json").exists():
        index += 1
    return index


def main() -> int:
    parser = argparse.ArgumentParser(
        description=(
            "Resume nested double-edge quadrature in CPU-bounded child "
            "processes."
        )
    )
    parser.add_argument("--frequency-hz", type=float, required=True)
    parser.add_argument("--thickness-cells", type=int, required=True)
    parser.add_argument("--intervals", type=int, required=True)
    parser.add_argument("--refined-intervals", type=int, required=True)
    parser.add_argument("--domain-minimum-x-m", type=float, required=True)
    parser.add_argument("--domain-maximum-x-m", type=float, required=True)
    parser.add_argument("--domain-minimum-z-m", type=float, required=True)
    parser.add_argument("--domain-maximum-z-m", type=float, required=True)
    parser.add_argument("--pml-width-m", type=float, required=True)
    parser.add_argument("--checkpoint", type=Path, required=True)
    parser.add_argument("--output-directory", type=Path, required=True)
    parser.add_argument("--label", required=True)
    parser.add_argument("--batch-size", type=int, default=32)
    parser.add_argument("--maximum-batches", type=int)
    arguments = parser.parse_args()
    if arguments.batch_size < 1:
        parser.error("--batch-size must be positive")
    if (
        arguments.maximum_batches is not None
        and arguments.maximum_batches < 1
    ):
        parser.error("--maximum-batches must be positive")
    arguments.output_directory.mkdir(parents=True, exist_ok=True)
    batch_index = next_batch_index(
        arguments.output_directory,
        arguments.label,
    )
    batches_run = 0
    while (
        arguments.maximum_batches is None
        or batches_run < arguments.maximum_batches
    ):
        output_path = arguments.output_directory / (
            f"{arguments.label}-batch{batch_index:02d}.json"
        )
        error_path = arguments.output_directory / (
            f"{arguments.label}-batch{batch_index:02d}.stderr.txt"
        )
        command = [
            sys.executable,
            str(VALIDATOR),
            "--frequency-hz",
            str(arguments.frequency_hz),
            "--thickness-cells",
            str(arguments.thickness_cells),
            "--intervals",
            str(arguments.intervals),
            "--refined-intervals",
            str(arguments.refined_intervals),
            "--domain-minimum-x-m",
            str(arguments.domain_minimum_x_m),
            "--domain-maximum-x-m",
            str(arguments.domain_maximum_x_m),
            "--domain-minimum-z-m",
            str(arguments.domain_minimum_z_m),
            "--domain-maximum-z-m",
            str(arguments.domain_maximum_z_m),
            "--pml-width-m",
            str(arguments.pml_width_m),
            "--checkpoint",
            str(arguments.checkpoint),
            "--maximum-new-solves",
            str(arguments.batch_size),
            "--json",
        ]
        with output_path.open("w", encoding="utf-8") as standard_output:
            with error_path.open("w", encoding="utf-8") as standard_error:
                completed = subprocess.run(
                    command,
                    stdout=standard_output,
                    stderr=standard_error,
                    check=False,
                )
        try:
            report = json.loads(output_path.read_text(encoding="utf-8"))
        except (json.JSONDecodeError, OSError) as error:
            print(
                json.dumps(
                    {
                        "status": "batch-error",
                        "batch": batch_index,
                        "return_code": completed.returncode,
                        "output": str(output_path),
                        "stderr": str(error_path),
                        "parse_error": str(error),
                    },
                    sort_keys=True,
                ),
                flush=True,
            )
            return 2
        batches_run += 1
        status = report.get("status", "complete")
        print(
            json.dumps(
                {
                    "status": status,
                    "batch": batch_index,
                    "return_code": completed.returncode,
                    "output": str(output_path),
                    "stderr": str(error_path),
                    "total_unique_helmholtz_solves": report.get(
                        "total_unique_helmholtz_solves",
                        report.get("unique_helmholtz_solves"),
                    ),
                    "passed": report.get("passed"),
                },
                sort_keys=True,
            ),
            flush=True,
        )
        if status == "incomplete":
            if completed.returncode != 3:
                return 2
            batch_index += 1
            continue
        if completed.returncode not in (0, 1):
            return completed.returncode
        return completed.returncode
    print(
        json.dumps(
            {
                "status": "batch-limit",
                "batches_run": batches_run,
                "next_batch": batch_index,
            },
            sort_keys=True,
        ),
        flush=True,
    )
    return 3


if __name__ == "__main__":
    raise SystemExit(main())
