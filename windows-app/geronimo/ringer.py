"""Local Windows call ringtone playback, with no third-party dependencies."""
from __future__ import annotations

import math
import struct
import tempfile
import threading
import wave
from pathlib import Path

try:
    import winsound as _winsound
except ImportError:
    _winsound = None


class CallRinger:
    """Play an original two-note loop while an incoming call is ringing."""

    def __init__(self) -> None:
        self._lock = threading.RLock()
        self._playing = False
        self._sound_path: Path | None = None

    @property
    def is_running(self) -> bool:
        with self._lock:
            return self._playing

    def _ensure_sound_file(self) -> Path:
        if self._sound_path and self._sound_path.is_file():
            return self._sound_path

        folder = Path(tempfile.gettempdir()) / "Geronimo"
        folder.mkdir(parents=True, exist_ok=True)
        path = folder / "call-ringtone.wav"
        sample_rate = 22050
        segments = ((880.0, 0.28), (0.0, 0.13), (660.0, 0.28), (0.0, 0.92))
        frames = bytearray()
        for frequency, duration in segments:
            count = int(sample_rate * duration)
            fade_samples = max(1, int(sample_rate * 0.012))
            for index in range(count):
                if frequency == 0:
                    sample = 0
                else:
                    envelope = min(1.0, index / fade_samples, (count - index - 1) / fade_samples)
                    value = 5200.0 * max(0.0, envelope) * math.sin(
                        2.0 * math.pi * frequency * index / sample_rate
                    )
                    sample = max(-32768, min(32767, int(value)))
                frames.extend(struct.pack("<h", sample))
        with wave.open(str(path), "wb") as sound:
            sound.setnchannels(1)
            sound.setsampwidth(2)
            sound.setframerate(sample_rate)
            sound.writeframes(frames)
        self._sound_path = path
        return path

    def start(self) -> bool:
        """Start looping. Returns False when Windows sound playback is unavailable."""
        with self._lock:
            if self._playing:
                return True
            if _winsound is None:
                return False
            try:
                path = self._ensure_sound_file()
                _winsound.PlaySound(
                    str(path),
                    _winsound.SND_FILENAME | _winsound.SND_ASYNC | _winsound.SND_LOOP,
                )
                self._playing = True
                return True
            except (OSError, RuntimeError, wave.Error):
                self._playing = False
                return False

    def stop(self) -> None:
        """Stop the loop immediately; safe to call more than once."""
        with self._lock:
            if self._playing and _winsound is not None:
                try:
                    _winsound.PlaySound(None, 0)
                except (OSError, RuntimeError):
                    pass
            self._playing = False
