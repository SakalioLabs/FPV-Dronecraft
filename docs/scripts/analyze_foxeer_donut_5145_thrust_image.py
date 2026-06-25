"""Transcribe Foxeer Donut 5145 public thrust-test image values.

Outputs:
  docs/data/foxeer_donut_5145_thrust_image_reference.csv

The source test is a public blog post containing Tyto Robotics screen captures.
The numeric rows below are manually transcribed from the visible sensor overlay
in the images cached under docs/data/raw/foxeer_donut_5145/.
"""

from __future__ import annotations

import csv
import math
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"
RAW = DATA / "raw" / "foxeer_donut_5145"
OUTPUT = DATA / "foxeer_donut_5145_thrust_image_reference.csv"

BLOG_URL = "https://blog.unmanned.tech/quiet-skies-powerful-flights-exploring-the-efficiency-of-foxeer-donut-5145-propellers/"
FOXEER_URL = "https://www.foxeer.com/foxeer-donut-5145-props-g-520"
G0 = 9.80665

CURRENT_APDRONE_MAX_THRUST_N = 13.5
CURRENT_APDRONE_THRUST_COEFF = 1.3918976015517363e-6
CURRENT_APDRONE_MAX_RPM = math.sqrt(CURRENT_APDRONE_MAX_THRUST_N / CURRENT_APDRONE_THRUST_COEFF) * 60.0 / (2.0 * math.pi)


TEST_POINTS = [
    {
        "propeller": "DALPROP 5146.5",
        "image_file": "dal_5146.png",
        "voltage_v": 23.72,
        "current_a": 35.74,
        "electric_power_w": 848.0,
        "thrust_gf": 1400.494,
        "torque_nm": 0.187,
        "vibration_g": 1.9,
        "rpm_14_poles": 30129.0,
        "right_panel_thrust_gf": 1400.0,
        "right_panel_power_w": 837.0,
        "right_panel_mech_power_w": 589.0,
        "right_panel_current_a": 35.3,
        "right_panel_voltage_v": 23.7,
    },
    {
        "propeller": "Foxeer Donut 5145",
        "image_file": "donut_5145.png",
        "voltage_v": 23.72,
        "current_a": 34.83,
        "electric_power_w": 826.0,
        "thrust_gf": 1382.403,
        "torque_nm": 0.184,
        "vibration_g": 1.7,
        "rpm_14_poles": 29802.0,
        "right_panel_thrust_gf": 1390.0,
        "right_panel_power_w": 822.0,
        "right_panel_mech_power_w": 575.0,
        "right_panel_current_a": 34.6,
        "right_panel_voltage_v": 23.7,
    },
    {
        "propeller": "DALPROP Nepal N2",
        "image_file": "nepal_n2.png",
        "voltage_v": 23.74,
        "current_a": 31.13,
        "electric_power_w": 739.0,
        "thrust_gf": 1404.282,
        "torque_nm": 0.167,
        "vibration_g": 2.0,
        "rpm_14_poles": 30944.0,
        "right_panel_thrust_gf": 1400.0,
        "right_panel_power_w": 745.0,
        "right_panel_mech_power_w": 542.0,
        "right_panel_current_a": 31.4,
        "right_panel_voltage_v": 23.7,
    },
]


def repo_path(path: Path) -> str:
    return path.resolve().relative_to(ROOT).as_posix()


def finite_or_blank(value: str | int | float) -> str | int | float:
    if isinstance(value, float) and not math.isfinite(value):
        return ""
    return value


def derived_point(row: dict[str, str | int | float]) -> dict[str, str | int | float]:
    rpm = float(row["rpm_14_poles"])
    omega = rpm * 2.0 * math.pi / 60.0
    thrust_n = float(row["thrust_gf"]) * G0 / 1000.0
    torque_nm = float(row["torque_nm"])
    mechanical_power_from_torque_w = torque_nm * omega
    return {
        **row,
        "row_type": "foxeer_donut_5145_public_test_max_point",
        "source_page": BLOG_URL,
        "source_context": "Public blog post with Tyto Robotics screen-capture images; values manually transcribed from visible sensor overlay.",
        "local_image_file": repo_path(RAW / str(row["image_file"])),
        "motor": "Flash 2207 1850KV",
        "test_voltage_nominal_v": 24.0,
        "thrust_n": thrust_n,
        "omega_rad_s": omega,
        "derived_thrust_coefficient_n_per_rad_s2": thrust_n / (omega * omega),
        "derived_torque_per_thrust_m": torque_nm / thrust_n,
        "mechanical_power_from_torque_w": mechanical_power_from_torque_w,
        "mechanical_over_electric_efficiency": mechanical_power_from_torque_w / float(row["electric_power_w"]),
        "thrust_g_per_w": float(row["thrust_gf"]) / float(row["electric_power_w"]),
        "thrust_g_per_a": float(row["thrust_gf"]) / float(row["current_a"]),
        "apDrone_max_thrust_ratio": CURRENT_APDRONE_MAX_THRUST_N / thrust_n,
        "apDrone_thrust_coeff_ratio": CURRENT_APDRONE_THRUST_COEFF / (thrust_n / (omega * omega)),
        "apDrone_max_rpm_ratio": CURRENT_APDRONE_MAX_RPM / rpm,
    }


def build_rows() -> list[dict[str, str | int | float]]:
    rows: list[dict[str, str | int | float]] = [
        {
            "row_type": "foxeer_donut_5145_official_spec",
            "source_page": FOXEER_URL,
            "source_context": "Official Foxeer product page.",
            "propeller": "Foxeer Donut 5145",
            "diameter_in": 5.1,
            "pitch_in": 4.5,
            "blade_count": 3,
            "hub_inner_diameter_mm": 5.0,
            "material": "PC",
            "weight_g": 4.3,
            "pack_contents": "2CW + 2CCW",
        },
        {
            "row_type": "foxeer_donut_5145_test_method",
            "source_page": BLOG_URL,
            "source_context": "Unmanned Tech blog test images.",
            "motor": "Flash 2207 1850KV",
            "test_voltage_nominal_v": 24.0,
            "note": "Values are maximum/final screen overlay readings from the three cached images. Motor differs from APdrone YSIDO 2507 1800KV, so use as a prop-family thrust/RPM/current anchor, not an exact APdrone drivetrain map.",
        },
    ]
    rows.extend(derived_point(point) for point in TEST_POINTS)

    donut = next(row for row in rows if row.get("propeller") == "Foxeer Donut 5145" and row["row_type"] == "foxeer_donut_5145_public_test_max_point")
    for other_name in ("DALPROP 5146.5", "DALPROP Nepal N2"):
        other = next(row for row in rows if row.get("propeller") == other_name and row["row_type"] == "foxeer_donut_5145_public_test_max_point")
        rows.append(
            {
                "row_type": "foxeer_donut_5145_public_test_comparison",
                "source_page": BLOG_URL,
                "source_context": "Comparison derived from the same blog screen-capture max points.",
                "propeller": "Foxeer Donut 5145",
                "comparison_propeller": other_name,
                "thrust_ratio_vs_comparison": float(donut["thrust_n"]) / float(other["thrust_n"]),
                "current_ratio_vs_comparison": float(donut["current_a"]) / float(other["current_a"]),
                "electric_power_ratio_vs_comparison": float(donut["electric_power_w"]) / float(other["electric_power_w"]),
                "rpm_ratio_vs_comparison": float(donut["rpm_14_poles"]) / float(other["rpm_14_poles"]),
                "torque_ratio_vs_comparison": float(donut["torque_nm"]) / float(other["torque_nm"]),
                "vibration_ratio_vs_comparison": float(donut["vibration_g"]) / float(other["vibration_g"]),
            }
        )
    return [{key: finite_or_blank(value) for key, value in row.items()} for row in rows]


def write_csv(path: Path, rows: list[dict[str, str | int | float]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fieldnames: list[str] = []
    for row in rows:
        for key in row:
            if key not in fieldnames:
                fieldnames.append(key)
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)


def main() -> None:
    rows = build_rows()
    write_csv(OUTPUT, rows)
    print(f"Wrote {repo_path(OUTPUT)}")


if __name__ == "__main__":
    main()
