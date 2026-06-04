#!/bin/env python3

import os
import sys
import time
import datetime
import serial

PORT = os.environ.get('SERIAL', '/dev/ttyUSB0')
BAUD = 115200

MAP = {
    0x01: 'dit down',
    0x02: 'dit up',
    0x03: 'dah down',
    0x04: 'dah up',
}

CHANNELS = {
    0x01: 'dit', 0x02: 'dit',
    0x03: 'dah', 0x04: 'dah',
}

ser = serial.Serial(PORT, BAUD)
last_time = {'dit': 0.0, 'dah': 0.0}
log = open('keyer.log', 'w')
try:
    while True:
        b = ser.read(1)
        if not b:
            break
        code = b[0]
        ts = datetime.datetime.now().strftime('%H:%M:%S.%f')
        log.write(f'{ts}  0x{code:02x}  {MAP.get(code, "?")}\n')
        log.flush()
        ch = CHANNELS.get(code)
        if ch is None:
            continue
        now = time.monotonic()
        if now - last_time[ch] >= 0.001:
            msg = MAP.get(code)
            if msg:
                print(msg, flush=True)
                last_time[ch] = now
except KeyboardInterrupt:
    pass
finally:
    log.close()
    ser.close()
