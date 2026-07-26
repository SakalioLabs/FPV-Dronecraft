#!/usr/bin/env python3
"""Inspect a checkpointed 2.5D batch run without reading its live checkpoint."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any


class InvalidBatchRun(ValueError):
    """Raised when batch artifacts do not describe one healthy run."""


def read_json(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise InvalidBatchRun(f"cannot parse {path.name}: {error}") from error
    if not isinstance(value, dict):
        raise InvalidBatchRun(f"{path.name} is not a JSON object")
    return value


def inspect_run(
    directory: Path,
    label: str,
    target_solves: int,
) -> dict[str, Any]:
    pattern = re.compile(rf"^{re.escape(label)}-batch(\d+)\.json$")
    indexed_paths: list[tuple[int, Path]] = []
    for path in directory.glob(f"{label}-batch*.json"):
        match = pattern.match(path.name)
        if match is not None:
            indexed_paths.append((int(match.group(1)), path))
    indexed_paths.sort()
    if not indexed_paths:
        raise InvalidBatchRun("no matching batch JSON files")
    indices = [index for index, _ in indexed_paths]
    if indices != list(range(1, indices[-1] + 1)):
        raise InvalidBatchRun(f"batch sequence has gaps: {indices}")

    completed: list[tuple[int, dict[str, Any]]] = []
    active_batch: int | None = None
    last_unique_solves = 0
    measured_new_solves = 0
    measured_seconds = 0.0
    terminal = False
    for position, (index, output_path) in enumerate(indexed_paths):
        error_path = output_path.with_name(
            f"{label}-batch{index:02d}.stderr.txt"
        )
        if not error_path.exists():
            raise InvalidBatchRun(f"missing {error_path.name}")
        if error_path.stat().st_size != 0:
            raise InvalidBatchRun(f"{error_path.name} is not empty")
        if output_path.stat().st_size == 0:
            if position != len(indexed_paths) - 1:
                raise InvalidBatchRun(
                    f"only the final batch may be active: {output_path.name}"
                )
            active_batch = index
            continue

        report = read_json(output_path)
        status = report.get("status", "complete")
        if status not in ("incomplete", "complete"):
            raise InvalidBatchRun(
                f"{output_path.name} has unexpected status {status!r}"
            )
        if terminal:
            raise InvalidBatchRun("a batch exists after a terminal report")
        if status == "complete":
            terminal = True
            if position != len(indexed_paths) - 1:
                raise InvalidBatchRun("terminal report is not the final batch")
        unique_solves = report.get(
            "total_unique_helmholtz_solves",
            report.get("unique_helmholtz_solves"),
        )
        if (
            isinstance(unique_solves, bool)
            or not isinstance(unique_solves, int)
            or unique_solves <= 0
        ):
            raise InvalidBatchRun(
                f"{output_path.name} has invalid unique solve count"
            )
        if unique_solves <= last_unique_solves:
            raise InvalidBatchRun("unique solve count is not increasing")
        last_unique_solves = unique_solves
        new_solves = report.get("new_helmholtz_solves")
        solve_seconds = report.get("solve_seconds")
        if (
            not isinstance(new_solves, bool)
            and isinstance(new_solves, int)
            and new_solves > 0
        ):
            if not isinstance(solve_seconds, (int, float)):
                raise InvalidBatchRun(
                    f"{output_path.name} lacks solve_seconds"
                )
            measured_new_solves += new_solves
            measured_seconds += float(solve_seconds)
        completed.append((index, report))

    if terminal and active_batch is not None:
        raise InvalidBatchRun("active batch exists after terminal report")
    if terminal and last_unique_solves != target_solves:
        raise InvalidBatchRun(
            "terminal unique solve count does not equal target"
        )
    seconds_per_solve = (
        measured_seconds / measured_new_solves
        if measured_new_solves
        else None
    )
    remaining_solves = max(0, target_solves - last_unique_solves)
    if terminal:
        status = "complete"
    elif active_batch is not None:
        status = "healthy-incomplete"
    else:
        status = "paused-incomplete"
    return {
        "active_batch": active_batch,
        "completed_batches": len(completed),
        "estimated_remaining_seconds": (
            seconds_per_solve * remaining_solves
            if seconds_per_solve is not None
            else None
        ),
        "last_completed_batch": (
            completed[-1][0] if completed else None
        ),
        "measured_new_solves": measured_new_solves,
        "measured_seconds": measured_seconds,
        "remaining_solves": remaining_solves,
        "seconds_per_solve": seconds_per_solve,
        "status": status,
        "target_solves": target_solves,
        "total_unique_helmholtz_solves": last_unique_solves,
    }


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Audit completed batch JSON and stderr files."
    )
    parser.add_argument("--directory", type=Path, required=True)
    parser.add_argument("--label", required=True)
    parser.add_argument("--target-solves", type=int, default=384)
    arguments = parser.parse_args()
    if arguments.target_solves < 1:
        parser.error("--target-solves must be positive")
    try:
        report = inspect_run(
            arguments.directory,
            arguments.label,
            arguments.target_solves,
        )
    except InvalidBatchRun as error:
        print(
            json.dumps(
                {"status": "invalid", "error": str(error)},
                indent=2,
                sort_keys=True,
            )
        )
        return 1
    print(json.dumps(report, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
