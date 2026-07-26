import argparse
import importlib.util
import tempfile
import unittest
import wave
from pathlib import Path

import numpy as np


MODULE_PATH = (
    Path(__file__).resolve().parents[2]
    / "tools"
    / "acoustics"
    / "capture_audio_session.py"
)
SPEC = importlib.util.spec_from_file_location(
    "capture_audio_session",
    MODULE_PATH,
)
capture = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(capture)


class CaptureAudioSessionTest(unittest.TestCase):
    def test_parses_only_audio_devices_and_deduplicates(self):
        output = """
[dshow @ 1] "Camera" (video)
[dshow @ 1] "Microphone A" (audio)
[dshow @ 1] "Microphone A" (audio)
[dshow @ 1] "Loopback B" (audio)
"""
        self.assertEqual(
            capture.parse_dshow_audio_devices(output),
            ["Microphone A", "Loopback B"],
        )

    def test_command_is_bounded_pcm24_and_exact_device(self):
        command = capture.capture_command(
            Path("ffmpeg.exe"),
            "Exact Device",
            4.25,
            48_000,
            2,
            Path("capture.wav"),
        )
        self.assertIn("audio=Exact Device", command)
        self.assertEqual(command[command.index("-t") + 1], "4.250000")
        self.assertEqual(command[command.index("-c:a") + 1], "pcm_s24le")
        self.assertNotIn("default", command)

    def test_rejects_capture_without_literal_consent(self):
        arguments = argparse.Namespace(
            consent_to_record="yes",
            device="Microphone",
            duration_s=1.0,
            sample_rate_hz=48_000,
            channels=1,
            output_wav=Path("a.wav"),
            output_report=Path("a.json"),
        )
        with self.assertRaisesRegex(ValueError, "RECORD_AUDIO"):
            capture.validate_capture_arguments(arguments)

    def test_rejects_unbounded_duration(self):
        arguments = argparse.Namespace(
            consent_to_record=capture.CONSENT_TOKEN,
            device="Microphone",
            duration_s=601.0,
            sample_rate_hz=48_000,
            channels=1,
            output_wav=Path("a.wav"),
            output_report=Path("a.json"),
        )
        with self.assertRaisesRegex(ValueError, "duration"):
            capture.validate_capture_arguments(arguments)

    def test_inspects_complete_pcm24_wav(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "fixture.wav"
            samples = np.array([0, 1, -1, 4_194_304], dtype=np.int32)
            packed = np.column_stack(
                (
                    samples & 0xFF,
                    (samples >> 8) & 0xFF,
                    (samples >> 16) & 0xFF,
                )
            ).astype(np.uint8).tobytes()
            with wave.open(str(path), "wb") as output:
                output.setnchannels(1)
                output.setsampwidth(3)
                output.setframerate(48_000)
                output.writeframes(packed)
            metadata = capture.inspect_pcm_wav(path)
            self.assertEqual(metadata["sample_width_bits"], 24)
            self.assertEqual(metadata["frame_count"], 4)
            self.assertEqual(len(metadata["wav_sha256"]), 64)


if __name__ == "__main__":
    unittest.main()
