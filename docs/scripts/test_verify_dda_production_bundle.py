import hashlib
import struct
import unittest

import verify_dda_production_bundle as verifier


def pack_cell(x, y, z):
    return (
        ((x & 0x3FFFFFF) << 38)
        | ((z & 0x3FFFFFF) << 12)
        | (y & 0xFFF)
    )


def encode_string(value):
    encoded = value.encode("utf-8")
    return struct.pack(">i", len(encoded)) + encoded


def valid_bundle(cells=None, complete=True):
    if cells is None:
        cells = [
            {
                "packed": pack_cell(0, 0, 0),
                "material_id": 0,
                "fill_fraction": 0.0,
            },
            {
                "packed": pack_cell(1, 2, 3),
                "material_id": 3,
                "fill_fraction": 0.75,
            },
        ]
    snapshot_hash = verifier.snapshot_sha256(complete, cells)
    payload = bytearray(verifier.BUNDLE_MAGIC)
    payload += struct.pack(
        ">iii",
        verifier.BUNDLE_SCHEMA,
        1,
        verifier.MATERIAL_SCHEMA,
    )
    payload += bytes.fromhex(verifier.material_table_sha256())
    payload += encode_string("test-minecraft")
    payload += encode_string("test-mod")
    payload += hashlib.sha256(b"content").digest()
    payload += struct.pack(">q", 7)
    payload += b"\x01" if complete else b"\x00"
    payload += bytes.fromhex(snapshot_hash)
    payload += struct.pack(">i", len(cells))
    for cell in cells:
        payload += struct.pack(
            ">Qid",
            cell["packed"],
            cell["material_id"],
            cell["fill_fraction"],
        )
    payload += struct.pack(">i6di", 1, 0.5, 0.5, 0.5, 4.5, 5.5, 6.5, 192)
    return bytes(payload)


class VerifyDdaProductionBundleTest(unittest.TestCase):
    def test_accepts_valid_bundle_and_unpacks_cells(self):
        summary = verifier.verify_bytes(valid_bundle())

        self.assertEqual("valid", summary["status"])
        self.assertEqual(2, summary["cells"])
        self.assertEqual(1, summary["rays"])
        self.assertEqual((0, 0, 0), summary["first_cell"])
        self.assertEqual((1, 2, 3), summary["last_cell"])

    def test_rejects_material_table_hash_change(self):
        payload = bytearray(valid_bundle())
        payload[20] ^= 1

        with self.assertRaisesRegex(
            verifier.BundleError, "material table hash changed"
        ):
            verifier.verify_bytes(bytes(payload))

    def test_rejects_malformed_utf8(self):
        payload = bytearray(valid_bundle())
        payload[56] = 0xC3

        with self.assertRaisesRegex(
            verifier.BundleError, "not valid UTF-8"
        ):
            verifier.verify_bytes(bytes(payload))

    def test_rejects_unsigned_cell_order_violation(self):
        cells = [
            {
                "packed": pack_cell(1, 2, 3),
                "material_id": 3,
                "fill_fraction": 0.75,
            },
            {
                "packed": pack_cell(0, 0, 0),
                "material_id": 0,
                "fill_fraction": 0.0,
            },
        ]

        with self.assertRaisesRegex(
            verifier.BundleError, "strictly ordered"
        ):
            verifier.verify_bytes(valid_bundle(cells))

    def test_rejects_trailing_bytes(self):
        with self.assertRaisesRegex(
            verifier.BundleError, "trailing bytes"
        ):
            verifier.verify_bytes(valid_bundle() + b"\x00")

    def test_require_complete_rejects_incomplete_snapshot(self):
        with self.assertRaisesRegex(
            verifier.BundleError, "snapshot is incomplete"
        ):
            verifier.verify_bytes(
                valid_bundle(complete=False),
                require_complete=True,
            )


if __name__ == "__main__":
    unittest.main()
