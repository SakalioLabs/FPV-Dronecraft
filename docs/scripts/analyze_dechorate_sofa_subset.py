#!/usr/bin/env python3
"""Analyze 66 hash-pinned measured dEchorate RIRs against room geometry."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
from collections import defaultdict
from pathlib import Path
from statistics import median
from typing import Any

import h5py
import numpy as np
from scipy.signal import butter, sosfiltfilt
from scipy.stats import spearmanr


FS = 48000
SPEED_OF_SOUND = 346.98
ROOM_SIZE = np.array([5.705, 5.965, 2.355], dtype=float)
VOXEL_SIZE = np.array([6.0, 6.0, 2.0], dtype=float)
FACETS = ("floor", "ceiling", "west", "south", "east", "north")
WALL_CODE = {
    "d": "direct",
    "f": "floor",
    "c": "ceiling",
    "w": "west",
    "s": "south",
    "e": "east",
    "n": "north",
}
PROGRESSIVE_CODES = (
    "000000",
    "010000",
    "011000",
    "011100",
    "011110",
    "011111",
)
BANDS = (500, 1000, 2000)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def close_vector(observed: np.ndarray, expected: list[float]) -> bool:
    return bool(np.allclose(observed.reshape(-1), expected, atol=1e-7, rtol=0))


def effective_code(room_code: str) -> str:
    return "010001" if room_code == "020002" else room_code


def image_time(
    source: np.ndarray,
    microphone: np.ndarray,
    facet: str,
    dimensions: np.ndarray,
) -> float:
    image = source.copy()
    if facet == "floor":
        image[2] = -source[2]
    elif facet == "ceiling":
        image[2] = 2 * dimensions[2] - source[2]
    elif facet == "west":
        image[0] = -source[0]
    elif facet == "east":
        image[0] = 2 * dimensions[0] - source[0]
    elif facet == "south":
        image[1] = -source[1]
    elif facet == "north":
        image[1] = 2 * dimensions[1] - source[1]
    else:
        raise ValueError(f"unknown facet {facet}")
    return float(np.linalg.norm(image - microphone) / SPEED_OF_SOUND)


def decay_fit(
    signal: np.ndarray,
    direct_sample: int,
    center_hz: int,
    upper_db: float,
    lower_db: float,
) -> dict[str, Any] | None:
    low = center_hz / math.sqrt(2)
    high = center_hz * math.sqrt(2)
    filtered = sosfiltfilt(
        butter(4, [low, high], btype="bandpass", fs=FS, output="sos"),
        signal,
    )
    tail = filtered[direct_sample:]
    if len(tail) < FS // 2:
        return None
    noise_samples = max(FS // 10, 1)
    noise_power = float(np.mean(np.square(tail[-noise_samples:])))
    raw = np.cumsum(np.square(tail)[::-1])[::-1]
    remaining = np.arange(len(tail), 0, -1, dtype=float)
    corrected = raw - noise_power * remaining
    corrected = np.maximum(corrected, np.finfo(float).tiny)
    corrected = np.maximum.accumulate(corrected[::-1])[::-1]
    edc_db = 10.0 * np.log10(corrected / corrected[0])
    indices = np.flatnonzero(
        (edc_db <= upper_db) & (edc_db >= lower_db)
    )
    if len(indices) < 100:
        return None
    start, end = int(indices[0]), int(indices[-1])
    if end - start < 100:
        return None
    x = np.arange(start, end + 1, dtype=float) / FS
    y = edc_db[start : end + 1]
    slope, intercept = np.polyfit(x, y, 1)
    if slope >= 0:
        return None
    predicted = slope * x + intercept
    residual = float(np.sum(np.square(y - predicted)))
    total = float(np.sum(np.square(y - np.mean(y))))
    r_squared = 1.0 - residual / total if total > 0 else 0.0
    return {
        "rt60_s": float(-60.0 / slope),
        "r_squared": r_squared,
        "fit_start_sample": direct_sample + start,
        "fit_end_sample": direct_sample + end,
        "noise_power": noise_power,
    }


def rir_metrics(signal: np.ndarray, direct_sample: int) -> dict[str, Any]:
    early_end = min(len(signal), direct_sample + int(0.080 * FS))
    early = float(np.sum(np.square(signal[direct_sample:early_end])))
    late = float(np.sum(np.square(signal[early_end:])))
    result: dict[str, Any] = {
        "early_energy": early,
        "late_energy": late,
        "early_to_late_db": 10.0 * math.log10(
            max(early, np.finfo(float).tiny)
            / max(late, np.finfo(float).tiny)
        ),
        "bands": {},
    }
    for center in BANDS:
        result["bands"][str(center)] = {
            "edt": decay_fit(signal, direct_sample, center, 0.0, -10.0),
            "t20": decay_fit(signal, direct_sample, center, -5.0, -25.0),
            "t30": decay_fit(signal, direct_sample, center, -5.0, -35.0),
        }
    return result


def auc(reflective: list[float], absorptive: list[float]) -> float:
    wins = 0.0
    total = len(reflective) * len(absorptive)
    for left in reflective:
        for right in absorptive:
            wins += 1.0 if left > right else 0.5 if left == right else 0.0
    return wins / total


def analyze(
    subset_manifest_path: Path,
    pins_path: Path,
    inventory_path: Path,
    d114_path: Path,
    sofa_directory: Path,
) -> dict[str, Any]:
    subset = json.loads(subset_manifest_path.read_text(encoding="utf-8"))
    pins = json.loads(pins_path.read_text(encoding="utf-8"))
    inventory = json.loads(inventory_path.read_text(encoding="utf-8"))
    d114 = json.loads(d114_path.read_text(encoding="utf-8"))
    if (
        subset.get("schema_version") != 1
        or pins.get("source_manifest_sha256") != sha256(subset_manifest_path)
        or inventory.get("source_manifest_sha256")
        != sha256(subset_manifest_path)
        or inventory.get("source_pins_sha256") != sha256(pins_path)
        or d114.get("status") != "valid-dechorate-facet-rir-experiment"
    ):
        raise ValueError("D115 source binding changed")
    if (
        inventory.get("aggregate_sha256") != pins["aggregate_sha256"]
        or inventory.get("total_bytes") != pins["total_bytes"]
        or len(inventory.get("files", [])) != 66
    ):
        raise ValueError("D115 inventory aggregate changed")
    pin_files = pins["files"]
    d114_entries = {
        (entry["room_code"], entry["source_id"], entry["microphone_id"]): entry
        for entry in d114["rir_extraction_manifest"]
    }
    source_contract = {
        source["id"]: source for source in d114["selected_sources"]
    }
    annotation_pin = pins["annotations"]
    annotation_path = sofa_directory / annotation_pin["filename"]
    if (
        annotation_path.stat().st_size != annotation_pin["bytes"]
        or sha256(annotation_path) != annotation_pin["sha256"]
    ):
        raise ValueError("D115 annotation pin changed")
    with h5py.File(annotation_path, "r") as annotations:
        echo_toa = np.array(annotations["echo_toa"])
        echo_wall = np.array(annotations["echo_wall"])
        if (
            echo_toa.shape != (7, 30, 6)
            or echo_wall.shape != (7, 30, 6)
            or not close_vector(annotations["room_size"][:], ROOM_SIZE.tolist())
        ):
            raise ValueError("D115 annotation structure changed")
        annotated_toa = echo_toa[:, :, 4]
        annotated_walls = echo_wall[:, :, 4]
    waveforms: dict[tuple[str, int, int], np.ndarray] = {}
    records: dict[tuple[str, int, int], dict[str, Any]] = {}
    stale_descriptions = 0
    for item in inventory["files"]:
        filename = item["filename"]
        expected_pin = pin_files.get(filename)
        path = sofa_directory / filename
        if (
            expected_pin is None
            or item["bytes"] != expected_pin[0]
            or item["sha256"] != expected_pin[1]
            or path.stat().st_size != expected_pin[0]
            or sha256(path) != expected_pin[1]
        ):
            raise ValueError(f"D115 SOFA pin changed: {filename}")
        key = (item["room_code"], item["source_id"], item["microphone_id"])
        contract = d114_entries.get(key)
        if contract is None:
            raise ValueError("D115 SOFA selection detached from D114")
        with h5py.File(path, "r") as sofa:
            title = sofa.attrs["Title"].decode()
            description = sofa.attrs["RoomDescription"].decode()
            receiver_index = item["receiver_index"]
            if (
                title != path.stem
                or tuple(sofa["Data.IR"].shape) != (1, 5, 48000)
                or float(sofa["Data.SamplingRate"][0]) != FS
                or not close_vector(sofa["RoomCornerB"][:], ROOM_SIZE.tolist())
            ):
                raise ValueError(f"D115 SOFA structure changed: {filename}")
            source = np.array(sofa["SourcePosition"][0], dtype=float)
            microphone = np.array(
                sofa["ReceiverPosition"][receiver_index, :, 0], dtype=float
            )
            expected_source = source_contract[item["source_id"]]
            expected_microphone = next(
                mic for mic in expected_source["microphones"]
                if mic["id"] == item["microphone_id"]
            )
            if (
                not close_vector(source, expected_source["position_m"])
                or not close_vector(
                    microphone, expected_microphone["position_m"]
                )
            ):
                raise ValueError(f"D115 SOFA coordinates changed: {filename}")
            signal = np.array(
                sofa["Data.IR"][0, receiver_index, :], dtype="<f8"
            )
            if not np.all(np.isfinite(signal)) or np.max(np.abs(signal)) <= 0:
                raise ValueError(f"D115 invalid measured RIR: {filename}")
            if "room_code:020002" in description:
                stale_descriptions += 1
            waveforms[key] = signal
            records[key] = {
                "room_code": item["room_code"],
                "source_id": item["source_id"],
                "microphone_id": item["microphone_id"],
                "filename": filename,
                "file_sha256": item["sha256"],
                "receiver_index": receiver_index,
                "waveform_sha256": hashlib.sha256(
                    signal.tobytes(order="C")
                ).hexdigest(),
                "source_position_m": source.tolist(),
                "microphone_position_m": microphone.tolist(),
                "geometric_direct_sample": float(
                    np.linalg.norm(source - microphone)
                    / SPEED_OF_SOUND
                    * FS
                ),
            }
    if len(waveforms) != 66 or set(waveforms) != set(d114_entries):
        raise ValueError("D115 measured RIR coverage changed")
    baseline_peaks: dict[tuple[int, int], int] = {}
    for source_id, microphone_id in {
        (key[1], key[2]) for key in waveforms
    }:
        key = ("000000", source_id, microphone_id)
        signal = waveforms[key]
        expected = records[key]["geometric_direct_sample"]
        low = max(0, int(expected) - 32)
        high = min(len(signal), int(expected) + 129)
        baseline_peaks[(source_id, microphone_id)] = (
            low + int(np.argmax(np.abs(signal[low:high])))
        )
    for key, signal in waveforms.items():
        _, source_id, microphone_id = key
        baseline_peak = baseline_peaks[(source_id, microphone_id)]
        low, high = baseline_peak - 16, baseline_peak + 17
        direct_peak = low + int(np.argmax(np.abs(signal[low:high])))
        records[key]["direct_peak_sample"] = direct_peak
        records[key]["source_chain_bias_samples"] = (
            baseline_peak - records[key]["geometric_direct_sample"]
        )
        records[key]["direct_shift_vs_absorptive_baseline_samples"] = (
            direct_peak - baseline_peak
        )
        records[key]["metrics"] = rir_metrics(signal, direct_peak)
    geometry_comparison = []
    with h5py.File(annotation_path, "r") as annotations:
        source = np.array(
            annotations["sources_directional_position"][:, 4], dtype=float
        )
        microphones = np.array(annotations["microphones"], dtype=float)
    for microphone_id in (10, 19, 20):
        microphone = microphones[:, microphone_id]
        walls = [
            WALL_CODE[value.decode()]
            for value in annotated_walls[:, microphone_id]
        ]
        toas = annotated_toa[:, microphone_id]
        direct_annotation = float(toas[walls.index("direct")])
        direct_geometry = float(
            np.linalg.norm(source - microphone) / SPEED_OF_SOUND
        )
        geometry_comparison.append(
            {
                "microphone_id": microphone_id,
                "facet": "direct",
                "annotation_sample": direct_annotation * FS,
                "physical_sample": direct_geometry * FS,
                "physical_residual_samples": (
                    direct_geometry - direct_annotation
                ) * FS,
                "voxel_sample": direct_geometry * FS,
                "voxel_residual_samples": (
                    direct_geometry - direct_annotation
                ) * FS,
            }
        )
        for facet in FACETS:
            annotation = float(toas[walls.index(facet)])
            physical = image_time(source, microphone, facet, ROOM_SIZE)
            voxel = image_time(source, microphone, facet, VOXEL_SIZE)
            geometry_comparison.append(
                {
                    "microphone_id": microphone_id,
                    "facet": facet,
                    "annotation_sample": annotation * FS,
                    "physical_sample": physical * FS,
                    "physical_residual_samples": (
                        physical - annotation
                    ) * FS,
                    "voxel_sample": voxel * FS,
                    "voxel_residual_samples": (
                        voxel - annotation
                    ) * FS,
                }
            )
    echo_observations = []
    echo_energy: dict[str, dict[str, list[float]]] = {
        facet: {"reflective": [], "absorptive": []} for facet in FACETS
    }
    baseline_echo_energy: dict[tuple[int, str], float] = {}
    for microphone_id in (10, 19, 20):
        walls = [
            WALL_CODE[value.decode()]
            for value in annotated_walls[:, microphone_id]
        ]
        toas = annotated_toa[:, microphone_id] * FS
        direct_annotation = float(toas[walls.index("direct")])
        baseline_key = ("000000", 4, microphone_id)
        baseline_direct = records[baseline_key]["direct_peak_sample"]
        baseline_signal = waveforms[baseline_key]
        for facet in FACETS:
            center = float(toas[walls.index(facet)])
            aligned = int(round(center + baseline_direct - direct_annotation))
            baseline_echo_energy[(microphone_id, facet)] = float(
                np.sum(np.square(baseline_signal[aligned - 8 : aligned + 9]))
            )
    for room_code in subset["google_drive_ids"]:
        code = effective_code(room_code)
        for microphone_id in (10, 19, 20):
            key = (room_code, 4, microphone_id)
            signal = waveforms[key]
            direct = records[key]["direct_peak_sample"]
            walls = [
                WALL_CODE[value.decode()]
                for value in annotated_walls[:, microphone_id]
            ]
            toas = annotated_toa[:, microphone_id] * FS
            direct_annotation = float(toas[walls.index("direct")])
            for facet in FACETS:
                center = float(toas[walls.index(facet)])
                aligned = center + direct - direct_annotation
                rounded = int(round(aligned))
                low, high = rounded - 12, rounded + 13
                observed = low + int(np.argmax(np.abs(signal[low:high])))
                energy = float(
                    np.sum(np.square(signal[rounded - 8 : rounded + 9]))
                )
                gain_db = 10.0 * math.log10(
                    max(energy, np.finfo(float).tiny)
                    / max(
                        baseline_echo_energy[(microphone_id, facet)],
                        np.finfo(float).tiny,
                    )
                )
                state = (
                    "reflective"
                    if code[FACETS.index(facet)] == "1"
                    else "absorptive"
                )
                echo_energy[facet][state].append(gain_db)
                echo_observations.append(
                    {
                        "room_code": room_code,
                        "microphone_id": microphone_id,
                        "facet": facet,
                        "state": state,
                        "timing_residual_samples": observed - aligned,
                        "energy_gain_db_vs_000000": gain_db,
                    }
                )
    echo_summary = {}
    for facet, states in echo_energy.items():
        if states["reflective"]:
            echo_summary[facet] = {
                "reflective_observations": len(states["reflective"]),
                "absorptive_observations": len(states["absorptive"]),
                "reflective_median_gain_db": median(states["reflective"]),
                "absorptive_median_gain_db": median(states["absorptive"]),
                "reflective_vs_absorptive_auc": auc(
                    states["reflective"], states["absorptive"]
                ),
            }
        else:
            echo_summary[facet] = {
                "reflective_observations": 0,
                "absorptive_observations": len(states["absorptive"]),
                "reflective_median_gain_db": None,
                "absorptive_median_gain_db": median(states["absorptive"]),
                "reflective_vs_absorptive_auc": None,
            }
    room_summary = {}
    for room_code in subset["google_drive_ids"]:
        room_records = [
            value for key, value in records.items() if key[0] == room_code
        ]
        bands = {}
        for center in BANDS:
            band = {}
            for metric in ("edt", "t20", "t30"):
                values = [
                    record["metrics"]["bands"][str(center)][metric]["rt60_s"]
                    for record in room_records
                    if record["metrics"]["bands"][str(center)][metric]
                    is not None
                    and record["metrics"]["bands"][str(center)][metric][
                        "r_squared"
                    ] >= 0.9
                ]
                band[f"{metric}_median_s"] = (
                    median(values) if values else None
                )
                band[f"{metric}_eligible_rirs"] = len(values)
            bands[str(center)] = band
        room_summary[room_code] = {
            "effective_facet_code": effective_code(room_code),
            "reflective_facet_count": effective_code(room_code).count("1"),
            "furniture": room_code == "020002",
            "early_to_late_median_db": median(
                record["metrics"]["early_to_late_db"]
                for record in room_records
            ),
            "bands": bands,
        }
    correlations = {}
    non_furniture = [
        code for code in subset["google_drive_ids"] if code != "020002"
    ]
    for center in BANDS:
        values = [
            room_summary[code]["bands"][str(center)]["t20_median_s"]
            for code in non_furniture
        ]
        counts = [
            room_summary[code]["reflective_facet_count"]
            for code in non_furniture
        ]
        valid = [
            (count, value)
            for count, value in zip(counts, values, strict=True)
            if value is not None
        ]
        correlation = spearmanr(
            [item[0] for item in valid], [item[1] for item in valid]
        )
        progressive = [
            room_summary[code]["bands"][str(center)]["t20_median_s"]
            for code in PROGRESSIVE_CODES
        ]
        correlations[str(center)] = {
            "reflective_count_t20_spearman": float(correlation.statistic),
            "reflective_count_t20_pvalue": float(correlation.pvalue),
            "progressive_t20_s": progressive,
            "progressive_non_decreasing_steps": sum(
                left is not None
                and right is not None
                and right >= left
                for left, right in zip(
                    progressive, progressive[1:]
                )
            ),
            "progressive_steps": len(progressive) - 1,
        }
    physical_residuals = [
        abs(item["physical_residual_samples"])
        for item in geometry_comparison
    ]
    voxel_residuals = [
        abs(item["voxel_residual_samples"]) for item in geometry_comparison
    ]
    direct_shifts = [
        abs(record["direct_shift_vs_absorptive_baseline_samples"])
        for record in records.values()
    ]
    return {
        "schema_version": 1,
        "status": "valid-dechorate-measured-sofa-analysis",
        "source_subset_manifest_sha256": sha256(subset_manifest_path),
        "source_pins_sha256": sha256(pins_path),
        "source_inventory_sha256": sha256(inventory_path),
        "source_d114_report_sha256": sha256(d114_path),
        "measured_rirs": len(records),
        "measured_audio_bytes": pins["total_bytes"],
        "sofa_identity_audit": {
            "title_matches_filename": 66,
            "stale_room_description_count": stale_descriptions,
            "stale_room_description_value":
                "room_code:020002 for every selected SOFA",
            "room_identity_basis":
                "hash-pinned public-folder filename and Drive id",
        },
        "records": [
            records[key] for key in sorted(records)
        ],
        "geometry_comparison": geometry_comparison,
        "geometry_summary": {
            "paths": len(geometry_comparison),
            "physical_absolute_residual_median_samples": median(
                physical_residuals
            ),
            "physical_absolute_residual_max_samples": max(
                physical_residuals
            ),
            "voxel_absolute_residual_median_samples": median(
                voxel_residuals
            ),
            "voxel_absolute_residual_max_samples": max(voxel_residuals),
        },
        "direct_path_summary": {
            "absolute_shift_vs_absorptive_baseline_median_samples": median(
                direct_shifts
            ),
            "absolute_shift_vs_absorptive_baseline_max_samples": max(
                direct_shifts
            ),
        },
        "echo_observations": echo_observations,
        "echo_summary": echo_summary,
        "room_summary": room_summary,
        "room_correlations": correlations,
        "decision": {
            "measured_rir_subset_materialized": True,
            "measured_rir_metrics_admitted": True,
            "sofa_room_description_admitted": False,
            "filename_and_drive_id_room_identity_admitted": True,
            "furniture_causal_effect_identified": False,
            "production_material_fit_eligible": False,
            "production_change_required": False,
            "next_action":
                "fit-controlled-surface-response-and-run-cpu-dda-holdout",
        },
        "physical_endpoint_opened": False,
        "captures_audio": False,
        "release_calibrated": False,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--subset-manifest", type=Path, required=True)
    parser.add_argument("--pins", type=Path, required=True)
    parser.add_argument("--inventory", type=Path, required=True)
    parser.add_argument("--d114-report", type=Path, required=True)
    parser.add_argument("--sofa-directory", type=Path, required=True)
    parser.add_argument("--output-json", type=Path, required=True)
    args = parser.parse_args()
    result = analyze(
        args.subset_manifest,
        args.pins,
        args.inventory,
        args.d114_report,
        args.sofa_directory,
    )
    args.output_json.parent.mkdir(parents=True, exist_ok=True)
    args.output_json.write_text(
        json.dumps(result, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(
        json.dumps(
            {
                "status": result["status"],
                "measured_rirs": result["measured_rirs"],
                "stale_room_descriptions": result[
                    "sofa_identity_audit"
                ]["stale_room_description_count"],
                "physical_max_residual_samples": result[
                    "geometry_summary"
                ]["physical_absolute_residual_max_samples"],
                "voxel_max_residual_samples": result["geometry_summary"][
                    "voxel_absolute_residual_max_samples"
                ],
            },
            sort_keys=True,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
