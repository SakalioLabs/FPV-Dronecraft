import json
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def test_current_high_band_fractional_delay_verifies(
    tmp_path: Path,
) -> None:
    output = tmp_path / "verification.json"
    subprocess.run(
        [
            sys.executable,
            str(
                ROOT
                / "docs/scripts/"
                "verify_minecraft_high_band_fractional_delay.py"
            ),
            "--report",
            str(
                ROOT
                / "build/research/"
                "minecraft-high-band-fractional-delay-v1.json"
            ),
            "--benchmark",
            str(
                ROOT
                / "build/research/"
                "minecraft-high-band-fractional-delay-benchmark-v1.json"
            ),
            "--contract",
            str(
                ROOT
                / "docs/acoustics/"
                "minecraft-high-band-fractional-delay-contract-v1.json"
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
        == "verified-minecraft-high-band-fractional-delay-"
        "runtime-gate-failed"
    )
