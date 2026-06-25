"""Build a lightweight NeuroBEM source inventory packet.

Outputs:
  docs/data/neurobem_source_inventory.csv
  docs/data/fpv_model_validation_summary.csv rows with category neurobem_packet_*

The full NeuroBEM archives are hundreds of megabytes. This script caches only
the public index and small text metadata files, then records the fields needed
for a future residual-aerodynamics extraction.
"""

from __future__ import annotations

import csv
import math
import re
import statistics
import urllib.request
from pathlib import Path
from typing import Iterable


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"
RAW = DATA / "raw" / "neurobem"
OUTPUT = DATA / "neurobem_source_inventory.csv"
SUMMARY = DATA / "fpv_model_validation_summary.csv"

BASE_URL = "https://download.ifi.uzh.ch/rpg/NeuroBEM/"
PROJECT_URL = "https://rpg.ifi.uzh.ch/NeuroBEM.html"
README_URL = BASE_URL + "Readme.md"
FLIGHTS_URL = BASE_URL + "Flights.txt"
TESTSET_URL = BASE_URL + "testset.txt"
INDEX_URL = BASE_URL


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
            writer.writerow(row)


def fetch_text(url: str, path: Path) -> str:
    path.parent.mkdir(parents=True, exist_ok=True)
    with urllib.request.urlopen(url, timeout=30) as response:
        data = response.read()
    text = data.decode("utf-8", errors="replace")
    path.write_text(text, encoding="utf-8")
    return text


def add_metric(
    rows: list[dict[str, str]],
    *,
    row_type: str,
    name: str,
    metric: str,
    value: object,
    unit: str,
    source_file: Path,
    source_url: str,
    evidence_role: str,
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


def parse_size_to_mb(size_text: str) -> float:
    match = re.fullmatch(r"\s*([0-9.]+)\s*([KMG]?)\s*", size_text)
    if not match:
        return math.nan
    value = float(match.group(1))
    suffix = match.group(2)
    if suffix == "K":
        return value / 1024.0
    if suffix == "M":
        return value
    if suffix == "G":
        return value * 1024.0
    return value / (1024.0 * 1024.0)


def parse_index(index_html: str) -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    pattern = re.compile(
        r'<a href="(?P<name>[^"]+)">[^<]+</a></td>'
        r'<td align="right">(?P<modified>[^<]+)</td>'
        r'<td align="right">(?P<size>[^<]+)</td>',
        re.IGNORECASE,
    )
    for match in pattern.finditer(index_html):
        name = match.group("name")
        if name.startswith("?") or name == "../":
            continue
        rows.append(
            {
                "name": name,
                "modified": " ".join(match.group("modified").split()),
                "size_text": " ".join(match.group("size").split()),
            }
        )
    return rows


def parse_flights(flights_text: str) -> list[dict[str, object]]:
    flights: list[dict[str, object]] = []
    for line in flights_text.splitlines():
        match = re.search(r'dataset\s*=\s*"(?P<id>[^"]+)"\s*;\s*%\s*(?P<note>.*)', line)
        if not match:
            continue
        note = match.group("note").strip()
        vel_match = re.search(r"vel\s*=\s*([0-9.]+)", note)
        delta_match = re.search(r"delta(?:_z)?\s*=\s*([0-9.]+)", note)
        motion = note.split(",")[0].strip()
        flights.append(
            {
                "id": match.group("id"),
                "date": match.group("id")[:10],
                "note": note,
                "motion": motion,
                "target_velocity_m_s": float(vel_match.group(1)) if vel_match else math.nan,
                "delta": float(delta_match.group(1)) if delta_match else math.nan,
            }
        )
    return flights


def parse_markdown_columns(readme_text: str) -> list[dict[str, str]]:
    columns: list[dict[str, str]] = []
    for line in readme_text.splitlines():
        cells = [cell.strip() for cell in line.strip().strip("|").split("|")]
        if len(cells) == 3 and cells[0].isdigit():
            columns.append(
                {
                    "column": cells[0],
                    "quantity": cells[1],
                    "header": cells[2],
                    "schema": "processed",
                }
            )
        elif len(cells) == 2 and cells[0].isdigit():
            columns.append(
                {
                    "column": cells[0],
                    "quantity": cells[1],
                    "header": "",
                    "schema": "prediction_or_residual",
                }
            )
    return columns


def add_source_inventory(packet: list[dict[str, str]], index_rows: list[dict[str, str]]) -> None:
    for row in index_rows:
        source_url = BASE_URL + row["name"]
        size_mb = parse_size_to_mb(row["size_text"])
        add_metric(
            packet,
            row_type="neurobem_packet_archive_inventory",
            name=row["name"],
            metric="size_mb",
            value=size_mb,
            unit="MB",
            source_file=RAW / "index.html",
            source_url=source_url,
            evidence_role="archive_inventory",
            note=f"Directory listing modified {row['modified']}; size label {row['size_text']}.",
        )
        add_metric(
            packet,
            row_type="neurobem_packet_archive_inventory",
            name=row["name"],
            metric="cache_policy",
            value="metadata_only" if size_mb > 1.0 else "small_text_ok",
            unit="text",
            source_file=RAW / "index.html",
            source_url=source_url,
            evidence_role="archive_inventory",
            note="Full archives are not downloaded by this script.",
        )


def add_readme_metrics(packet: list[dict[str, str]], readme_text: str) -> None:
    source_file = RAW / "Readme.md"
    metrics = [
        ("dataset_duration_minutes", 75.0, "min", "Readme says recordings cover 1h:15min of agile quadrotor flights."),
        ("sample_rate_hz", 400.0, "Hz", "Readme says accurate measurements are provided at 400 Hz."),
        ("vehicle_mass_kg", 0.772, "kg", "Readme gives the NeuroBEM drone mass."),
        ("inertia_xx_kg_m2", 0.0025, "kg*m^2", "Readme gives diagonal inertia elements."),
        ("inertia_yy_kg_m2", 0.0021, "kg*m^2", "Readme gives diagonal inertia elements."),
        ("inertia_zz_kg_m2", 0.0043, "kg*m^2", "Readme gives diagonal inertia elements."),
    ]
    for metric, value, unit, note in metrics:
        add_metric(
            packet,
            row_type="neurobem_packet_dataset_metric",
            name="NeuroBEM_dataset",
            metric=metric,
            value=value,
            unit=unit,
            source_file=source_file,
            source_url=README_URL,
            evidence_role="dataset_metadata",
            note=note,
        )
    if "Vicon" in readme_text:
        add_metric(
            packet,
            row_type="neurobem_packet_dataset_metric",
            name="NeuroBEM_dataset",
            metric="has_vicon_ground_truth",
            value=1,
            unit="boolean",
            source_file=source_file,
            source_url=README_URL,
            evidence_role="dataset_metadata",
            note="Readme describes Vicon position, velocity, pose, acceleration, and body-rate measurements.",
        )


def add_flight_metrics(packet: list[dict[str, str]], flights: list[dict[str, object]], test_segments: list[str]) -> None:
    velocities = [
        float(row["target_velocity_m_s"])
        for row in flights
        if isinstance(row["target_velocity_m_s"], float) and math.isfinite(row["target_velocity_m_s"])
    ]
    source_file = RAW / "Flights.txt"
    add_metric(
        packet,
        row_type="neurobem_packet_flight_inventory",
        name="all_flights",
        metric="flight_count",
        value=len(flights),
        unit="count",
        source_file=source_file,
        source_url=FLIGHTS_URL,
        evidence_role="flight_inventory",
        note="Parsed dataset lines from Flights.txt.",
    )
    add_metric(
        packet,
        row_type="neurobem_packet_flight_inventory",
        name="testset_segments",
        metric="segment_count",
        value=len(test_segments),
        unit="count",
        source_file=RAW / "testset.txt",
        source_url=TESTSET_URL,
        evidence_role="test_split_inventory",
        note="Parsed nonempty lines from testset.txt.",
    )
    for metric, value, unit in [
        ("target_velocity_count", len(velocities), "count"),
        ("target_velocity_min_m_s", min(velocities), "m/s"),
        ("target_velocity_median_m_s", statistics.median(velocities), "m/s"),
        ("target_velocity_max_m_s", max(velocities), "m/s"),
    ]:
        add_metric(
            packet,
            row_type="neurobem_packet_flight_inventory",
            name="all_flights",
            metric=metric,
            value=value,
            unit=unit,
            source_file=source_file,
            source_url=FLIGHTS_URL,
            evidence_role="flight_inventory",
            note="Target velocities are parsed from the human-readable flight comments, not measured speed maxima.",
        )
    by_date: dict[str, int] = {}
    by_motion: dict[str, list[float]] = {}
    for row in flights:
        by_date[str(row["date"])] = by_date.get(str(row["date"]), 0) + 1
        motion = str(row["motion"])
        by_motion.setdefault(motion, [])
        velocity = float(row["target_velocity_m_s"])
        if math.isfinite(velocity):
            by_motion[motion].append(velocity)
    for date, count in sorted(by_date.items()):
        add_metric(
            packet,
            row_type="neurobem_packet_flight_date_inventory",
            name=date,
            metric="flight_count",
            value=count,
            unit="count",
            source_file=source_file,
            source_url=FLIGHTS_URL,
            evidence_role="flight_inventory",
        )
    for motion, motion_velocities in sorted(by_motion.items()):
        add_metric(
            packet,
            row_type="neurobem_packet_flight_motion_inventory",
            name=motion,
            metric="flight_count",
            value=sum(1 for row in flights if row["motion"] == motion),
            unit="count",
            source_file=source_file,
            source_url=FLIGHTS_URL,
            evidence_role="trajectory_family_inventory",
            note="Motion label is parsed from the text before the first comma in the Flights.txt comment.",
        )
        if motion_velocities:
            add_metric(
                packet,
                row_type="neurobem_packet_flight_motion_inventory",
                name=motion,
                metric="target_velocity_range_m_s",
                value=f"{min(motion_velocities):.6g}..{max(motion_velocities):.6g}",
                unit="m/s",
                source_file=source_file,
                source_url=FLIGHTS_URL,
                evidence_role="trajectory_family_inventory",
            )


def add_column_schema(packet: list[dict[str, str]], columns: list[dict[str, str]]) -> None:
    source_file = RAW / "Readme.md"
    for column in columns:
        add_metric(
            packet,
            row_type="neurobem_packet_column_schema",
            name=f"column_{column['column']}",
            metric="quantity",
            value=column["quantity"],
            unit="text",
            source_file=source_file,
            source_url=README_URL,
            evidence_role=column["schema"],
            note=f"Header abbreviation: {column['header']}" if column["header"] else "",
        )


def add_extraction_targets(packet: list[dict[str, str]]) -> None:
    source_file = RAW / "Readme.md"
    targets = [
        (
            "body_velocity",
            "columns_15_17",
            "velocity body x/y/z [m/s]",
            "Regress residual force against body-axis speed and dynamic pressure.",
        ),
        (
            "body_acceleration",
            "columns_12_14",
            "acceleration body x/y/z [m/s^2]",
            "Reconstruct measured force with mass 0.772 kg.",
        ),
        (
            "motor_speed",
            "columns_21_24",
            "motor speed back/front right/left [rad/s]",
            "Separate thrust scale and rotor-speed-dependent residuals.",
        ),
        (
            "battery_voltage",
            "column_29",
            "battery voltage [V]",
            "Cross-check sag and power availability during agile residual fits.",
        ),
        (
            "predicted_force",
            "columns_30_32",
            "predicted force body x/y/z [N]",
            "Compare published model prediction with measured acceleration-derived force.",
        ),
        (
            "residual_force",
            "columns_36_38",
            "residual force body x/y/z [N]",
            "Direct target for residual-aerodynamics packet if predictions archive is sampled.",
        ),
    ]
    for name, metric, value, note in targets:
        add_metric(
            packet,
            row_type="neurobem_packet_extraction_target",
            name=name,
            metric=metric,
            value=value,
            unit="text",
            source_file=source_file,
            source_url=README_URL,
            evidence_role="future_residual_drag_fit",
            note=note,
        )


def sync_summary(packet_rows: Iterable[dict[str, str]]) -> int:
    existing = read_rows(SUMMARY)
    kept = [row for row in existing if not row.get("category", "").startswith("neurobem_packet_")]
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


def build_packet() -> list[dict[str, str]]:
    index_html = fetch_text(INDEX_URL, RAW / "index.html")
    readme_text = fetch_text(README_URL, RAW / "Readme.md")
    flights_text = fetch_text(FLIGHTS_URL, RAW / "Flights.txt")
    testset_text = fetch_text(TESTSET_URL, RAW / "testset.txt")

    index_rows = parse_index(index_html)
    flights = parse_flights(flights_text)
    columns = parse_markdown_columns(readme_text)
    test_segments = [line.strip() for line in testset_text.splitlines() if line.strip()]

    packet: list[dict[str, str]] = []
    add_source_inventory(packet, index_rows)
    add_readme_metrics(packet, readme_text)
    add_flight_metrics(packet, flights, test_segments)
    add_column_schema(packet, columns)
    add_extraction_targets(packet)
    add_metric(
        packet,
        row_type="neurobem_packet_method",
        name="method",
        metric="scope_and_caveat",
        value=(
            "This packet caches only NeuroBEM metadata. Use it to plan a future residual-aerodynamics extraction from "
            "processed_data.zip or predictions.tar.xz. Do not download the large archives into the repository without "
            "an explicit storage decision."
        ),
        unit="text",
        source_file=RAW / "Readme.md",
        source_url=f"{PROJECT_URL}; {INDEX_URL}",
        evidence_role="method",
        note="Full archives are 225-659 MB each in the public directory listing.",
    )
    return packet


def main() -> None:
    packet = build_packet()
    write_csv(OUTPUT, packet)
    synced = sync_summary(packet)
    print(f"Wrote {repo_path(OUTPUT)} with {len(packet)} rows")
    print(f"Synced {synced} rows into {repo_path(SUMMARY)}")


if __name__ == "__main__":
    main()
