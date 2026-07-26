import json
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def test_current_worker_handoff_reference_verifies(tmp_path: Path) -> None:
    output = tmp_path / "verification.json"
    subprocess.run(
        [
            sys.executable,
            str(
                ROOT
                / "docs/scripts/verify_minecraft_local_plane_worker_handoff.py"
            ),
            "--report",
            str(
                ROOT
                / "build/research/minecraft-local-plane-worker-handoff-v1.json"
            ),
            "--contract",
            str(
                ROOT
                / "docs/acoustics/"
                "minecraft-local-plane-worker-handoff-contract-v1.json"
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
        == "verified-minecraft-local-plane-worker-handoff-reference"
    )
