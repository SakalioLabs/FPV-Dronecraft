"""Build a JIRS 2024 ground/ceiling/wall experimental packet.

Outputs:
  docs/data/surface_jirs2024_effect_packet.csv
  docs/data/fpv_model_validation_summary.csv rows with category
  surface_jirs2024_packet_*

The source is the Springer supplementary zip for "Ground, Ceiling and Wall
Effect Evaluation of Small Quadcopters in Pressure-controlled Environments".
It contains CSV files used for the paper figures/derived quantities. This packet
keeps every CSV measurement row with normalized distance columns and adds compact
ground/ceiling/wall summaries for the simulator handoff.
"""

from __future__ import annotations

import csv
import io
import math
import re
import urllib.request
import zipfile
from collections import Counter
from pathlib import Path
from statistics import mean, median
from typing import Iterable

import numpy as np
from scipy.io import loadmat


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"
RAW = DATA / "raw" / "surface_jirs2024_effect"

OUTPUT = DATA / "surface_jirs2024_effect_packet.csv"
SUMMARY = DATA / "fpv_model_validation_summary.csv"
ZIP_PATH = RAW / "10846_2024_2155_MOESM1_ESM.zip"

ARTICLE_URL = "https://link.springer.com/article/10.1007/s10846-024-02155-7"
ARTICLE_DOI_URL = "https://doi.org/10.1007/s10846-024-02155-7"
SUPPLEMENT_ZIP_URL = (
    "https://static-content.springer.com/esm/art%3A10.1007%2Fs10846-024-02155-7/"
    "MediaObjects/10846_2024_2155_MOESM1_ESM.zip"
)
ZENODO_URL = "https://zenodo.org/records/11384638"
ZENODO_DOI_URL = "https://doi.org/10.5281/zenodo.11384638"

ARTICLE_TITLE = "Ground, Ceiling and Wall Effect Evaluation of Small Quadcopters in Pressure-controlled Environments"
PUBLICATION_DATE = "2024-08-24"


def repo_path(path: Path) -> str:
    return path.resolve().relative_to(ROOT).as_posix()


def value_text(value: object) -> str:
    if value is None:
        return ""
    if isinstance(value, str):
        return value
    if isinstance(value, bool):
        return "1" if value else "0"
    if isinstance(value, int):
        return str(value)
    if isinstance(value, float):
        if not math.isfinite(value):
            return ""
        return f"{value:.12g}"
    return str(value)


def write_csv(path: Path, rows: Iterable[dict[str, object]]) -> None:
    row_list = list(rows)
    path.parent.mkdir(parents=True, exist_ok=True)
    fieldnames: list[str] = []
    for row in row_list:
        for key in row:
            if key not in fieldnames:
                fieldnames.append(key)
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        for row in row_list:
            writer.writerow({key: value_text(row.get(key, "")) for key in fieldnames})


def read_rows(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8") as handle:
        return list(csv.DictReader(handle))


def add_metric(
    rows: list[dict[str, object]],
    *,
    row_type: str,
    name: str,
    metric: str,
    value: object,
    unit: str,
    source_file: Path | str,
    source_url: str,
    evidence_role: str,
    note: str = "",
    **extra: object,
) -> None:
    row = {
        "row_type": row_type,
        "name": name,
        "metric": metric,
        "value": value,
        "unit": unit,
        "source_file": repo_path(source_file) if isinstance(source_file, Path) else source_file,
        "source_url": source_url,
        "evidence_role": evidence_role,
        "note": note,
    }
    row.update(extra)
    rows.append(row)


def download_zip() -> bytes:
    RAW.mkdir(parents=True, exist_ok=True)
    if ZIP_PATH.exists() and ZIP_PATH.stat().st_size > 0:
        return ZIP_PATH.read_bytes()
    request = urllib.request.Request(SUPPLEMENT_ZIP_URL, headers={"User-Agent": "codex-fpv-surface-data"})
    with urllib.request.urlopen(request, timeout=60.0) as response:
        data = response.read()
    ZIP_PATH.write_bytes(data)
    return data


def numeric(row: dict[str, str], predicate) -> float:
    for key, value in row.items():
        if key and predicate(key) and value not in ("", "-"):
            try:
                return float(value)
            except ValueError:
                pass
    return math.nan


def parse_measurements(zip_bytes: bytes) -> tuple[list[dict[str, object]], dict[str, int]]:
    measurements: list[dict[str, object]] = []
    counts: Counter[str] = Counter()
    with zipfile.ZipFile(ZIP_PATH) if ZIP_PATH.exists() else zipfile.ZipFile(io.BytesIO(zip_bytes)) as archive:
        names = archive.namelist()
        counts["zip_file_count"] = len(names)
        counts["csv_file_count"] = sum(name.endswith(".csv") for name in names)
        counts["mat_file_count"] = sum(name.endswith(".mat") for name in names)
        counts["pdf_file_count"] = sum(name.endswith(".pdf") for name in names)
        for name in names:
            if not name.endswith(".csv"):
                continue
            filename = name.rsplit("/", 1)[-1]
            match = re.match(r"(GroundEffect|CeilingEffect|WallEffect)_(\d+)_([^.]*)\.csv", filename)
            if not match:
                continue
            effect = match.group(1).replace("Effect", "").lower()
            prop_diameter_in = float(match.group(2))
            facility = match.group(3)
            prop_diameter_m = prop_diameter_in * 0.0254
            prop_diameter_cm = prop_diameter_in * 2.54
            text = archive.read(name).decode("utf-8-sig", errors="replace")
            for row_index, row in enumerate(csv.DictReader(io.StringIO(text)), start=1):
                distance_text = row.get("D [cm]") or row.get("\ufeffD [cm]") or ""
                try:
                    distance_cm = float(distance_text)
                except ValueError:
                    continue
                pwm = int(numeric(row, lambda key: key == "PWM"))
                omega_1 = numeric(row, lambda key: key.startswith("Omega_1") or key == "n [rpm]")
                omega_2 = numeric(row, lambda key: key.startswith("Omega_2"))
                rpm_values = [value for value in (omega_1, omega_2) if math.isfinite(value)]
                rpm_mean = mean(rpm_values) if rpm_values else math.nan
                fz_n = numeric(row, lambda key: key.startswith("F_z"))
                wall_force_n = numeric(row, lambda key: key.startswith("F_Wall"))
                wall_moment_nm = numeric(row, lambda key: key.startswith("M_Wall"))
                measurements.append(
                    {
                        "effect": effect,
                        "prop_diameter_in": prop_diameter_in,
                        "prop_diameter_m": prop_diameter_m,
                        "facility": facility,
                        "source_csv": filename,
                        "row_index": row_index,
                        "distance_cm": distance_cm,
                        "distance_over_diameter": distance_cm / prop_diameter_cm,
                        "distance_over_radius": distance_cm / (prop_diameter_cm / 2.0),
                        "pwm": pwm,
                        "rpm_1": omega_1,
                        "rpm_2": omega_2,
                        "rpm_mean": rpm_mean,
                        "fz_n": fz_n,
                        "wall_force_n": wall_force_n,
                        "abs_wall_force_n": abs(wall_force_n) if math.isfinite(wall_force_n) else math.nan,
                        "wall_moment_nm": wall_moment_nm,
                        "abs_wall_moment_nm": abs(wall_moment_nm) if math.isfinite(wall_moment_nm) else math.nan,
                    }
                )
    return measurements, dict(counts)


def uncertainty_group(field_name: str) -> str:
    base = field_name.split("_", 1)[1]
    if base.startswith("Fz"):
        return "Fz"
    if base.startswith("Fhtal"):
        return "Fhtal"
    if base.startswith("Mhtal"):
        return "Mhtal"
    return ""


def parse_direct_uncertainties(zip_bytes: bytes) -> list[dict[str, object]]:
    summaries: list[dict[str, object]] = []
    with zipfile.ZipFile(ZIP_PATH) if ZIP_PATH.exists() else zipfile.ZipFile(io.BytesIO(zip_bytes)) as archive:
        for name in archive.namelist():
            if not name.endswith(".mat"):
                continue
            mat = loadmat(io.BytesIO(archive.read(name)), squeeze_me=True, struct_as_record=False)
            roots = [key for key in mat if not key.startswith("__")]
            if not roots:
                continue
            root = roots[0]
            obj = mat[root]
            groups: dict[str, list[float]] = {"Fz": [], "Fhtal": [], "Mhtal": []}
            for field in getattr(obj, "_fieldnames", []):
                if not field.startswith("uncert_"):
                    continue
                group = uncertainty_group(field)
                if not group:
                    continue
                values = np.asarray(getattr(obj, field), dtype=float).ravel()
                values = values[np.isfinite(values)]
                groups[group].extend(float(value) for value in values)
            filename = name.rsplit("/", 1)[-1]
            for group, values in groups.items():
                if not values:
                    continue
                arr = np.asarray(values, dtype=float)
                unit = "N" if group in ("Fz", "Fhtal") else "N*m"
                for metric, value in [
                    ("uncertainty_sample_count", int(arr.size)),
                    ("uncertainty_min", float(np.min(arr))),
                    ("uncertainty_p50", float(np.percentile(arr, 50))),
                    ("uncertainty_p90", float(np.percentile(arr, 90))),
                    ("uncertainty_max", float(np.max(arr))),
                ]:
                    summaries.append(
                        {
                            "source_mat": filename,
                            "effect": root,
                            "uncertainty_group": group,
                            "metric": metric,
                            "value": value,
                            "unit": "count" if metric == "uncertainty_sample_count" else unit,
                        }
                    )
    return summaries


def attach_far_ratios(measurements: list[dict[str, object]]) -> None:
    baselines: dict[tuple[str, float, str, int], dict[str, object]] = {}
    for row in measurements:
        if row["effect"] not in ("ground", "ceiling"):
            continue
        key = (str(row["effect"]), float(row["prop_diameter_in"]), str(row["facility"]), int(row["pwm"]))
        if key not in baselines or float(row["distance_cm"]) > float(baselines[key]["distance_cm"]):
            baselines[key] = row
    for row in measurements:
        if row["effect"] not in ("ground", "ceiling"):
            row["far_baseline_distance_cm"] = ""
            row["fz_over_far_same_pwm"] = ""
            continue
        key = (str(row["effect"]), float(row["prop_diameter_in"]), str(row["facility"]), int(row["pwm"]))
        baseline = baselines[key]
        baseline_fz = float(baseline["fz_n"])
        row["far_baseline_distance_cm"] = baseline["distance_cm"]
        row["fz_over_far_same_pwm"] = float(row["fz_n"]) / baseline_fz if baseline_fz else math.nan


def add_source_rows(rows: list[dict[str, object]], counts: dict[str, int], zip_bytes: bytes) -> None:
    for metric, value, unit, source_url, note in [
        ("article_title", ARTICLE_TITLE, "text", ARTICLE_URL, "Open-access Springer/JIRS article title."),
        ("article_doi", ARTICLE_DOI_URL, "url", ARTICLE_DOI_URL, "Article DOI."),
        ("article_publication_date", PUBLICATION_DATE, "date", ARTICLE_URL, "Springer metadata online publication date."),
        ("supplement_zip_url", SUPPLEMENT_ZIP_URL, "url", SUPPLEMENT_ZIP_URL, "Springer supplementary zip used for CSV extraction."),
        ("supplement_zenodo_doi", ZENODO_DOI_URL, "url", ZENODO_URL, "Supplementary repository DOI cited by the article."),
        ("supplement_zip_size_bytes", len(zip_bytes), "bytes", SUPPLEMENT_ZIP_URL, "Downloaded/cached supplementary zip size."),
        ("zip_file_count", counts.get("zip_file_count", 0), "files", SUPPLEMENT_ZIP_URL, "All entries in the supplementary zip."),
        ("csv_file_count", counts.get("csv_file_count", 0), "files", SUPPLEMENT_ZIP_URL, "CSV files parsed for measurement rows."),
        ("mat_file_count", counts.get("mat_file_count", 0), "files", SUPPLEMENT_ZIP_URL, "MAT files include uncertainty and coefficient fields."),
        ("pdf_file_count", counts.get("pdf_file_count", 0), "files", SUPPLEMENT_ZIP_URL, "Measurement uncertainty PDF is present."),
    ]:
        add_metric(
            rows,
            row_type="surface_jirs2024_packet_source",
            name="jirs2024_surface_effect",
            metric=metric,
            value=value,
            unit=unit,
            source_file=ZIP_PATH,
            source_url=source_url,
            evidence_role="source_inventory",
            note=note,
        )


def add_measurement_rows(rows: list[dict[str, object]], measurements: list[dict[str, object]]) -> None:
    for row in measurements:
        is_wall = row["effect"] == "wall"
        metric = "wall_force_n" if is_wall else "fz_n"
        value = row["wall_force_n"] if is_wall else row["fz_n"]
        name = (
            f"{row['effect']}_{value_text(row['prop_diameter_in'])}in_{row['facility']}"
            f"_d{value_text(row['distance_cm'])}_pwm{row['pwm']}_r{row['row_index']}"
        )
        add_metric(
            rows,
            row_type="surface_jirs2024_packet_measurement",
            name=name,
            metric=metric,
            value=value,
            unit="N",
            source_file=ZIP_PATH,
            source_url=SUPPLEMENT_ZIP_URL,
            evidence_role="normalized_measurement",
            note="Distance is from propeller plane for ground/ceiling and closest motor axis for wall, following source CSV legend.",
            **row,
        )


def add_uncertainty_rows(rows: list[dict[str, object]], uncertainty_rows: list[dict[str, object]]) -> None:
    for row in uncertainty_rows:
        add_metric(
            rows,
            row_type="surface_jirs2024_packet_uncertainty_summary",
            name=f"{row['source_mat']}_{row['uncertainty_group']}",
            metric=str(row["metric"]),
            value=row["value"],
            unit=str(row["unit"]),
            source_file=ZIP_PATH,
            source_url=SUPPLEMENT_ZIP_URL,
            evidence_role="measurement_uncertainty",
            note=(
                "Direct MAT uncertainty fields only. Coefficient uncertainty fields are intentionally excluded "
                "because nondimensional coefficients can produce huge uncertainty values near zero denominators."
            ),
            source_mat=row["source_mat"],
            effect=row["effect"],
            uncertainty_group=row["uncertainty_group"],
        )


def finite(values: Iterable[object]) -> list[float]:
    clean = []
    for value in values:
        try:
            parsed = float(value)
        except (TypeError, ValueError):
            continue
        if math.isfinite(parsed):
            clean.append(parsed)
    return clean


def add_summary_rows(rows: list[dict[str, object]], measurements: list[dict[str, object]]) -> None:
    effect_counts = Counter(str(row["effect"]) for row in measurements)
    for effect, count in sorted(effect_counts.items()):
        add_metric(
            rows,
            row_type="surface_jirs2024_packet_summary",
            name="measurement_row_count",
            metric=f"{effect}_sample_count",
            value=count,
            unit="rows",
            source_file=OUTPUT,
            source_url=SUPPLEMENT_ZIP_URL,
            evidence_role="compact_surface_handoff",
            note="Parsed numeric rows from supplementary CSV files.",
        )

    for effect in ("ground", "ceiling"):
        effect_rows = [row for row in measurements if row["effect"] == effect]
        near_rows = [row for row in effect_rows if float(row["distance_cm"]) < 99.0]
        closest_rows: list[dict[str, object]] = []
        for prop in sorted({float(row["prop_diameter_in"]) for row in effect_rows}):
            min_distance = min(float(row["distance_cm"]) for row in effect_rows if float(row["prop_diameter_in"]) == prop)
            closest_rows.extend(
                row
                for row in effect_rows
                if float(row["prop_diameter_in"]) == prop and abs(float(row["distance_cm"]) - min_distance) <= 1e-9
            )
        ratio_rows = {
            f"{effect}_near_fz_ratio_min": (min(finite(row["fz_over_far_same_pwm"] for row in near_rows)), "ratio", "Minimum non-far Fz ratio versus far same-PWM baseline."),
            f"{effect}_near_fz_ratio_p50": (median(finite(row["fz_over_far_same_pwm"] for row in near_rows)), "ratio", "Median non-far Fz ratio versus far same-PWM baseline."),
            f"{effect}_near_fz_ratio_max": (max(finite(row["fz_over_far_same_pwm"] for row in near_rows)), "ratio", "Maximum non-far Fz ratio versus far same-PWM baseline."),
            f"{effect}_closest_fz_ratio_p50": (median(finite(row["fz_over_far_same_pwm"] for row in closest_rows)), "ratio", "Closest-distance median Fz ratio versus far same-PWM baseline."),
            f"{effect}_closest_fz_ratio_max": (max(finite(row["fz_over_far_same_pwm"] for row in closest_rows)), "ratio", "Closest-distance maximum Fz ratio versus far same-PWM baseline."),
            f"{effect}_closest_distance_over_radius_min": (min(finite(row["distance_over_radius"] for row in closest_rows)), "h/R", "Closest normalized distance in the parsed CSVs."),
            f"{effect}_closest_distance_over_radius_max": (max(finite(row["distance_over_radius"] for row in closest_rows)), "h/R", "Closest normalized distance in the parsed CSVs."),
            f"{effect}_far_baseline_distance_cm": (100.0, "cm", "Farthest ground/ceiling CSV distance used as far same-PWM baseline."),
        }
        for metric, (value, unit, note) in ratio_rows.items():
            add_metric(
                rows,
                row_type="surface_jirs2024_packet_summary",
                name=f"{effect}_effect_summary",
                metric=metric,
                value=value,
                unit=unit,
                source_file=OUTPUT,
                source_url=SUPPLEMENT_ZIP_URL,
                evidence_role="compact_surface_handoff",
                note=note,
            )

    wall_rows = [row for row in measurements if row["effect"] == "wall"]
    strongest_force = max(wall_rows, key=lambda row: float(row["abs_wall_force_n"]))
    strongest_moment = max(wall_rows, key=lambda row: float(row["abs_wall_moment_nm"]))
    wall_metrics = {
        "wall_abs_force_p50": (median(finite(row["abs_wall_force_n"] for row in wall_rows)), "N", "Median absolute wall force across parsed wall rows."),
        "wall_abs_force_max": (float(strongest_force["abs_wall_force_n"]), "N", "Maximum absolute wall force across parsed wall rows."),
        "wall_signed_force_min": (min(finite(row["wall_force_n"] for row in wall_rows)), "N", "Most negative signed wall force across parsed wall rows."),
        "wall_signed_force_max": (max(finite(row["wall_force_n"] for row in wall_rows)), "N", "Most positive signed wall force across parsed wall rows."),
        "wall_abs_moment_p50": (median(finite(row["abs_wall_moment_nm"] for row in wall_rows)), "N*m", "Median absolute wall moment across parsed wall rows."),
        "wall_abs_moment_max": (float(strongest_moment["abs_wall_moment_nm"]), "N*m", "Maximum absolute wall moment across parsed wall rows."),
        "wall_distance_over_radius_min": (min(finite(row["distance_over_radius"] for row in wall_rows)), "d/R", "Closest wall distance normalized by prop radius."),
        "wall_distance_over_radius_max": (max(finite(row["distance_over_radius"] for row in wall_rows)), "d/R", "Farthest wall distance normalized by prop radius."),
        "wall_strongest_force_source": (str(strongest_force["source_csv"]), "text", "CSV containing maximum absolute wall force."),
        "wall_strongest_force_pwm": (int(strongest_force["pwm"]), "PWM", "PWM of maximum absolute wall force row."),
        "wall_strongest_force_distance_over_radius": (float(strongest_force["distance_over_radius"]), "d/R", "Normalized distance of maximum absolute wall force row."),
        "wall_strongest_moment_source": (str(strongest_moment["source_csv"]), "text", "CSV containing maximum absolute wall moment."),
        "wall_strongest_moment_pwm": (int(strongest_moment["pwm"]), "PWM", "PWM of maximum absolute wall moment row."),
        "wall_strongest_moment_distance_over_radius": (float(strongest_moment["distance_over_radius"]), "d/R", "Normalized distance of maximum absolute wall moment row."),
    }
    for metric, (value, unit, note) in wall_metrics.items():
        add_metric(
            rows,
            row_type="surface_jirs2024_packet_summary",
            name="wall_effect_summary",
            metric=metric,
            value=value,
            unit=unit,
            source_file=OUTPUT,
            source_url=SUPPLEMENT_ZIP_URL,
            evidence_role="compact_surface_handoff",
            note=note,
        )

    add_metric(
        rows,
        row_type="surface_jirs2024_packet_method",
        name="handoff_guidance",
        metric="recommended_use",
        value=(
            "Use these rows as direct measured ground/ceiling thrust and wall force/moment anchors. Ground/ceiling "
            "ratios are normalized to the farthest 100 cm same-PWM row, while wall rows are signed force/moment "
            "measurements and should not be collapsed into pure thrust loss."
        ),
        unit="text",
        source_file=OUTPUT,
        source_url=SUPPLEMENT_ZIP_URL,
        evidence_role="handoff_guidance",
        note="Distance definitions follow the source CSV legends; uncertainty fields are available in the accompanying MAT/PDF files.",
    )


def sync_summary(packet_rows: list[dict[str, object]]) -> int:
    existing = read_rows(SUMMARY) if SUMMARY.exists() else []
    kept = [row for row in existing if not row.get("category", "").startswith("surface_jirs2024_packet_")]
    added: list[dict[str, str]] = []
    for row in packet_rows:
        added.append(
            {
                "category": str(row["row_type"]),
                "name": str(row["name"]),
                "metric": str(row["metric"]),
                "value": value_text(row["value"]),
                "unit": str(row["unit"]),
                "source": str(row.get("source_url") or row.get("source_file", "")),
            }
        )
    write_csv(SUMMARY, kept + added)
    return len(added)


def build_rows() -> list[dict[str, object]]:
    zip_bytes = download_zip()
    measurements, counts = parse_measurements(zip_bytes)
    uncertainty_rows = parse_direct_uncertainties(zip_bytes)
    attach_far_ratios(measurements)
    rows: list[dict[str, object]] = []
    add_source_rows(rows, counts, zip_bytes)
    add_measurement_rows(rows, measurements)
    add_uncertainty_rows(rows, uncertainty_rows)
    add_summary_rows(rows, measurements)
    return rows


def main() -> None:
    rows = build_rows()
    write_csv(OUTPUT, rows)
    synced = sync_summary(rows)
    print(f"Wrote {repo_path(OUTPUT)} with {len(rows)} rows")
    print(f"Synced {synced} rows into {repo_path(SUMMARY)}")


if __name__ == "__main__":
    main()
