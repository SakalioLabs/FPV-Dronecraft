import hashlib
import tempfile
import unittest
from pathlib import Path

import fetch_serration_spectral_reference as fetch


class SerrationSpectralReferenceFetchTest(unittest.TestCase):
    def test_validate_accepts_exact_pinned_contract(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "reference.h5"
            payload = b"pinned-test-payload"
            path.write_bytes(payload)
            fetch.validate(
                path,
                expected_bytes=len(payload),
                expected_md5=hashlib.md5(payload).hexdigest(),
                expected_sha256=hashlib.sha256(payload).hexdigest(),
            )

    def test_validate_rejects_wrong_size_before_hashes(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "reference.h5"
            path.write_bytes(b"wrong")
            with self.assertRaisesRegex(ValueError, "byte count"):
                fetch.validate(path)

    def test_validate_rejects_wrong_hash(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "reference.h5"
            payload = b"hash-test"
            path.write_bytes(payload)
            with self.assertRaisesRegex(ValueError, "MD5"):
                fetch.validate(
                    path,
                    expected_bytes=len(payload),
                    expected_md5="0" * 32,
                    expected_sha256=hashlib.sha256(payload).hexdigest(),
                )


if __name__ == "__main__":
    unittest.main()
