#!/usr/bin/env python3
"""Verify actual FDN smoothing against D078 doorway endpoints."""

from __future__ import annotations

import argparse
import json
import math
from pathlib import Path


BANDS = ("low", "mid", "high")
CHECKPOINTS = [0, 1, 2400, 4800, 9600, 19200, 48000]


def load(path: Path) -> dict[str, object]:
    return json.loads(path.read_text(encoding="utf-8"))


def nonincreasing(values: list[float]) -> bool:
    return all(after <= before for before, after in zip(values, values[1:]))


def nondecreasing(values: list[float]) -> bool:
    return all(after >= before for before, after in zip(values, values[1:]))


def fraction_progress(start: float, current: float, target: float) -> float:
    return (current - start) / (target - start)


def residual_fraction(start: float, current: float, target: float) -> float:
    return abs(current - target) / abs(start - target)


def verify(
    transition: dict[str, object], doorway: dict[str, object]
) -> dict[str, object]:
    if transition.get("schema_version") != 1:
        raise ValueError("transition schema_version must be 1")
    if transition.get("status") != "valid-diagnostic":
        raise ValueError("transition status is invalid")
    if transition.get("sample_rate_hz") != 48000:
        raise ValueError("sample rate changed")
    if transition.get("environment_update_ticks") != 20:
        raise ValueError("environment update period changed")
    if transition.get("transition_seconds") != 0.2:
        raise ValueError("transition duration changed")
    if doorway.get("status") != "valid-diagnostic":
        raise ValueError("doorway report status is invalid")

    closed = doorway["steps"][0]
    opened = doorway["steps"][-1]
    expected_closed = {
        "rt60_s": closed["rt60_s"],
        "wet_gain": closed["wet_gain"],
    }
    expected_open = {
        "rt60_s": opened["rt60_s"],
        "wet_gain": opened["wet_gain"],
    }
    if transition["closed_endpoint"] != expected_closed:
        raise ValueError("closed endpoint detached from D078")
    if transition["open_endpoint"] != expected_open:
        raise ValueError("open endpoint detached from D078")

    opening = transition["opening"]
    closing = transition["closing"]
    for rows, name in ((opening, "opening"), (closing, "closing")):
        if [row["samples"] for row in rows] != CHECKPOINTS:
            raise ValueError(f"{name} checkpoints changed")

    opening_wet = [row["current_wet_gain"] for row in opening]
    closing_wet = [row["current_wet_gain"] for row in closing]
    opening_feedback = {
        band: [row["current_feedback_gain"][band] for row in opening]
        for band in BANDS
    }
    closing_feedback = {
        band: [row["current_feedback_gain"][band] for row in closing]
        for band in BANDS
    }
    if not nonincreasing(opening_wet):
        raise ValueError("opening wet gain is not nonincreasing")
    if not nondecreasing(closing_wet):
        raise ValueError("closing wet gain is not nondecreasing")
    if not all(nonincreasing(values) for values in opening_feedback.values()):
        raise ValueError("opening feedback is not nonincreasing")
    if not all(nondecreasing(values) for values in closing_feedback.values()):
        raise ValueError("closing feedback is not nondecreasing")

    opening_progress_200ms = fraction_progress(
        opening_wet[0],
        opening[4]["current_wet_gain"],
        opening[0]["target_wet_gain"],
    )
    closing_progress_200ms = fraction_progress(
        closing_wet[0],
        closing[4]["current_wet_gain"],
        closing[0]["target_wet_gain"],
    )
    expected_progress = 1.0 - math.exp(-1.0)
    opening_one_sample = fraction_progress(
        opening_wet[0],
        opening_wet[1],
        opening[0]["target_wet_gain"],
    )
    closing_one_sample = fraction_progress(
        closing_wet[0],
        closing_wet[1],
        closing[0]["target_wet_gain"],
    )
    opening_residual_1s = residual_fraction(
        opening_wet[0],
        opening[-1]["current_wet_gain"],
        opening[0]["target_wet_gain"],
    )
    closing_residual_1s = residual_fraction(
        closing_wet[0],
        closing[-1]["current_wet_gain"],
        closing[0]["target_wet_gain"],
    )
    return {
        "schema_version": 1,
        "status": "valid-diagnostic",
        "opening_progress_at_200_ms": opening_progress_200ms,
        "closing_progress_at_200_ms": closing_progress_200ms,
        "expected_one_time_constant_progress": expected_progress,
        "opening_one_sample_progress": opening_one_sample,
        "closing_one_sample_progress": closing_one_sample,
        "opening_residual_at_1_s": opening_residual_1s,
        "closing_residual_at_1_s": closing_residual_1s,
        "gates": {
            "endpoints_bound_to_d078": True,
            "opening_wet_and_feedback_nonincreasing": True,
            "closing_wet_and_feedback_nondecreasing": True,
            "one_sample_progress_below_0_02_percent": (
                opening_one_sample < 0.0002
                and closing_one_sample < 0.0002
            ),
            "progress_at_200_ms_matches_one_time_constant": (
                abs(opening_progress_200ms - expected_progress) < 0.001
                and abs(closing_progress_200ms - expected_progress) < 0.001
            ),
            "residual_at_1_s_below_0_7_percent": (
                opening_residual_1s < 0.007
                and closing_residual_1s < 0.007
            ),
            "minecraft_release_calibrated": False,
        },
        "release_calibrated": False,
        "claim_boundary": (
            "FDN parameter-domain smoothing only; not moving-door geometry "
            "sampling or OpenAL scheduling."
        ),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--transition-report", type=Path, required=True)
    parser.add_argument("--doorway-report", type=Path, required=True)
    parser.add_argument("--output-json", type=Path)
    args = parser.parse_args()
    result = verify(
        load(args.transition_report),
        load(args.doorway_report),
    )
    payload = json.dumps(result, indent=2, sort_keys=True) + "\n"
    if args.output_json is not None:
        args.output_json.parent.mkdir(parents=True, exist_ok=True)
        args.output_json.write_text(payload, encoding="utf-8")
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
