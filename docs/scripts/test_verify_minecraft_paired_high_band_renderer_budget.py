import json
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def test_current_paired_high_band_renderer_budget_verifies(
    tmp_path: Path,
) -> None:
    output = tmp_path / "verification.json"
    subprocess.run(
        [
            sys.executable,
            str(
                ROOT
                / "docs/scripts/"
                "verify_minecraft_paired_high_band_renderer_budget.py"
            ),
            "--report",
            str(
                ROOT
                / "build/research/"
                "minecraft-paired-high-band-renderer-budget-v1.json"
            ),
            "--d121k-verification",
            str(
                ROOT
                / "build/research/"
                "minecraft-high-band-fractional-delay-verification-v1.json"
            ),
            "--contract",
            str(
                ROOT
                / "docs/acoustics/"
                "minecraft-paired-high-band-renderer-budget-contract-v1.json"
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
        == "verified-minecraft-paired-high-band-renderer-budget"
    )
