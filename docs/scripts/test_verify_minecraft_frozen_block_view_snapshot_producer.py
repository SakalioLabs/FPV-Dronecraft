import json
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def test_current_frozen_block_view_producer_verifies(
    tmp_path: Path,
) -> None:
    output = tmp_path / "verification.json"
    subprocess.run(
        [
            sys.executable,
            str(
                ROOT
                / "docs/scripts/"
                "verify_minecraft_frozen_block_view_snapshot_producer.py"
            ),
            "--report",
            str(
                ROOT
                / "build/research/"
                "minecraft-frozen-block-view-snapshot-producer-v1.json"
            ),
            "--contract",
            str(
                ROOT
                / "docs/acoustics/"
                "minecraft-frozen-block-view-snapshot-producer-contract-v1.json"
            ),
            "--output-json",
            str(output),
        ],
        check=True,
        cwd=ROOT,
    )
    result = json.loads(output.read_text(encoding="utf-8"))
    assert (
        result["status"]
        == "verified-frozen-block-view-snapshot-producer-reference"
    )
