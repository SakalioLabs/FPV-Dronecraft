"""Build a Blackbird source-inventory and availability packet.

Outputs:
  docs/data/blackbird_source_inventory_packet.csv
  docs/data/fpv_model_validation_summary.csv rows with category
  blackbird_source_inventory_packet_*

Blackbird is a useful open UAV dataset lead for synchronized state, motor RPM,
PWM, IMU, and mocap-style trajectories. This packet deliberately stops short of
residual-force fitting when the raw MIT data endpoint is unreachable; it records
the official source structure, expected CSV schema, preview-flight inventory, and
current download status so the coding agent knows what is confirmed versus what
still needs raw files.
"""

from __future__ import annotations

import csv
import math
import re
import urllib.error
import urllib.request
from pathlib import Path
from typing import Iterable


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"

OUTPUT = DATA / "blackbird_source_inventory_packet.csv"
SUMMARY = DATA / "fpv_model_validation_summary.csv"

README_URL = "https://raw.githubusercontent.com/mit-aera/Blackbird-Dataset/master/README.md"
DOWNLOADER_URL = (
    "https://raw.githubusercontent.com/mit-aera/Blackbird-Dataset/master/"
    "fileTreeUtilities/sequenceDownloader.py"
)
OFFICIAL_DATA_ROOT = "http://blackbird-dataset.mit.edu/BlackbirdDatasetData"
ARXIV_URL = "https://arxiv.org/abs/1810.01987"
IJRR_DOI_URL = "https://doi.org/10.1177/0278364920908331"

RACING_REFERENCE_SPEED_MPS = 12.5
RACING_CURRENT_X_DRAG_LIMITED_SPEED_MPS = 17.03

DOWNLOAD_SAMPLE_FLIGHTS = (
    "mouse/yawForward/maxSpeed7p0",
    "clover/yawForward/maxSpeed5p0",
    "picasso/yawConstant/maxSpeed6p0",
)
DOWNLOAD_SAMPLE_FILES = (
    "csv/blackbird_slash_rotor_rpm.csv",
    "csv/blackbird_slash_pwm.csv",
    "csv/blackbird_slash_state.csv",
    "groundTruthPoses.csv",
)


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


def request_text(url: str, timeout_s: float = 30.0) -> str:
    request = urllib.request.Request(url, headers={"User-Agent": "codex-fpv-data-inventory"})
    with urllib.request.urlopen(request, timeout=timeout_s) as response:
        return response.read().decode("utf-8", errors="replace")


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


def speed_token_to_mps(token: str) -> float:
    return float(token.replace("p", "."))


def parse_preview_flights(readme: str) -> list[dict[str, object]]:
    pattern = re.compile(
        r"^\[[^\]]+\]:\s+"
        r"(?P<url>https?://blackbird-dataset\.mit\.edu/BlackbirdDatasetData/"
        r"(?P<trajectory>[^/]+)/(?P<yaw>[^/]+)/maxSpeed(?P<speed>[0-9]p[0-9])/"
        r"previewVideos/)\s*$",
        re.MULTILINE,
    )
    flights: dict[tuple[str, str, str], dict[str, object]] = {}
    for match in pattern.finditer(readme):
        key = (match.group("trajectory"), match.group("yaw"), match.group("speed"))
        flights[key] = {
            "trajectory": match.group("trajectory"),
            "yaw_mode": match.group("yaw"),
            "max_speed_mps": speed_token_to_mps(match.group("speed")),
            "flight_path": f"{match.group('trajectory')}/{match.group('yaw')}/maxSpeed{match.group('speed')}",
            "preview_url": match.group("url"),
        }
    return sorted(
        flights.values(),
        key=lambda row: (str(row["yaw_mode"]), str(row["trajectory"]), float(row["max_speed_mps"])),
    )


def parse_downloader_file_lists(script_text: str) -> dict[str, list[str]]:
    lists: dict[str, list[str]] = {}
    for list_name in ("flightFileList", "csvFiles", "globalFiles", "environmentFiles"):
        match = re.search(rf"{list_name}\s*=\s*\[(?P<body>.*?)\]", script_text, re.DOTALL)
        if not match:
            lists[list_name] = []
            continue
        lists[list_name] = re.findall(r'"([^"]+)"', match.group("body"))
    return lists


def attempt_head(url: str, timeout_s: float = 15.0) -> dict[str, object]:
    request = urllib.request.Request(url, method="HEAD", headers={"User-Agent": "codex-fpv-data-inventory"})
    try:
        with urllib.request.urlopen(request, timeout=timeout_s) as response:
            return {
                "ok": True,
                "http_status": int(response.status),
                "content_length_bytes": response.headers.get("Content-Length", ""),
                "error": "",
            }
    except urllib.error.HTTPError as exc:
        return {
            "ok": False,
            "http_status": int(exc.code),
            "content_length_bytes": exc.headers.get("Content-Length", "") if exc.headers else "",
            "error": str(exc),
        }
    except Exception as exc:  # noqa: BLE001 - keep endpoint failure evidence.
        return {
            "ok": False,
            "http_status": "",
            "content_length_bytes": "",
            "error": f"{type(exc).__name__}: {exc}",
        }


def add_source_rows(rows: list[dict[str, object]], readme: str) -> None:
    full_dataset_match = re.search(r"full dataset is quite large \((?P<size>[0-9.]+)TB\)", readme)
    full_dataset_tb = float(full_dataset_match.group("size")) if full_dataset_match else math.nan
    add_metric(
        rows,
        row_type="blackbird_source_inventory_packet_source",
        name="Blackbird official GitHub README",
        metric="full_dataset_size",
        value=full_dataset_tb,
        unit="TB",
        source_url=README_URL,
        evidence_role="source_inventory",
        note="The README says chunks can be downloaded separately; this packet only probes small CSV paths.",
    )
    add_metric(
        rows,
        row_type="blackbird_source_inventory_packet_source",
        name="Blackbird sequence downloader",
        metric="official_data_root",
        value=OFFICIAL_DATA_ROOT,
        unit="url",
        source_url=DOWNLOADER_URL,
        evidence_role="source_inventory",
        note="Downloader script defines the S3-style BlackbirdDatasetData path and expected CSV files.",
    )
    add_metric(
        rows,
        row_type="blackbird_source_inventory_packet_source",
        name="Blackbird publication",
        metric="arxiv_source",
        value=ARXIV_URL,
        unit="url",
        source_url=ARXIV_URL,
        evidence_role="paper_source",
        note="Use the paper for dataset scope and citation; raw fitting still requires downloadable CSV/rosbag files.",
    )
    add_metric(
        rows,
        row_type="blackbird_source_inventory_packet_source",
        name="Blackbird publication",
        metric="ijrr_doi",
        value=IJRR_DOI_URL,
        unit="url",
        source_url=IJRR_DOI_URL,
        evidence_role="paper_source",
        note="Official IJRR publication DOI from the README citation block.",
    )


def add_preview_rows(rows: list[dict[str, object]], flights: list[dict[str, object]]) -> None:
    for flight in flights:
        add_metric(
            rows,
            row_type="blackbird_source_inventory_packet_preview_flight",
            name=str(flight["flight_path"]),
            metric="declared_top_speed",
            value=flight["max_speed_mps"],
            unit="m/s",
            source_url=str(flight["preview_url"]),
            evidence_role="readme_preview_inventory",
            note="Preview-link inventory from README; not raw mocap/RPM data.",
            trajectory=flight["trajectory"],
            yaw_mode=flight["yaw_mode"],
            flight_path=flight["flight_path"],
        )


def add_schema_rows(rows: list[dict[str, object]], file_lists: dict[str, list[str]]) -> None:
    for list_name, files in file_lists.items():
        for index, file_name in enumerate(files):
            add_metric(
                rows,
                row_type="blackbird_source_inventory_packet_expected_file",
                name=file_name,
                metric="expected_file_index",
                value=index,
                unit="index",
                source_url=DOWNLOADER_URL,
                evidence_role=list_name,
                note="Expected path component from the official sequenceDownloader.py helper.",
                file_group=list_name,
                file_name=file_name,
            )


def add_download_attempt_rows(rows: list[dict[str, object]]) -> list[dict[str, object]]:
    attempts: list[dict[str, object]] = []
    for flight_path in DOWNLOAD_SAMPLE_FLIGHTS:
        for file_name in DOWNLOAD_SAMPLE_FILES:
            url = f"{OFFICIAL_DATA_ROOT}/{flight_path}/{file_name}"
            result = attempt_head(url)
            attempts.append({"flight_path": flight_path, "file_name": file_name, "url": url, **result})
            add_metric(
                rows,
                row_type="blackbird_source_inventory_packet_download_attempt",
                name=f"{flight_path}/{file_name}",
                metric="head_request_ok",
                value=1 if result["ok"] else 0,
                unit="boolean",
                source_url=url,
                evidence_role="download_probe",
                note="HEAD probe against a small raw-data CSV path; failure means do not fit Blackbird residuals from local raw data yet.",
                flight_path=flight_path,
                file_name=file_name,
                http_status=result["http_status"],
                content_length_bytes=result["content_length_bytes"],
                error=result["error"],
            )
    return attempts


def add_summary_rows(
    rows: list[dict[str, object]],
    flights: list[dict[str, object]],
    file_lists: dict[str, list[str]],
    attempts: list[dict[str, object]],
) -> None:
    speeds = [float(row["max_speed_mps"]) for row in flights]
    yaw_forward = [row for row in flights if row["yaw_mode"] == "yawForward"]
    yaw_constant = [row for row in flights if row["yaw_mode"] == "yawConstant"]
    trajectories = {str(row["trajectory"]) for row in flights}
    expected_csv_files = file_lists.get("csvFiles", [])
    success_count = sum(1 for row in attempts if row["ok"])
    failure_count = len(attempts) - success_count
    summary_metrics = {
        "readme_preview_flight_count": (len(flights), "count"),
        "readme_preview_trajectory_count": (len(trajectories), "count"),
        "yaw_forward_preview_flight_count": (len(yaw_forward), "count"),
        "yaw_constant_preview_flight_count": (len(yaw_constant), "count"),
        "max_readme_preview_speed_mps": (max(speeds) if speeds else math.nan, "m/s"),
        "max_speed_over_racing_reference_12p5_mps": (
            max(speeds) / RACING_REFERENCE_SPEED_MPS if speeds else math.nan,
            "ratio",
        ),
        "max_speed_over_current_racing_drag_limited_x_speed": (
            max(speeds) / RACING_CURRENT_X_DRAG_LIMITED_SPEED_MPS if speeds else math.nan,
            "ratio",
        ),
        "expected_csv_file_count": (len(expected_csv_files), "count"),
        "expected_primary_flight_file_count": (len(file_lists.get("flightFileList", [])), "count"),
        "download_probe_attempt_count": (len(attempts), "count"),
        "download_probe_success_count": (success_count, "count"),
        "download_probe_failure_count": (failure_count, "count"),
        "sample_raw_csv_available_now": (1 if success_count > 0 else 0, "boolean"),
    }
    for metric, (value, unit) in summary_metrics.items():
        add_metric(
            rows,
            row_type="blackbird_source_inventory_packet_summary",
            name="Blackbird source inventory handoff",
            metric=metric,
            value=value,
            unit=unit,
            source_url="",
            evidence_role="compact_blackbird_handoff",
            note="Use this as a source/inventory packet until raw Blackbird CSV downloads are reachable.",
        )


def build_rows() -> list[dict[str, object]]:
    readme = request_text(README_URL)
    downloader = request_text(DOWNLOADER_URL)
    flights = parse_preview_flights(readme)
    file_lists = parse_downloader_file_lists(downloader)
    rows: list[dict[str, object]] = []
    add_source_rows(rows, readme)
    add_preview_rows(rows, flights)
    add_schema_rows(rows, file_lists)
    attempts = add_download_attempt_rows(rows)
    add_summary_rows(rows, flights, file_lists, attempts)
    return rows


def sync_summary(packet_rows: Iterable[dict[str, object]]) -> int:
    existing = read_rows(SUMMARY)
    kept = [row for row in existing if not row.get("category", "").startswith("blackbird_source_inventory_packet_")]
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
