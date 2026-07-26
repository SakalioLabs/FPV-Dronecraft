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
    "manage_audio_lab_session_for_verifier",
    ROOT / "tools" / "acoustics" / "manage_audio_lab_session.py",
)
verifier = load_module(
    "verify_audio_lab_session_fixture",
    ROOT / "docs" / "scripts" / "verify_audio_lab_session_fixture.py",
)


class AudioLabSessionFixtureVerifyTest(unittest.TestCase):
    def make_fixture(self, base: Path):
        manager.generate_fixture(
            base / "session",
            base / "plan.json",
            base / "report.json",
            base / "blind",
        )

    def test_accepts_complete_fixture(self):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory)
            self.make_fixture(base)
            result = verifier.verify(
                base / "report.json",
                base / "plan.json",
                base / "blind",
            )
            self.assertEqual(
                result["status"],
                "valid-audio-lab-session-fixture",
            )
            self.assertEqual(result["x_answer_balance"], {"A": 9, "B": 9})
            self.assertTrue(result["gates"]["native_timing_hash_bound"])

    def test_rejects_loopback_overclaim(self):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory)
            self.make_fixture(base)
            report_path = base / "report.json"
            report = json.loads(report_path.read_text(encoding="utf-8"))
            report["real_loopback_evidence_complete"] = True
            report_path.write_text(json.dumps(report), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "real loopback"):
                verifier.verify(
                    report_path,
                    base / "plan.json",
                    base / "blind",
                )

    def test_rejects_public_backend_leak(self):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory)
            self.make_fixture(base)
            public_path = base / "blind" / "public-manifest.json"
            public = json.loads(public_path.read_text(encoding="utf-8"))
            public["trials"][0]["a_backend"] = "dry"
            public_path.write_text(json.dumps(public), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "leaks fields"):
                verifier.verify(
                    base / "report.json",
                    base / "plan.json",
                    base / "blind",
                )

    def test_rejects_native_timing_overclaim(self):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory)
            self.make_fixture(base)
            report_path = base / "report.json"
            report = json.loads(report_path.read_text(encoding="utf-8"))
            report["cases"][0]["native_timing"][
                "end_to_end_latency_measured"
            ] = True
            report_path.write_text(json.dumps(report), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "timing identity"):
                verifier.verify(
                    report_path,
                    base / "plan.json",
                    base / "blind",
                )

    def test_rejects_unbalanced_private_answers(self):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory)
            self.make_fixture(base)
            private_path = base / "blind" / "private-answer-key.json"
            private = json.loads(private_path.read_text(encoding="utf-8"))
            target = next(
                trial for trial in private["trials"] if trial["x_is"] == "B"
            )
            target["x_is"] = "A"
            target["source_wav_sha256"]["X"] = target[
                "source_wav_sha256"
            ]["A"]
            private_path.write_text(json.dumps(private), encoding="utf-8")
            with self.assertRaisesRegex(
                ValueError,
                "blind audio copy is detached",
            ):
                verifier.verify(
                    base / "report.json",
                    base / "plan.json",
                    base / "blind",
                )


if __name__ == "__main__":
    unittest.main()
