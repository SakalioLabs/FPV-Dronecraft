#!/usr/bin/env python3
"""Exercise native bundle verifier corruption paths from CTest."""

from __future__ import annotations

import argparse
import json
import subprocess
import tempfile
from pathlib import Path


def run(
    executable: Path,
    payload: bytes,
    expected_results: bytes | None,
    temporary_directory: Path,
    name: str,
    expected_code: int,
    expected_text: str,
) -> None:
    bundle = temporary_directory / f"{name}.bin"
    bundle.write_bytes(payload)
    command = [str(executable)]
    if expected_results is not None:
        sidecar = temporary_directory / f"{name}.expected.bin"
        sidecar.write_bytes(expected_results)
        command += ["--expected-results", str(sidecar)]
    command.append(str(bundle))
    result = subprocess.run(
        command,
        capture_output=True,
        check=False,
        text=True,
        timeout=30,
    )
    combined = result.stdout + result.stderr
    if result.returncode != expected_code or expected_text not in combined:
        raise RuntimeError(
            f"{name}: code={result.returncode}, output={combined!r}"
        )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--executable", type=Path, required=True)
    parser.add_argument("--fixture", type=Path, required=True)
    parser.add_argument("--expected-results", type=Path)
    arguments = parser.parse_args()
    original = arguments.fixture.read_bytes()
    expected = (
        arguments.expected_results.read_bytes()
        if arguments.expected_results is not None
        else None
    )
    with tempfile.TemporaryDirectory() as directory:
        temporary_directory = Path(directory)
        run(
            arguments.executable,
            original,
            expected,
            temporary_directory,
            "valid",
            0,
            '"status":"valid"',
        )
        material_hash = bytearray(original)
        material_hash[20] ^= 1
        run(
            arguments.executable,
            bytes(material_hash),
            expected,
            temporary_directory,
            "material-hash",
            1,
            "material table hash changed",
        )
        malformed_utf8 = bytearray(original)
        malformed_utf8[56] = 0xC3
        run(
            arguments.executable,
            bytes(malformed_utf8),
            expected,
            temporary_directory,
            "malformed-utf8",
            1,
            "not valid UTF-8",
        )
        run(
            arguments.executable,
            original[:-1],
            expected,
            temporary_directory,
            "truncated",
            1,
            "truncated production bundle",
        )
        run(
            arguments.executable,
            original + b"\x00",
            expected,
            temporary_directory,
            "trailing",
            1,
            "trailing bytes",
        )
        if expected is not None:
            sidecar_bundle_hash = bytearray(expected)
            sidecar_bundle_hash[12] ^= 1
            run(
                arguments.executable,
                original,
                bytes(sidecar_bundle_hash),
                temporary_directory,
                "sidecar-bundle-hash",
                1,
                "input bundle hash mismatch",
            )
            sidecar_gain = bytearray(expected)
            sidecar_gain[121] ^= 1
            run(
                arguments.executable,
                original,
                bytes(sidecar_gain),
                temporary_directory,
                "sidecar-gain",
                1,
                "gain mismatch",
            )
            run(
                arguments.executable,
                original,
                expected[:-1],
                temporary_directory,
                "sidecar-truncated",
                1,
                "truncated production bundle",
            )
            run(
                arguments.executable,
                original,
                expected + b"\x00",
                temporary_directory,
                "sidecar-trailing",
                1,
                "trailing bytes after expected results",
            )
    print(
        json.dumps(
            {
                "status": "passed",
                "cases": 9 if expected is not None else 5,
                "fixture_bytes": len(original),
            },
            sort_keys=True,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
