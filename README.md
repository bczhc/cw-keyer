cw-keyer
===

Morse Code keyer and visualizer.

Supported keyer modes: Iambic A, Iambic B, Ultimatic and Straight.

## Keyer

`keyer` is a state machine program. It receives events from stdin and produces events to stdout.

Input events (4):

`dit down`, `dit up`, `dah down`, `dah up`

which basically indicates if the dit/dah paddle is pressed.

Output events (8):

- `dit down`/`dit up`/`dah down`/`dah up`: just echoes of stdin
- `dit`, `dah`: dit/dah is recognized
- `key on`, `key off`: output key (tone) starts/stops
- `char`, `word`: indicates the boundary of a character/word

## Visualizer

The `visualizer` WGPU subproject receives stdin events:

- `dit down`, `dit up`, `dah down`, `dah up`
- `key on`, `key off`

and draw a representation of the dit/dah/tone lanes in realtime to demonstrate the timing.

## Usage

Some interesting things can be done with programs above and these Python scripts altogether.

- `audio-out.py`: play tones according to `key on` and `key off` from stdin
- `gamepad-pipe.py`: map two buttons of my gamepad to these four paddle events (`(dit|dah) (down|up)`)
- `interface/read-serial.py`: read bytes sent from an ESP8266 chipboard, from `/dev/ttyUSBx`, and map them to these four paddle events

### Dual-lever Paddle

I literally grabbed an ESP8266 and made it the keyer interface.

So first, grab an ESP8266 and set things up like this:

TODO

Pins and mappings:

- D1 low: dit down - 0x01
- D1 high: dit up - 0x02
- D2 low: dah down - 0x03
- D2 high: dah up - 0x04

Corresponding byte will be sent to the serial @ 115200 baud, so read them using `interface/read-serial.py`.

Wire things up:

```shell
interface/read-serial.py | target/debug/keyer -m ultimatic -w25 | pee ./audio-out.py visualizer/target/debug/visualizer ./decoder-wtype.py
```

Demonstration:

TODO

Note the `./decoder-wtype.py` is optional. It receives `dit`, `dah`, `char`, `word` events and decodes them, and use [`wtype`](https://github.com/atx/wtype) to type on Wayland.

### Gamepad

Similar to above:

```shell
./gamepad-pipe-py | target/debug/keyer -m ultimatic -w25 | pee ./audio-out.py visualizer/target/debug/visualizer ./decoder-wtype.py
```

Demonstration:

TODO

## Notes

I use this as a software-defined Morse keyer solution. Stuff is _mostly_ vibe coded with OpenCode:deepseek-v4-pro, as a disclaimer, and at least the README is written by me all by-hand :).

The project is inspired by <https://www.youtube.com/watch?v=Hn4j2nfdKNE> and their <didahdit.com> website.
