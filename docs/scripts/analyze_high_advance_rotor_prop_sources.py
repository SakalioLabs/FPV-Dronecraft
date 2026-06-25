"""Build a high-advance-ratio propeller/rotor source packet.

Outputs:
  docs/data/apc_high_advance_propeller_reference.csv
  docs/data/high_advance_rotor_source_inventory.csv
  docs/data/high_advance_rotor_prop_packet.csv
  docs/data/fpv_model_validation_summary.csv rows with category high_advance_packet_*

APC performance files are axial propeller predictions generated from actual
propeller geometry with a vortex-method tool. They extend beyond the UIUC
5-inch experimental J range, but they are not edgewise rotor data and should not
be treated as direct retreating-blade-stall calibration.
"""

from __future__ import annotations

import argparse
import csv
import math
import re
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable
from urllib.request import Request, urlopen


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"

APC_REFERENCE = DATA / "apc_high_advance_propeller_reference.csv"
SOURCE_INVENTORY = DATA / "high_advance_rotor_source_inventory.csv"
PACKET = DATA / "high_advance_rotor_prop_packet.csv"
SUMMARY = DATA / "fpv_model_validation_summary.csv"

APC_PAGE_URL = "https://www.apcprop.com/technical-information/performance-data/"
APC_FILE_BASE_URL = "https://www.apcprop.com/files/"
UIUC_PAGE_URL = "https://m-selig.ae.illinois.edu/props/volume-2/propDB-volume-2.html"
NASA_HARRIS_PDF_URL = "https://rotorcraft.arc.nasa.gov/Publications/files/NASA%20CR%202008-215370%20Harris.pdf"
NASA_KOTTAPALLI_PDF_URL = "https://rotorcraft.arc.nasa.gov/Publications/files/A-6-F_Kottapalli.pdf"
NASA_DATTA_PDF_URL = "https://rotorcraft.arc.nasa.gov/Publications/files/Datta_Yeo_Norman_JAHS%20Apr2013.pdf"
UMD_MACH_SCALE_PDF_URL = "https://drum.lib.umd.edu/bitstreams/c5e6d200-dc11-4826-b1d7-74dc4298321c/download"
ERF_CC_ROTOR_PDF_URL = "https://dspace-erf.nlr.nl/bitstreams/44f2b3bd-67ae-4003-89d7-4479c27cb44d/download"
DLR_ERF_PAGE_URL = "https://elib.dlr.de/191749/"

CURRENT_LIFT_DISSYMMETRY_MU_START = 0.08
CURRENT_LIFT_DISSYMMETRY_MU_END = 0.34
CURRENT_RETREATING_STALL_MU_START = 0.42
CURRENT_RETREATING_STALL_MU_END = 0.82
CURRENT_HIGH_ADVANCE_LOSS_MU_START = 0.46
UIUC_EXPERIMENTAL_J_MAX = 0.571


@dataclass(frozen=True)
class ApcProp:
    filename: str
    label: str
    diameter_in: float
    pitch_in: float
    blade_count: int
    role: str

    @property
    def url(self) -> str:
        return APC_FILE_BASE_URL + self.filename


SELECTED_APC_PROPS = [
    ApcProp("PER3_5x45E.dat", "APC_5x4.5E", 5.0, 4.5, 2, "5-inch moderate-pitch axial reference"),
    ApcProp("PER3_5x4E-3.dat", "APC_5x4E_3blade", 5.0, 4.0, 3, "5-inch three-blade axial reference"),
    ApcProp("PER3_51x50E-3.dat", "APC_5.1x5.0E_3blade", 5.1, 5.0, 3, "5.1-inch three-blade FPV-adjacent reference"),
    ApcProp("PER3_5x5E.dat", "APC_5x5E", 5.0, 5.0, 2, "5-inch square-pitch axial reference"),
    ApcProp("PER3_5x75E.dat", "APC_5x7.5E", 5.0, 7.5, 2, "5-inch high-pitch bridge toward high J"),
    ApcProp("PER3_5x11E.dat", "APC_5x11E", 5.0, 11.0, 2, "5-inch extreme-pitch high-J coverage"),
    ApcProp("PER3_6x6E.dat", "APC_6x6E", 6.0, 6.0, 2, "6-inch square-pitch adjacent scale reference"),
]


TARGETS = [
    ("uiuc_5in_forward_flow_max", UIUC_EXPERIMENTAL_J_MAX, "UIUC selected 5-inch experimental upper J"),
    ("code_lift_dissymmetry_start", math.pi * CURRENT_LIFT_DISSYMMETRY_MU_START, "current lift-dissymmetry start"),
    ("code_lift_dissymmetry_end", math.pi * CURRENT_LIFT_DISSYMMETRY_MU_END, "current lift-dissymmetry end"),
    ("code_retreating_stall_start", math.pi * CURRENT_RETREATING_STALL_MU_START, "current retreating-blade-stall start"),
    ("code_high_advance_loss_start", math.pi * CURRENT_HIGH_ADVANCE_LOSS_MU_START, "current high-advance-loss start"),
    ("code_retreating_stall_end", math.pi * CURRENT_RETREATING_STALL_MU_END, "current retreating-blade-stall upper end"),
]


NUMBER_RE = re.compile(r"[-+]?\d*\.\d+|[-+]?\d+")


def repo_path(path: Path) -> str:
    return path.resolve().relative_to(ROOT).as_posix()


def read_rows(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8") as handle:
        return list(csv.DictReader(handle))


def write_csv(path: Path, rows: Iterable[dict[str, object]]) -> None:
    rows = list(rows)
    path.parent.mkdir(parents=True, exist_ok=True)
    fieldnames: list[str] = []
    for row in rows:
        for key in row:
            if key not in fieldnames:
                fieldnames.append(key)
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        for row in rows:
            writer.writerow(row)


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


def fetch_text(url: str, *, timeout_s: int = 60) -> str:
    last_error: Exception | None = None
    for attempt in range(1, 5):
        try:
            request = Request(url, headers={"User-Agent": "codex-high-advance-parser"})
            with urlopen(request, timeout=timeout_s) as response:
                return response.read().decode("utf-8", "replace")
        except Exception as exc:  # pragma: no cover - network retry path
            last_error = exc
            if attempt == 4:
                break
            time.sleep(min(2**attempt, 8))
    raise RuntimeError(f"failed to download {url}") from last_error


def parse_apc_file(prop: ApcProp) -> list[dict[str, object]]:
    text = fetch_text(prop.url)
    title = ""
    software_version = ""
    simulation_date = ""
    rows: list[dict[str, object]] = []
    current_rpm: int | None = None
    static_by_rpm: dict[int, tuple[float, float]] = {}

    for line_number, line in enumerate(text.splitlines(), start=1):
        if line_number == 1:
            title = line.strip()
        if "v202" in line and not software_version:
            software_version = line.strip()
        if "Simulation Date:" in line:
            simulation_date = line.split("Simulation Date:", 1)[1].strip()
        if "PROP RPM" in line:
            match = re.search(r"PROP RPM\s*=\s*(\d+)", line)
            if match:
                current_rpm = int(match.group(1))
            continue
        values = [float(value) for value in NUMBER_RE.findall(line)]
        if current_rpm is None or len(values) < 15:
            continue
        speed_mph, j, efficiency, ct, cp = values[:5]
        if j < 0 or j > 6 or abs(ct) > 5 or abs(cp) > 5:
            continue
        power_hp, torque_in_lbf, thrust_lbf, power_w, torque_n_m, thrust_n = values[5:11]
        thrust_g_w, mach, reyn75, fom = values[11:15]
        if j == 0:
            static_by_rpm[current_rpm] = (ct, cp)
        static_ct, static_cp = static_by_rpm.get(current_rpm, (math.nan, math.nan))
        ct_over_static = ct / static_ct if static_ct and math.isfinite(static_ct) else math.nan
        cp_over_static = cp / static_cp if static_cp and math.isfinite(static_cp) else math.nan
        qt_static = static_cp / static_ct if static_ct and static_cp and math.isfinite(static_ct) else math.nan
        qt_current = cp / ct if ct and math.isfinite(ct) else math.nan
        qt_over_static = qt_current / qt_static if qt_static and math.isfinite(qt_static) else math.nan
        rows.append(
            {
                "row_type": "apc_performance_point",
                "propeller": prop.label,
                "filename": prop.filename,
                "source_url": prop.url,
                "apc_title": title,
                "software_version": software_version,
                "simulation_date": simulation_date,
                "diameter_in": prop.diameter_in,
                "pitch_in": prop.pitch_in,
                "pitch_to_diameter": prop.pitch_in / prop.diameter_in,
                "blade_count": prop.blade_count,
                "role": prop.role,
                "rpm": current_rpm,
                "speed_mph": speed_mph,
                "speed_m_s": speed_mph * 0.44704,
                "j": j,
                "code_mu_equivalent_j_over_pi": j / math.pi,
                "efficiency_pe": efficiency,
                "ct": ct,
                "cp": cp,
                "power_hp": power_hp,
                "torque_in_lbf": torque_in_lbf,
                "thrust_lbf": thrust_lbf,
                "power_w": power_w,
                "torque_n_m": torque_n_m,
                "thrust_n": thrust_n,
                "thrust_g_per_w": thrust_g_w,
                "mach_tip": mach,
                "reynolds_75": reyn75,
                "figure_of_merit": fom,
                "same_rpm_static_ct": static_ct,
                "same_rpm_static_cp": static_cp,
                "ct_over_same_rpm_static_ct": ct_over_static,
                "cp_over_same_rpm_static_cp": cp_over_static,
                "q_over_t_over_same_rpm_static_q_over_t": qt_over_static,
            }
        )
    if not rows:
        raise RuntimeError(f"no APC performance rows parsed from {prop.filename}")
    return rows


def nearest_row(rows: list[dict[str, object]], target_j: float) -> dict[str, object]:
    return min(rows, key=lambda row: abs(float(row["j"]) - target_j))


def summarize_prop(rows: list[dict[str, object]]) -> dict[str, object]:
    prop = str(rows[0]["propeller"])
    positive = [row for row in rows if float(row["ct"]) > 0]
    nonpositive = [row for row in rows if float(row["ct"]) <= 0]
    max_positive = max(positive, key=lambda row: float(row["j"])) if positive else rows[-1]
    nearest_zero = min(rows, key=lambda row: abs(float(row["ct"])))
    return {
        "row_type": "apc_selected_prop_summary",
        "propeller": prop,
        "filename": rows[0]["filename"],
        "source_url": rows[0]["source_url"],
        "role": rows[0]["role"],
        "diameter_in": rows[0]["diameter_in"],
        "pitch_in": rows[0]["pitch_in"],
        "pitch_to_diameter": rows[0]["pitch_to_diameter"],
        "blade_count": rows[0]["blade_count"],
        "row_count": len(rows),
        "rpm_count": len({int(row["rpm"]) for row in rows}),
        "j_max": max(float(row["j"]) for row in rows),
        "code_mu_equivalent_max": max(float(row["code_mu_equivalent_j_over_pi"]) for row in rows),
        "highest_positive_ct_j": float(max_positive["j"]),
        "highest_positive_ct_mu_equivalent": float(max_positive["code_mu_equivalent_j_over_pi"]),
        "nearest_zero_ct_j": float(nearest_zero["j"]),
        "nearest_zero_ct": float(nearest_zero["ct"]),
        "has_nonpositive_ct": bool(nonpositive),
        "min_ct": min(float(row["ct"]) for row in rows),
        "max_ct": max(float(row["ct"]) for row in rows),
        "max_efficiency_pe": max(float(row["efficiency_pe"]) for row in rows),
    }


def build_source_inventory(apc_rows: list[dict[str, object]], prop_summaries: list[dict[str, object]]) -> list[dict[str, object]]:
    rows: list[dict[str, object]] = [
        {
            "row_type": "source_inventory",
            "name": "APC_propeller_performance_files",
            "source_url": APC_PAGE_URL,
            "evidence_type": "axial_propeller_prediction_data",
            "advance_ratio_range": "selected files span J=0..2.466, equivalent code mu=0..0.785",
            "use": "High-J axial CT/CP trend and zero-thrust/windmilling boundary checks beyond UIUC 5-inch experimental range.",
            "caveat": "APC states these are proprietary vortex-method predictions from actual geometry, not wind-tunnel measurements and not edgewise rotor mu data.",
        },
        {
            "row_type": "source_inventory",
            "name": "UIUC_propeller_database",
            "source_url": UIUC_PAGE_URL,
            "evidence_type": "small_propeller_wind_tunnel_data",
            "advance_ratio_range": f"local selected 5-inch rows reach about J={UIUC_EXPERIMENTAL_J_MAX:.3f}",
            "use": "Low-to-mid J measured CT/CP anchor for 5-inch propellers.",
            "caveat": "Does not reach current retreating-blade-stall/high-advance-loss thresholds.",
        },
        {
            "row_type": "source_inventory",
            "name": "NASA_CR_2008_215370_Harris",
            "source_url": NASA_HARRIS_PDF_URL,
            "evidence_type": "high_advance_ratio_rotor_theory_vs_test",
            "advance_ratio_range": "high advance ratio rotor performance theory/test comparison",
            "use": "Rotorcraft high-mu method source for edgewise/reverse-flow behavior.",
            "caveat": "Rotorcraft-scale data; use for threshold/method direction, not direct 5-inch prop coefficients.",
        },
        {
            "row_type": "source_inventory",
            "name": "NASA_UH60A_slowed_rotor_Kottapalli",
            "source_url": NASA_KOTTAPALLI_PDF_URL,
            "evidence_type": "full_scale_slowed_rotor_high_mu_wind_tunnel",
            "advance_ratio_range": "mu=0.3..1.0, 40% NR slowed rotor",
            "use": "Performance and loads correlation source with CT, CP, L/DE, torque, H-force, and blade-load trends.",
            "caveat": "Full-scale UH-60A rotor; not an FPV propeller model.",
        },
        {
            "row_type": "source_inventory",
            "name": "NASA_Datta_Yeo_Norman_UH60A_JAHS",
            "source_url": NASA_DATTA_PDF_URL,
            "evidence_type": "full_scale_slowed_rotor_high_mu_measurement",
            "advance_ratio_range": "up to mu=1.0",
            "use": "High-mu aeromechanics source for reverse chord dynamic stall, retreating-side behavior, loads, hub loads, and airloads.",
            "caveat": "Use for qualitative/threshold behavior and high-mu caution, not small-prop axial CT/CP.",
        },
        {
            "row_type": "source_inventory",
            "name": "UMD_mach_scale_rotor_high_mu",
            "source_url": UMD_MACH_SCALE_PDF_URL,
            "evidence_type": "mach_scale_rotor_high_mu_test",
            "advance_ratio_range": "reported thrust reversal between mu=0.8 and 0.9",
            "use": "Edgewise rotor high-mu thrust-reversal context close to the current stall upper range.",
            "caveat": "Mach-scale rotor data, not FPV propeller data.",
        },
        {
            "row_type": "source_inventory",
            "name": "ERF_high_advance_ratio_circulation_control_rotor",
            "source_url": ERF_CC_ROTOR_PDF_URL,
            "evidence_type": "high_advance_ratio_rotor_method",
            "advance_ratio_range": "mu=0.4..0.7",
            "use": "Rotor analytical-method context across the current stall onset band.",
            "caveat": "Circulation-control rotor; only a method/phenomenology anchor for FPV.",
        },
        {
            "row_type": "source_inventory",
            "name": "DLR_ERF2023_rotor_stall_computation",
            "source_url": DLR_ERF_PAGE_URL,
            "evidence_type": "rotor_stall_code_validation_source",
            "advance_ratio_range": "dynamic-stall rotor-code context",
            "use": "Source triage for future dynamic-stall validation references.",
            "caveat": "DLR page indicates the PDF access status may be limited; keep as source inventory until data is accessible.",
        },
    ]
    rows.extend(prop_summaries)
    rows.append(
        {
            "row_type": "apc_selected_set_summary",
            "name": "selected_apc_high_advance_files",
            "source_url": APC_PAGE_URL,
            "selected_file_count": len(prop_summaries),
            "parsed_row_count": len(apc_rows),
            "j_max": max(float(row["j"]) for row in apc_rows),
            "code_mu_equivalent_max": max(float(row["code_mu_equivalent_j_over_pi"]) for row in apc_rows),
            "highest_positive_ct_mu_equivalent": max(float(row["highest_positive_ct_mu_equivalent"]) for row in prop_summaries),
            "note": "Selected to bracket 5-inch FPV-like pitch, three-blade variants, and high-pitch axial J coverage.",
        }
    )
    return rows


def add_metric(
    rows: list[dict[str, str]],
    *,
    row_type: str,
    name: str,
    metric: str,
    value: object,
    unit: str,
    source_file: Path,
    source_url: str = "",
    evidence_role: str = "",
    note: str = "",
) -> None:
    rows.append(
        {
            "row_type": row_type,
            "name": name,
            "metric": metric,
            "value": value_text(value),
            "unit": unit,
            "source_file": repo_path(source_file),
            "source_url": source_url,
            "evidence_role": evidence_role,
            "note": note,
        }
    )


def add_threshold_metrics(packet: list[dict[str, str]]) -> None:
    threshold_metrics = [
        ("uiuc_5in_experimental_max", UIUC_EXPERIMENTAL_J_MAX, UIUC_EXPERIMENTAL_J_MAX / math.pi, UIUC_PAGE_URL),
        (
            "current_lift_dissymmetry_start",
            math.pi * CURRENT_LIFT_DISSYMMETRY_MU_START,
            CURRENT_LIFT_DISSYMMETRY_MU_START,
            "drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/DronePhysics.java",
        ),
        (
            "current_lift_dissymmetry_end",
            math.pi * CURRENT_LIFT_DISSYMMETRY_MU_END,
            CURRENT_LIFT_DISSYMMETRY_MU_END,
            "drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/DronePhysics.java",
        ),
        (
            "current_retreating_stall_start",
            math.pi * CURRENT_RETREATING_STALL_MU_START,
            CURRENT_RETREATING_STALL_MU_START,
            "drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/DronePhysics.java",
        ),
        (
            "current_high_advance_loss_start",
            math.pi * CURRENT_HIGH_ADVANCE_LOSS_MU_START,
            CURRENT_HIGH_ADVANCE_LOSS_MU_START,
            "drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/DronePhysics.java",
        ),
        (
            "current_retreating_stall_end",
            math.pi * CURRENT_RETREATING_STALL_MU_END,
            CURRENT_RETREATING_STALL_MU_END,
            "drone-sim-core/src/main/java/com/tenicana/dronecraft/sim/DronePhysics.java",
        ),
    ]
    for name, j_value, mu_value, source in threshold_metrics:
        add_metric(
            packet,
            row_type="high_advance_packet_threshold",
            name=name,
            metric="equivalent_axial_propeller_j",
            value=j_value,
            unit="J",
            source_file=PACKET,
            source_url=source,
            evidence_role="unit_mapping",
            note="For axial propeller data only: J = pi * project_mu. Edgewise rotor mu is a different flow condition.",
        )
        add_metric(
            packet,
            row_type="high_advance_packet_threshold",
            name=name,
            metric="project_mu",
            value=mu_value,
            unit="mu",
            source_file=PACKET,
            source_url=source,
            evidence_role="unit_mapping",
        )


def build_packet(
    apc_rows: list[dict[str, object]],
    source_rows: list[dict[str, object]],
    prop_summaries: list[dict[str, object]],
) -> list[dict[str, str]]:
    packet: list[dict[str, str]] = []

    for row in [r for r in source_rows if r.get("row_type") == "source_inventory"]:
        add_metric(
            packet,
            row_type="high_advance_packet_source_inventory",
            name=str(row["name"]),
            metric="advance_ratio_range",
            value=row["advance_ratio_range"],
            unit="text",
            source_file=SOURCE_INVENTORY,
            source_url=str(row["source_url"]),
            evidence_role=str(row["evidence_type"]),
            note=str(row["caveat"]),
        )
        add_metric(
            packet,
            row_type="high_advance_packet_source_inventory",
            name=str(row["name"]),
            metric="use",
            value=row["use"],
            unit="text",
            source_file=SOURCE_INVENTORY,
            source_url=str(row["source_url"]),
            evidence_role=str(row["evidence_type"]),
        )

    prop_metric_units = [
        ("row_count", "count"),
        ("rpm_count", "count"),
        ("diameter_in", "in"),
        ("pitch_in", "in"),
        ("pitch_to_diameter", "ratio"),
        ("blade_count", "count"),
        ("j_max", "J"),
        ("code_mu_equivalent_max", "mu"),
        ("highest_positive_ct_j", "J"),
        ("highest_positive_ct_mu_equivalent", "mu"),
        ("nearest_zero_ct_j", "J"),
        ("nearest_zero_ct", "CT"),
        ("max_efficiency_pe", "efficiency"),
    ]
    for row in prop_summaries:
        for metric, unit in prop_metric_units:
            add_metric(
                packet,
                row_type="high_advance_packet_apc_prop_summary",
                name=str(row["propeller"]),
                metric=metric,
                value=row[metric],
                unit=unit,
                source_file=SOURCE_INVENTORY,
                source_url=str(row["source_url"]),
                evidence_role="apc_axial_prediction_summary",
                note=str(row["role"]),
            )

    rows_by_prop: dict[str, list[dict[str, object]]] = {}
    for row in apc_rows:
        rows_by_prop.setdefault(str(row["propeller"]), []).append(row)
    target_metric_units = [
        ("target_j", "J"),
        ("target_mu_equivalent", "mu"),
        ("nearest_j", "J"),
        ("nearest_mu_equivalent", "mu"),
        ("target_delta_j", "J"),
        ("target_within_file_j_range", "boolean"),
        ("ct", "CT"),
        ("cp", "CP"),
        ("efficiency_pe", "efficiency"),
        ("ct_over_same_rpm_static_ct", "ratio"),
        ("cp_over_same_rpm_static_cp", "ratio"),
        ("q_over_t_over_same_rpm_static_q_over_t", "ratio"),
    ]
    for propeller, rows in rows_by_prop.items():
        j_max = max(float(row["j"]) for row in rows)
        for target_name, target_j, target_note in TARGETS:
            near = nearest_row(rows, target_j)
            target_row = {
                "target_j": target_j,
                "target_mu_equivalent": target_j / math.pi,
                "nearest_j": near["j"],
                "nearest_mu_equivalent": near["code_mu_equivalent_j_over_pi"],
                "target_delta_j": abs(float(near["j"]) - target_j),
                "target_within_file_j_range": target_j <= j_max,
                "ct": near["ct"],
                "cp": near["cp"],
                "efficiency_pe": near["efficiency_pe"],
                "ct_over_same_rpm_static_ct": near["ct_over_same_rpm_static_ct"],
                "cp_over_same_rpm_static_cp": near["cp_over_same_rpm_static_cp"],
                "q_over_t_over_same_rpm_static_q_over_t": near["q_over_same_rpm_static_q_over_t"]
                if "q_over_same_rpm_static_q_over_t" in near
                else near["q_over_t_over_same_rpm_static_q_over_t"],
            }
            for metric, unit in target_metric_units:
                add_metric(
                    packet,
                    row_type="high_advance_packet_apc_target_point",
                    name=f"{propeller}:{target_name}",
                    metric=metric,
                    value=target_row[metric],
                    unit=unit,
                    source_file=APC_REFERENCE,
                    source_url=str(rows[0]["source_url"]),
                    evidence_role="nearest_apc_axial_point_to_project_threshold",
                    note=target_note,
                )

    add_threshold_metrics(packet)

    max_mu_summary = max(prop_summaries, key=lambda row: float(row["code_mu_equivalent_max"]))
    max_positive_summary = max(prop_summaries, key=lambda row: float(row["highest_positive_ct_mu_equivalent"]))
    moderate_pitch = next(row for row in prop_summaries if row["propeller"] == "APC_5x4.5E")
    three_blade = next(row for row in prop_summaries if row["propeller"] == "APC_5.1x5.0E_3blade")
    extreme_pitch_rows = rows_by_prop["APC_5x11E"]
    stall_start_near = nearest_row(extreme_pitch_rows, math.pi * CURRENT_RETREATING_STALL_MU_START)
    high_loss_near = nearest_row(extreme_pitch_rows, math.pi * CURRENT_HIGH_ADVANCE_LOSS_MU_START)
    summary_metrics = [
        ("selected_apc_prop_count", len(prop_summaries), "count"),
        ("selected_apc_row_count", len(apc_rows), "count"),
        ("selected_apc_max_j", max_mu_summary["j_max"], "J"),
        ("selected_apc_max_mu_equivalent", max_mu_summary["code_mu_equivalent_max"], "mu"),
        ("selected_apc_max_mu_propeller", max_mu_summary["propeller"], "text"),
        ("selected_apc_highest_positive_ct_mu", max_positive_summary["highest_positive_ct_mu_equivalent"], "mu"),
        ("selected_apc_highest_positive_ct_propeller", max_positive_summary["propeller"], "text"),
        ("moderate_5x4p5_positive_ct_mu_limit", moderate_pitch["highest_positive_ct_mu_equivalent"], "mu"),
        ("three_blade_5p1x5_positive_ct_mu_limit", three_blade["highest_positive_ct_mu_equivalent"], "mu"),
        ("extreme_5x11_ct_at_current_stall_start", stall_start_near["ct"], "CT"),
        ("extreme_5x11_ct_ratio_at_current_stall_start", stall_start_near["ct_over_same_rpm_static_ct"], "ratio"),
        ("extreme_5x11_ct_at_high_advance_loss_start", high_loss_near["ct"], "CT"),
        ("extreme_5x11_ct_ratio_at_high_advance_loss_start", high_loss_near["ct_over_same_rpm_static_ct"], "ratio"),
    ]
    for metric, value, unit in summary_metrics:
        add_metric(
            packet,
            row_type="high_advance_packet_summary",
            name="high_advance_rotor_prop_summary",
            metric=metric,
            value=value,
            unit=unit,
            source_file=PACKET,
            source_url=APC_PAGE_URL,
            evidence_role="compact_handoff_summary",
            note="APC rows are axial propeller predictions; NASA/UMD rows are edgewise rotor high-mu source inventory.",
        )

    add_metric(
        packet,
        row_type="high_advance_packet_method",
        name="method",
        metric="scope_and_caveat",
        value=(
            "Use APC high-J files to understand axial propeller CT/CP and zero-thrust/windmilling shape beyond UIUC's "
            "5-inch experimental range. Use NASA/UMD/DLR rotor papers for edgewise high-mu and retreating-blade-stall "
            "phenomenology. Do not calibrate 5-inch FPV retreating-blade stall directly from APC axial J data."
        ),
        unit="text",
        source_file=PACKET,
        source_url=APC_PAGE_URL,
        evidence_role="method_caveat",
    )
    return packet


def sync_summary(packet_rows: Iterable[dict[str, str]]) -> int:
    existing = read_rows(SUMMARY)
    kept = [row for row in existing if not row.get("category", "").startswith("high_advance_packet_")]
    added: list[dict[str, str]] = []
    for row in packet_rows:
        added.append(
            {
                "category": row["row_type"],
                "name": row["name"],
                "metric": row["metric"],
                "value": row["value"],
                "unit": row["unit"],
                "source": row.get("source_url") or row.get("source_file", ""),
            }
        )
    write_csv(SUMMARY, kept + added)
    return len(added)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--skip-download", action="store_true", help="Reserved for future cached operation.")
    return parser.parse_args()


def main() -> None:
    parse_args()
    apc_rows: list[dict[str, object]] = []
    for prop in SELECTED_APC_PROPS:
        print(f"Downloading {prop.filename}")
        apc_rows.extend(parse_apc_file(prop))
    prop_summaries = [summarize_prop([row for row in apc_rows if row["propeller"] == prop.label]) for prop in SELECTED_APC_PROPS]
    source_rows = build_source_inventory(apc_rows, prop_summaries)
    packet_rows = build_packet(apc_rows, source_rows, prop_summaries)

    write_csv(APC_REFERENCE, apc_rows)
    write_csv(SOURCE_INVENTORY, source_rows)
    write_csv(PACKET, packet_rows)
    synced = sync_summary(packet_rows)

    print(f"Wrote {repo_path(APC_REFERENCE)} with {len(apc_rows)} rows")
    print(f"Wrote {repo_path(SOURCE_INVENTORY)} with {len(source_rows)} rows")
    print(f"Wrote {repo_path(PACKET)} with {len(packet_rows)} rows")
    print(f"Synced {synced} rows into {repo_path(SUMMARY)}")


if __name__ == "__main__":
    main()
