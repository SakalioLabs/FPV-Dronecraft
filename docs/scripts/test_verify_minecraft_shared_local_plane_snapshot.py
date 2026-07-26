import json
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def test_current_shared_snapshot_reference_verifies(tmp_path: Path) -> None:
    output = tmp_path / "verification.json"
    subprocess.run(
        [
            sys.executable,
            str(
                ROOT
                / "docs/scripts/"
                "verify_minecraft_shared_local_plane_snapshot.py"
            ),
            "--report",
            str(
                ROOT
                / "build/research/"
                "minecraft-shared-local-plane-snapshot-v1.json"
            ),
            "--contract",
            str(
                ROOT
                / "docs/acoustics/"
                "minecraft-shared-local-plane-snapshot-contract-v1.json"
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
        == "verified-minecraft-shared-local-plane-snapshot-reference"
    )
