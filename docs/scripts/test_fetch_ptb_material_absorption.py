import hashlib
import tempfile
import unittest
import zipfile
from pathlib import Path

import fetch_ptb_material_absorption as fetch


class PtbMaterialFetchTest(unittest.TestCase):
    def test_validate_accepts_pinned_workbook_entry(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "ptb.zip"
            workbook = b"synthetic xls fixture"
            with zipfile.ZipFile(path, "w") as archive:
                archive.writestr(fetch.WORKBOOK_ENTRY, workbook)
            payload = path.read_bytes()
            fetch.validate(
                path,
                expected_bytes=len(payload),
                expected_sha256=hashlib.sha256(payload).hexdigest(),
                expected_workbook_sha256=hashlib.sha256(
                    workbook
                ).hexdigest(),
            )

    def test_validate_rejects_workbook_hash_change(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "ptb.zip"
            with zipfile.ZipFile(path, "w") as archive:
                archive.writestr(fetch.WORKBOOK_ENTRY, b"fixture")
            payload = path.read_bytes()
            with self.assertRaisesRegex(ValueError, "workbook SHA-256"):
                fetch.validate(
                    path,
                    expected_bytes=len(payload),
                    expected_sha256=hashlib.sha256(payload).hexdigest(),
                    expected_workbook_sha256="0" * 64,
                )


if __name__ == "__main__":
    unittest.main()
