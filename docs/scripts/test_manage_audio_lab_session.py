import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


MODULE_PATH = (
    Path(__file__).resolve().parents[2]
    / "tools"
    / "acoustics"
    / "manage_audio_lab_session.py"
)
SPEC = importlib.util.spec_from_file_location(
    "manage_audio_lab_session",
    MODULE_PATH,
)
session = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(session)


class ManageAudioLabSessionTest(unittest.TestCase):
    def test_plan_is_deterministic_complete_and_randomized(self):
        left = session.generate_plan("session-001", 3, "seed")
        right = session.generate_plan("session-001", 3, "seed")
        self.assertEqual(left, right)
        self.assertEqual(left["capture_count"], 18)
        self.assertEqual(left["condition_count"], 6)
        self.assertFalse(left["recording_started"])
        first_block = [
            (item["backend"], item["variant"])
            for item in left["captures"][:6]
        ]
        canonical = [
            (backend, variant)
            for backend in session.BACKENDS
            for variant in session.VARIANTS
        ]
        self.assertNotEqual(first_block, canonical)

    def test_rejects_fewer_than_three_takes(self):
        with self.assertRaisesRegex(ValueError, r"\[3, 20\]"):
            session.generate_plan("session-001", 2, "seed")

    def test_fixture_materializes_hash_bound_balanced_abx(self):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory)
            report = session.generate_fixture(
                base / "session",
                base / "plan.json",
                base / "report.json",
                base / "blind",
            )
            self.assertEqual(report["capture_count"], 18)
            self.assertTrue(report["evidence_complete"])
            self.assertTrue(report["same_capture_chain"])
            self.assertTrue(report["native_timing_evidence_complete"])
            self.assertFalse(
                report["native_timing_identity"][
                    "end_to_end_latency_measured"
                ]
            )
            self.assertFalse(report["real_loopback_evidence_complete"])
            self.assertFalse(report["continuity_gate_passed"])
            public = json.loads(
                (base / "blind" / "public-manifest.json").read_text(
                    encoding="utf-8"
                )
            )
            private = json.loads(
                (base / "blind" / "private-answer-key.json").read_text(
                    encoding="utf-8"
                )
            )
            self.assertEqual(public["trial_count"], 18)
            self.assertEqual(private["trial_count"], 18)
            self.assertNotIn("a_backend", public["trials"][0])
            answers = [trial["x_is"] for trial in private["trials"]]
            self.assertEqual(answers.count("A"), 9)
            self.assertEqual(answers.count("B"), 9)

    def test_rejects_detached_wav_hash(self):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory)
            session.generate_fixture(
                base / "session",
                base / "plan.json",
                base / "report.json",
                base / "blind",
            )
            plan = session.load_json(base / "plan.json")
            case = plan["captures"][0]
            capture_path = (
                base / "session" / case["artifacts"]["capture_report"]
            )
            capture = json.loads(capture_path.read_text(encoding="utf-8"))
            capture["audio"]["wav_sha256"] = "0" * 64
            capture_path.write_text(json.dumps(capture), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "WAV hash binding failed"):
                session.materialize(
                    base / "plan.json",
                    base / "session",
                    base / "second-report.json",
                    base / "second-blind",
                )

    def test_rejects_tampered_native_timing(self):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory)
            session.generate_fixture(
                base / "session",
                base / "plan.json",
                base / "report.json",
                base / "blind",
            )
            plan = session.load_json(base / "plan.json")
            case = plan["captures"][0]
            timeline_path = (
                base / "session" / case["artifacts"]["timeline"]
            )
            timeline = json.loads(
                timeline_path.read_text(encoding="utf-8")
            )
            timeline["native_timing"]["before_boundary"]["second"][
                "alc_error_code"
            ] = 1
            timeline_path.write_text(
                json.dumps(timeline),
                encoding="utf-8",
            )
            with self.assertRaisesRegex(ValueError, "OpenAL error"):
                session.materialize(
                    base / "plan.json",
                    base / "session",
                    base / "second-report.json",
                    base / "second-blind",
                )


if __name__ == "__main__":
    unittest.main()
