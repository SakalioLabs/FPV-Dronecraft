"""Build a Blackbird mirror/download-status packet.

Outputs:
  docs/data/blackbird_mirror_probe_packet.csv
  docs/data/fpv_model_validation_summary.csv rows with category
  blackbird_mirror_probe_packet_*

The earlier Blackbird inventory packet confirmed the expected CSV/RPM/mocap
schema but found HTTP 502 responses from the official MIT data endpoint. This
packet checks current availability, GitHub download issues, and likely mirrors.
It intentionally parses only metadata and the Academic Torrents .torrent file;
it does not download the multi-terabyte dataset.
"""

from __future__ import annotations

import csv
import hashlib
import json
import math
import re
import urllib.error
import urllib.request
from collections import Counter
from pathlib import Path
from typing import Iterable


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "docs" / "data"

OUTPUT = DATA / "blackbird_mirror_probe_packet.csv"
SUMMARY = DATA / "fpv_model_validation_summary.csv"

README_URL = "https://raw.githubusercontent.com/mit-aera/Blackbird-Dataset/master/README.md"
ISSUES_API_URL = "https://api.github.com/repos/mit-aera/Blackbird-Dataset/issues?state=open&per_page=100"
OFFICIAL_ROOT = "http://blackbird-dataset.mit.edu/BlackbirdDatasetData"
ACADEMIC_TORRENT_INFOHASH = "eb542a231dbeb2125e4ec88ddd18841a867c2656"
ACADEMIC_TORRENT_DETAIL_URL = f"https://academictorrents.com/details/{ACADEMIC_TORRENT_INFOHASH}"
ACADEMIC_TORRENT_DOWNLOAD_URL = f"https://academictorrents.com/download/{ACADEMIC_TORRENT_INFOHASH}.torrent"
OPENDATALAB_URLS = (
    "https://opendatalab.com/OpenDataLab/Blackbird",
    "https://opendatalab.org.cn/OpenDataLab/Blackbird",
)

SAMPLE_RAW_FILES = (
    "mouse/yawForward/maxSpeed7p0/csv/blackbird_slash_rotor_rpm.csv",
    "mouse/yawForward/maxSpeed7p0/csv/blackbird_slash_pwm.csv",
    "mouse/yawForward/maxSpeed7p0/csv/blackbird_slash_state.csv",
    "mouse/yawForward/maxSpeed7p0/groundTruthPoses.csv",
)

ISSUE_KEYWORDS = ("download", "server", "link", "access", "unreachable", "address", "down")


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


def request_bytes(url: str, *, method: str = "GET", timeout_s: float = 25.0) -> tuple[int, str, bytes, dict[str, str], str]:
    request = urllib.request.Request(
        url,
        method=method,
        headers={"User-Agent": "codex-fpv-data-probe"},
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout_s) as response:
            return int(response.status), response.geturl(), response.read(), dict(response.headers), ""
    except urllib.error.HTTPError as exc:
        body = exc.read() if exc.fp is not None else b""
        return int(exc.code), url, body, dict(exc.headers or {}), str(exc)
    except Exception as exc:  # noqa: BLE001 - endpoint failure is evidence.
        return 0, url, b"", {}, f"{type(exc).__name__}: {exc}"


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


class BDecoder:
    def __init__(self, data: bytes) -> None:
        self.data = data
        self.index = 0

    def parse(self) -> object:
        token = self.data[self.index : self.index + 1]
        if token == b"i":
            self.index += 1
            end = self.data.index(b"e", self.index)
            value = int(self.data[self.index : end])
            self.index = end + 1
            return value
        if token == b"l":
            self.index += 1
            values = []
            while self.data[self.index : self.index + 1] != b"e":
                values.append(self.parse())
            self.index += 1
            return values
        if token == b"d":
            self.index += 1
            values: dict[bytes, object] = {}
            while self.data[self.index : self.index + 1] != b"e":
                key = self.parse()
                value = self.parse()
                if isinstance(key, bytes):
                    values[key] = value
            self.index += 1
            return values
        if token.isdigit():
            colon = self.data.index(b":", self.index)
            length = int(self.data[self.index : colon])
            self.index = colon + 1
            value = self.data[self.index : self.index + length]
            self.index += length
            return value
        raise ValueError(f"Unexpected bencode token {token!r} at {self.index}")


def decode_torrent(data: bytes) -> dict[bytes, object]:
    decoded = BDecoder(data).parse()
    if not isinstance(decoded, dict):
        raise TypeError("Torrent root was not a dictionary")
    return decoded


def torrent_files(torrent: dict[bytes, object]) -> tuple[dict[bytes, object], list[tuple[str, int]]]:
    info = torrent.get(b"info")
    if not isinstance(info, dict):
        raise TypeError("Torrent info dictionary missing")
    raw_files = info.get(b"files")
    if not isinstance(raw_files, list):
        single_name = info.get(b"name", b"")
        length = int(info.get(b"length", 0))
        return info, [(single_name.decode("utf-8", errors="replace"), length)]
    files: list[tuple[str, int]] = []
    for raw_file in raw_files:
        if not isinstance(raw_file, dict):
            continue
        raw_path = raw_file.get(b"path", [])
        parts = [part.decode("utf-8", errors="replace") for part in raw_path if isinstance(part, bytes)]
        files.append(("/".join(parts), int(raw_file.get(b"length", 0))))
    return info, files


def add_official_probe_rows(rows: list[dict[str, object]]) -> None:
    status, final_url, body, headers, error = request_bytes(f"{OFFICIAL_ROOT}/", method="GET", timeout_s=20.0)
    add_metric(
        rows,
        row_type="blackbird_mirror_probe_packet_official_probe",
        name="official_root",
        metric="http_status",
        value=status,
        unit="status",
        source_url=OFFICIAL_ROOT,
        evidence_role="download_status",
        note=error or f"Final URL: {final_url}; response bytes: {len(body)}.",
    )
    for file_path in SAMPLE_RAW_FILES:
        url = f"{OFFICIAL_ROOT}/{file_path}"
        status, final_url, body, headers, error = request_bytes(url, method="HEAD", timeout_s=15.0)
        add_metric(
            rows,
            row_type="blackbird_mirror_probe_packet_official_probe",
            name=file_path,
            metric="http_status",
            value=status,
            unit="status",
            source_url=url,
            evidence_role="sample_raw_csv_probe",
            note=error or f"Final URL: {final_url}; content length {headers.get('Content-Length', '')}.",
        )


def add_github_issue_rows(rows: list[dict[str, object]]) -> None:
    status, final_url, body, headers, error = request_bytes(ISSUES_API_URL, timeout_s=25.0)
    issues = json.loads(body.decode("utf-8", errors="replace")) if status == 200 else []
    relevant = [
        issue
        for issue in issues
        if any(keyword in str(issue.get("title", "")).lower() for keyword in ISSUE_KEYWORDS)
    ]
    add_metric(
        rows,
        row_type="blackbird_mirror_probe_packet_github_issue",
        name="open_issue_scan",
        metric="download_related_open_issue_count",
        value=len(relevant),
        unit="issues",
        source_url=ISSUES_API_URL,
        evidence_role="download_status",
        note=error or "Open GitHub issues whose title mentions download/server/link/access availability.",
    )
    for issue in relevant:
        for metric, value, unit in [
            ("issue_number", issue.get("number", ""), "number"),
            ("created_at", issue.get("created_at", ""), "timestamp"),
            ("updated_at", issue.get("updated_at", ""), "timestamp"),
            ("title", issue.get("title", ""), "text"),
        ]:
            add_metric(
                rows,
                row_type="blackbird_mirror_probe_packet_github_issue",
                name=f"issue_{issue.get('number', '')}",
                metric=metric,
                value=value,
                unit=unit,
                source_url=str(issue.get("html_url", final_url)),
                evidence_role="download_status",
                note="Open repository issue indicating current or historical dataset access problems.",
            )


def add_academic_torrent_rows(rows: list[dict[str, object]]) -> dict[str, object]:
    detail_status, _, detail_body, _, detail_error = request_bytes(ACADEMIC_TORRENT_DETAIL_URL, timeout_s=25.0)
    detail_text = detail_body.decode("utf-8", errors="replace")
    title_match = re.search(r"<title>(.*?)</title>", detail_text, re.IGNORECASE | re.DOTALL)
    detail_title = re.sub(r"\s+", " ", title_match.group(1)).strip() if title_match else ""
    add_metric(
        rows,
        row_type="blackbird_mirror_probe_packet_mirror_probe",
        name="academic_torrents_detail",
        metric="http_status",
        value=detail_status,
        unit="status",
        source_url=ACADEMIC_TORRENT_DETAIL_URL,
        evidence_role="mirror_candidate",
        note=detail_error or detail_title,
    )

    torrent_status, _, torrent_data, headers, torrent_error = request_bytes(
        ACADEMIC_TORRENT_DOWNLOAD_URL, timeout_s=30.0
    )
    add_metric(
        rows,
        row_type="blackbird_mirror_probe_packet_mirror_probe",
        name="academic_torrents_torrent",
        metric="http_status",
        value=torrent_status,
        unit="status",
        source_url=ACADEMIC_TORRENT_DOWNLOAD_URL,
        evidence_role="mirror_candidate",
        note=torrent_error or f"Content-Type: {headers.get('Content-Type', '')}.",
    )
    if torrent_status != 200 or not torrent_data:
        return {"torrent_available": 0, "csv_count": 0, "total_size_bytes": 0, "file_count": 0}

    torrent = decode_torrent(torrent_data)
    info, files = torrent_files(torrent)
    extensions = Counter(
        path.rsplit(".", 1)[-1].lower() if "." in path.rsplit("/", 1)[-1] else "(none)"
        for path, _ in files
    )
    folder_kinds = Counter(path.split("/")[3] if len(path.split("/")) > 3 else "(short)" for path, _ in files)
    csv_matches = [path for path, _ in files if path.lower().endswith(".csv")]
    actuator_matches = [
        path
        for path, _ in files
        if any(token in path.lower() for token in ("rotor", "pwm", "state", "groundtruth", "imu", "mocap"))
    ]
    total_size = sum(size for _, size in files)
    decoded_name = info.get(b"name", b"")
    torrent_name = decoded_name.decode("utf-8", errors="replace") if isinstance(decoded_name, bytes) else str(decoded_name)
    tracker = torrent.get(b"announce", b"")
    tracker_text = tracker.decode("utf-8", errors="replace") if isinstance(tracker, bytes) else str(tracker)
    for metric, value, unit, note in [
        ("torrent_sha1", hashlib.sha1(torrent_data).hexdigest(), "sha1", "SHA1 of downloaded .torrent metadata file, not BitTorrent infohash."),
        ("torrent_name", torrent_name, "text", "Name field inside the torrent info dictionary."),
        ("announce_tracker", tracker_text, "url", "Tracker URL from torrent metadata."),
        ("file_count", len(files), "files", "Total files listed in torrent metadata."),
        ("total_size_bytes", total_size, "bytes", "Total byte size listed in torrent metadata."),
        ("tar_file_count", extensions.get("tar", 0), "files", "Camera/image archive files in torrent metadata."),
        ("mp4_file_count", extensions.get("mp4", 0), "files", "Preview/video files in torrent metadata."),
        ("csv_file_count", len(csv_matches), "files", "CSV files found by file-extension scan."),
        ("actuator_or_mocap_name_match_count", len(actuator_matches), "files", "Filenames matching rotor/pwm/state/groundTruth/imu/mocap tokens."),
        ("images_folder_count", folder_kinds.get("images", 0), "files", "Files whose fourth path component is images."),
        ("videos_folder_count", folder_kinds.get("videos", 0), "files", "Files whose fourth path component is videos."),
    ]:
        add_metric(
            rows,
            row_type="blackbird_mirror_probe_packet_mirror_inventory",
            name="academic_torrents_blackbird",
            metric=metric,
            value=value,
            unit=unit,
            source_url=ACADEMIC_TORRENT_DOWNLOAD_URL,
            evidence_role="torrent_metadata",
            note=note,
        )
    return {
        "torrent_available": 1,
        "csv_count": len(csv_matches),
        "actuator_match_count": len(actuator_matches),
        "total_size_bytes": total_size,
        "file_count": len(files),
    }


def add_opendatalab_rows(rows: list[dict[str, object]]) -> None:
    for url in OPENDATALAB_URLS:
        status, final_url, body, _, error = request_bytes(url, timeout_s=25.0)
        text = body.decode("utf-8", errors="replace")
        direct_listing = int(any(token in text for token in ("groundTruthPoses", "blackbird_slash_rotor_rpm", ".csv")))
        add_metric(
            rows,
            row_type="blackbird_mirror_probe_packet_mirror_probe",
            name=url.replace("https://", ""),
            metric="direct_raw_file_listing_detected",
            value=direct_listing,
            unit="bool",
            source_url=url,
            evidence_role="mirror_candidate",
            note=error or f"HTTP {status}, final URL {final_url}, HTML bytes {len(body)}; static HTML scan only.",
        )


def add_summary_rows(rows: list[dict[str, object]], torrent_summary: dict[str, object]) -> None:
    official_statuses = [
        int(row["value"])
        for row in rows
        if row["row_type"] == "blackbird_mirror_probe_packet_official_probe"
        and row["metric"] == "http_status"
        and str(row["value"]).isdigit()
    ]
    issue_count = next(
        int(row["value"])
        for row in rows
        if row["row_type"] == "blackbird_mirror_probe_packet_github_issue"
        and row["metric"] == "download_related_open_issue_count"
    )
    official_any_ok = int(any(200 <= status < 300 for status in official_statuses))
    academic_csv_ready = int(torrent_summary.get("csv_count", 0) > 0)
    residual_fit_ready = int(official_any_ok or academic_csv_ready)
    for metric, value, unit, note in [
        ("official_raw_endpoint_any_ok", official_any_ok, "bool", "True only if root or sample raw CSV probes returned 2xx."),
        ("official_probe_status_max", max(official_statuses) if official_statuses else 0, "status", "Useful quick signal; current 502 means official raw endpoint is still not usable from this run."),
        ("github_download_related_open_issue_count", issue_count, "issues", "Open repository issues mentioning download/server/link/access availability."),
        ("academic_torrent_available", torrent_summary.get("torrent_available", 0), "bool", "Academic Torrents metadata file can be downloaded."),
        ("academic_torrent_file_count", torrent_summary.get("file_count", 0), "files", "Metadata file count; not locally downloaded data."),
        ("academic_torrent_total_size_tb", float(torrent_summary.get("total_size_bytes", 0)) / 1e12, "TB", "Torrent total size from metadata."),
        ("academic_torrent_csv_file_count", torrent_summary.get("csv_count", 0), "files", "CSV files found in torrent metadata."),
        ("blackbird_residual_fit_ready_from_probe", residual_fit_ready, "bool", "Raw state/RPM/mocap CSV access is still missing if this is 0."),
    ]:
        add_metric(
            rows,
            row_type="blackbird_mirror_probe_packet_summary",
            name="blackbird_download_status",
            metric=metric,
            value=value,
            unit=unit,
            source_url=ACADEMIC_TORRENT_DETAIL_URL if "academic" in metric else OFFICIAL_ROOT,
            evidence_role="compact_download_handoff",
            note=note,
        )
    add_metric(
        rows,
        row_type="blackbird_mirror_probe_packet_method",
        name="handoff_guidance",
        metric="recommended_use",
        value=(
            "Do not start Blackbird residual-force fitting from this packet alone. The official raw endpoint is still "
            "unavailable from probes, and the Academic Torrents metadata appears to mirror camera tar/video files but "
            "not the CSV/RPM/mocap files required for drag/residual calibration."
        ),
        unit="text",
        source_url=ACADEMIC_TORRENT_DETAIL_URL,
        evidence_role="handoff_guidance",
        note="Retry official raw CSV downloads later or find a mirror that explicitly lists csv/blackbird_slash_rotor_rpm.csv and groundTruthPoses.csv.",
    )


def sync_summary(packet_rows: list[dict[str, object]]) -> int:
    existing = read_rows(SUMMARY) if SUMMARY.exists() else []
    kept = [row for row in existing if not row.get("category", "").startswith("blackbird_mirror_probe_packet_")]
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
    rows: list[dict[str, object]] = []
    add_official_probe_rows(rows)
    add_github_issue_rows(rows)
    torrent_summary = add_academic_torrent_rows(rows)
    add_opendatalab_rows(rows)
    add_summary_rows(rows, torrent_summary)
    return rows


def main() -> None:
    rows = build_rows()
    write_csv(OUTPUT, rows)
    synced = sync_summary(rows)
    print(f"Wrote {repo_path(OUTPUT)} with {len(rows)} rows")
    print(f"Synced {synced} rows into {repo_path(SUMMARY)}")


if __name__ == "__main__":
    main()
