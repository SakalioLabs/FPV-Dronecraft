#!/usr/bin/env python3
"""Validate the public acoustic-source dataset admissibility registry."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any


DEFAULT_REGISTRY = (
    Path(__file__).resolve().parents[1]
    / "acoustics"
    / "public-source-data-admissibility-v1.json"
)


def load_and_validate(path: Path) -> dict[str, Any]:
    registry = json.loads(path.read_text(encoding="utf-8"))
    if registry.get("schema_version") != 1:
        raise ValueError("schema_version must be 1")

    gates = registry.get("hard_gates")
    if not isinstance(gates, list) or not gates or len(gates) != len(set(gates)):
        raise ValueError("hard_gates must be a non-empty unique list")

    datasets = registry.get("datasets")
    if not isinstance(datasets, list) or not datasets:
        raise ValueError("datasets must be a non-empty list")

    seen_ids: set[str] = set()
    for dataset in datasets:
        dataset_id = dataset.get("id")
        if not isinstance(dataset_id, str) or not dataset_id:
            raise ValueError("every dataset needs a non-empty id")
        if dataset_id in seen_ids:
            raise ValueError(f"duplicate dataset id: {dataset_id}")
        seen_ids.add(dataset_id)

        for key in ("name", "primary_url", "license"):
            if not isinstance(dataset.get(key), str) or not dataset[key]:
                raise ValueError(f"{dataset_id}: missing {key}")
        if not dataset["primary_url"].startswith("https://"):
            raise ValueError(f"{dataset_id}: primary_url must use https")

        status = dataset.get("gates")
        if not isinstance(status, dict) or set(status) != set(gates):
            raise ValueError(
                f"{dataset_id}: gates must exactly match registry hard_gates"
            )
        if any(type(status[gate]) is not bool for gate in gates):
            raise ValueError(f"{dataset_id}: every gate must be boolean")

        derived_eligibility = all(status[gate] for gate in gates)
        declared_eligibility = dataset.get("release_profile_eligible")
        if type(declared_eligibility) is not bool:
            raise ValueError(
                f"{dataset_id}: release_profile_eligible must be boolean"
            )
        if declared_eligibility != derived_eligibility:
            raise ValueError(
                f"{dataset_id}: eligibility disagrees with hard gates"
            )

        uses = dataset.get("admissible_uses")
        if not isinstance(uses, list) or not uses or not all(
            isinstance(item, str) and item for item in uses
        ):
            raise ValueError(f"{dataset_id}: admissible_uses must be non-empty")

        blockers = dataset.get("blocking_reasons")
        failed_count = sum(not status[gate] for gate in gates)
        if failed_count and (
            not isinstance(blockers, list)
            or not blockers
            or not all(isinstance(item, str) and item for item in blockers)
        ):
            raise ValueError(
                f"{dataset_id}: failed gates require blocking_reasons"
            )
        if not failed_count and blockers:
            raise ValueError(
                f"{dataset_id}: eligible dataset must not list blockers"
            )

    return registry


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--registry",
        type=Path,
        default=DEFAULT_REGISTRY,
        help="Registry JSON to validate.",
    )
    args = parser.parse_args()
    registry = load_and_validate(args.registry)
    eligible = [
        item["id"]
        for item in registry["datasets"]
        if item["release_profile_eligible"]
    ]
    print(
        "public source data registry:"
        f" {len(registry['datasets'])} datasets,"
        f" {len(eligible)} release-profile eligible"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
