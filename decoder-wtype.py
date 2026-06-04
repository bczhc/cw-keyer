#!/usr/bin/env python3
"""Read keyer output from stdin, decode morse, type via wtype."""

import sys
import argparse

_MORSE_MAP = {
    ".-": "a", "-...": "b", "-.-.": "c", "-..": "d",
    ".": "e", "..-.": "f", "--.": "g", "....": "h",
    "..": "i", ".---": "j", "-.-": "k", ".-..": "l",
    "--": "m", "-.": "n", "---": "o", ".--.": "p",
    "--.-": "q", ".-.": "r", "...": "s", "-": "t",
    "..-": "u", "...-": "v", ".--": "w", "-..-": "x",
    "-.--": "y", "--..": "z",
    ".----": "1", "..---": "2", "...--": "3", "....-": "4",
    ".....": "5", "-....": "6", "--...": "7", "---..": "8",
    "----.": "9", "-----": "0",
    ".-.-.-": ".", "--..--": ",", "..--..": "?", ".----.": "'",
    "-.-.--": "!", "-..-.": "/", "-.--.": "(", "-.--.-": ")",
    ".-...": "&", "---...": ":", "-.-.-.": ";", "-...-": "=",
    ".-.-.": "+", "-....-": "-", "..--.-": "_", ".-..-.": '"',
    "...-..-": "$", ".--.-.": "@",
    "..--": " ",
    "-...-": "\n",
}

import subprocess


def type_char(ch):
    try:
        subprocess.run(
            ["wtype", ch],
            check=True,
            timeout=0.5,
            stdin=subprocess.DEVNULL,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
    except Exception:
        pass


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument(
        "--no-auto-space",
        action="store_true",
        help="don't add spaces on word breaks; use ..-- (dit dit dah dah) instead",
    )
    args = ap.parse_args()

    pat = ""
    last_char = ""

    for line in sys.stdin:
        e = line.strip()
        if e == "dit":
            pat += "."
        elif e == "dah":
            pat += "-"
        elif e == "char":
            ch = _MORSE_MAP.get(pat)
            if ch is not None:
                type_char(ch)
                last_char = ch
            pat = ""
        elif e == "word":
            if not args.no_auto_space and last_char != "\n":
                type_char(" ")
            pat = ""
        elif e == "key on":
            pass
        elif e == "key off":
            pass


if __name__ == "__main__":
    main()
