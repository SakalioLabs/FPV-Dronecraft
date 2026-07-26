#!/usr/bin/env python3
"""D109 screen of direct fresh-EFX reset versus short linear onset slew."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

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


def _sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _rms(signal: np.ndarray) -> float:
    return float(np.sqrt(np.mean(np.square(signal))))


def _candidate(
    cold: np.ndarray,
    dry: np.ndarray,
    slew_ms: int,
) -> np.ndarray:
    candidate = cold.copy()
    slew_frames = round(slew_ms * SAMPLE_RATE / 1_000)
    if slew_frames == 0:
        return candidate
    for boundary in RECOVERY_FRAMES:
        wet = cold[
            boundary : boundary + ANALYSIS_FRAMES
        ] - dry[boundary : boundary + ANALYSIS_FRAMES]
        gain = np.minimum(
            np.arange(ANALYSIS_FRAMES, dtype=np.float64) / slew_frames,
            1.0,
        )
        candidate[boundary : boundary + ANALYSIS_FRAMES] = np.rint(
            dry[boundary : boundary + ANALYSIS_FRAMES] + wet * gain
        )
    return candidate


def _metrics(
    candidate: np.ndarray,
    cold: np.ndarray,
    dry: np.ndarray,
    warm: np.ndarray,
    slew_ms: int,
) -> dict:
    boundaries = []
    for stage, boundary in zip(FAULT_STAGES, RECOVERY_FRAMES):
        local = np.concatenate(
            (
                np.abs(
                    np.diff(
                        candidate[boundary - 2_000 : boundary - 100]
                    )
                ),
                np.abs(
                    np.diff(
                        candidate[boundary + 100 : boundary + 2_000]
                    )
                ),
            )
        )
        step = float(abs(candidate[boundary] - candidate[boundary - 1]))
        local_p99 = float(np.percentile(local, 99.0))
        candidate_wet = (
            candidate[boundary : boundary + ANALYSIS_FRAMES]
            - dry[boundary : boundary + ANALYSIS_FRAMES]
        )
        direct_wet = (
            cold[boundary : boundary + ANALYSIS_FRAMES]
            - dry[boundary : boundary + ANALYSIS_FRAMES]
        )
        warm_wet = (
            warm[boundary : boundary + ANALYSIS_FRAMES]
            - dry[boundary : boundary + ANALYSIS_FRAMES]
        )
        boundaries.append(
            {
                "fault_stage": stage,
                "frame": boundary,
                "residual_step_absolute": step,
                "local_difference_p99": local_p99,
                "step_to_p99_ratio": step / max(local_p99, 1.0),
                "twenty_ms_output_to_dry_rms_ratio": _rms(
                    candidate[
                        boundary : boundary + TWENTY_MS_FRAMES
                    ]
                )
                / max(
                    _rms(
                        dry[
                            boundary : boundary + TWENTY_MS_FRAMES
                        ]
                    ),
                    1.0e-12,
                ),
                "hundred_ms_wet_to_direct_rms_ratio": _rms(
                    candidate_wet
                )
                / max(_rms(direct_wet), 1.0e-12),
                "hundred_ms_wet_to_warm_rms_ratio": _rms(candidate_wet)
                / max(_rms(warm_wet), 1.0e-12),
                "normalized_rmse_vs_direct": _rms(
                    candidate[
                        boundary : boundary + ANALYSIS_FRAMES
                    ]
                    - cold[boundary : boundary + ANALYSIS_FRAMES]
                )
                / max(
                    _rms(
                        cold[
                            boundary : boundary + ANALYSIS_FRAMES
                        ]
                    ),
                    1.0e-12,
                ),
            }
        )
    return {
        "slew_ms": slew_ms,
        "boundaries": boundaries,
        "maximum_step_to_p99_ratio": max(
            item["step_to_p99_ratio"] for item in boundaries
        ),
        "minimum_twenty_ms_output_to_dry_rms_ratio": min(
            item["twenty_ms_output_to_dry_rms_ratio"]
            for item in boundaries
        ),
        "minimum_hundred_ms_wet_to_direct_rms_ratio": min(
            item["hundred_ms_wet_to_direct_rms_ratio"]
            for item in boundaries
        ),
        "minimum_hundred_ms_wet_to_warm_rms_ratio": min(
            item["hundred_ms_wet_to_warm_rms_ratio"]
            for item in boundaries
        ),
    }


def analyze(
    d105: dict,
    d105_bytes: bytes,
    cold_bytes: bytes,
    dry_bytes: bytes,
    java_wet_bytes: bytes,
    warm_report: dict,
    warm_report_bytes: bytes,
    warm_bytes: bytes,
) -> dict:
    if (
        d105.get("status") != "valid-backend-cold-start-render"
        or d105.get("sample_rate_hz") != SAMPLE_RATE
        or d105.get("render_frames") != RENDER_FRAMES
        or warm_report.get("status")
        != "valid-backend-failover-continuity-diagnostic"
    ):
        raise ValueError("D105/D104 source status changed")
    payloads = {
        "cold": cold_bytes,
        "dry": dry_bytes,
        "java_wet": java_wet_bytes,
    }
    for name, payload in payloads.items():
        if (
            len(payload) != OUTPUT_BYTES
            or d105.get(f"{name}_pcm_sha256") != _sha256(payload)
        ):
            raise ValueError(f"D105 {name} PCM detached")
    if (
        len(warm_bytes) != OUTPUT_BYTES
        or warm_report.get("output_pcm_sha256") != _sha256(warm_bytes)
    ):
        raise ValueError("D104 warm PCM detached")
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
            raise ValueError("Java wet escaped into EFX recovery")
    cases = [
        _metrics(
            _candidate(cold, dry, slew_ms),
            cold,
            dry,
            warm,
            slew_ms,
        )
        for slew_ms in SLEW_MILLISECONDS
    ]
    direct = cases[0]
    for case in cases:
        case["click_safe"] = (
            case["maximum_step_to_p99_ratio"] <= MAXIMUM_STEP_TO_P99
        )
        case["no_total_dropout"] = (
            case["minimum_twenty_ms_output_to_dry_rms_ratio"]
            >= MINIMUM_OUTPUT_TO_DRY
        )
        case["wet_energy_retained"] = (
            case["minimum_hundred_ms_wet_to_direct_rms_ratio"]
            >= MINIMUM_WET_RETENTION
        )
        case["step_improvement_vs_direct"] = 1.0 - (
            case["maximum_step_to_p99_ratio"]
            / max(direct["maximum_step_to_p99_ratio"], 1.0e-12)
        )
        case["slew_justified"] = (
            case["slew_ms"] > 0
            and case["click_safe"]
            and case["no_total_dropout"]
            and case["wet_energy_retained"]
            and case["step_improvement_vs_direct"]
            >= MINIMUM_STEP_IMPROVEMENT
        )
    direct_accepted = direct["click_safe"] and direct["no_total_dropout"]
    justified = [case for case in cases if case["slew_justified"]]
    selected = min(
        (case["slew_ms"] for case in justified),
        default=0 if direct_accepted else -1,
    )
    return {
        "schema_version": 1,
        "status": "valid-efx-recovery-slew-screen",
        "sample_rate_hz": SAMPLE_RATE,
        "render_frames": RENDER_FRAMES,
        "source_d105_report_sha256": _sha256(d105_bytes),
        "source_d104_report_sha256": _sha256(warm_report_bytes),
        "source_pcm_sha256": {
            "cold": _sha256(cold_bytes),
            "dry": _sha256(dry_bytes),
            "java_wet": _sha256(java_wet_bytes),
            "warm": _sha256(warm_bytes),
        },
        "recovery_frames": list(RECOVERY_FRAMES),
        "fault_stages": list(FAULT_STAGES),
        "slew_milliseconds": list(SLEW_MILLISECONDS),
        "thresholds": {
            "maximum_step_to_p99_ratio": MAXIMUM_STEP_TO_P99,
            "minimum_output_to_dry_rms_ratio": MINIMUM_OUTPUT_TO_DRY,
            "minimum_wet_retention_ratio": MINIMUM_WET_RETENTION,
            "minimum_step_improvement_vs_direct": (
                MINIMUM_STEP_IMPROVEMENT
            ),
        },
        "cases": cases,
        "selected_slew_ms": selected,
        "direct_reset_accepted": selected == 0 and direct_accepted,
        "production_change_required": selected > 0,
        "exclusive_wet_owner": True,
        "double_wet_crossfade": False,
        "linear_slot_gain_postprocess": True,
        "new_openal_render_per_candidate": False,
        "physical_endpoint_opened": False,
        "captures_audio": False,
        "release_calibrated": False,
        "claim_boundary": (
            "Linear wet-slot gain candidates are post-processed from the "
            "exact D105 isolated OpenAL Soft cold render. This screens "
            "whether a slew is justified; candidates are not newly rendered "
            "OpenAL streams, physical captures, or audible thresholds."
        ),
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--d105-report", type=Path, required=True)
    parser.add_argument("--cold-pcm", type=Path, required=True)
    parser.add_argument("--dry-pcm", type=Path, required=True)
    parser.add_argument("--java-wet-pcm", type=Path, required=True)
    parser.add_argument("--d104-report", type=Path, required=True)
    parser.add_argument("--warm-pcm", type=Path, required=True)
    parser.add_argument("--output-json", type=Path, required=True)
    args = parser.parse_args()
    d105_bytes = args.d105_report.read_bytes()
    d104_bytes = args.d104_report.read_bytes()
    result = analyze(
        json.loads(d105_bytes),
        d105_bytes,
        args.cold_pcm.read_bytes(),
        args.dry_pcm.read_bytes(),
        args.java_wet_pcm.read_bytes(),
        json.loads(d104_bytes),
        d104_bytes,
        args.warm_pcm.read_bytes(),
    )
    args.output_json.parent.mkdir(parents=True, exist_ok=True)
    args.output_json.write_text(
        json.dumps(result, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(result, sort_keys=True))


if __name__ == "__main__":
    main()
