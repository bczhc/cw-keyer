#!/usr/bin/env python3
"""Read "key on" / "key off" from stdin and press/release ctrl via evdev."""

import sys
import signal
from evdev import UInput, ecodes as e

ui = UInput(
    events={e.EV_KEY: [e.KEY_LEFTCTRL]},
    name="cw-keyer-key",
)

running = True

def cleanup():
    global running
    running = False

signal.signal(signal.SIGINT, lambda *_: cleanup())
signal.signal(signal.SIGTERM, lambda *_: cleanup())

try:
    for line in sys.stdin:
        if not running:
            break
        line = line.strip()
        if line == "key on":
            ui.write(e.EV_KEY, e.KEY_LEFTCTRL, 1)
            ui.syn()
        elif line == "key off":
            ui.write(e.EV_KEY, e.KEY_LEFTCTRL, 0)
            ui.syn()
finally:
    ui.close()
