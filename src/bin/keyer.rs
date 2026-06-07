//! Morse keyer binary: reads paddle events from stdin, echoes them and writes
//! keyer events to stdout.
//!
//! ## stdin (one per line)
//!   dit down  /  dit up
//!   dah down  /  dah up
//!
//! ## stdout (one per line)
//! Echoed paddle events:
//!   dit down  /  dit up
//!   dah down  /  dah up
//!
//! Keyer events:
//!   key on    — output key closes (tone starts)
//!   key off   — output key opens  (tone stops)
//!   dit       — dit element recognized
//!   dah       — dah element recognized
//!   char      — character boundary (inter-character silence elapsed)
//!   word      — word boundary     (inter-word silence elapsed)
//!
//! `key on`/`key off` encode the output-envelope timing; `dit`/`dah`/`char`/`word`
//! are semantic labels for downstream decoders.

use std::io::{self, stdin, stdout, BufRead, Write};
use std::sync::mpsc::{self, TryRecvError};
use std::time::Instant;
use std::thread;

use clap::Parser;
use keyer_lib::{KeyEvent, Keyer, KeyerMode};

// ── CLI ────────────────────────────────────────────────────────────

#[derive(Parser)]
#[command(name = "keyer", about = "Morse keyer — stdin paddle events, stdout key events")]
struct Cli {
    /// Words per minute
    #[arg(short, long, default_value = "20")]
    wpm: f64,

    /// Keyer mode: iambic-a, iambic-b, ultimatic, or straight
    #[arg(short = 'm', long, default_value = "iambic-b")]
    mode: String,
}

// ── helpers ────────────────────────────────────────────────────────

enum Cmd {
    Dit(bool),
    Dah(bool),
}

// ── main ───────────────────────────────────────────────────────────

fn main() -> io::Result<()> {
    let cli = Cli::parse();

    let mode = match cli.mode.as_str() {
        "iambic-a" => KeyerMode::IambicA,
        "iambic-b" => KeyerMode::IambicB,
        "ultimatic" => KeyerMode::Ultimatic,
        "straight" => KeyerMode::Straight,
        _ => {
            eprintln!(
                "unknown mode: {} (expected iambic-a, iambic-b, ultimatic, or straight)",
                cli.mode
            );
            std::process::exit(1);
        }
    };

    let (tx, rx) = mpsc::channel::<Cmd>();

    thread::spawn(move || {
        let tx = tx;
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
        // stdin EOF
        drop(tx);
    });

    let mut keyer = Keyer::new(cli.wpm, mode);
    let start = Instant::now();

    let mut stdout = stdout().lock();

    let mut idle_since: Option<f64> = None;

    loop {
        let now = start.elapsed().as_secs_f64();

        match rx.try_recv() {
            Ok(cmd) => {
                match cmd {
                    Cmd::Dit(true) => {
                        writeln!(stdout, "dit down")?;
                        stdout.flush()?;
                        keyer.set_dit(true, now);
                    }
                    Cmd::Dit(false) => {
                        writeln!(stdout, "dit up")?;
                        stdout.flush()?;
                        keyer.set_dit(false, now);
                    }
                    Cmd::Dah(true) => {
                        writeln!(stdout, "dah down")?;
                        stdout.flush()?;
                        keyer.set_dah(true, now);
                    }
                    Cmd::Dah(false) => {
                        writeln!(stdout, "dah up")?;
                        stdout.flush()?;
                        keyer.set_dah(false, now);
                    }
                }
                idle_since = None;
            }
            Err(TryRecvError::Empty) => {}
            Err(TryRecvError::Disconnected) => {
                // Stdin is EOF. This is unexpected in actual use so exit the keyer immediately.
                return Ok(());
            }
        }

        let events = keyer.tick(now);
        let had_events = !events.is_empty();

        for event in events {
            let msg = match event {
                KeyEvent::KeyDown => "key on",
                KeyEvent::KeyUp => "key off",
                KeyEvent::Dit => "dit",
                KeyEvent::Dah => "dah",
                KeyEvent::CharSpace => "char",
                KeyEvent::WordSpace => "word",
            };
            writeln!(stdout, "{msg}")?;
            stdout.flush()?;
        }

        if !had_events {
            if idle_since.is_none() {
                idle_since = Some(now);
            }
        } else {
            idle_since = None;
        }

        thread::sleep(std::time::Duration::from_micros(100));
    }
}
