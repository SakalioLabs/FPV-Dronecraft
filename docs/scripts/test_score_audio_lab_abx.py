import csv
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def load_module(name: str, path: Path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


manager = load_module(
    "manage_audio_lab_session_for_abx",
    ROOT / "tools" / "acoustics" / "manage_audio_lab_session.py",
)
scorer = load_module(
    "score_audio_lab_abx",
    ROOT / "tools" / "acoustics" / "score_audio_lab_abx.py",
)


class ScoreAudioLabAbxTest(unittest.TestCase):
    def make_fixture(self, base: Path):
        manager.generate_fixture(
            base / "session",
            base / "plan.json",
            base / "session-report.json",
            base / "blind",
        )
        public_path = base / "blind" / "public-manifest.json"
        private_path = base / "blind" / "private-answer-key.json"
        protocol = scorer.create_protocol(
            base / "session-report.json",
            public_path,
            private_path,
            12,
            3,
            "fixture-presentation-seed",
            0.05,
            0.75,
        )
        scorer.atomic_write_json(base / "protocol.json", protocol)
        public, private = scorer.validate_manifests(
            public_path,
            private_path,
        )
        scorer.write_fixture_responses(
            base / "responses.csv",
            public,
            private,
            "fixture-presentation-seed",
        )
        return scorer.score(
            base / "protocol.json",
            base / "session-report.json",
            public_path,
            private_path,
            base / "responses.csv",
        )

    def test_exact_binomial_reference_values(self):
        self.assertAlmostEqual(
            scorer.binomial_upper_tail(11, 12),
            13 / 4096,
        )
        self.assertAlmostEqual(
            scorer.binomial_two_sided(12, 12),
            2 / 4096,
        )
        self.assertEqual(scorer.binomial_two_sided(6, 12), 1.0)

    def test_fixture_detects_only_planted_dry_differences(self):
        with tempfile.TemporaryDirectory() as directory:
            report = self.make_fixture(Path(directory))
            detected = [
                item
                for item in report["discrimination"]
                if item["discriminability_detected"]
            ]
            self.assertEqual(len(detected), 4)
            self.assertTrue(
                all("dry" in item["backend_pair"] for item in detected)
            )
            preferences = [
                item
                for item in report["preference"]
                if item["preference_detected"]
            ]
            self.assertEqual(len(preferences), 4)
            self.assertTrue(
                all(
                    item["preferred_backend_direction"] != "dry"
                    for item in preferences
                )
            )
            self.assertFalse(report["release_calibrated"])

    def test_rejects_incomplete_participant(self):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory)
            self.make_fixture(base)
            response_path = base / "responses.csv"
            with response_path.open(
                encoding="utf-8", newline=""
            ) as response_stream:
                rows = list(csv.DictReader(response_stream))
            rows.pop()
            with response_path.open("w", encoding="utf-8", newline="") as stream:
                writer = csv.DictWriter(
                    stream, fieldnames=scorer.RESPONSE_FIELDS
                )
                writer.writeheader()
                writer.writerows(rows)
            with self.assertRaisesRegex(ValueError, "coverage mismatch"):
                scorer.score(
                    base / "protocol.json",
                    base / "session-report.json",
                    base / "blind" / "public-manifest.json",
                    base / "blind" / "private-answer-key.json",
                    response_path,
                )

    def test_rejects_replay_limit_violation(self):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory)
            self.make_fixture(base)
            response_path = base / "responses.csv"
            with response_path.open(
                encoding="utf-8", newline=""
            ) as response_stream:
                rows = list(csv.DictReader(response_stream))
            rows[0]["extra_replays"] = "4"
            with response_path.open("w", encoding="utf-8", newline="") as stream:
                writer = csv.DictWriter(
                    stream, fieldnames=scorer.RESPONSE_FIELDS
                )
                writer.writeheader()
                writer.writerows(rows)
            with self.assertRaisesRegex(ValueError, r"\[0,3\]"):
                scorer.score(
                    base / "protocol.json",
                    base / "session-report.json",
                    base / "blind" / "public-manifest.json",
                    base / "blind" / "private-answer-key.json",
                    response_path,
                )

    def test_rejects_nonpreregistered_presentation_order(self):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory)
            self.make_fixture(base)
            response_path = base / "responses.csv"
            with response_path.open(
                encoding="utf-8", newline=""
            ) as response_stream:
                rows = list(csv.DictReader(response_stream))
            rows[0]["presentation_index"], rows[1]["presentation_index"] = (
                rows[1]["presentation_index"],
                rows[0]["presentation_index"],
            )
            with response_path.open("w", encoding="utf-8", newline="") as stream:
                writer = csv.DictWriter(
                    stream, fieldnames=scorer.RESPONSE_FIELDS
                )
                writer.writeheader()
                writer.writerows(rows)
            with self.assertRaisesRegex(
                ValueError,
                "presentation order does not match preregistration",
            ):
                scorer.score(
                    base / "protocol.json",
                    base / "session-report.json",
                    base / "blind" / "public-manifest.json",
                    base / "blind" / "private-answer-key.json",
                    response_path,
                )


if __name__ == "__main__":
    unittest.main()
