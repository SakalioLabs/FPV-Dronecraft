import copy
import json
import tempfile
import unittest
from pathlib import Path

from verify_public_source_data_registry import DEFAULT_REGISTRY, load_and_validate


class PublicSourceDataRegistryTest(unittest.TestCase):
    def test_repository_registry_is_valid_and_has_no_release_profile(self):
        registry = load_and_validate(DEFAULT_REGISTRY)
        self.assertGreaterEqual(len(registry["datasets"]), 9)
        self.assertFalse(
            any(
                dataset["release_profile_eligible"]
                for dataset in registry["datasets"]
            )
        )

    def test_manual_eligibility_override_is_rejected(self):
        registry = load_and_validate(DEFAULT_REGISTRY)
        modified = copy.deepcopy(registry)
        modified["datasets"][0]["release_profile_eligible"] = True
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "registry.json"
            path.write_text(
                json.dumps(modified, ensure_ascii=False),
                encoding="utf-8",
            )
            with self.assertRaisesRegex(ValueError, "eligibility"):
                load_and_validate(path)

    def test_missing_hard_gate_is_rejected(self):
        registry = load_and_validate(DEFAULT_REGISTRY)
        modified = copy.deepcopy(registry)
        del modified["datasets"][0]["gates"][registry["hard_gates"][0]]
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "registry.json"
            path.write_text(
                json.dumps(modified, ensure_ascii=False),
                encoding="utf-8",
            )
            with self.assertRaisesRegex(ValueError, "exactly match"):
                load_and_validate(path)


if __name__ == "__main__":
    unittest.main()
