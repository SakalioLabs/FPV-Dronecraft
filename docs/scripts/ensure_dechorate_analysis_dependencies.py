#!/usr/bin/env python3
"""Install the pinned, gitignored Python stack used for SOFA analysis."""

from __future__ import annotations

import argparse
import os
import subprocess
import sys
from pathlib import Path


PACKAGES = ("numpy==2.5.1", "h5py==3.16.0", "scipy==1.18.0")


def usable(target: Path) -> bool:
    environment = os.environ.copy()
    environment["PYTHONPATH"] = str(target)
    result = subprocess.run(
        [
            sys.executable,
            "-c",
            (
                "import h5py,numpy,scipy;"
                "assert h5py.__version__=='3.16.0';"
                "assert numpy.__version__=='2.5.1';"
                "assert scipy.__version__=='1.18.0'"
            ),
        ],
        env=environment,
        capture_output=True,
        check=False,
        text=True,
    )
    return result.returncode == 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--target", type=Path, required=True)
    args = parser.parse_args()
    args.target.mkdir(parents=True, exist_ok=True)
    if not usable(args.target):
        subprocess.run(
            [
                sys.executable,
                "-m",
                "pip",
                "install",
                "--disable-pip-version-check",
                "--target",
                str(args.target),
                *PACKAGES,
            ],
            check=True,
        )
    if not usable(args.target):
        raise RuntimeError("D115 pinned Python analysis stack is unavailable")
    print("verified dEchorate analysis dependencies")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
