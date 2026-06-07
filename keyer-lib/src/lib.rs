//! Queue-based keyer supporting Iambic A, Iambic B, Ultimatic, and Straight modes.
//!
//! Architecture follows the queue model from didahdit.com.

use std::collections::VecDeque;

/// Events emitted by the keyer.  Callers drain these from [`Keyer::tick`].
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum KeyEvent {
    KeyOn,
    KeyOff,
    Dit,
    Dah,
    CharSpace,
    WordSpace,
}

/// Keyer mode.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum KeyerMode {
    IambicA,
    IambicB,
    Ultimatic,
    Straight,
}

#[derive(Clone, Copy, PartialEq, Eq)]
pub enum Element {
    Dit,
    Dah,
}

impl Element {
    fn opposite(self) -> Self {
        match self {
            Element::Dit => Element::Dah,
            Element::Dah => Element::Dit,
        }
    }
}

#[derive(Clone, Copy, PartialEq)]
enum PlayState {
    Idle,
    PlayingElement { start: f64, duration: f64 },
    InGap { start: f64 },
}

pub struct Keyer {
    mode: KeyerMode,
    dit_paddle: bool,
    dah_paddle: bool,
    last_pressed: Option<Element>,
    last_queued: Option<Element>,
    squeezed_last_element: bool,
    queue: VecDeque<Element>,
    key_on: bool,
    play_state: PlayState,
    inter_element: f64,
    dit_len: f64,
    dah_len: f64,
    char_deadline: Option<f64>,
    word_deadline: Option<f64>,
    straight_hold_start: f64,
    events: Vec<KeyEvent>,
}

impl Keyer {
    pub fn new(wpm: f64, mode: KeyerMode) -> Self {
        let unit = 1.2 / wpm;
        Self {
            mode,
            dit_paddle: false,
            dah_paddle: false,
            last_pressed: None,
            last_queued: None,
            squeezed_last_element: false,
            queue: VecDeque::new(),
            key_on: false,
            play_state: PlayState::Idle,
            inter_element: unit,
            dit_len: unit,
            dah_len: 3.0 * unit,
            char_deadline: None,
            word_deadline: None,
            straight_hold_start: 0.0,
            events: Vec::new(),
        }
    }

    pub fn is_key_on(&self) -> bool {
        self.key_on
    }

    pub fn set_dit(&mut self, pressed: bool, now: f64) -> bool {
        if self.mode == KeyerMode::Straight {
            let was_any = self.dit_paddle || self.dah_paddle;
            self.dit_paddle = pressed;
            let is_any = self.dit_paddle || self.dah_paddle;
            self.straight_key_update(was_any, is_any, now);
            return true;
        }
        self.dit_paddle = pressed;
        if pressed {
            self.last_pressed = Some(Element::Dit);
            self.enqueue(Element::Dit);
            return true;
        }
        false
    }

    pub fn set_dah(&mut self, pressed: bool, now: f64) -> bool {
        if self.mode == KeyerMode::Straight {
            let was_any = self.dit_paddle || self.dah_paddle;
            self.dah_paddle = pressed;
            let is_any = self.dit_paddle || self.dah_paddle;
            self.straight_key_update(was_any, is_any, now);
            return true;
        }
        self.dah_paddle = pressed;
        if pressed {
            self.last_pressed = Some(Element::Dah);
            self.enqueue(Element::Dah);
            return true;
        }
        false
    }

    fn straight_key_update(&mut self, was_any: bool, is_any: bool, now: f64) {
        if is_any && !was_any {
            self.char_deadline = None;
            self.word_deadline = None;
            self.straight_hold_start = now;
            self.key_on = true;
            self.events.push(KeyEvent::KeyOn);
        } else if !is_any && was_any {
            self.key_on = false;
            self.events.push(KeyEvent::KeyOff);

            let elapsed = now - self.straight_hold_start;
            let threshold = self.dah_len * 2.0 / 3.0;
            let element = if elapsed >= threshold {
                Element::Dah
            } else {
                Element::Dit
            };
            self.events.push(match element {
                Element::Dit => KeyEvent::Dit,
                Element::Dah => KeyEvent::Dah,
            });

            self.char_deadline = Some(now + 3.0 * self.inter_element);
            self.word_deadline = Some(now + 7.0 * self.inter_element);
        }
    }

    /// Advance the state machine. Returns all events that occurred since the
    /// last call.
    pub fn tick(&mut self, now: f64) -> Vec<KeyEvent> {
        if self.mode == KeyerMode::Straight {
            if let Some(dl) = self.char_deadline {
                if now >= dl {
                    self.events.push(KeyEvent::CharSpace);
                    self.char_deadline = None;
                }
            }
            if let Some(dl) = self.word_deadline {
                if now >= dl {
                    self.events.push(KeyEvent::WordSpace);
                    self.word_deadline = None;
                    self.char_deadline = None;
                }
            }
            return std::mem::take(&mut self.events);
        }

        let prev = self.key_on;

        match self.play_state {
            PlayState::Idle => {
                self.serve_queue_or_decide(now);
            }
            PlayState::PlayingElement { start, duration } => {
                if now - start >= duration {
                    self.key_on = false;
                    self.play_state = PlayState::InGap { start: now };
                }
            }
            PlayState::InGap { start } => {
                if now - start >= self.inter_element {
                    self.serve_queue_or_decide(now);
                }
            }
        }

        if self.key_on != prev {
            self.events.push(if self.key_on {
                KeyEvent::KeyOn
            } else {
                KeyEvent::KeyOff
            });
        }

        if let Some(dl) = self.char_deadline {
            if now >= dl {
                self.events.push(KeyEvent::CharSpace);
                self.char_deadline = None;
            }
        }
        if let Some(dl) = self.word_deadline {
            if now >= dl {
                self.events.push(KeyEvent::WordSpace);
                self.word_deadline = None;
                self.char_deadline = None; // word supersedes char
            }
        }

        std::mem::take(&mut self.events)
    }

    fn serve_queue_or_decide(&mut self, now: f64) {
        if let Some(element) = self.queue.pop_front() {
            self.start_element(element, now);
            return;
        }

        let squeezing = self.dit_paddle && self.dah_paddle;

        if squeezing {
            match self.mode {
                KeyerMode::Ultimatic => {
                    if let Some(element) = self.last_pressed {
                        self.enqueue(element);
                    }
                }
                KeyerMode::IambicA | KeyerMode::IambicB => {
                    if let Some(ref last) = self.last_queued {
                        self.enqueue(last.opposite());
                    }
                }
                KeyerMode::Straight => unreachable!(),
            }
        } else if self.dit_paddle {
            self.enqueue(Element::Dit);
        } else if self.dah_paddle {
            self.enqueue(Element::Dah);
        } else if self.mode == KeyerMode::IambicB && self.squeezed_last_element {
            if let Some(ref last) = self.last_queued {
                self.enqueue(last.opposite());
            }
            self.squeezed_last_element = false;
        } else {
            return;
        }

        if let Some(element) = self.queue.pop_front() {
            self.start_element(element, now);
        }
    }

    fn enqueue(&mut self, element: Element) {
        self.queue.push_back(element);
        self.last_queued = Some(element);
        self.events.push(match element {
            Element::Dit => KeyEvent::Dit,
            Element::Dah => KeyEvent::Dah,
        });
    }

    fn start_element(&mut self, element: Element, now: f64) {
        let duration = match element {
            Element::Dit => self.dit_len,
            Element::Dah => self.dah_len,
        };
        self.key_on = true;
        self.play_state = PlayState::PlayingElement {
            start: now,
            duration,
        };
        self.last_queued = Some(element);
        self.squeezed_last_element = self.dit_paddle && self.dah_paddle;

        let end = now + duration;
        self.char_deadline = Some(end + 3.0 * self.inter_element);
        self.word_deadline = Some(end + 7.0 * self.inter_element);
    }
}
