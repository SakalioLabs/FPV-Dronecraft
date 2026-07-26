import hashlib
import tempfile
import unittest
import zipfile
from pathlib import Path

import fetch_air_rir_reference as fetch


def write_archive(path: Path, license_payload: bytes) -> bytes:
    with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_STORED) as archive:
        archive.writestr(fetch.LICENSE_ENTRY, license_payload)
    return path.read_bytes()


class AirRirReferenceFetchTest(unittest.TestCase):
    def test_validate_accepts_pinned_archive_and_license(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "air.zip"
            license_payload = b"MIT License\nsynthetic fixture\n"
            payload = write_archive(path, license_payload)
            fetch.validate(
                path,
                expected_bytes=len(payload),
                expected_sha256=hashlib.sha256(payload).hexdigest(),
                expected_license_sha256=hashlib.sha256(
                    license_payload
                ).hexdigest(),
            )

    def test_validate_rejects_archive_hash_change(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "air.zip"
            license_payload = b"MIT License\nsynthetic fixture\n"
            payload = write_archive(path, license_payload)
            with self.assertRaisesRegex(ValueError, "SHA-256"):
                fetch.validate(
                    path,
                    expected_bytes=len(payload),
                    expected_sha256="0" * 64,
                    expected_license_sha256=hashlib.sha256(
                        license_payload
                    ).hexdigest(),
                )

    def test_validate_rejects_non_mit_embedded_license(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "air.zip"
            license_payload = b"unknown terms\n"
            payload = write_archive(path, license_payload)
            with self.assertRaisesRegex(ValueError, "not identified as MIT"):
                fetch.validate(
                    path,
                    expected_bytes=len(payload),
                    expected_sha256=hashlib.sha256(payload).hexdigest(),
                    expected_license_sha256=hashlib.sha256(
                        license_payload
                    ).hexdigest(),
                )


if __name__ == "__main__":
    unittest.main()
