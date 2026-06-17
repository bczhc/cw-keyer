//! Debounce filter for dah paddle events.
//!
//! Ensures every dah press has a minimum duration of `MIN_DAH_DURATION` (10 ms).
//! When the first dah-down arrives the filter immediately echoes it, then starts
//! a timer.  Any dah-up / dah-down events that occur while the timer is running
//! are absorbed (only the *latest* paddle state is remembered).  Once the timer
//! expires:
//!
//! - If the paddle is still held → wait for the physical dah-up.
//! - If the paddle has already been released → emit dah-up right away.
//!
//! Dit events pass through unchanged.
//!
//! ## stdin / stdout (one per line)
//!   dit down  /  dit up
//!   dah down  /  dah up

use std::io::{self, stdin, stdout, BufRead, Write};
use std::sync::mpsc::{self, TryRecvError};
use std::thread;
use std::time::Instant;

/// Minimum dah duration in seconds.
const MIN_DAH_DURATION: f64 /* in seconds */ = 0.020;

// ── channel ──────────────────────────────────────────────────────────

enum Cmd {
    Dit(bool), // true = down, false = up
    Dah(bool),
}

// ── dah state machine ────────────────────────────────────────────────

enum DahState {
    /// No dah activity.
    Idle,
    /// Timer running — absorbing bounce, recording latest paddle state.
    Debouncing {
        deadline: f64,
        pressed: bool,
    },
    /// Legitimate dah-down already output; waiting for physical dah-up.
    Down,
}

// ── main ─────────────────────────────────────────────────────────────

fn main() -> io::Result<()> {
    let (tx, rx) = mpsc::channel::<Cmd>();

    // Reader thread — parses stdin lines, sends commands.
    thread::spawn(move || {
        let stdin = stdin().lock();
        for line in stdin.lines() {
            let line = match line {
                Ok(l) => l,
                Err(_) => break,
            };
            let cmd = match line.trim() {
                "dit down" => Some(Cmd::Dit(true)),
                "dit up" => Some(Cmd::Dit(false)),
                "dah down" => Some(Cmd::Dah(true)),
                "dah up" => Some(Cmd::Dah(false)),
                _ => None,
            };
            if let Some(c) = cmd {
                if tx.send(c).is_err() {
                    break;
                }
            }
        }
    });

    let start = Instant::now();
    let mut stdout = stdout().lock();
    let mut dah: DahState = DahState::Idle;

    loop {
        let now = start.elapsed().as_secs_f64();

        // ── timer expiry ─────────────────────────────────────────────
        if let DahState::Debouncing { deadline, pressed } = &dah {
            if now >= *deadline {
                match pressed {
                    true => {
                        // Paddle still held — legitimate long press.
                        dah = DahState::Down;
                    }
                    false => {
                        // Paddle already released — emit dah-up to meet
                        // the minimum duration.
                        writeln!(stdout, "dah up")?;
                        stdout.flush()?;
                        dah = DahState::Idle;
                    }
                }
            }
        }

        // ── incoming commands ────────────────────────────────────────
        match rx.try_recv() {
            Ok(cmd) => match cmd {
                Cmd::Dit(down) => {
                    writeln!(stdout, "dit {}", if down { "down" } else { "up" })?;
                    stdout.flush()?;
                }
                Cmd::Dah(down) => match &dah {
                    DahState::Idle => {
                        if down {
                            // First dah-down — echo immediately, start timer.
                            writeln!(stdout, "dah down")?;
                            stdout.flush()?;
                            dah = DahState::Debouncing {
                                deadline: now + MIN_DAH_DURATION,
                                pressed: true,
                            };
                        }
                        // Spurious dah-up while idle → ignore.
                    }
                    DahState::Debouncing { deadline, .. } => {
                        // Absorb — only remember latest paddle state.
                        dah = DahState::Debouncing {
                            deadline: *deadline,
                            pressed: down,
                        };
                    }
                    DahState::Down => {
                        if !down {
                            writeln!(stdout, "dah up")?;
                            stdout.flush()?;
                            dah = DahState::Idle;
                        }
                        // Redundant dah-down while already down → ignore.
                    }
                },
            },
            Err(TryRecvError::Empty) => {}
            Err(TryRecvError::Disconnected) => break,
        }

        thread::sleep(std::time::Duration::from_micros(100));
    }

    Ok(())
}
