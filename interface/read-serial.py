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
raw_log = open('interface-raw.log', 'w')
out_log = open('interface-out.log', 'w')

def output(msg):
    print(msg, flush=True)
    ts = datetime.datetime.now().strftime('%H:%M:%S.%f')
    out_log.write(f'{ts}  {msg}\n')
    out_log.flush()

def flush_pending(now):
    expired = [ch for ch, p in pending.items() if now - p['time'] >= 0.001]
    for ch in expired:
        msg = MAP.get(pending[ch]['code'])
        if msg:
            output(msg)
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
        raw_log.write(f'{ts}  0x{code:02x}  {MAP.get(code, "?")}\n')
        raw_log.flush()

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
                    output(msg)
                pending[ch] = {'code': code, 'time': now}

except KeyboardInterrupt:
    pass
finally:
    raw_log.close()
    out_log.close()
    ser.close()
