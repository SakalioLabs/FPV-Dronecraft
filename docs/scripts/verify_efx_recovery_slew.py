#!/usr/bin/env python3
"""Verify D109 direct-reset versus linear fresh-EFX onset-slew screening."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path
from typing import Any

import numpy as np


SAMPLE_RATE = 48_000
RENDER_FRAMES = 192_000
OUTPUT_BYTES = RENDER_FRAMES * 2
RECOVERY_FRAMES = (24_000, 72_000, 120_000)
FAULT_STAGES = ("resource-create", "parameter-write", "source-route")
SLEW_MILLISECONDS = (0, 5, 10, 20, 50)
ANALYSIS_FRAMES = 4_800
TWENTY_MS_FRAMES = 960
MAXIMUM_STEP_TO_P99 = 2.0
MINIMUM_OUTPUT_TO_DRY = 0.5
MINIMUM_WET_RETENTION = 0.95
MINIMUM_STEP_IMPROVEMENT = 0.10
METRIC_TOLERANCE = 1.0e-9


def _sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _rms(signal: np.ndarray) -> float:
    return float(np.sqrt(np.mean(np.square(signal))))


def _close(actual: Any, expected: float, name: str) -> None:
    if (
        not isinstance(actual, (int, float))
        or not math.isfinite(actual)
        or not math.isclose(
            actual,
            expected,
            rel_tol=0.0,
            abs_tol=METRIC_TOLERANCE,
        )
    ):
        raise ValueError(f"D109 {name} metric changed")


def _candidate(
    cold: np.ndarray,
    dry: np.ndarray,
    slew_ms: int,
) -> np.ndarray:
    result = cold.copy()
    slew_frames = round(slew_ms * SAMPLE_RATE / 1_000)
    if slew_frames == 0:
        return result
    gain = np.minimum(
        np.arange(ANALYSIS_FRAMES, dtype=np.float64) / slew_frames,
        1.0,
    )
    for boundary in RECOVERY_FRAMES:
        dry_window = dry[boundary : boundary + ANALYSIS_FRAMES]
        wet = cold[boundary : boundary + ANALYSIS_FRAMES] - dry_window
        result[boundary : boundary + ANALYSIS_FRAMES] = np.rint(
            dry_window + wet * gain
        )
    return result


def _boundary_metrics(
    candidate: np.ndarray,
    cold: np.ndarray,
    dry: np.ndarray,
    warm: np.ndarray,
    boundary: int,
) -> dict[str, float]:
    local = np.concatenate(
        (
            np.abs(np.diff(candidate[boundary - 2_000 : boundary - 100])),
            np.abs(np.diff(candidate[boundary + 100 : boundary + 2_000])),
        )
    )
    step = float(abs(candidate[boundary] - candidate[boundary - 1]))
    p99 = float(np.percentile(local, 99.0))
    candidate_window = candidate[
        boundary : boundary + ANALYSIS_FRAMES
    ]
    dry_window = dry[boundary : boundary + ANALYSIS_FRAMES]
    direct_window = cold[boundary : boundary + ANALYSIS_FRAMES]
    warm_window = warm[boundary : boundary + ANALYSIS_FRAMES]
    candidate_wet = candidate_window - dry_window
    direct_wet = direct_window - dry_window
    warm_wet = warm_window - dry_window
    return {
        "residual_step_absolute": step,
        "local_difference_p99": p99,
        "step_to_p99_ratio": step / max(p99, 1.0),
        "twenty_ms_output_to_dry_rms_ratio": _rms(
            candidate[
                boundary : boundary + TWENTY_MS_FRAMES
            ]
        )
        / max(
            _rms(dry[boundary : boundary + TWENTY_MS_FRAMES]),
            1.0e-12,
        ),
        "hundred_ms_wet_to_direct_rms_ratio": _rms(candidate_wet)
        / max(_rms(direct_wet), 1.0e-12),
        "hundred_ms_wet_to_warm_rms_ratio": _rms(candidate_wet)
        / max(_rms(warm_wet), 1.0e-12),
        "normalized_rmse_vs_direct": _rms(
            candidate_window - direct_window
        )
        / max(_rms(direct_window), 1.0e-12),
    }


def verify(
    report: dict[str, Any],
    report_sha256: str,
    d105: dict[str, Any],
    d105_sha256: str,
    cold_bytes: bytes,
    dry_bytes: bytes,
    java_wet_bytes: bytes,
    d104: dict[str, Any],
    d104_sha256: str,
    warm_bytes: bytes,
) -> dict[str, Any]:
    if (
        report.get("schema_version") != 1
        or report.get("status") != "valid-efx-recovery-slew-screen"
        or report.get("sample_rate_hz") != SAMPLE_RATE
        or report.get("render_frames") != RENDER_FRAMES
        or report.get("recovery_frames") != list(RECOVERY_FRAMES)
        or report.get("fault_stages") != list(FAULT_STAGES)
        or report.get("slew_milliseconds") != list(SLEW_MILLISECONDS)
    ):
        raise ValueError("D109 report shape or status is invalid")
    if (
        report.get("source_d105_report_sha256") != d105_sha256
        or report.get("source_d104_report_sha256") != d104_sha256
    ):
        raise ValueError("D109 source report binding changed")
    if (
        d105.get("status") != "valid-backend-cold-start-render"
        or d105.get("sample_rate_hz") != SAMPLE_RATE
        or d105.get("render_frames") != RENDER_FRAMES
        or d104.get("status")
        != "valid-backend-failover-continuity-diagnostic"
    ):
        raise ValueError("D105/D104 source status changed")

    payloads = {
        "cold": cold_bytes,
        "dry": dry_bytes,
        "java_wet": java_wet_bytes,
    }
    source_hashes = report.get("source_pcm_sha256")
    if not isinstance(source_hashes, dict):
        raise ValueError("D109 source PCM manifest is invalid")
    for name, payload in payloads.items():
        digest = _sha256(payload)
        if (
            len(payload) != OUTPUT_BYTES
            or d105.get(f"{name}_pcm_sha256") != digest
            or source_hashes.get(name) != digest
        ):
            raise ValueError(f"D109 {name} PCM binding changed")
    warm_digest = _sha256(warm_bytes)
    if (
        len(warm_bytes) != OUTPUT_BYTES
        or d104.get("output_pcm_sha256") != warm_digest
        or source_hashes.get("warm") != warm_digest
    ):
        raise ValueError("D109 warm PCM binding changed")

    expected_thresholds = {
        "maximum_step_to_p99_ratio": MAXIMUM_STEP_TO_P99,
        "minimum_output_to_dry_rms_ratio": MINIMUM_OUTPUT_TO_DRY,
        "minimum_wet_retention_ratio": MINIMUM_WET_RETENTION,
        "minimum_step_improvement_vs_direct": MINIMUM_STEP_IMPROVEMENT,
    }
    thresholds = report.get("thresholds")
    if not isinstance(thresholds, dict):
        raise ValueError("D109 threshold manifest is invalid")
    for key, expected in expected_thresholds.items():
        if not math.isclose(
            thresholds.get(key, float("nan")),
            expected,
            rel_tol=0.0,
            abs_tol=1.0e-12,
        ):
            raise ValueError("D109 registered threshold changed")

    required_true = (
        "exclusive_wet_owner",
        "linear_slot_gain_postprocess",
    )
    required_false = (
        "double_wet_crossfade",
        "new_openal_render_per_candidate",
        "physical_endpoint_opened",
        "captures_audio",
        "release_calibrated",
    )
    for key in required_true:
        if report.get(key) is not True:
            raise ValueError(f"D109 {key} must be True")
    for key in required_false:
        if report.get(key) is not False:
            raise ValueError(f"D109 {key} must be False")

    cold = np.frombuffer(cold_bytes, dtype="<i2").astype(np.float64)
    dry = np.frombuffer(dry_bytes, dtype="<i2").astype(np.float64)
    java_wet = np.frombuffer(
        java_wet_bytes, dtype="<i2"
    ).astype(np.float64)
    warm = np.frombuffer(warm_bytes, dtype="<i2").astype(np.float64)
    for boundary in RECOVERY_FRAMES:
        if np.any(
            java_wet[boundary : boundary + ANALYSIS_FRAMES] != 0
        ):
            raise ValueError("D109 Java wet escaped into EFX recovery")

    cases = report.get("cases")
    if (
        not isinstance(cases, list)
        or [item.get("slew_ms") for item in cases]
        != list(SLEW_MILLISECONDS)
    ):
        raise ValueError("D109 candidate order changed")

    recomputed: list[dict[str, Any]] = []
    for case, slew_ms in zip(cases, SLEW_MILLISECONDS):
        candidate = _candidate(cold, dry, slew_ms)
        boundaries = case.get("boundaries")
        if (
            not isinstance(boundaries, list)
            or [
                (item.get("fault_stage"), item.get("frame"))
                for item in boundaries
            ]
            != list(zip(FAULT_STAGES, RECOVERY_FRAMES))
        ):
            raise ValueError("D109 boundary manifest changed")
        calculated_boundaries = []
        for item, boundary in zip(boundaries, RECOVERY_FRAMES):
            calculated = _boundary_metrics(
                candidate,
                cold,
                dry,
                warm,
                boundary,
            )
            for key, value in calculated.items():
                _close(item.get(key), value, key)
            calculated_boundaries.append(calculated)
        aggregate = {
            "maximum_step_to_p99_ratio": max(
                item["step_to_p99_ratio"]
                for item in calculated_boundaries
            ),
            "minimum_twenty_ms_output_to_dry_rms_ratio": min(
                item["twenty_ms_output_to_dry_rms_ratio"]
                for item in calculated_boundaries
            ),
            "minimum_hundred_ms_wet_to_direct_rms_ratio": min(
                item["hundred_ms_wet_to_direct_rms_ratio"]
                for item in calculated_boundaries
            ),
            "minimum_hundred_ms_wet_to_warm_rms_ratio": min(
                item["hundred_ms_wet_to_warm_rms_ratio"]
                for item in calculated_boundaries
            ),
        }
        for key, value in aggregate.items():
            _close(case.get(key), value, key)
        recomputed.append({"slew_ms": slew_ms, **aggregate})

    direct = recomputed[0]
    justified: list[int] = []
    for case, calculated in zip(cases, recomputed):
        click_safe = (
            calculated["maximum_step_to_p99_ratio"]
            <= MAXIMUM_STEP_TO_P99
        )
        no_dropout = (
            calculated["minimum_twenty_ms_output_to_dry_rms_ratio"]
            >= MINIMUM_OUTPUT_TO_DRY
        )
        retained = (
            calculated[
                "minimum_hundred_ms_wet_to_direct_rms_ratio"
            ]
            >= MINIMUM_WET_RETENTION
        )
        improvement = 1.0 - (
            calculated["maximum_step_to_p99_ratio"]
            / direct["maximum_step_to_p99_ratio"]
        )
        justified_case = (
            calculated["slew_ms"] > 0
            and click_safe
            and no_dropout
            and retained
            and improvement >= MINIMUM_STEP_IMPROVEMENT
        )
        expected_flags = {
            "click_safe": click_safe,
            "no_total_dropout": no_dropout,
            "wet_energy_retained": retained,
            "slew_justified": justified_case,
        }
        for key, expected in expected_flags.items():
            if case.get(key) is not expected:
                raise ValueError(f"D109 {key} flag changed")
        _close(
            case.get("step_improvement_vs_direct"),
            improvement,
            "step_improvement_vs_direct",
        )
        if justified_case:
            justified.append(calculated["slew_ms"])

    direct_accepted = (
        direct["maximum_step_to_p99_ratio"] <= MAXIMUM_STEP_TO_P99
        and direct["minimum_twenty_ms_output_to_dry_rms_ratio"]
        >= MINIMUM_OUTPUT_TO_DRY
    )
    selected = min(justified, default=0 if direct_accepted else -1)
    if (
        report.get("selected_slew_ms") != selected
        or report.get("direct_reset_accepted")
        is not (selected == 0 and direct_accepted)
        or report.get("production_change_required") is not (selected > 0)
    ):
        raise ValueError("D109 selection policy changed")
    if selected != 0:
        raise ValueError("D109 direct reset is no longer selected")

    positive_cases = recomputed[1:]
    return {
        "schema_version": 1,
        "status": "valid-efx-recovery-slew-verification",
        "source_report_sha256": report_sha256,
        "source_d105_report_sha256": d105_sha256,
        "source_d104_report_sha256": d104_sha256,
        "source_pcm_sha256": source_hashes,
        "selected_slew_ms": selected,
        "direct_maximum_step_to_p99_ratio": (
            direct["maximum_step_to_p99_ratio"]
        ),
        "best_positive_slew_maximum_step_to_p99_ratio": min(
            item["maximum_step_to_p99_ratio"]
            for item in positive_cases
        ),
        "best_positive_slew_minimum_wet_retention_ratio": max(
            item["minimum_hundred_ms_wet_to_direct_rms_ratio"]
            for item in positive_cases
        ),
        "gates": {
            "exact_d105_d104_pcm_hash_bound": True,
            "all_candidates_independently_recomputed": True,
            "direct_reset_click_safe": True,
            "direct_reset_no_total_dropout": True,
            "no_positive_slew_justified": not justified,
            "exclusive_wet_owner": True,
            "production_change_required": False,
            "physical_playback_or_capture_opened": False,
            "release_calibrated": False,
        },
        "captures_audio": False,
        "release_calibrated": False,
        "claim_boundary": (
            "Independently recomputes deterministic linear wet-gain "
            "post-processing over exact D105/D104 software-render PCM. "
            "It verifies an engineering policy choice, not a new OpenAL "
            "render, physical endpoint result, audible threshold, or "
            "release calibration."
        ),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--d105-report", type=Path, required=True)
    parser.add_argument("--cold-pcm", type=Path, required=True)
    parser.add_argument("--dry-pcm", type=Path, required=True)
    parser.add_argument("--java-wet-pcm", type=Path, required=True)
    parser.add_argument("--d104-report", type=Path, required=True)
    parser.add_argument("--warm-pcm", type=Path, required=True)
    parser.add_argument("--output-json", type=Path)
    args = parser.parse_args()
    report_bytes = args.report.read_bytes()
    d105_bytes = args.d105_report.read_bytes()
    d104_bytes = args.d104_report.read_bytes()
    result = verify(
        json.loads(report_bytes),
        _sha256(report_bytes),
        json.loads(d105_bytes),
        _sha256(d105_bytes),
        args.cold_pcm.read_bytes(),
        args.dry_pcm.read_bytes(),
        args.java_wet_pcm.read_bytes(),
        json.loads(d104_bytes),
        _sha256(d104_bytes),
        args.warm_pcm.read_bytes(),
    )
    rendered = json.dumps(result, indent=2, sort_keys=True) + "\n"
    if args.output_json is not None:
        args.output_json.parent.mkdir(parents=True, exist_ok=True)
        args.output_json.write_text(rendered, encoding="utf-8")
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
