import json
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def test_current_fractional_delay_selection_verifies(
    tmp_path: Path,
) -> None:
    output = tmp_path / "verification.json"
    subprocess.run(
        [
            sys.executable,
            str(
                ROOT
                / "docs/scripts/"
                "verify_minecraft_fractional_delay_selection.py"
            ),
            "--report",
            str(
                ROOT
                / "build/research/"
                "minecraft-fractional-delay-selection-v1.json"
            ),
            "--benchmark",
            str(
                ROOT
                / "build/research/"
                "minecraft-fractional-delay-renderer-benchmark-v1.json"
            ),
            "--contract",
            str(
                ROOT
                / "docs/acoustics/"
                "minecraft-fractional-delay-selection-contract-v1.json"
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
        == "verified-minecraft-fractional-delay-selection"
    )
