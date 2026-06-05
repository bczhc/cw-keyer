#!/bin/env python3

import os
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

OPPOSITE = {
    0x01: 0x02, 0x02: 0x01,
    0x03: 0x04, 0x04: 0x03,
}

import time
import datetime

ser = serial.Serial(PORT, BAUD, timeout=0.001)
pending = {}
log = open('keyer.log', 'w')

def flush_pending(now):
    expired = [ch for ch, p in pending.items() if now - p['time'] >= 0.001]
    for ch in expired:
        msg = MAP.get(pending[ch]['code'])
        if msg:
            print(msg, flush=True)
        del pending[ch]

try:
    while True:
        b = ser.read(1)
        now = time.monotonic()
        flush_pending(now)

        if not b:
            continue

        code = b[0]
        ts = datetime.datetime.now().strftime('%H:%M:%S.%f')
        log.write(f'{ts}  0x{code:02x}  {MAP.get(code, "?")}\n')
        log.flush()

        ch = CHANNELS.get(code)
        if ch is None:
            continue

        if ch not in pending:
            pending[ch] = {'code': code, 'time': now}
        else:
            p = pending[ch]
            if code == OPPOSITE.get(p['code']) and (now - p['time']) < 0.001:
                del pending[ch]
            else:
                msg = MAP.get(p['code'])
                if msg:
                    print(msg, flush=True)
                pending[ch] = {'code': code, 'time': now}

except KeyboardInterrupt:
    pass
finally:
    log.close()
    ser.close()
