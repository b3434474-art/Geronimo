from __future__ import annotations

import sys
import unittest
import wave
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from geronimo.ringer import CallRinger


class FakeWinSound:
    SND_FILENAME = 1
    SND_ASYNC = 2
    SND_LOOP = 8

    def __init__(self):
        self.calls = []

    def PlaySound(self, sound, flags):
        self.calls.append((sound, flags))


class CallRingerTests(unittest.TestCase):
    def test_builds_valid_wave_file_and_starts_once(self):
        fake = FakeWinSound()
        with patch("geronimo.ringer._winsound", fake):
            ringer = CallRinger()
            self.assertTrue(ringer.start())
            self.assertTrue(ringer.is_running)
            self.assertTrue(ringer.start())
            self.assertEqual(len(fake.calls), 1)

            sound_path, flags = fake.calls[0]
            self.assertTrue(Path(sound_path).is_file())
            self.assertEqual(flags, FakeWinSound.SND_FILENAME | FakeWinSound.SND_ASYNC | FakeWinSound.SND_LOOP)
            with wave.open(sound_path, "rb") as sound:
                self.assertEqual(sound.getnchannels(), 1)
                self.assertEqual(sound.getsampwidth(), 2)
                self.assertEqual(sound.getframerate(), 22050)
                self.assertGreater(sound.getnframes(), 22050)

    def test_stop_is_idempotent_and_stops_playback(self):
        fake = FakeWinSound()
        with patch("geronimo.ringer._winsound", fake):
            ringer = CallRinger()
            self.assertTrue(ringer.start())
            ringer.stop()
            ringer.stop()
            self.assertFalse(ringer.is_running)
            self.assertEqual(fake.calls[-1], (None, 0))
            self.assertEqual(len(fake.calls), 2)

    def test_non_windows_environment_reports_unavailable(self):
        with patch("geronimo.ringer._winsound", None):
            ringer = CallRinger()
            self.assertFalse(ringer.start())
            self.assertFalse(ringer.is_running)
            ringer.stop()


if __name__ == "__main__":
    unittest.main()
