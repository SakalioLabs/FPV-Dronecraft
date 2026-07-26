import struct
import tempfile
import unittest
from pathlib import Path

import verify_dda_production_bundle as bundle_verifier
import verify_dda_production_expected_results as results_verifier
from test_verify_dda_production_bundle import valid_bundle


def expected_sidecar(bundle_payload):
    bundle = bundle_verifier.verify_bytes(
        bundle_payload,
        require_complete=True,
        include_details=True,
    )
    cells = {
        cell["packed"]: (cell["material_id"], cell["fill_fraction"])
        for cell in bundle["_cell_records"]
    }
    payload = bytearray(results_verifier.MAGIC)
    payload += struct.pack(">i", results_verifier.SCHEMA)
    payload += bytes.fromhex(bundle["file_sha256"])
    payload += bytes.fromhex(bundle["snapshot_sha256"])
    payload += struct.pack(">i", bundle["rays"])
    for ray_id, ray in enumerate(bundle["_ray_records"]):
        expected = results_verifier.trace(ray, cells)
        flags = (
            (
                results_verifier.FLAG_REACHED
                if expected["reached"]
                else 0
            )
            | (
                results_verifier.FLAG_STOPPED
                if expected["stopped"]
                else 0
            )
            | (
                results_verifier.FLAG_TRUNCATED
                if expected["truncated"]
                else 0
            )
        )
        payload += struct.pack(
            ">iiiiB6dB",
            ray_id,
            len(expected["segments"]),
            expected["visited"],
            expected["material_cells"],
            flags,
            *expected["loss"],
            *expected["gain"],
            1 if expected["first_material"] is not None else 0,
        )
        if expected["first_material"] is not None:
            payload += struct.pack(">Q", expected["first_material"])
        for packed, length, material_id, fill in expected["segments"]:
            payload += struct.pack(">Qdid", packed, length, material_id, fill)
    return bytes(payload)


class VerifyDdaProductionExpectedResultsTest(unittest.TestCase):
    def verify(self, bundle_payload, sidecar_payload):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            bundle = root / "bundle.bin"
            sidecar = root / "expected.bin"
            bundle.write_bytes(bundle_payload)
            sidecar.write_bytes(sidecar_payload)
            return results_verifier.verify_expected_results(bundle, sidecar)

    def test_accepts_independently_encoded_expected_results(self):
        bundle = valid_bundle()
        summary = self.verify(bundle, expected_sidecar(bundle))

        self.assertEqual("valid", summary["status"])
        self.assertEqual(1, summary["rays"])
        self.assertGreater(summary["segments"], 0)

    def test_rejects_bundle_identity_change(self):
        bundle = valid_bundle()
        sidecar = bytearray(expected_sidecar(bundle))
        sidecar[12] ^= 1

        with self.assertRaisesRegex(
            bundle_verifier.BundleError, "input bundle hash mismatch"
        ):
            self.verify(bundle, bytes(sidecar))

    def test_rejects_expected_band_change(self):
        bundle = valid_bundle()
        sidecar = bytearray(expected_sidecar(bundle))
        first_gain_offset = 80 + 4 + 4 + 4 + 4 + 1 + 24
        sidecar[first_gain_offset] ^= 1

        with self.assertRaisesRegex(
            bundle_verifier.BundleError, "gain mismatch"
        ):
            self.verify(bundle, bytes(sidecar))

    def test_rejects_trailing_bytes(self):
        bundle = valid_bundle()

        with self.assertRaisesRegex(
            bundle_verifier.BundleError, "trailing bytes"
        ):
            self.verify(bundle, expected_sidecar(bundle) + b"\x00")


if __name__ == "__main__":
    unittest.main()
