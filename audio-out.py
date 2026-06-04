#!/usr/bin/env python3
"""Read 'key on' / 'key off' from stdin, output sine-wave audio.

Usage:
    python3 stick_pipe.py | ./target/release/pipe -w 20 | python3 audio_out.py
    echo "key on"  | python3 audio_out.py   # test tone
"""

import sys
import time
import select
import threading
import sounddevice as sd
import numpy as np

FREQUENCY = 700
SAMPLE_RATE = 48000
VOLUME = 0.5
BLOCKSIZE = 64
RAMP_SAMPLES = 64  # ~1.3 ms fade to avoid clicks

# ── shared state ───────────────────────────────────────────────────

_lock = threading.Lock()
_key_down = False
_phase = 0.0
_current_amp = 0.0


def set_key_down(down):
    global _key_down
    with _lock:
        _key_down = down


def audio_callback(outdata, frames, _time_info, _status):
    global _phase, _current_amp

    with _lock:
        key_down = _key_down

    target_amp = VOLUME if key_down else 0.0

    t = (np.arange(frames) + _phase) / SAMPLE_RATE
    ramp = np.empty(frames, dtype=np.float32)
    ramp_len = min(RAMP_SAMPLES, frames)

    # First part: ramp from current to target
    ramp[:ramp_len] = np.linspace(_current_amp, target_amp, ramp_len, dtype=np.float32)
    # Remainder: target
    if frames > ramp_len:
        ramp[ramp_len:] = target_amp

    outdata[:, 0] = ramp * np.sin(2 * np.pi * FREQUENCY * t, dtype=np.float32)

    _phase = (_phase + frames) % SAMPLE_RATE
    _current_amp = target_amp


# ── main ────────────────────────────────────────────────────────────

stream = sd.OutputStream(
    samplerate=SAMPLE_RATE, channels=1, callback=audio_callback,
    blocksize=BLOCKSIZE, latency="low",
)
stream.start()

try:
    for line in sys.stdin:
        line = line.strip()
        if line.endswith("key on"):
            set_key_down(True)
        elif line.endswith("key off"):
            set_key_down(False)
except KeyboardInterrupt:
    pass
finally:
    stream.stop()
    stream.close()
