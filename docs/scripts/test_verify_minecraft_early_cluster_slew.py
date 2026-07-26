import json
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def test_current_cluster_slew_reference_verifies(
    tmp_path: Path,
) -> None:
    output = tmp_path / "verification.json"
    subprocess.run(
        [
            sys.executable,
            str(
                ROOT
                / "docs/scripts/"
                "verify_minecraft_early_cluster_slew.py"
            ),
            "--report",
            str(
                ROOT
                / "build/research/"
                "minecraft-early-cluster-slew-v1.json"
            ),
            "--contract",
            str(
                ROOT
                / "docs/acoustics/"
                "minecraft-early-cluster-slew-contract-v1.json"
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
        == "verified-early-reflection-cluster-slew-reference"
    )
