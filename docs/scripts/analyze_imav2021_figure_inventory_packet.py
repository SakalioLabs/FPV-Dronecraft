"""Inventory digitization targets from the IMAV 2021 forward-flow PDF.

Outputs:
  docs/data/imav2021_figure_inventory_packet.csv
  docs/data/fpv_model_validation_summary.csv rows with category imav2021_figure_inventory_packet_*

The IMAV 2021 paper embeds its result figures as images. Those figures show
propulsive efficiency versus RPM, not raw thrust/torque coefficient curves.
This packet therefore records image files, subplot metadata, color mappings,
and advance-ratio coverage for a later digitization pass. It deliberately does
not turn eta-only curves into CT/CP fits.
"""

from __future__ import annotations

import csv
import math
from pathlib import Path
from typing import Iterable


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"
RAW = DATA / "raw" / "imav2021_forward_flow"
PDF = RAW / "imav2021_21_propulsive_efficiency.pdf"
EXTRACTED = RAW / "extracted_images"
OUTPUT = DATA / "imav2021_figure_inventory_packet.csv"
SUMMARY = DATA / "fpv_model_validation_summary.csv"

SOURCE_URL = "https://www.imavs.org/papers/2021/21.pdf"
DIAMETER_M = 5.0 * 0.0254

IMAGE_EXPECTATIONS = [
    {
        "page": 2,
        "image": "page02_image01_Image34.jpg",
        "figure": "apparatus_photo",
        "content": "wind_tunnel_or_test_stand_photo",
    },
    {
        "page": 2,
        "image": "page02_image02_Image35.png",
        "figure": "flow_angle_diagram",
        "content": "force_flow_angle_definition",
    },
    {
        "page": 3,
        "image": "page03_image01_Image43.png",
        "figure": "Figure 3",
        "content": "sample_mean_and_standard_deviation_eta_curve_15m_s_45deg_full_rpm",
    },
    {
        "page": 3,
        "image": "page03_image02_Image44.png",
        "figure": "Figure 4",
        "content": "sample_cropped_eta_curve_15m_s_45deg",
    },
    {
        "page": 4,
        "image": "page04_image01_Image47.png",
        "figure": "Figure 5",
        "content": "eta_vs_rpm_10m_s_angles_30_35_40_45_50",
    },
    {
        "page": 4,
        "image": "page04_image02_Image48.png",
        "figure": "Figure 6",
        "content": "eta_vs_rpm_15m_s_angles_30_35_40_45_50",
    },
    {
        "page": 5,
        "image": "page05_image01_Image51.png",
        "figure": "Figure 7",
        "content": "eta_vs_rpm_20m_s_angles_30_35_40_45_50",
    },
    {
        "page": 5,
        "image": "page05_image02_Image52.png",
        "figure": "Figure 8",
        "content": "eta_vs_rpm_axial_90deg_10_15_20m_s",
    },
]

FIGURE_PANELS = [
    ("Figure 5", "page04_image01_Image47.png", 10.0, angle, idx)
    for idx, angle in enumerate([30, 35, 40, 45, 50], 1)
] + [
    ("Figure 6", "page04_image02_Image48.png", 15.0, angle, idx)
    for idx, angle in enumerate([30, 35, 40, 45, 50], 1)
] + [
    ("Figure 7", "page05_image01_Image51.png", 20.0, angle, idx)
    for idx, angle in enumerate([30, 35, 40, 45, 50], 1)
] + [
    ("Figure 8", "page05_image02_Image52.png", speed, 90, idx)
    for idx, speed in enumerate([10.0, 15.0, 20.0], 1)
]

LINE_COLORS = [
    ("blue", "HQ 5x4", "lower pitch baseline"),
    ("orange", "HQ 5x4.3", "slightly higher pitch"),
    ("purple", "HQ 5x4.8", "higher pitch"),
    ("green", "HQ 5x5", "highest listed pitch"),
]


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
            writer.writerow({key: value_text(row.get(key)) for key in fieldnames})


def image_size(path: Path) -> tuple[int | None, int | None]:
    try:
        from PIL import Image

        with Image.open(path) as image:
            return image.size
    except Exception:
        return None, None


def extract_pdf_images() -> int:
    try:
        from pypdf import PdfReader
    except Exception:
        return 0
    if not PDF.exists():
        return 0
    EXTRACTED.mkdir(parents=True, exist_ok=True)
    reader = PdfReader(str(PDF))
    count = 0
    for page_index, page in enumerate(reader.pages, 1):
        for image_index, image in enumerate(getattr(page, "images", []), 1):
            output = EXTRACTED / f"page{page_index:02d}_image{image_index:02d}_{image.name}"
            output.write_bytes(image.data)
            count += 1
    return count


def advance_ratio(speed_m_s: float, rpm: float) -> float:
    return speed_m_s / ((rpm / 60.0) * DIAMETER_M)


def add_row(
    rows: list[dict[str, object]],
    *,
    row_type: str,
    name: str,
    metric: str,
    value: object,
    unit: str,
    evidence_role: str,
    source_file: str = "",
    note: str = "",
) -> None:
    rows.append(
        {
            "row_type": row_type,
            "name": name,
            "metric": metric,
            "value": value,
            "unit": unit,
            "source_file": source_file,
            "source_url": SOURCE_URL,
            "evidence_role": evidence_role,
            "note": note,
        }
    )


def build_packet() -> list[dict[str, object]]:
    extracted_count = extract_pdf_images()
    rows: list[dict[str, object]] = []

    add_row(
        rows,
        row_type="imav2021_figure_inventory_packet_method",
        name="extraction",
        metric="embedded_image_extract_count",
        value=extracted_count,
        unit="count",
        source_file=repo_path(PDF),
        evidence_role="image_extraction",
        note="Zero means pypdf was unavailable or the PDF cache was absent; expected images may still already be cached.",
    )
    add_row(
        rows,
        row_type="imav2021_figure_inventory_packet_method",
        name="digitization_caveat",
        metric="figures_5_to_8_curve_type",
        value="propulsive_efficiency_vs_rpm",
        unit="text",
        source_file=repo_path(PDF),
        evidence_role="fit_caveat",
        note="Digitizing these figures gives eta(RPM) by flow speed/angle/prop, not isolated CT or CP.",
    )
    add_row(
        rows,
        row_type="imav2021_figure_inventory_packet_method",
        name="digitization_caveat",
        metric="ct_cp_fit_status",
        value="raw_thrust_torque_logs_still_required",
        unit="text",
        source_file=repo_path(PDF),
        evidence_role="fit_caveat",
        note="The paper logged thrust and torque, but the public result figures do not expose enough numeric information for a CT/CP fit.",
    )

    for expected in IMAGE_EXPECTATIONS:
        path = EXTRACTED / expected["image"]
        width, height = image_size(path) if path.exists() else (None, None)
        for metric, value, unit in [
            ("exists_in_local_raw_cache", path.exists(), "boolean"),
            ("page", expected["page"], "page"),
            ("pixel_width", width, "px"),
            ("pixel_height", height, "px"),
            ("figure", expected["figure"], "text"),
            ("content", expected["content"], "text"),
        ]:
            add_row(
                rows,
                row_type="imav2021_figure_inventory_packet_image",
                name=expected["image"],
                metric=metric,
                value=value,
                unit=unit,
                source_file=repo_path(path),
                evidence_role="figure_image_inventory",
            )

    for color, propeller, note in LINE_COLORS:
        add_row(
            rows,
            row_type="imav2021_figure_inventory_packet_line_color",
            name=color,
            metric="propeller_label",
            value=propeller,
            unit="text",
            source_file=repo_path(EXTRACTED / "page04_image01_Image47.png"),
            evidence_role="digitization_schema",
            note=note,
        )

    for figure, image, speed, angle, panel_index in FIGURE_PANELS:
        image_path = EXTRACTED / image
        panel_name = f"{figure}_panel_{panel_index}_{speed:g}m_s_{angle}deg"
        for metric, value, unit, note in [
            ("source_image", repo_path(image_path), "path", "Use the extracted embedded PDF image, not a screenshot."),
            ("flow_speed", speed, "m/s", ""),
            ("flow_angle", angle, "deg", ""),
            ("x_axis", "rpm", "text", ""),
            ("x_min", 15000, "rpm", "The paper crops high-quality results to begin at 15,000 rpm."),
            ("x_max_approx", 30000, "rpm", "Upper endpoint varies by prop/configuration; use visible curve end for each line."),
            ("j_at_15000rpm", advance_ratio(speed, 15000.0), "J", "Computed for 5-inch diameter."),
            ("j_at_30000rpm", advance_ratio(speed, 30000.0), "J", "Computed for 5-inch diameter."),
            ("y_axis", "propulsive_efficiency", "text", "Eta, not CT or CP."),
            ("line_labels", "HQ 5x4; HQ 5x4.3; HQ 5x4.8; HQ 5x5", "text", "Use line-color rows for mapping."),
            ("digitization_status", "pending_eta_only", "text", "Useful for eta trends; raw logs required for thrust/torque coefficient fits."),
        ]:
            add_row(
                rows,
                row_type="imav2021_figure_inventory_packet_digitization_panel",
                name=panel_name,
                metric=metric,
                value=value,
                unit=unit,
                source_file=repo_path(image_path),
                evidence_role="digitization_target",
                note=note,
            )

    return rows


def sync_summary(packet_rows: Iterable[dict[str, object]]) -> int:
    existing = read_rows(SUMMARY)
    kept = [row for row in existing if not row.get("category", "").startswith("imav2021_figure_inventory_packet_")]
    added = [
        {
            "category": row["row_type"],
            "name": row["name"],
            "metric": row["metric"],
            "value": value_text(row["value"]),
            "unit": row["unit"],
            "source": row["source_url"],
        }
        for row in packet_rows
    ]
    write_csv(SUMMARY, kept + added)
    return len(added)


def main() -> None:
    packet = build_packet()
    write_csv(OUTPUT, packet)
    synced = sync_summary(packet)
    print(f"Wrote {repo_path(OUTPUT)} with {len(packet)} rows")
    print(f"Synced {synced} rows into {repo_path(SUMMARY)}")


if __name__ == "__main__":
    main()
