"""Build a partial-ground / partial-ceiling lead packet.

Outputs:
  docs/data/partial_surface_effect_lead_packet.csv
  docs/data/fpv_model_validation_summary.csv rows with category
  partial_surface_effect_lead_packet_*

Cai, Gunasekaran, and Ol (Journal of Aircraft, 2023) is a useful lead for
finite-size ground and ceiling effects. The SOAR metadata exposes a detailed
abstract and DOI, but not a public full-text bundle. This packet therefore
records traceable threshold-level evidence and maps it to current rotor
diameters, without pretending to contain raw thrust/power curve points.
"""

from __future__ import annotations

import csv
import json
import math
import re
import urllib.request
from pathlib import Path
from typing import Iterable


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"

OUTPUT = DATA / "partial_surface_effect_lead_packet.csv"
SUMMARY = DATA / "fpv_model_validation_summary.csv"
SURFACE_NEARFIELD = DATA / "surface_nearfield_calibration_packet.csv"

SOAR_ITEM_UUID = "20c3f481-4556-41fa-8c79-a4b0b6d3c620"
SOAR_API_URL = f"https://soar.wichita.edu/server/api/core/items/{SOAR_ITEM_UUID}"
SOAR_BUNDLES_URL = f"{SOAR_API_URL}/bundles"
SOAR_LANDING_URL = f"https://soar.wichita.edu/items/{SOAR_ITEM_UUID}"
HANDLE_URL = "https://hdl.handle.net/10057/29736"
DOI_URL = "https://doi.org/10.2514/1.C036974"

NEGLIGIBLE_PLATE_DIAMETER_OVER_D = 0.5
COMPARABLE_PLATE_DIAMETER_OVER_D = 1.0
CURVE_FIT_RELATIVE_ACCURACY = 0.06
MINECRAFT_BLOCK_WIDTH_M = 1.0

PRESETS = {
    "racingQuad": {"radius_m": 0.0635, "note": "5-inch FPV baseline"},
    "apDrone": {"radius_m": 0.06477, "note": "Foxeer Donut 5145 proxy radius"},
    "cinewhoop": {"radius_m": 0.0380, "note": "3-inch ducted/cinewhoop scale"},
    "heavyLift": {"radius_m": 0.1270, "note": "10-inch lift scale"},
    "hexLift": {"radius_m": 0.1050, "note": "8.27-inch lift scale"},
    "octoLift": {"radius_m": 0.1150, "note": "9.06-inch lift scale"},
    "coaxialX8": {"radius_m": 0.1150, "note": "9.06-inch coaxial scale"},
}

AREA_GATE_SCAN = (0.0, 0.25, 0.5, 0.75, 1.0, 1.5)


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


def request_json(url: str, timeout_s: float = 30.0) -> dict[str, object]:
    request = urllib.request.Request(url, headers={"User-Agent": "codex-fpv-partial-surface"})
    with urllib.request.urlopen(request, timeout=timeout_s) as response:
        return json.load(response)


def metadata_value(item: dict[str, object], key: str, default: str = "") -> str:
    metadata = item.get("metadata", {})
    if not isinstance(metadata, dict):
        return default
    entries = metadata.get(key, [])
    if not isinstance(entries, list) or not entries:
        return default
    first = entries[0]
    if not isinstance(first, dict):
        return default
    return str(first.get("value", default))


def add_metric(
    rows: list[dict[str, object]],
    *,
    row_type: str,
    name: str,
    metric: str,
    value: object,
    unit: str,
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
        "source_file": repo_path(OUTPUT),
        "source_url": source_url,
        "evidence_role": evidence_role,
        "note": note,
    }
    row.update(extra)
    rows.append(row)


def smoothstep_edge_gate(plate_diameter_over_d: float) -> float:
    if plate_diameter_over_d <= NEGLIGIBLE_PLATE_DIAMETER_OVER_D:
        return 0.0
    if plate_diameter_over_d >= COMPARABLE_PLATE_DIAMETER_OVER_D:
        return 1.0
    x = (plate_diameter_over_d - NEGLIGIBLE_PLATE_DIAMETER_OVER_D) / (
        COMPARABLE_PLATE_DIAMETER_OVER_D - NEGLIGIBLE_PLATE_DIAMETER_OVER_D
    )
    return x * x * (3.0 - 2.0 * x)


def current_h1_ground_multipliers() -> dict[str, float]:
    if not SURFACE_NEARFIELD.exists():
        return {}
    rows = read_rows(SURFACE_NEARFIELD)
    values: dict[str, float] = {}
    for row in rows:
        if (
            row.get("row_type") == "surface_nearfield_current_ground_ceiling"
            and row.get("metric") == "current_ground_multiplier"
            and row.get("name", "").endswith("_h_over_r_1.0")
        ):
            preset = row["name"].replace("_h_over_r_1.0", "")
            try:
                values[preset] = float(row["value"])
            except ValueError:
                pass
    return values


def add_source_rows(rows: list[dict[str, object]], item: dict[str, object], bundles: dict[str, object]) -> None:
    abstract = metadata_value(item, "dc.description.abstract")
    title = metadata_value(item, "dc.title")
    doi = metadata_value(item, "dc.identifier.doi")
    citation = metadata_value(item, "dc.identifier.citation")
    begin_page = metadata_value(item, "dc.source.beginpage")
    end_page = metadata_value(item, "dc.source.endpage")
    bundle_count = 0
    embedded = bundles.get("_embedded", {})
    if isinstance(embedded, dict):
        bundle_list = embedded.get("bundles", [])
        if isinstance(bundle_list, list):
            bundle_count = len(bundle_list)

    for metric, value, unit, source_url in [
        ("title", title, "text", SOAR_API_URL),
        ("doi", doi, "text", DOI_URL),
        ("citation", citation, "text", SOAR_API_URL),
        ("begin_page", begin_page, "page", SOAR_API_URL),
        ("end_page", end_page, "page", SOAR_API_URL),
        ("public_bundle_count", bundle_count, "count", SOAR_BUNDLES_URL),
        ("abstract_character_count", len(abstract), "count", SOAR_API_URL),
    ]:
        add_metric(
            rows,
            row_type="partial_surface_effect_lead_packet_source",
            name="Cai/Gunasekaran/Ol partial surface paper",
            metric=metric,
            value=value,
            unit=unit,
            source_url=source_url,
            evidence_role="source_metadata",
            note="SOAR metadata and DOI traceability. Bundle count is checked separately because no public full-text bundle is exposed by the repository item.",
        )


def add_abstract_check_rows(rows: list[dict[str, object]], item: dict[str, object]) -> None:
    abstract = metadata_value(item, "dc.description.abstract")
    checks = {
        "mentions_partial_ground": "partial ground" in abstract.lower(),
        "mentions_partial_ceiling": "partial ceiling" in abstract.lower(),
        "mentions_circular_and_annular_plates": "circular and annular plates" in abstract.lower(),
        "mentions_force_balance": "force balance" in abstract.lower(),
        "mentions_plate_equal_prop_diameter": bool(re.search(r"diameter equal to that of the propeller", abstract, re.I)),
        "mentions_less_than_half_prop_diameter": bool(re.search(r"less than half of the propeller diameter", abstract, re.I)),
        "mentions_superimposed": "superimposed" in abstract.lower(),
        "mentions_curve_fit_within_6_percent": "within 6%" in abstract.lower(),
    }
    for metric, value in checks.items():
        add_metric(
            rows,
            row_type="partial_surface_effect_lead_packet_abstract_check",
            name="SOAR abstract text check",
            metric=metric,
            value=1 if value else 0,
            unit="boolean",
            source_url=SOAR_API_URL,
            evidence_role="abstract_text_check",
            note="Boolean check against repository abstract text; use the DOI/full text for curve digitization when available.",
        )


def add_threshold_rows(rows: list[dict[str, object]]) -> None:
    threshold_metrics = {
        "negligible_effect_plate_diameter_over_prop_diameter": (
            NEGLIGIBLE_PLATE_DIAMETER_OVER_D,
            "D",
            "Repository abstract reports ground or ceiling effect is negligible for a plate less than half propeller diameter.",
        ),
        "negligible_effect_plate_area_over_prop_disk_area": (
            NEGLIGIBLE_PLATE_DIAMETER_OVER_D**2,
            "area ratio",
            "Circular patch area ratio corresponding to a 0.5D plate diameter.",
        ),
        "comparable_to_infinite_plate_diameter_over_prop_diameter": (
            COMPARABLE_PLATE_DIAMETER_OVER_D,
            "D",
            "Repository abstract reports a plate with diameter equal to the propeller diameter gives thrust and power effects comparable to an infinite plate.",
        ),
        "comparable_to_infinite_plate_area_over_prop_disk_area": (
            COMPARABLE_PLATE_DIAMETER_OVER_D**2,
            "area ratio",
            "Circular patch area ratio for a 1.0D plate diameter.",
        ),
        "curve_fit_relative_accuracy": (
            CURVE_FIT_RELATIVE_ACCURACY,
            "fraction",
            "Repository abstract reports a curve fit to thrust and power data accurate to within 6%.",
        ),
        "ceiling_superposition_confidence_over_ground": (
            1.0,
            "qualitative flag",
            "Repository abstract says circular/annular superposition holds for ceiling effect, less so for ground effect.",
        ),
    }
    for metric, (value, unit, note) in threshold_metrics.items():
        add_metric(
            rows,
            row_type="partial_surface_effect_lead_packet_threshold",
            name="partial-surface threshold",
            metric=metric,
            value=value,
            unit=unit,
            source_url=SOAR_API_URL,
            evidence_role="abstract_threshold",
            note=note,
        )


def add_gate_scan_rows(rows: list[dict[str, object]]) -> None:
    for plate_diameter_over_d in AREA_GATE_SCAN:
        gate = smoothstep_edge_gate(plate_diameter_over_d)
        add_metric(
            rows,
            row_type="partial_surface_effect_lead_packet_gate_scan",
            name=f"plate_diameter_over_D_{plate_diameter_over_d:g}",
            metric="proposed_initial_partial_surface_gate",
            value=gate,
            unit="multiplier",
            source_url=SOAR_API_URL,
            evidence_role="derived_gate_candidate",
            note=(
                "Derived initial implementation candidate: zero through 0.5D, smooth transition to full by 1.0D. "
                "Replace with digitized thrust/power curves when the paper data are available."
            ),
            plate_diameter_over_prop_diameter=plate_diameter_over_d,
            circular_patch_area_over_prop_disk_area=plate_diameter_over_d**2,
            source_negligible_limit_D=NEGLIGIBLE_PLATE_DIAMETER_OVER_D,
            source_full_limit_D=COMPARABLE_PLATE_DIAMETER_OVER_D,
        )


def add_preset_scale_rows(rows: list[dict[str, object]]) -> None:
    h1_ground = current_h1_ground_multipliers()
    for preset, spec in PRESETS.items():
        radius = float(spec["radius_m"])
        diameter = 2.0 * radius
        negligible_patch = NEGLIGIBLE_PLATE_DIAMETER_OVER_D * diameter
        comparable_patch = COMPARABLE_PLATE_DIAMETER_OVER_D * diameter
        current_h1 = h1_ground.get(preset, math.nan)
        for metric, value, unit, note in [
            ("propeller_diameter", diameter, "m", "Current preset rotor diameter used for partial-surface scaling."),
            ("negligible_patch_diameter", negligible_patch, "m", "Patch diameter below this is expected to have negligible ground/ceiling effect by the abstract threshold."),
            ("comparable_to_infinite_patch_diameter", comparable_patch, "m", "Patch diameter at this scale is expected to be comparable to an infinite plate by the abstract threshold."),
            ("minecraft_1m_block_width_over_prop_diameter", MINECRAFT_BLOCK_WIDTH_M / diameter, "D", "One full Minecraft block is much larger than a single prop disk for all current presets."),
            ("current_h_over_r_1_ground_multiplier", current_h1, "multiplier", "Current full-ground h/R=1 multiplier from the existing near-surface packet, if available."),
        ]:
            add_metric(
                rows,
                row_type="partial_surface_effect_lead_packet_preset_scale",
                name=preset,
                metric=metric,
                value=value,
                unit=unit,
                source_url=SOAR_API_URL if metric != "current_h_over_r_1_ground_multiplier" else repo_path(SURFACE_NEARFIELD),
                evidence_role="preset_scale_mapping",
                note=note,
                preset=preset,
                rotor_radius_m=radius,
                preset_note=spec["note"],
            )


def add_summary_rows(rows: list[dict[str, object]]) -> None:
    racing_diameter = 2.0 * PRESETS["racingQuad"]["radius_m"]
    heavy_diameter = 2.0 * PRESETS["heavyLift"]["radius_m"]
    summary_metrics = {
        "source_public_bundle_count": (0, "count"),
        "negligible_plate_diameter_over_D": (NEGLIGIBLE_PLATE_DIAMETER_OVER_D, "D"),
        "full_like_plate_diameter_over_D": (COMPARABLE_PLATE_DIAMETER_OVER_D, "D"),
        "negligible_plate_area_over_disk_area": (NEGLIGIBLE_PLATE_DIAMETER_OVER_D**2, "area ratio"),
        "curve_fit_accuracy": (CURVE_FIT_RELATIVE_ACCURACY, "fraction"),
        "racingQuad_negligible_patch_diameter": (NEGLIGIBLE_PLATE_DIAMETER_OVER_D * racing_diameter, "m"),
        "racingQuad_full_like_patch_diameter": (COMPARABLE_PLATE_DIAMETER_OVER_D * racing_diameter, "m"),
        "heavyLift_negligible_patch_diameter": (NEGLIGIBLE_PLATE_DIAMETER_OVER_D * heavy_diameter, "m"),
        "heavyLift_full_like_patch_diameter": (COMPARABLE_PLATE_DIAMETER_OVER_D * heavy_diameter, "m"),
        "minecraft_1m_over_racingQuad_prop_diameter": (MINECRAFT_BLOCK_WIDTH_M / racing_diameter, "D"),
        "needs_fulltext_digitization": (1, "boolean"),
    }
    for metric, (value, unit) in summary_metrics.items():
        add_metric(
            rows,
            row_type="partial_surface_effect_lead_packet_summary",
            name="partial surface handoff",
            metric=metric,
            value=value,
            unit=unit,
            source_url="" if metric != "needs_fulltext_digitization" else DOI_URL,
            evidence_role="compact_partial_surface_handoff",
            note="Use as an area-gating and digitization target for partial ground/ceiling, not as raw thrust/power curves.",
        )
    add_metric(
        rows,
        row_type="partial_surface_effect_lead_packet_method",
        name="method",
        metric="scope_and_caveat",
        value=(
            "SOAR exposes metadata and abstract but no public full-text bundle. The packet stores abstract-level "
            "thresholds: <0.5D patch negligible, 1.0D patch comparable to infinite plate, circular/annular "
            "superposition, and 6% curve-fit claim. Raw curve rows still need full-text digitization or a data table."
        ),
        unit="text",
        source_url=SOAR_LANDING_URL,
        evidence_role="method_caveat",
        note="Do not use this packet to tune absolute ground-effect multipliers without the existing ZJU/near-surface rows.",
    )


def build_rows() -> list[dict[str, object]]:
    item = request_json(SOAR_API_URL)
    bundles = request_json(SOAR_BUNDLES_URL)
    rows: list[dict[str, object]] = []
    add_source_rows(rows, item, bundles)
    add_abstract_check_rows(rows, item)
    add_threshold_rows(rows)
    add_gate_scan_rows(rows)
    add_preset_scale_rows(rows)
    add_summary_rows(rows)
    return rows


def sync_summary(packet_rows: Iterable[dict[str, object]]) -> int:
    existing = read_rows(SUMMARY)
    kept = [row for row in existing if not row.get("category", "").startswith("partial_surface_effect_lead_packet_")]
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


def main() -> None:
    rows = build_rows()
    write_csv(OUTPUT, rows)
    synced = sync_summary(rows)
    print(f"Wrote {repo_path(OUTPUT)} with {len(rows)} rows")
    print(f"Synced {synced} rows into {repo_path(SUMMARY)}")


if __name__ == "__main__":
    main()
