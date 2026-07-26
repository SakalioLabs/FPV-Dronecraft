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
    "manage_audio_lab_session_for_abx_verifier",
    ROOT / "tools" / "acoustics" / "manage_audio_lab_session.py",
)
scorer = load_module(
    "score_audio_lab_abx_for_verifier",
    ROOT / "tools" / "acoustics" / "score_audio_lab_abx.py",
)
verifier = load_module(
    "verify_audio_lab_abx_fixture",
    ROOT / "docs" / "scripts" / "verify_audio_lab_abx_fixture.py",
)


class AudioLabAbxFixtureVerifyTest(unittest.TestCase):
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
        report = scorer.score(
            base / "protocol.json",
            base / "session-report.json",
            public_path,
            private_path,
            base / "responses.csv",
        )
        scorer.atomic_write_json(base / "report.json", report)

    def test_accepts_preregistered_fixture(self):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory)
            self.make_fixture(base)
            result = verifier.verify(
                base / "protocol.json",
                base / "responses.csv",
                base / "report.json",
            )
            self.assertEqual(
                result["status"],
                "valid-audio-lab-abx-statistics-fixture",
            )
            self.assertEqual(
                result["synthetic_discriminability_detections"],
                4,
            )

    def test_rejects_release_overclaim(self):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory)
            self.make_fixture(base)
            report_path = base / "report.json"
            report = json.loads(report_path.read_text(encoding="utf-8"))
            report["release_calibrated"] = True
            report_path.write_text(json.dumps(report), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "release calibration"):
                verifier.verify(
                    base / "protocol.json",
                    base / "responses.csv",
                    report_path,
                )

    def test_rejects_trial_level_inference(self):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory)
            self.make_fixture(base)
            report_path = base / "report.json"
            report = json.loads(report_path.read_text(encoding="utf-8"))
            report["inference_unit"] = "individual-trial"
            report_path.write_text(json.dumps(report), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "inference unit"):
                verifier.verify(
                    base / "protocol.json",
                    base / "responses.csv",
                    report_path,
                )


if __name__ == "__main__":
    unittest.main()
