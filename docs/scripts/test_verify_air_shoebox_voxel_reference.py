import copy
import json
import tempfile
import unittest
from pathlib import Path

from verify_air_shoebox_voxel_reference import validate


def reports():
    room_bands = {
        "booth": {"low": 0.1, "mid": 0.2, "high": 0.3},
        "lecture": {"low": 0.4, "mid": 0.5, "high": 0.6},
    }
    air = {
        "schema_version": 1,
        "status": "valid-reference-diagnostic",
        "room_summary": [
            {
                "room": room,
                "mean_band_rt60_seconds": bands,
            }
            for room, bands in room_bands.items()
        ],
    }
    voxel = {
        "schema_version": 1,
        "status": "valid-diagnostic",
        "release_calibrated": False,
        "gates": {
            "maximum_probe_mfp_relative_error": 0.08,
            "maximum_probe_rt60_relative_error": 0.06,
            "minimum_current_stone_rt60_ratio": 2.5,
            "probe_mfp_within_10_percent": True,
            "probe_rt60_within_10_percent": True,
            "current_stone_at_least_2x_measured": True,
            "minecraft_release_calibrated": False,
        },
        "rooms": [
            {
                "id": f"air-{room}",
                "measured_rt60_s": bands,
            }
            for room, bands in room_bands.items()
        ],
    }
    return air, voxel


def write_reports(directory: Path, air, voxel):
    air_path = directory / "air.json"
    voxel_path = directory / "voxel.json"
    air_path.write_text(
        json.dumps(air, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    import hashlib

    voxel["source_air_report_sha256"] = hashlib.sha256(
        air_path.read_bytes()
    ).hexdigest()
    voxel_path.write_text(
        json.dumps(voxel, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    return air_path, voxel_path


class AirShoeboxVoxelVerificationTest(unittest.TestCase):
    def test_accepts_reports_bound_by_hash_and_band_values(self):
        with tempfile.TemporaryDirectory() as directory:
            air, voxel = reports()
            paths = write_reports(Path(directory), air, voxel)
            result = validate(*paths)
            self.assertEqual(result["status"], "valid")
            self.assertFalse(result["minecraft_release_calibrated"])

    def test_rejects_detached_air_hash(self):
        with tempfile.TemporaryDirectory() as directory:
            air, voxel = reports()
            paths = write_reports(Path(directory), air, voxel)
            modified = json.loads(paths[0].read_text(encoding="utf-8"))
            modified["extra"] = "changes identity"
            paths[0].write_text(json.dumps(modified), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "not bound"):
                validate(*paths)

    def test_rejects_band_value_drift_even_with_updated_hash(self):
        with tempfile.TemporaryDirectory() as directory:
            air, voxel = reports()
            modified = copy.deepcopy(voxel)
            modified["rooms"][0]["measured_rt60_s"]["low"] += 0.01
            paths = write_reports(Path(directory), air, modified)
            with self.assertRaisesRegex(ValueError, "detached"):
                validate(*paths)


if __name__ == "__main__":
    unittest.main()
